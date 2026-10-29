package me.faizal.carhome;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;

/**
 * The launcher: one full-screen WebView showing the launcher-prototype pages, registered as the Home app.
 * Pages are served from assets/www under a private https origin (https://carhome.app/), so localStorage, fetch,
 * ES modules and the file picker behave as on a normal site. Bridge.java exposes the tablet's data as window.Android.
 */
public class MainActivity extends Activity {
    static final String HOST = "carhome.app";
    static final String ORIGIN = "https://" + HOST + "/";
    // Start page: the theme last shown (Controls > Theme, saved by Bridge.setPrefs). Lite (lite.html, the default) has
    // the same features as Glass (glass.html) at a fraction of the drawing cost; only one of them is ever loaded.
    String startPage() {
        return "glass".equals(getSharedPreferences("carhome", MODE_PRIVATE).getString("theme", "lite")) ? "glass.html" : "lite.html";
    }
    private static final int PICK_IMAGE = 1, PERMS = 2;

    WebView web;
    Bridge bridge;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);   // woken by the charger: show over the lock screen and turn the screen on
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        WebView.setWebContentsDebuggingEnabled(true);   // chrome://inspect from the Mac, for measuring
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(7, 8, 10));
        web.setVerticalScrollBarEnabled(false);         // the pages are fixed-size; no scrollbar line on the edge
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);                  // photo picker results are content:// URIs
        s.setTextZoom(100);                             // ignore the system font size; the layout is fixed

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                return HOST.equals(u.getHost()) ? asset(u.getPath()) : null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (HOST.equals(req.getUrl().getHost())) return false;   // our pages
                // Never open other sites in here. A link the user tapped (the creator's Threads link on the About page)
                // opens in its own app or the browser instead.
                if (req.isForMainFrame() && req.hasGesture()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            // <input type=file accept=image/*> (Wallpaper > From gallery): the system photo picker, no storage permission.
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent pick = Build.VERSION.SDK_INT >= 33
                        ? new Intent(MediaStore.ACTION_PICK_IMAGES)
                        : new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(pick, PICK_IMAGE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        bridge = new Bridge(this, web);
        web.addJavascriptInterface(bridge, "Android");
        setContentView(web);
        web.loadUrl(ORIGIN + startPage());

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMS);
        }
    }

    private final java.util.Map<String, byte[]> icons = new java.util.HashMap<>();

    /**
     * icon/<pkg>.png: that app's launcher icon as a 96 px PNG, drawn once and kept. Adaptive icons are drawn as a full
     * square, without One UI's rounded mask, so the page's tile corners shape them (masked, they sat inside the tile
     * with different corners). The visible part of an adaptive icon is the middle 72 of its 108 dp layers.
     */
    private WebResourceResponse icon(String pkg) {
        byte[] png;
        synchronized (icons) { png = icons.get(pkg); }
        if (png == null) {
            try {
                android.graphics.drawable.Drawable d = getPackageManager().getApplicationIcon(pkg);
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c = new android.graphics.Canvas(b);
                if (d instanceof android.graphics.drawable.AdaptiveIconDrawable) {
                    android.graphics.drawable.AdaptiveIconDrawable ad = (android.graphics.drawable.AdaptiveIconDrawable) d;
                    for (android.graphics.drawable.Drawable layer : new android.graphics.drawable.Drawable[]{ad.getBackground(), ad.getForeground()}) {
                        if (layer == null) continue;
                        layer.setBounds(-24, -24, 120, 120);
                        layer.draw(c);
                    }
                } else {
                    d.setBounds(0, 0, 96, 96);
                    d.draw(c);
                }
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                png = out.toByteArray();
                synchronized (icons) { icons.put(pkg, png); }
            } catch (Exception e) {
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
            }
        }
        return new WebResourceResponse("image/png", null, new java.io.ByteArrayInputStream(png));
    }

    /** Serve assets/www/<path> with the right content type; icon/<pkg>.png from the installed app. */
    private WebResourceResponse asset(String path) {
        if (path != null && path.startsWith("/icon/") && path.endsWith(".png")) return icon(path.substring(6, path.length() - 4));
        if (path == null || path.equals("/")) path = "/" + startPage();
        try {
            InputStream in = getAssets().open("www" + path);
            String mime = mime(path);
            boolean text = mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json") || mime.endsWith("svg+xml");
            return new WebResourceResponse(mime, text ? "utf-8" : null, in);
        } catch (IOException e) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
        }
    }

    private static String mime(String p) {
        String l = p.toLowerCase();
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".js") || l.endsWith(".mjs")) return "application/javascript";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".json")) return "application/json";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".webp")) return "image/webp";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        if (req == PICK_IMAGE && fileCallback != null) {
            Uri u = result == RESULT_OK && data != null ? data.getData() : null;
            fileCallback.onReceiveValue(u != null ? new Uri[]{u} : null);
            fileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req == PERMS) bridge.startLocation();
    }

    // While Car Home is not visible at all (another app full screen, screen off), its page stops drawing and animating
    // and GPS stops. Visible-but-behind (the nav app in a pop-up window over the map panel) keeps running.
    // Not WebView.pauseTimers(): that freezes every WebView in the process, including a second Car Home instance
    // (one opened from the app drawer next to the Home one), which left the screensaver page frozen half-loaded.
    // The page's own timers are 1-second ones and cost next to nothing.
    @Override
    protected void onStart() {
        super.onStart();
        web.onResume();
        web.resumeTimers();   // undo a pause left by an older build
        bridge.start();
    }

    @Override
    protected void onStop() {
        bridge.stop();
        web.onPause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        bridge.stop();
        web.destroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) immersive();
    }

    /** Home pressed while already home: close drawers, or leave the screensaver. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) bridge.home();   // also when the charger woke the tablet (Assist)
    }

    /** Back: leave the screensaver; on home it does nothing (a launcher has nowhere to go back to). */
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
    }

    private void immersive() {
        View d = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = d.getWindowInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
