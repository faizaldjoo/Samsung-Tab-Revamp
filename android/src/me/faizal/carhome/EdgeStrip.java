package me.faizal.carhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Volume and brightness over full-screen apps (launcher-prototype/overlay.html, option B, chosen 2026-10-28).
 * Two accessibility overlay windows from Assist (no "draw over other apps" permission needed):
 *  - the handle: a thin pill mid-height on the sidebar's edge; tap it to open the strip (dragging it inward works only
 *    from inside the handle: a drag from the very edge is One UI's Back gesture, and One UI ignores
 *    setSystemGestureExclusionRects for overlay windows, checked 2026-10-28);
 *  - the strip: full height, pin on top, then volume and brightness sliders. It hides 4 s after the last touch or on
 *    a tap outside it, unless pinned (kept across restarts).
 * Shown only while another app is in front (Assist.onAccessibilityEvent), never over Car Home or the One UI launcher.
 * Drawn on a Canvas, still; it redraws only while being touched.
 */
final class EdgeStrip {
    private static final int HIDE_MS = 4000;
    private static final int RAISED = 0xff2a2f37, TEXT = 0xfff1f3f5, MUTED = 0xffaab1bb, INK = 0xff2a2e35, BG = 0xe6171a1f;
    private static final char PIN = '', SPEAKER = '', SUN = '';

    private final Context ctx;
    private final WindowManager wm;
    private final AudioManager am;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float d;
    private final Typeface icons;
    private final HandleView handle;
    private final StripView strip;
    private boolean shown, open, pinned, right = true, askedWrite;

    EdgeStrip(Context c) {
        ctx = c;
        wm = c.getSystemService(WindowManager.class);
        am = c.getSystemService(AudioManager.class);
        prefs = c.getSharedPreferences("carhome", Context.MODE_PRIVATE);
        d = c.getResources().getDisplayMetrics().density;
        icons = Typeface.createFromAsset(c.getAssets(), "fonts/phosphor-fill.ttf");
        pinned = prefs.getBoolean("stripPinned", false);
        handle = new HandleView(c);
        strip = new StripView(c);
    }

    private int dp(float v) { return Math.round(v * d); }

    /** Another app is in front (true) or Car Home / the launcher (false). */
    void show(boolean on) {
        if (on == shown) return;
        shown = on;
        if (on) {
            right = !"left".equals(prefs.getString("side", "right"));
            if (pinned) openStrip(); else add(handle, dp(44), dp(160), Gravity.CENTER_VERTICAL);
        } else {
            remove(handle);
            remove(strip);
            open = false;
            main.removeCallbacks(autoHide);
        }
    }

    void destroy() { show(false); }

    private void openStrip() {
        remove(handle);
        if (!open) {
            open = true;
            strip.read();
            add(strip, dp(116), WindowManager.LayoutParams.MATCH_PARENT, Gravity.TOP);
        }
        touched();
    }

    private void closeStrip() {
        if (!open || pinned) return;
        open = false;
        remove(strip);
        if (shown) add(handle, dp(44), dp(160), Gravity.CENTER_VERTICAL);
    }

    private final Runnable autoHide = this::closeStrip;

    private void touched() {
        main.removeCallbacks(autoHide);
        if (!pinned) main.postDelayed(autoHide, HIDE_MS);
    }

    private void add(View v, int w, int h, int vertical) {
        if (v.isAttachedToWindow()) return;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.gravity = (right ? Gravity.RIGHT : Gravity.LEFT) | vertical;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try { wm.addView(v, lp); } catch (Exception ignored) { }
    }

