package me.faizal.carhome;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * window.Android in the pages. Calls from JS arrive on a binder thread, so anything touching views or starting
 * activities is posted to the main thread. Pushes go the other way as host.on(type, data) in the page:
 * battery, net, gps, place, media, home.
 */
public class Bridge {
    private final MainActivity act;
    private final WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final PackageManager pm;
    private boolean started, askedWriteSettings;

    // Last values, re-sent when a page calls ready() (a page load starts from nothing).
    private JSONObject battery, net, gps, place, media;
    private static JSONObject navInfo;          // shared with MediaListener, which runs as its own service
    private static Bridge current;

    /** Navigation notification from MediaListener (Waze / Google Maps); empty = navigation ended. */
    static void nav(JSONObject o) {
        navInfo = o.length() == 0 ? null : o;
        Bridge b = current;
        if (b != null) b.push("nav", o);
    }

    Bridge(MainActivity act, WebView web) {
        this.act = act;
        this.web = web;
        this.prefs = act.getSharedPreferences("carhome", Context.MODE_PRIVATE);
        this.pm = act.getPackageManager();
    }

    // ---------------------------------------------------------------- lifecycle

    void start() {
        current = this;
        if (started) return;
        started = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        act.registerReceiver(receiver, f);
        ConnectivityManager cm = act.getSystemService(ConnectivityManager.class);
        cm.registerDefaultNetworkCallback(netCallback);
        startLocation();
        startMedia();
    }

