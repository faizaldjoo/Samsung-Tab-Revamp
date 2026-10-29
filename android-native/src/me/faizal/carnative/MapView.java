package me.faizal.carnative;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The map panel's map: raster tiles at zoom 16, north up, the car's position under the puck (50%, 64% of the panel).
 * Drawn only when the position changes (once a second at most, from GPS) or a tile arrives; nothing animates.
 * Sources (Controls > Map):
 *  - Dark: CARTO's Dark Matter raster tiles (the same style as lite.html's vector Dark map, by CARTO, on OSM data), @2x.
 *  - OpenStreetMap: the standard OSM tiles, darkened once per tile as it loads (lite.html's DARK filter as a colour
 *    matrix), never on every draw.
 * Tiles are kept in memory (the last 64) and on disk (app cache), so driving the same roads needs no network.
 */
final class MapView extends View {
    static final int Z = 16;
    private static final String UA = "CarHomeNative/1.0 (personal car launcher; Android)";

    private final float tileDp = Ui.dp(256);
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG), puck = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint attrib = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dst = new Rect();
    private final LruCache<String, Bitmap> mem = new LruCache<>(64);
    private final Set<String> pending = new HashSet<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File disk;

    private String src = "dark";
    private double lat = -6.842, lon = 107.921;

    MapView(Context c) {
        super(c);
        disk = new File(c.getCacheDir(), "tiles");
        puck.setColor(Ui.ACCENT);
        ring.setColor(0xffffffff);
        attrib.setColor(Ui.FAINT);
        attrib.setTextSize(Ui.dp(9));
        attrib.setTypeface(Ui.font(400));
        attrib.setTextAlign(Paint.Align.RIGHT);
        setBackgroundColor(0xff111418);
    }

    void setSource(String s) {
        if (s.equals(src)) return;
        src = s;
        mem.evictAll();
        invalidate();
    }

    void setPosition(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        double n = 1 << Z, r = Math.toRadians(lat);
        double x = (lon + 180) / 360 * n, y = (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * n;
        float px = W * 0.5f, py = H * 0.64f;
        int x0 = (int) Math.floor(x - px / tileDp), y0 = (int) Math.floor(y - py / tileDp);
        int cols = (int) Math.ceil(W / tileDp) + 1, rows = (int) Math.ceil(H / tileDp) + 1;
        float left = (float) (px - (x - x0) * tileDp), top = (float) (py - (y - y0) * tileDp);
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                Bitmap b = tile(x0 + i, y0 + j);
                if (b == null) continue;
                int l = Math.round(left + i * tileDp), t = Math.round(top + j * tileDp);
                dst.set(l, t, Math.round(l + tileDp), Math.round(t + tileDp));
                c.drawBitmap(b, null, dst, paint);
            }
        }
        c.drawCircle(px, py, Ui.dp(11), ring);
        c.drawCircle(px, py, Ui.dp(8), puck);
        c.drawText("dark".equals(src) ? "© OpenStreetMap © CARTO" : "© OpenStreetMap contributors", W - Ui.dp(6), H - Ui.dp(3), attrib);
    }

    private String url(int x, int y) {
        return "dark".equals(src)
                ? "https://" + "abcd".charAt((x + y) & 3) + ".basemaps.cartocdn.com/dark_all/" + Z + "/" + x + "/" + y + "@2x.png"
                : "https://tile.openstreetmap.org/" + Z + "/" + x + "/" + y + ".png";
    }

    /** The tile if it is ready; otherwise starts loading it (disk, then network) and returns null. */
    private Bitmap tile(int x, int y) {
        String key = src + "/" + x + "_" + y;
        Bitmap b = mem.get(key);
        if (b != null || pending.contains(key)) return b;
        pending.add(key);
        final String s = src, u = url(x, y);
        pool.execute(() -> {
            Bitmap got = load(s, key, u);
            main.post(() -> {
                pending.remove(key);
                if (got != null && s.equals(src)) { mem.put(key, got); invalidate(); }
            });
        });
        return null;
    }

    private Bitmap load(String s, String key, String u) {
        File f = new File(disk, key + ".png");
        try {
            if (!f.exists()) {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setRequestProperty("User-Agent", UA);
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                f.getParentFile().mkdirs();
                File tmp = new File(f.getPath() + ".part");
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }
                if (!tmp.renameTo(f)) return null;
            }
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b == null) { f.delete(); return null; }
            return "osm".equals(s) ? darken(b) : b;
        } catch (Exception e) {
            return null;                 // offline: tried again on the next draw that needs it
        }
    }

    // lite.html's DARK filter: invert(1) hue-rotate(180deg) brightness(0.85) contrast(0.9) saturate(0.4), as CSS
    // defines each step's matrix, applied once to the tile.
    private static final ColorMatrixColorFilter DARK;
    static {
        ColorMatrix m = new ColorMatrix(new float[]{-1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0});
        m.postConcat(hueRotate(180));
        m.postConcat(new ColorMatrix(new float[]{.85f, 0, 0, 0, 0, 0, .85f, 0, 0, 0, 0, 0, .85f, 0, 0, 0, 0, 0, 1, 0}));
        float k = 0.9f, o = 255 * (0.5f - 0.5f * k);
        m.postConcat(new ColorMatrix(new float[]{k, 0, 0, 0, o, 0, k, 0, 0, o, 0, 0, k, 0, o, 0, 0, 0, 1, 0}));
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(0.4f);
        m.postConcat(sat);
        DARK = new ColorMatrixColorFilter(m);
    }

    private static ColorMatrix hueRotate(float deg) {
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new ColorMatrix(new float[]{
                0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0, 0,
                0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0, 0,
                0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0, 0,
                0, 0, 0, 1, 0});
    }

    private static Bitmap darken(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        p.setColorFilter(DARK);
        new Canvas(out).drawBitmap(src, 0, 0, p);
        src.recycle();
        return out;
    }
}