    private void remove(View v) {
        if (v.isAttachedToWindow()) try { wm.removeView(v); } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- the handle

    private final class HandleView extends View {
        private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float downX = Float.NaN;

        HandleView(Context c) {
            super(c);
            pill.setColor(0xb3ffffff);
            edge.setColor(0x59000000);
        }


        @Override
        protected void onDraw(Canvas c) {
            float w = dp(10), h = dp(140), x = right ? getWidth() - dp(6) - w : dp(6), y = (getHeight() - h) / 2;
            r.set(x - 1, y - 1, x + w + 1, y + h + 1);
            c.drawRoundRect(r, w, w, edge);
            r.set(x, y, x + w, y + h);
            c.drawRoundRect(r, w / 2, w / 2, pill);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downX = e.getRawX(); return true;
                case MotionEvent.ACTION_MOVE:
                    if (!Float.isNaN(downX) && (right ? downX - e.getRawX() : e.getRawX() - downX) > dp(24)) { downX = Float.NaN; openStrip(); }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!Float.isNaN(downX)) openStrip();          // a tap
                    downX = Float.NaN;
                    return true;
            }
            return false;
        }
    }

    // ---------------------------------------------------------------- the strip

    private final class StripView extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF(), pin = new RectF(), vol = new RectF(), bri = new RectF(), tmp = new RectF();
        private final Path clip = new Path();
        private int volume, brightness;         // 0-100
        private RectF dragging;
        private boolean pinDown;

        StripView(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(1, dp(1)));
            line.setColor(0x1fffffff);
            glyph.setTypeface(icons);
            glyph.setTextAlign(Paint.Align.CENTER);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(dp(15));
            label.setColor(MUTED);
            label.setFakeBoldText(true);
        }

        /** Current values (the volume keys or Car Home may have changed them). */
        void read() {
            volume = Math.round(100f * am.getStreamVolume(AudioManager.STREAM_MUSIC) / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            brightness = Math.round(Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) / 2.55f);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float m = dp(10), sw = dp(96), x = right ? w - m - sw : m;
            box.set(x, m, x + sw, h - m);
            float p = dp(10), labelH = dp(15) + dp(6), gap = dp(12);
            pin.set(box.left + p, box.top + p, box.right - p, box.top + p + dp(44));
            float top = pin.bottom + gap, each = (box.bottom - dp(12) - top - gap) / 2;
            vol.set(box.left + p, top, box.right - p, top + each - labelH);
            bri.set(box.left + p, top + each + gap, box.right - p, top + 2 * each + gap - labelH);
        }

        @Override
        protected void onDraw(Canvas c) {
            bg.setColor(BG);
            c.drawRoundRect(box, dp(26), dp(26), bg);
            c.drawRoundRect(box, dp(26), dp(26), line);
            bg.setColor(pinned ? TEXT : 0xff20242b);
            c.drawRoundRect(pin, dp(14), dp(14), bg);
            glyph.setTextSize(dp(22));
            glyph.setColor(pinned ? 0xff0e1013 : MUTED);
            c.drawText(String.valueOf(PIN), pin.centerX(), pin.centerY() + dp(8), glyph);
            track(c, vol, volume, SPEAKER);
            track(c, bri, brightness, SUN);
        }

        private void track(Canvas c, RectF t, int v, char icon) {
            float r = dp(20);
            bg.setColor(RAISED);
            c.drawRoundRect(t, r, r, bg);
            c.save();
            clip.reset();
            clip.addRoundRect(t, r, r, Path.Direction.CW);
            c.clipPath(clip);
            fill.setColor(TEXT);
            tmp.set(t.left, t.bottom - t.height() * v / 100f, t.right, t.bottom);
            c.drawRect(tmp, fill);
            c.restore();
            glyph.setTextSize(dp(28));
            glyph.setColor(v < 18 ? TEXT : INK);
            c.drawText(String.valueOf(icon), t.centerX(), t.bottom - dp(14), glyph);
            c.drawText(v + "%", t.centerX(), t.bottom + dp(6) + dp(14), label);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_OUTSIDE:
                    closeStrip();                       // a tap on the app beside it
                    return true;
                case MotionEvent.ACTION_DOWN:
                    if (!box.contains(x, y)) { closeStrip(); return false; }
                    touched();
                    if (pin.contains(x, y)) { pinDown = true; return true; }
                    dragging = vol.top <= y && y <= vol.bottom + dp(21) ? vol : bri.top <= y && y <= bri.bottom + dp(21) ? bri : null;
                    if (dragging != null) set(y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    touched();
                    if (dragging != null) set(y);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (pinDown && pin.contains(x, y)) {
                        pinned = !pinned;
                        prefs.edit().putBoolean("stripPinned", pinned).apply();
                        invalidate();
                    }
                    pinDown = false;
                    dragging = null;
                    touched();
                    return true;
            }
            return false;
        }

        private void set(float y) {
            int v = Math.max(0, Math.min(100, Math.round((dragging.bottom - y) / dragging.height() * 100)));
            if (dragging == vol) {
                if (v == volume) return;
                volume = v;
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), step = Math.round(v / 100f * max);
                if (step != am.getStreamVolume(AudioManager.STREAM_MUSIC)) am.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0);
            } else {
                if (v == brightness) return;
                if (!Settings.System.canWrite(ctx)) {
                    // "Modify system settings" for Car Home: asked once per run, as on the home screen.
                    if (!askedWrite) {
                        askedWrite = true;
                        ctx.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.getPackageName()))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                    return;
                }
                brightness = v;
                Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, Math.round(v * 2.55f)));
            }
            invalidate();
        }
    }
}
