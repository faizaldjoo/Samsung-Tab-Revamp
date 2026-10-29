package me.faizal.carnative;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The tablet's data and the system actions, for the native screens. Same sources as the WebView app's Bridge.java
 * (android/), but values go straight to a Listener on the main thread instead of host.on(...) in a page.
 */
final class Tablet {
    interface Listener {
        void onBattery(int level, boolean charging, int temp);
        void onNet(boolean wifi, String ssid, boolean data, boolean bt);
        void onGps(Location l);
        void onPlace(String place);
        void onMedia(Media m);
        void onNav(Nav n);
        void onWeather(int temp, int code, int hi, int lo);
        void onCarStart();
    }

    /** What is playing; null fields = unknown. title empty = nothing playing. */
    static final class Media {
        String pkg = "", app = "", title = "", artist = "";
        Bitmap art;
        long len = -1, pos;       // seconds; len -1 = live radio
        boolean playing, skip = true;
    }

    /** The nav app's notification: Google Maps puts the next turn in it (title "110 m", text = street, sub = ETA line). */
    static final class Nav {
        String pkg = "", title = "", text = "", sub = "", big = "";
        Bitmap icon;
    }

    private final Activity act;
    private final Listener out;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PackageManager pm;
    private boolean started, askedWriteSettings;
    boolean autoOpen = true;

    private static Nav navInfo;
    private static Tablet current;

    /** From MediaListener (its own service): null = navigation ended. */
    static void nav(Nav n) {
        navInfo = n;
        Tablet t = current;
        if (t != null) t.main.post(() -> t.out.onNav(n));
    }

    Tablet(Activity act, Listener out) {
        this.act = act;
        this.out = out;
        this.pm = act.getPackageManager();
    }

    void start() {
        current = this;
        if (started) return;
        started = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        act.registerReceiver(receiver, f);
        act.getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(netCallback);
        startLocation();
        startMedia();
        out.onNav(navInfo);
    }

