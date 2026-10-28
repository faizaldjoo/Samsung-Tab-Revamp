package me.faizal.carhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Settings > Accessibility > Car Home. Android lets only an accessibility service open the recent apps screen, and
 * One UI gives apps no way to start split screen (checked 2026-10-28 on One UI 6.1: FLAG_ACTIVITY_LAUNCH_ADJACENT and
 * windowing mode multi-window open the app full screen; GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN returns false). So split
 * taps through One UI's own route, as you would by hand:
 *   first app full screen -> recent apps -> its icon ("Advanced options, <app>") -> "Open in split screen view"
 *   -> the "Select app" picker in the other half -> the second app (from All apps if it isn't listed).
 * Launching the second app directly at that point cancels the split, so it is tapped in the picker.
 * Activities started from here are allowed while Car Home is in the background (bound service).
 */
public class Assist extends AccessibilityService {
    static Assist current;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int run;                       // the split in progress; a new one cancels the old one's pending steps

    private EdgeStrip strip;

    @Override
    protected void onServiceConnected() {
        current = this;
        strip = new EdgeStrip(this);
        IntentFilter f = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(power, f);
    }

    /**
     * The charger is the car's ignition. This service keeps running in the background and while the screen is off.
     * Charger out = the car is off (#3): pause the music, show Car Home's screensaver whatever app is in front, and put
     * the tablet to sleep 2 minutes later unless the charger comes back.
     * Charger in = the car has started (#4): with the screen off, wake it and bring up Car Home's home screen; either
     * way, resume the music that played last, once the car's Bluetooth has had a moment to connect.
     */
    static final long SLEEP_AFTER_MS = 120_000, RESUME_AFTER_MS = 3_000;

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_POWER_DISCONNECTED.equals(i.getAction())) unplugged();
            else pluggedIn();
        }
    };

    private void unplugged() {
        main.removeCallbacks(resume);
        if (getSystemService(AudioManager.class).isMusicActive()) mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        startActivity(new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_SAVER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main.removeCallbacks(sleep);
        main.postDelayed(sleep, SLEEP_AFTER_MS);
    }

    private void pluggedIn() {
        main.removeCallbacks(sleep);
        PowerManager pm = getSystemService(PowerManager.class);
        if (!pm.isInteractive()) {
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "carhome:carstart");
            wl.acquire(3000);
            startActivity(new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        main.removeCallbacks(resume);
        main.postDelayed(resume, RESUME_AFTER_MS);
    }

    /** Still unplugged after 2 minutes: screen off (this tablet has no lock screen, so "lock" just means sleep). */
    private final Runnable sleep = () -> {
        if (!plugged()) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    };

    /** Play: Android hands the key to the app that played last (YT Music, Spotify, radio) and restarts it if needed. */
    private final Runnable resume = () -> {
        if (plugged() && !getSystemService(AudioManager.class).isMusicActive()) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
    };

    private boolean plugged() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private void mediaKey(int code) {
        AudioManager am = getSystemService(AudioManager.class);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    @Override
    public boolean onUnbind(Intent i) {
        current = null;
        main.removeCallbacks(sleep);
        main.removeCallbacks(resume);
        if (strip != null) strip.destroy();
        try { unregisterReceiver(power); } catch (Exception ignored) { }
        return super.onUnbind(i);
    }

    /** Window changes: the volume / brightness strip shows while another app is in front (not Car Home or One UI's launcher). */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        // Window lists settle a moment after the event (an app coming to the front reported none at first): check
        // once things are quiet.
        main.removeCallbacks(checkFront);
        main.postDelayed(checkFront, 300);
    }

    private final Runnable checkFront = this::checkFront;

    private void checkFront() {
        if (strip == null) return;
        // The front app window's package. Some apps (Waze) give no window content to accessibility: their root is
        // null, which still means "another app".
        String top = null;
        boolean app = false;
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {    // top first
                if (w.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                app = true;
                AccessibilityNodeInfo r = w.getRoot();
                top = r == null ? "" : str(r.getPackageName());
                break;
            }
        } catch (Exception ignored) { }
        if (!app) return;                 // only the keyboard, the shade or an overlay changed
        strip.show(!top.equals(getPackageName()) && !top.equals(LAUNCHER));
    }
    @Override public void onInterrupt() { }

    static boolean on() { return current != null; }

    void recents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }

    interface Done { void done(boolean ok); }

    /** Recent apps > Close all; done(false) when there was nothing to close (no button), after going back home. */
    void closeAll(Done done) {
        final int me = ++run;
        performGlobalAction(GLOBAL_ACTION_RECENTS);
        step(me, n -> "Close all".equals(str(n.getText())), () -> done.done(true),
                () -> { performGlobalAction(GLOBAL_ACTION_HOME); done.done(false); });
    }

    /** Split screen with app A on the left and app B on the right; done(false) if a step was not found in time. */
    void split(Intent a, String labelA, String labelB, Done finish) {
        final int me = ++run;
        // A step not found: back home, so One UI's half-done split or app list doesn't stay on screen.
        Done done = ok -> { if (!ok) performGlobalAction(GLOBAL_ACTION_HOME); finish.done(ok); };
        startActivity(a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main.postDelayed(() -> {
            if (me != run) return;
            performGlobalAction(GLOBAL_ACTION_RECENTS);
            // Each step waits for its target to appear (up to 3 s), then taps it.
            step(me, n -> n.getContentDescription() != null && n.getContentDescription().toString().startsWith("Advanced options")
                    && n.getContentDescription().toString().contains(labelA), () ->
              step(me, n -> n.getText() != null && n.getText().toString().toLowerCase().contains("split"), () ->
                step(me, n -> labelB.equals(str(n.getText())) || labelB.equals(str(n.getContentDescription())), () -> done.done(true),
                  // Not among recent / most used apps: open the picker's All apps list and look there.
                  () -> step(me, n -> "All apps Button".equals(str(n.getContentDescription())), () ->
                          find(me, labelB, 0, 0, done), () -> done.done(false))),
                () -> done.done(false)),
              () -> done.done(false));
        }, 1200);
    }

    private interface Match { boolean test(AccessibilityNodeInfo n); }

    private void step(int me, Match m, Runnable then, Runnable fail) { poll(me, m, then, fail, 0); }

    private void poll(int me, Match m, Runnable then, Runnable fail, int tries) {
        main.postDelayed(() -> {
            if (me != run) return;
            AccessibilityNodeInfo hit = searchAll(m);
            if (hit == null) {
                if (tries < 20) poll(me, m, then, fail, tries + 1);
                else { android.util.Log.d("CarHome", "split step not found"); fail.run(); }
                return;
            }
            android.util.Log.d("CarHome", "split tap: " + str(hit.getText()) + " / " + str(hit.getContentDescription()));
            click(hit);
            then.run();
        }, 150);
    }

    /**
     * All apps in the picker: look for the label, scrolling down page by page. The list takes a moment to open (it was
     * missed at 600 ms once, 2026-10-28), so with nothing to scroll yet it looks again, for up to 3 s.
     */
    private void find(int me, String label, int page, int waits, Done done) {
        main.postDelayed(() -> {
            if (me != run) return;
            AccessibilityNodeInfo hit = searchAll(n -> label.equals(str(n.getText())) || label.equals(str(n.getContentDescription())));
            if (hit != null) { android.util.Log.d("CarHome", "split tap: " + label); click(hit); done.done(true); return; }
            AccessibilityNodeInfo list = searchAll(AccessibilityNodeInfo::isScrollable);
            if (page < 8 && list != null && list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) find(me, label, page + 1, 0, done);
            else if (waits < 10) find(me, label, page, waits + 1, done);
            else done.done(false);
        }, page == 0 && waits == 0 ? 600 : 300);
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString(); }

    static final String LAUNCHER = "com.sec.android.app.launcher";

    /** The One UI launcher's windows on screen (the split picker is not always the active one). */
    private AccessibilityNodeInfo searchAll(Match m) {
        for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || !LAUNCHER.equals(str(root.getPackageName()))) continue;   // walking other apps' trees is slow
            AccessibilityNodeInfo hit = search(root, m);
            if (hit != null) return hit;
        }
        return null;
    }

    private static AccessibilityNodeInfo search(AccessibilityNodeInfo n, Match m) {
        if (n == null) return null;
        if (m.test(n) && n.isVisibleToUser()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo hit = search(n.getChild(i), m);
            if (hit != null) return hit;
        }
        return null;
    }

    /** Click the node, or the nearest clickable parent (labels often sit inside the clickable tile). */
    private static void click(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo p = n; p != null; p = p.getParent()) {
            if (p.isClickable()) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); return; }
        }
    }
}
