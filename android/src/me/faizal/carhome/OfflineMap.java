package me.faizal.carhome;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.webkit.WebResourceResponse;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The map's tiles and label fonts, served to the page as https://carhome.app/tiles/{z}/{x}/{y}.pbf and
 * /fonts/{stack}/{range}.pbf (user, 2026-10-30). Inside Sumedang + greater Bandung they come from
 * sumedang-bandung.mbtiles in the app's files folder (offline-map/build.sh makes it, about 55 MB, copied with adb, not
 * in the APK), so the map works without signal there. Everywhere else, and for anything the file lacks, they come
 * from OpenFreeMap as before. Fonts are kept once fetched, so street names show offline after the first online use.
 * Runs on WebView's request threads, never the UI thread.
 */
final class OfflineMap {
    static final String FILE = "sumedang-bandung.mbtiles";
    private static final String TILEJSON = "https://tiles.openfreemap.org/planet";
    private static final String FONTS = "https://tiles.openfreemap.org/fonts/";

    private static SQLiteDatabase db;
    private static long dbChecked, dbStamp;
    private static String tileUrl;            // OpenFreeMap's current tile URL template, from its TileJSON

    private OfflineMap() { }

    static File file(Context c) {
        File dir = c.getExternalFilesDir(null);
        return new File(dir != null ? dir : c.getFilesDir(), FILE);
    }

    /** The map file, opened once; looked for again a minute later if missing, reopened if replaced. */
    private static synchronized SQLiteDatabase db(Context c) {
        long now = System.currentTimeMillis();
        if (now - dbChecked < 60_000 && (db != null || dbChecked != 0)) return db;
        dbChecked = now;
        File f = file(c);
        if (!f.exists()) { close(); return null; }
        if (db != null && f.lastModified() == dbStamp) return db;
        close();
        try {
            // A do-nothing error handler: Android's default one renames or deletes a database it finds corrupt (found
            // 2026-10-30: the file, opened while adb was still copying it, became sumedang-bandung.mbtiles.corrupt).
            db = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY, dbObj -> { });
            dbStamp = f.lastModified();
        } catch (Exception e) { db = null; DriveLog.add(c, "offline map: can't open " + FILE + ": " + e.getMessage()); }
        return db;
    }

    private static void close() { if (db != null) { try { db.close(); } catch (Exception ignored) { } db = null; } }

    /** One line for the drive log: whether the offline map is there, its size and when it was built. */
    static String describe(Context c) {
        File f = file(c);
        if (!f.exists()) return "offline map: none (network map everywhere)";
        String built = "";
        SQLiteDatabase d = db(c);
        if (d != null) try (Cursor m = d.rawQuery("SELECT value FROM metadata WHERE name IN ('planetiler:osm:osmosisreplicationtime','mtime') LIMIT 1", null)) {
            if (m.moveToFirst()) built = ", data from " + m.getString(0);
        } catch (Exception ignored) { }
        return "offline map: " + FILE + " " + (f.length() / 1_048_576) + " MB" + built;
    }

    static WebResourceResponse tile(Context c, int z, int x, int y) {
        byte[] data = null;
        String from = "file";
        SQLiteDatabase d = db(c);
        if (d != null) {
            // MBTiles counts rows from the bottom (TMS).
            try (Cursor t = d.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                    new String[]{ "" + z, "" + x, "" + ((1 << z) - 1 - y) })) {
                if (t.moveToFirst()) data = gunzip(t.getBlob(0));
            } catch (Exception ignored) { }
        }
        if (data == null) {
            from = "network";
            String url = tileUrl();
            if (url != null) data = get(url.replace("{z}", "" + z).replace("{x}", "" + x).replace("{y}", "" + y));
        }
        return pbf(data, from);
    }

    static WebResourceResponse font(Context c, String stackAndRange) {
        File cached = new File(c.getFilesDir(), "fonts/" + stackAndRange.replace("..", ""));
        byte[] data = null;
        if (cached.exists()) data = read(cached);
        if (data == null) {
            data = get(FONTS + stackAndRange.replace(" ", "%20"));
            if (data != null) try {
                cached.getParentFile().mkdirs();
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(cached)) { out.write(data); }
            } catch (Exception ignored) { }
        }
        return pbf(data, "font");
    }

    private static synchronized String tileUrl() {
        if (tileUrl != null) return tileUrl;
        byte[] j = get(TILEJSON);
        try { if (j != null) tileUrl = new JSONObject(new String(j, "UTF-8")).getJSONArray("tiles").getString(0); } catch (Exception ignored) { }
        return tileUrl;
    }

    /** A missing tile is an empty one (204): the map draws nothing there instead of failing. */
    private static WebResourceResponse pbf(byte[] data, String from) {
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("X-Tile-Source", from);                  // file / network: lets a test tell where a tile came from
        if (data == null) return new WebResourceResponse("application/x-protobuf", null, 204, "No Content", h, new ByteArrayInputStream(new byte[0]));
        return new WebResourceResponse("application/x-protobuf", null, 200, "OK", h, new ByteArrayInputStream(data));
    }

    private static byte[] get(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000); con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "nDashMo");
            if (con.getResponseCode() != 200) return null;
            try (InputStream in = con.getInputStream()) { return gunzip(readAll(in)); }    // usually already unzipped
        } catch (Exception e) { return null; }
        finally { if (con != null) con.disconnect(); }
    }

    /** Tiles are stored gzipped; the page wants plain protobuf. */
    private static byte[] gunzip(byte[] b) throws java.io.IOException {
        if (b == null || b.length < 2 || (b[0] & 0xff) != 0x1f || (b[1] & 0xff) != 0x8b) return b;
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) { return readAll(in); }
    }

    private static byte[] read(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) { return readAll(in); } catch (Exception e) { return null; }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
