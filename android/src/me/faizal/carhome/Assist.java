package me.faizal.carhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
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
        Power.attach(this, this);
    }

    @Override
    public boolean onUnbind(Intent i) {
        current = null;
        Power.detach(this);
        if (strip != null) strip.destroy();
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