    void stop() {
        if (!started) return;
        started = false;
        try { act.unregisterReceiver(receiver); } catch (Exception ignored) { }
        try { act.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback); } catch (Exception ignored) { }
        try { act.getSystemService(LocationManager.class).removeUpdates(locationListener); } catch (Exception ignored) { }
        stopMedia();
    }

    // ---------------------------------------------------------------- apps

    static final class App {
        final String pkg, name;
        App(String pkg, String name) { this.pkg = pkg; this.name = name; }
    }

    /** Every app with a launcher icon except this one, A to Z. */
    List<App> apps() {
        TreeMap<String, String> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(act.getPackageName())) continue;
            byName.put(r.loadLabel(pm) + "\u0000" + pkg, pkg);
        }
        List<App> out = new ArrayList<>();
        for (Map.Entry<String, String> e : byName.entrySet()) out.add(new App(e.getValue(), e.getKey().split("\u0000")[0]));
        return out;
    }

    boolean has(String pkg) { return pkg != null && pm.getLaunchIntentForPackage(pkg) != null; }

    void openApp(String pkg) {
        Intent i = pkg == null ? null : pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        act.startActivity(i);
    }

    /** Wi-Fi, Bluetooth, data and hotspot cannot be switched by apps since Android 10: open the system panel. */
    void openPanel(String which) {
        // As in the WebView app (checked on the tablet 2026-10-27): Wi-Fi panel, internet panel, One UI's Bluetooth
        // pop-up; hotspot and DND pull down the quick settings drawer.
        if (("hotspot".equals(which) || "dnd".equals(which)) && expandQuickSettings()) return;
        Intent i;
        switch (which) {
            case "wifi": i = new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS); break;
            case "data": i = new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.ACTION_WIRELESS_SETTINGS); break;
            case "bt":
                i = new Intent("com.samsung.settings.bluetooth.scandialog.LAUNCH");
                if (i.resolveActivity(pm) == null) i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                break;
            case "hotspot": i = new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.TetherSettings")); break;
            case "dnd": i = new Intent("android.settings.ZEN_MODE_SETTINGS"); break;
            case "media": i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); break;
            default: i = new Intent(Settings.ACTION_SETTINGS);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { act.startActivity(i); } catch (Exception e) { act.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    }

    /** StatusBarManager.expandSettingsPanel: hidden API, allowed at target 28 with EXPAND_STATUS_BAR. */
    private boolean expandQuickSettings() {
        try {
            Object sb = act.getSystemService("statusbar");
            sb.getClass().getMethod("expandSettingsPanel").invoke(sb);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- volume (music stream) and screen brightness, 0-100

    int volume() {
        AudioManager am = act.getSystemService(AudioManager.class);
        return Math.round(100f * am.getStreamVolume(AudioManager.STREAM_MUSIC) / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    void setVolume(int pct) {
        AudioManager am = act.getSystemService(AudioManager.class);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(pct / 100f * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
    }

    int brightness() {
        return Math.round(Settings.System.getInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) / 2.55f);
    }

    boolean autoBrightness() {
        return Settings.System.getInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }

    boolean setBrightness(int pct) {
        if (!canWriteSettings()) return false;
        Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, Math.round(pct * 2.55f)));
        return true;
    }

    boolean setAutoBrightness(boolean on) {
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
            act.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + act.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                out.onBattery(Math.round(100f * level / scale), charging, Math.round(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f));
            } else if (Intent.ACTION_POWER_CONNECTED.equals(a)) {
                if (autoOpen) out.onCarStart();      // the tablet charges from the car: the car has started
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(a)) {
                pushNet();
            }
        }
    };

    // ---------------------------------------------------------------- network

    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) { main.post(Tablet.this::pushNet); }
        @Override public void onLost(Network n) { main.post(Tablet.this::pushNet); }
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
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            out.onNet(wifi, ssid, cell, bt != null && bt.isEnabled());
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- location: GPS at 1 Hz, place names, weather

    private Location lastGeocoded, lastWeather;
    private long lastGeocodeAt, lastWeatherAt;

    void startLocation() {
        if (!started) return;
        if (act.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            weather(-6.842, 107.921);           // no fix yet: Sumedang, as in the pages
            return;
        }
        LocationManager lm = act.getSystemService(LocationManager.class);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener, Looper.getMainLooper());
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) locationListener.onLocationChanged(last);
            else weather(-6.842, 107.921);
        } catch (Exception ignored) { }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            out.onGps(l);
            long now = System.currentTimeMillis();
            // Place name: first fix, then every 5 minutes or 3 km. Weather: first fix, then every 20 minutes.
            if (lastGeocoded == null || now - lastGeocodeAt > 300000 || l.distanceTo(lastGeocoded) > 3000) {
                lastGeocoded = l;
                lastGeocodeAt = now;
                geocode(l);
            }
            if (lastWeather == null || now - lastWeatherAt > 20 * 60000) {
                lastWeather = l;
                lastWeatherAt = now;
                weather(l.getLatitude(), l.getLongitude());
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
                String district = strip(a.getLocality() != null ? a.getLocality() : a.getSubLocality());
                String area = strip(a.getSubAdminArea());
                String place = !district.isEmpty() && !district.equals(area) ? (area.isEmpty() ? district : district + ", " + area) : area;
                if (!place.isEmpty()) main.post(() -> out.onPlace(place));
            } catch (Exception ignored) { }
        }).start();
    }

    private static String strip(String n) {
        return n == null ? "" : n.replaceFirst("(?i)^(Kabupaten|Kab\\.|Kota|Kecamatan|Kec\\.)\\s+", "");
    }

    /** Open-Meteo, no key: current temperature and weather code, today's high and low. */
    private void weather(double lat, double lon) {
        new Thread(() -> {
            try {
                URL u = new URL(String.format(java.util.Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                        + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto", lat, lon));
                JSONObject j = new JSONObject(read(u));
                JSONObject cur = j.getJSONObject("current"), d = j.getJSONObject("daily");
                int t = (int) Math.round(cur.getDouble("temperature_2m")), code = cur.getInt("weather_code");
                int hi = (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
                int lo = (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
                main.post(() -> out.onWeather(t, code, hi, lo));
            } catch (Exception ignored) { }
        }).start();
    }

    static String read(URL u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ---------------------------------------------------------------- media sessions (needs Notification access)

    private MediaSessionManager msm;
    private MediaController controller;
    private ComponentName listener() { return new ComponentName(act, MediaListener.class); }

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pickController;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { pushMedia(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { pushMedia(); }
        @Override public void onSessionDestroyed() { pickController(null); }
    };

    boolean mediaAccess() {
        String flat = Settings.Secure.getString(act.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(act.getPackageName());
    }

    private void startMedia() {
        msm = act.getSystemService(MediaSessionManager.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener(), main);
            pickController(msm.getActiveSessions(listener()));
        } catch (SecurityException e) {
            out.onMedia(new Media());               // no Notification access yet: the music card shows how to grant it
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
    private Bitmap lastArt;

    private void pushMedia() {
        Media o = new Media();
        if (controller != null) {
            MediaMetadata m = controller.getMetadata();
            PlaybackState s = controller.getPlaybackState();
            o.pkg = controller.getPackageName();
            o.app = o.pkg;
            try { o.app = pm.getApplicationLabel(pm.getApplicationInfo(o.pkg, 0)).toString(); } catch (Exception ignored) { }
            if (m != null) {
                o.title = nz(m.getString(MediaMetadata.METADATA_KEY_TITLE));
                String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                o.artist = nz(artist);
                long len = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                o.len = len > 0 ? len / 1000 : -1;
                String key = o.pkg + "|" + o.title;           // album art, scaled down once per track
                if (!key.equals(lastArtKey)) { lastArtKey = key; lastArt = art(m); }
                o.art = lastArt;
            }
            if (s != null) {
                o.playing = s.getState() == PlaybackState.STATE_PLAYING;
                o.pos = Math.max(0, s.getPosition() / 1000);
                o.skip = (s.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0;
            }
        }
        out.onMedia(o);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static Bitmap art(MediaMetadata m) {
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) return null;
        int s = Ui.dp(58);
        return Bitmap.createScaledBitmap(b, s, Math.max(1, s * b.getHeight() / b.getWidth()), true);
    }

    /** Transport controls: prev / toggle / next. */
    void media(String cmd) {
        if (controller == null) return;
        MediaController.TransportControls t = controller.getTransportControls();
        PlaybackState s = controller.getPlaybackState();
        switch (cmd) {
            case "prev": t.skipToPrevious(); break;
            case "next": t.skipToNext(); break;
            case "toggle":
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play();
                break;
        }
    }
}
