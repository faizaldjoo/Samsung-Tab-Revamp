package me.faizal.carhome;

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

import java.util.HashSet;
import java.util.Set;

/**
 * The charger is the car's ignition (#3 #4). Charger out = the car is off: pause the music and put the tablet to sleep
 * at once (user, 2026-10-29). Charger in = the car has started: with the screen off, wake it and bring up Car Home's
 * home screen; either way, resume the music that played last.
 *
 * Runs from whichever background service Android keeps bound: Assist (accessibility) or MediaListener (notification
 * access). Accessibility goes off after each install (found 2026-10-29: on a drive nothing happened on unplug or plug
 * in), notification access stays on, so pause and resume no longer depend on it. Only the sleep itself needs Assist
 * (GLOBAL_ACTION_LOCK_SCREEN); without it the screen times out as usual.
 */
final class Power {
    /** The wait lets the playing app take the pause key. Resume: once at 3 s, again at 10 s if nothing plays by then
     *  (the car's Bluetooth connecting a few seconds in can stop the first one). */
    static final long SLEEP_AFTER_MS = 500, RESUME_AFTER_MS = 3_000, RESUME_AGAIN_MS = 10_000;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Set<Object> owners = new HashSet<>();
    private static Context app;

    static void attach(Object owner, Context c) {
        if (!owners.add(owner) || owners.size() > 1) return;
        app = c.getApplicationContext();
        IntentFilter f = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        app.registerReceiver(power, f);
    }

    static void detach(Object owner) {
        if (!owners.remove(owner) || !owners.isEmpty()) return;
        main.removeCallbacksAndMessages(null);
        try { app.unregisterReceiver(power); } catch (Exception ignored) { }
    }

    private static final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_POWER_DISCONNECTED.equals(i.getAction())) unplugged();
            else pluggedIn();
        }
    };

    private static void unplugged() {
        main.removeCallbacks(resume);
        if (app.getSystemService(AudioManager.class).isMusicActive()) mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        main.removeCallbacks(sleep);
        main.postDelayed(sleep, SLEEP_AFTER_MS);
    }

    private static void pluggedIn() {
        main.removeCallbacks(sleep);
        PowerManager pm = app.getSystemService(PowerManager.class);
        if (!pm.isInteractive()) {
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "carhome:carstart");
            wl.acquire(3000);
            // As the Home button does (HOME category): an explicit start of MainActivity made a second copy of Car Home
            // in its own task, next to the home one, both running (found 2026-10-29). Android allows the start from
            // the background only for Assist; without it the screen still wakes, on whatever was showing.
            Context from = Assist.current != null ? Assist.current : app;
            try {
                from.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
        main.removeCallbacks(resume);
        main.postDelayed(resume, RESUME_AFTER_MS);
        main.postDelayed(resume, RESUME_AGAIN_MS);
    }

    /** Still unplugged: screen off (this tablet has no lock screen, so "lock" just means sleep). */
    private static final Runnable sleep = () -> {
        if (!plugged() && Assist.current != null) Assist.current.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    };

    /** Play: Android hands the key to the app that played last (YT Music, Spotify, radio) and restarts it if needed. */
    private static final Runnable resume = () -> {
        if (plugged() && !app.getSystemService(AudioManager.class).isMusicActive()) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
    };

    private static boolean plugged() {
        Intent b = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private static void mediaKey(int code) {
        AudioManager am = app.getSystemService(AudioManager.class);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private Power() { }
}
