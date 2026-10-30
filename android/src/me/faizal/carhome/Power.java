package me.faizal.carhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.bluetooth.BluetoothAdapter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
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
    /** Car audio not back this long after plug-in: open One UI's Bluetooth pop-up (paired devices), one tap to
     *  reconnect. Apps can't connect a Bluetooth device themselves (system apps only). */
    static final long BT_CHECK_MS = 15_000;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Set<Object> owners = new HashSet<>();
    private static Context app;

    static void attach(Object owner, Context c) {
        if (!owners.add(owner)) return;
        DriveLog.add(c, "power: watching from " + owner.getClass().getSimpleName());
        if (owners.size() > 1) return;
        app = c.getApplicationContext();
        IntentFilter f = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        app.registerReceiver(power, f);
        f = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        app.registerReceiver(btState, f);
        app.getSystemService(AudioManager.class).registerAudioDeviceCallback(btAudio, main);
    }

    static void detach(Object owner) {
        if (!owners.remove(owner)) return;
        if (app != null) DriveLog.add(app, "power: " + owner.getClass().getSimpleName() + " stopped" + (owners.isEmpty() ? ", nothing watches the charger now" : ""));
        if (!owners.isEmpty()) return;
        main.removeCallbacksAndMessages(null);
        try { app.unregisterReceiver(power); } catch (Exception ignored) { }
        try { app.unregisterReceiver(btState); } catch (Exception ignored) { }
        app.getSystemService(AudioManager.class).unregisterAudioDeviceCallback(btAudio);
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
        main.removeCallbacks(btCheck);
        boolean music = app.getSystemService(AudioManager.class).isMusicActive();
        DriveLog.add(app, "UNPLUGGED (car off): " + (music ? "music playing, pause sent" : "no music playing")
                + (Assist.current != null ? ", sleep in 0.5 s" : ", accessibility OFF so no sleep (screen times out)"));
        if (music) mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        main.removeCallbacks(sleep);
        main.postDelayed(sleep, SLEEP_AFTER_MS);
    }

    private static void pluggedIn() {
        main.removeCallbacks(sleep);
        PowerManager pm = app.getSystemService(PowerManager.class);
        DriveLog.add(app, "PLUGGED IN (car on): screen " + (pm.isInteractive() ? "was on" : "was off, waking")
                + (Assist.current != null ? "" : ", accessibility OFF so home can't be brought up from the background"));
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
            } catch (Exception e) { DriveLog.add(app, "plug in: couldn't open home: " + e); }
        }
        main.removeCallbacks(resume);
        main.postDelayed(resume, RESUME_AFTER_MS);
        main.postDelayed(resume, RESUME_AGAIN_MS);
        main.removeCallbacks(btCheck);
        main.postDelayed(btCheck, BT_CHECK_MS);
    }

    // ---- Bluetooth (2026-10-30, user: after unplugging, the car's Bluetooth had to be reconnected by hand). nDashMo
    // never switches Bluetooth; these only write the drive log, so a drive shows whether Bluetooth went off or the car
    // didn't reconnect. Car audio = a Bluetooth A2DP output (AudioDeviceCallback needs no permission).

    private static String btAudioName() {
        for (AudioDeviceInfo d : app.getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return String.valueOf(d.getProductName());
        }
        return null;
    }

    private static final AudioDeviceCallback btAudio = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) { log(added, "connected"); }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { log(removed, "disconnected"); }

        private void log(AudioDeviceInfo[] ds, String what) {
            for (AudioDeviceInfo d : ds) {
                if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) DriveLog.add(app, "Bluetooth audio " + what + ": " + d.getProductName());
            }
        }
    };

    private static final BroadcastReceiver btState = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            if (s == BluetoothAdapter.STATE_ON) DriveLog.add(app, "Bluetooth switched ON");
            else if (s == BluetoothAdapter.STATE_OFF) DriveLog.add(app, "Bluetooth switched OFF");
        }
    };

    /** 15 s after plug-in: the car's audio should be back. If not, the Bluetooth pop-up (or settings) for one tap. */
    private static final Runnable btCheck = () -> {
        if (!plugged()) return;
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        String car = btAudioName();
        if (car != null) { DriveLog.add(app, "Bluetooth audio OK after plug-in: " + car); return; }
        DriveLog.add(app, "Bluetooth audio NOT back 15 s after plug-in (Bluetooth " + (bt != null && bt.isEnabled() ? "on" : "OFF") + "), opening the Bluetooth pop-up");
        Intent i = new Intent("com.samsung.settings.bluetooth.scandialog.LAUNCH");
        if (i.resolveActivity(app.getPackageManager()) == null) i = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        Context from = Assist.current != null ? Assist.current : app;
        try { from.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { DriveLog.add(app, "couldn't open the Bluetooth pop-up: " + e); }
    };

    /** Still unplugged: screen off (this tablet has no lock screen, so "lock" just means sleep). */
    private static final Runnable sleep = () -> {
        if (plugged()) { DriveLog.add(app, "sleep skipped: power back already"); return; }
        if (Assist.current == null) return;
        boolean ok = Assist.current.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        DriveLog.add(app, ok ? "screen off (sleep)" : "sleep FAILED (lock screen action refused)");
    };

    /** Play: Android hands the key to the app that played last (YT Music, Spotify, radio) and restarts it if needed. */
    private static final Runnable resume = () -> {
        if (!plugged()) { DriveLog.add(app, "resume skipped: unplugged again"); return; }
        if (app.getSystemService(AudioManager.class).isMusicActive()) { DriveLog.add(app, "resume: music already playing"); return; }
        mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        DriveLog.add(app, "resume: play sent");
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
