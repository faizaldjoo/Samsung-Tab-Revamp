package me.faizal.carnative;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * Sizes, colours, fonts and small view helpers. Sizes are in dp and match lite.html's CSS px one to one (the WebView
 * laid lite.html out in dp too), so the native screens have the same proportions.
 * Text ignores the system font size (COMPLEX_UNIT_DIP): the layout is fixed, as in the pages.
 */
final class Ui {
    private Ui() { }

    static float density = 1;

    static int dp(float v) { return Math.round(v * density); }

    // Palette: lite.html's :root, and body.night for Night.
    static int BG, PANEL, RAISED, LINE, TEXT, MUTED, FAINT;
    static final int ACCENT = 0xff5aa7ff, WARN = 0xffff7a6b, OK = 0xff34c759;

    static void palette(boolean night) {
        if (night) {
            BG = 0xff08090b; PANEL = 0xff101216; RAISED = 0xff171a1f; LINE = 0xff1f2329; TEXT = 0xffd9dde2; MUTED = 0xff9098a2;
        } else {
            BG = 0xff0e1013; PANEL = 0xff171a1f; RAISED = 0xff20242b; LINE = 0xff2a2f37; TEXT = 0xfff1f3f5; MUTED = 0xffaab1bb;
        }
        FAINT = 0xff7d8590;
    }

    // ---- fonts: Geist is one variable font (weight axis 100-900); one Typeface per weight, made once.

    private static final SparseArray<Typeface> geist = new SparseArray<>();
    static Typeface icons, clock;

    static void fonts(Context c) {
        icons = Typeface.createFromAsset(c.getAssets(), "fonts/phosphor-fill.ttf");
        clock = Typeface.createFromAsset(c.getAssets(), "fonts/sofia-700.ttf");
        for (int w : new int[]{300, 400, 500, 600, 700}) {
            geist.put(w, new Typeface.Builder(c.getAssets(), "fonts/geist.ttf").setFontVariationSettings("'wght' " + w).build());
        }
    }

    static Typeface font(int weight) {
        Typeface t = geist.get(weight);
        return t != null ? t : geist.get(400);
    }

    // ---- Phosphor Fill code points (vendor/phosphor/fill/style.css)

    static final char SKIP_BACK = '', SKIP_FWD = '', PLAY = '', PAUSE = '', WIFI = '',
            BT = '', BT_OFF = '', CELL = '', CELL_OFF = '', BROADCAST = '', MOON = '',
            SUN = '', SUN_DIM = '', SPEAKER = '', GEAR = '', EYES = '', DOTS = '',
            HOUSE = '', CAR = '', PIN = '', NAV_ARROW = '', OBD = '', PLUGS = '',
            RESET = '', COMPASS = '', PATH = '', TIMER = '', GAUGE = '', THERMO = '',
            THERMO_HOT = '', BAT_CHARGING = '', BAT_HIGH = '', MUSIC = '', LOCK = '',
            TURN_LEFT = '', TURN_RIGHT = '', U_TURN = '', ROUNDABOUT = '', FLAG = '';

    // ---- views

    static TextView text(Context c, float size, int weight, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        t.setTypeface(font(weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    static TextView num(Context c, float size, int weight, int color) {
        TextView t = text(c, size, weight, color);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    static TextView icon(Context c, char glyph, float size, int color) {
        TextView t = new TextView(c);
        t.setTypeface(icons);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        t.setText(String.valueOf(glyph));
        return t;
    }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    static GradientDrawable panel(float radiusDp) {
        GradientDrawable d = round(PANEL, radiusDp);
        d.setStroke(Math.max(1, dp(1)), LINE);
        return d;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** lite.html's .press:active: dim to 60% while pressed. No animation. */
    static <T extends View> T press(T v) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) view.setAlpha(0.6f);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) view.setAlpha(1f);
            return false;
        });
        return v;
    }

    static int alpha(int color, float a) { return (color & 0xffffff) | (Math.round(a * 255) << 24); }

    static final int WHITE = Color.WHITE;
}