    void stop() {
        if (!started) return;
        started = false;
        try { act.unregisterReceiver(receiver); } catch (Exception ignored) { }
        try { act.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback); } catch (Exception ignored) { }
        try { act.getSystemService(LocationManager.class).removeUpdates(locationListener); } catch (Exception ignored) { }
        stopMedia();
    }

    void home() { push("home", new JSONObject()); }


    /** Run JS host.on(type, data) on the main thread. */
    private void push(String type, JSONObject data) {
        final String js = "window.host&&host.on(" + JSONObject.quote(type) + "," + data + ")";
        main.post(() -> web.evaluateJavascript(js, null));
    }

    // ---------------------------------------------------------------- page calls

    /** The page has loaded: send everything we know so far. */
    @JavascriptInterface
    public void ready() {
        if (battery != null) push("battery", battery);
        pushNet();
        if (gps != null) push("gps", gps);
        if (place != null) push("place", place);
        if (media != null) push("media", media); else push("media", new JSONObject());
        push("nav", navInfo != null ? navInfo : new JSONObject());
    }

    /** Time of the last unplug (ms since 1970, Power.java), 0 if none yet. The trip resets if it is newer than the trip. */
    @JavascriptInterface
    public long lastUnplug() {
        return prefs.getLong("unplugAt", 0);
    }

    @JavascriptInterface
    public boolean has(String pkg) {
        return pkg != null && pm.getLaunchIntentForPackage(pkg) != null;
    }

    /** Every app with a launcher icon (not Car Home itself), sorted by name: [{pkg, name}]. Icons: icon/<pkg>.png. */
    @JavascriptInterface
    public String apps() {
        org.json.JSONArray out = new org.json.JSONArray();
        java.util.TreeMap<String, String> byName = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(act.getPackageName())) continue;
            byName.put(String.valueOf(r.loadLabel(pm)) + "\u0000" + pkg, pkg);
        }
        for (java.util.Map.Entry<String, String> e : byName.entrySet()) {
            try { out.put(new JSONObject().put("pkg", e.getValue()).put("name", e.getKey().split("\u0000")[0])); } catch (Exception ignored) { }
        }
        return out.toString();
    }

    @JavascriptInterface
    public void openApp(String pkg) {
        main.post(() -> {
            Intent i = pkg == null ? null : pm.getLaunchIntentForPackage(pkg);
            if (i == null) return;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        });
    }

    // ---- split screen and recent apps: through Car Home's accessibility service (Assist), once the user turns it on

    @JavascriptInterface
    public boolean assistOn() { return Assist.on(); }

    /** Accessibility settings, to turn Car Home on there (Android 14 first wants App info > Allow restricted settings). */
    @JavascriptInterface
    public void openAssistSettings() {
        main.post(() -> act.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
    }

    @JavascriptInterface
    public boolean recents() {
        Assist a = Assist.current;
        if (a == null) return false;
        main.post(a::recents);
        return true;
    }

    /** Split screen with two apps; the page hears host.on('split', {ok}) when it is done or a step failed. */
    @JavascriptInterface
    public boolean split(String pkgA, String pkgB) {
        Assist a = Assist.current;
        Intent ia = pkgA == null ? null : pm.getLaunchIntentForPackage(pkgA);
        if (a == null || ia == null || !has(pkgB)) return false;
        main.post(() -> a.split(ia, label(pkgA), label(pkgB), ok -> {
            try { push("split", new JSONObject().put("ok", ok)); } catch (Exception ignored) { }
        }));
        return true;
    }

    /** Free and total memory in MB, for the Clean up button. */
    @JavascriptInterface
    public String memory() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        act.getSystemService(ActivityManager.class).getMemoryInfo(mi);
        return "{\"free\":" + (mi.availMem >> 20) + ",\"total\":" + (mi.totalMem >> 20) + "}";
    }

    /**
     * Clean up: end every other app's cached background processes (an app playing music or navigating runs a
     * foreground service, which this leaves alone), then One UI's recent apps > Close all (Assist). The page hears
     * host.on('clean', {ok, freed, free}) in MB once memory has settled; ok = false when no recent apps were open.
     */
    @JavascriptInterface
    public boolean cleanUp() {
        Assist a = Assist.current;
        if (a == null) return false;
        ActivityManager am = act.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        final long before = mi.availMem;
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(launcher, 0)) {
            String p = r.activityInfo.packageName;
            if (!p.equals(act.getPackageName())) am.killBackgroundProcesses(p);
        }
        main.post(() -> a.closeAll(ok -> main.postDelayed(() -> {
            ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            try {
                push("clean", new JSONObject().put("ok", ok).put("freed", Math.max(0, (after.availMem - before) >> 20))
                        .put("free", after.availMem >> 20));
            } catch (Exception ignored) { }
        }, 1500)));
        return true;
    }

    /** The name the launcher shows (the launcher activity's label: "YT Music", where the app's own is "YouTube Music"). */
    private String label(String pkg) {
        try { return pm.resolveActivity(pm.getLaunchIntentForPackage(pkg), 0).loadLabel(pm).toString(); } catch (Exception e) { return pkg; }
    }

    /** Settings the launcher needs outside the page (start page, the edge strip's side). */
    @JavascriptInterface
    public void setPrefs(String json) {
        try {
            JSONObject s = new JSONObject(json);
            prefs.edit().remove("autoOpen").remove("navPkg")   // the car-start nav app setting, removed 2026-10-28
                    .putString("theme", "glass".equals(s.optString("theme")) ? "glass" : "lite")
                    .putString("side", "left".equals(s.optString("side")) ? "left" : "right").apply();
        } catch (Exception ignored) { }
    }

    // Wi-Fi, Bluetooth, data and hotspot cannot be switched by apps since Android 10: open the system panel instead.
    @JavascriptInterface
    public void openPanel(String which) {
        main.post(() -> {
            // Each opens its own pop-up over Car Home (checked on the tablet 2026-10-27):
            // Wi-Fi = Android's Wi-Fi panel (switch + networks), data = the internet panel, Bluetooth = One UI's
            // Bluetooth pop-up (paired devices + scan). One UI's own Wi-Fi pop-up and its quick settings tile details
            // can't be opened by other apps. Hotspot and DND pull down the quick settings drawer (one tap there).
            if (("hotspot".equals(which) || "dnd".equals(which)) && expandQuickSettings(which)) return;
            Intent i;
            switch (which) {
                case "wifi": i = new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS); break;
                case "data": i = new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.ACTION_WIRELESS_SETTINGS); break;
                case "bt":
                    i = new Intent("com.samsung.settings.bluetooth.scandialog.LAUNCH");
                    if (i.resolveActivity(act.getPackageManager()) == null) i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    break;
                case "hotspot": i = new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.TetherSettings")); break;
                case "dnd": i = new Intent("android.settings.ZEN_MODE_SETTINGS"); break;
                case "media": i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); break;
                case "location": i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.getPackageName())); break;
                case "home": i = new Intent(Settings.ACTION_HOME_SETTINGS); break;
                default: i = new Intent(Settings.ACTION_SETTINGS);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { act.startActivity(i); } catch (Exception e) { act.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        });
    }

    /**
     * StatusBarManager.expandSettingsPanel: not in the public SDK, but allowed for this app's target (28) and needs only
     * the normal EXPAND_STATUS_BAR permission. The tile name asks for that tile's detail where the system supports it;
     * One UI ignores it (also its own names Wifi / Bluetooth / Hotspot / Dnd) and opens the whole drawer.
     */
    private boolean expandQuickSettings(String which) {
        try {
            Object sb = act.getSystemService("statusbar");
            String tile = "wifi".equals(which) ? "wifi" : "bt".equals(which) ? "bt" : "data".equals(which) ? "cell" : which;
            try {
                sb.getClass().getMethod("expandSettingsPanel", String.class).invoke(sb, tile);
            } catch (NoSuchMethodException e) {
                sb.getClass().getMethod("expandSettingsPanel").invoke(sb);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Keep the screen on while the page asks (the screensaver), whatever the screen timeout; off again on false. */
    @JavascriptInterface
    public void keepAwake(boolean on) {
        main.post(() -> {
            if (on) act.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else act.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    // ---- volume (music stream) and screen brightness, 0-100

    @JavascriptInterface
    public int volume() {
        AudioManager am = act.getSystemService(AudioManager.class);
        return Math.round(100f * am.getStreamVolume(AudioManager.STREAM_MUSIC) / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    @JavascriptInterface
    public void setVolume(int pct) {
        AudioManager am = act.getSystemService(AudioManager.class);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(pct / 100f * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
    }

    /** One volume step up (dir > 0) or down, showing One UI's volume indicator (the two-finger swipe). */
    @JavascriptInterface
    public void volumeStep(int dir) {
        act.getSystemService(AudioManager.class).adjustStreamVolume(AudioManager.STREAM_MUSIC,
                dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
    }

    @JavascriptInterface
    public int brightness() {
        int v = Settings.System.getInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        return Math.round(v / 2.55f);
    }

    @JavascriptInterface
    public boolean autoBrightness() {
        return Settings.System.getInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }

    @JavascriptInterface
    public boolean setBrightness(int pct) {
        if (!canWriteSettings()) return false;
        Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, Math.round(pct * 2.55f)));
        return true;
    }

    @JavascriptInterface
    public boolean setAutoBrightness(boolean on) {
        if (!canWriteSettings()) return false;
        Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                on ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        return true;
    }

    /** "Modify system settings" is granted by the user on a settings screen; ask once per run. */
    private boolean canWriteSettings() {
        if (Settings.System.canWrite(act)) return true;
        if (!askedWriteSettings) {
            askedWriteSettings = true;
            main.post(() -> act.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + act.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
        return false;
    }

    // ---------------------------------------------------------------- battery, power, Bluetooth

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                // "Charging" = power plugged in (the car is on), as Assist decides it. The battery status said FULL at
                // 100% even with the charger out, so unplugging a full tablet didn't reset the trip (found 2026-10-29).
                boolean charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                try {
                    battery = new JSONObject().put("level", Math.round(100f * level / scale)).put("charging", charging)
                            .put("temp", Math.round(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f));
                    push("battery", battery);
                } catch (Exception ignored) { }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(a)) {
                pushNet();
            }
        }
    };

    // ---------------------------------------------------------------- network

    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) { pushNet(); }
        @Override public void onLost(Network n) { pushNet(); }
    };

    private void pushNet() {
        try {
            ConnectivityManager cm = act.getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            boolean wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean cell = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            String ssid = "";
            if (wifi) {
                WifiInfo w = act.getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo();
                if (w != null && w.getSSID() != null) ssid = w.getSSID().replace("\"", "");
                if (ssid.startsWith("<")) ssid = "";       // <unknown ssid> without location permission
            }
            // online: Android has checked this network really reaches the internet (a hotspot without data, or used-up
            // data, is connected but not validated). The pages show "no network" when it is false (#5).
            boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            net = new JSONObject().put("wifi", wifi).put("ssid", ssid).put("data", cell).put("online", online)
                    .put("bt", bt != null && bt.isEnabled());
            push("net", net);
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- location: GPS fixes at 1 Hz, place names

    private Location lastGeocoded;
    private long lastGeocodeAt;

    void startLocation() {
        if (!started) return;
        if (act.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        LocationManager lm = act.getSystemService(LocationManager.class);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener, Looper.getMainLooper());
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) locationListener.onLocationChanged(last);
        } catch (Exception ignored) { }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            try {
                gps = new JSONObject().put("lat", l.getLatitude()).put("lon", l.getLongitude())
                        .put("speed", l.hasSpeed() ? l.getSpeed() : 0).put("heading", l.hasBearing() ? l.getBearing() : JSONObject.NULL)
                        // How sure GPS is of that direction (degrees); pages ignore shaky ones at crawling speed.
                        .put("headingAcc", l.hasBearing() && l.hasBearingAccuracy() ? l.getBearingAccuracyDegrees() : JSONObject.NULL)
                        .put("accuracy", l.hasAccuracy() ? l.getAccuracy() : 99).put("t", l.getTime());
                push("gps", gps);
            } catch (Exception ignored) { }
            // Place name: first fix, then every 5 minutes or 3 km.
            long now = System.currentTimeMillis();
            if (lastGeocoded == null || now - lastGeocodeAt > 300000 || l.distanceTo(lastGeocoded) > 3000) {
                lastGeocoded = l;
                lastGeocodeAt = now;
                geocode(l);
            }
        }

        @Override public void onStatusChanged(String p, int s, Bundle e) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
    };

    private void geocode(Location l) {
        if (!Geocoder.isPresent()) return;
        new Thread(() -> {
            try {
                @SuppressWarnings("deprecation")
                List<Address> r = new Geocoder(act).getFromLocation(l.getLatitude(), l.getLongitude(), 1);
                if (r == null || r.isEmpty()) return;
                Address a = r.get(0);
                String district = a.getLocality() != null ? a.getLocality() : a.getSubLocality();
                place = new JSONObject().put("district", district == null ? "" : district)
                        .put("area", a.getSubAdminArea() == null ? "" : a.getSubAdminArea());
                push("place", place);
            } catch (Exception ignored) { }
        }).start();
    }

    // ---------------------------------------------------------------- media sessions (needs Notification access)

    private MediaSessionManager msm;
    private MediaController controller;
    private final ComponentName listener() { return new ComponentName(act, MediaListener.class); }

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pickController;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { pushMedia(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { pushMedia(); }
        @Override public void onSessionDestroyed() { pickController(null); }
    };

    // ---- Drive log (DriveLog.java): the pages add their own events, and the About page shows the newest lines

    @JavascriptInterface
    public void log(String what) { if (what != null) DriveLog.add(act, "page: " + (what.length() > 300 ? what.substring(0, 300) : what)); }

    @JavascriptInterface
    public String driveLog(int lines) { return DriveLog.tail(act, Math.max(1, Math.min(lines, 2000))); }

    // ---- About page (Controls > About): the version and what nDashMo needs turned on

    @JavascriptInterface
    public String version() {
        try { return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    @JavascriptInterface
    public boolean locationOn() {
        return act.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** nDashMo is the default Home app (the Home button and swipe-up open it). */
    @JavascriptInterface
    public boolean isHome() {
        android.content.pm.ResolveInfo r = act.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && act.getPackageName().equals(r.activityInfo.packageName);
    }

    @JavascriptInterface
    public boolean mediaAccess() {
        String flat = Settings.Secure.getString(act.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(act.getPackageName());
    }

    private void startMedia() {
        msm = act.getSystemService(MediaSessionManager.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener(), main);
            pickController(msm.getActiveSessions(listener()));
        } catch (SecurityException e) {
            media = null;                               // no Notification access yet: the page shows how to grant it
        }
    }

    private void stopMedia() {
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessionsChanged); } catch (Exception ignored) { }
        if (controller != null) controller.unregisterCallback(mediaCallback);
        controller = null;
    }

    /** Follow the session that is playing; otherwise the most recent one. */
    private void pickController(List<MediaController> list) {
        if (list == null) {
            try { list = msm.getActiveSessions(listener()); } catch (Exception e) { return; }
        }
        MediaController pick = null;
        for (MediaController c : list) {
            PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { pick = c; break; }
        }
        if (pick == null && !list.isEmpty()) pick = list.get(0);
        if (controller != null) controller.unregisterCallback(mediaCallback);
        controller = pick;
        if (controller != null) controller.registerCallback(mediaCallback, main);
        pushMedia();
    }

    private String lastArtKey;
    private String lastArt;

    private void pushMedia() {
        try {
            if (controller == null) { media = new JSONObject(); push("media", media); return; }
            MediaMetadata m = controller.getMetadata();
            PlaybackState s = controller.getPlaybackState();
            String pkg = controller.getPackageName();
            String label = pkg;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) { }
            JSONObject o = new JSONObject().put("pkg", pkg).put("app", label);
            if (m != null) {
                o.put("title", nz(m.getString(MediaMetadata.METADATA_KEY_TITLE)));
                String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                o.put("artist", nz(artist));
                long len = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                o.put("len", len > 0 ? len / 1000 : JSONObject.NULL);   // no length = live radio
                // Album art, small: encoded once per track, but looked for again until it arrives. YT Music sends a new
                // track's title first and its artwork in a later metadata update; caching "none" on the first one left
                // every song without art (2026-10-31).
                String key = pkg + "|" + o.optString("title");
                if (!key.equals(lastArtKey) || lastArt == null) {
                    boolean newTrack = !key.equals(lastArtKey);
                    lastArtKey = key; lastArt = art(m);
                    if (newTrack || lastArt != null) DriveLog.add(act, "media art: " + (lastArt != null ? artFrom : "none yet") + " (" + label + ")");
                }
                o.put("art", lastArt == null ? JSONObject.NULL : lastArt);
            }
            if (s != null) {
                o.put("playing", s.getState() == PlaybackState.STATE_PLAYING);
                o.put("pos", Math.max(0, s.getPosition() / 1000));
                long actions = s.getActions();
                o.put("skip", (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
            }
            media = o;
            push("media", o);
        } catch (Exception ignored) { }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String artFrom = "";

    /** The track's artwork from wherever the app put it (album art, art, display icon, the description's icon). */
    private static String art(MediaMetadata m) {
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART); artFrom = "album art";
        if (b == null) { b = m.getBitmap(MediaMetadata.METADATA_KEY_ART); artFrom = "art"; }
        if (b == null) { b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON); artFrom = "display icon"; }
        if (b == null && m.getDescription() != null) { b = m.getDescription().getIconBitmap(); artFrom = "description icon"; }
        if (b == null) return null;
        Bitmap small = Bitmap.createScaledBitmap(b, 240, Math.max(1, 240 * b.getHeight() / b.getWidth()), true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 82, out);
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** Transport controls for the player buttons: prev / toggle / next; pause when the charger is pulled (#3). */
    @JavascriptInterface
    public void media(String cmd) {
        main.post(() -> {
            if (controller == null) return;
            MediaController.TransportControls t = controller.getTransportControls();
            PlaybackState s = controller.getPlaybackState();
            switch (cmd) {
                case "prev": t.skipToPrevious(); break;
                case "next": t.skipToNext(); break;
                case "pause": t.pause(); break;
                case "toggle":
                    if (s != null && s.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play();
                    break;
            }
        });
    }
}
