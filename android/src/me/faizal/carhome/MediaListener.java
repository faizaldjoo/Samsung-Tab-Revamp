package me.faizal.carhome;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Base64;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

/**
 * Notification access. Two jobs:
 * 1. Its existence lets Bridge read and control other apps' media sessions (MediaSessionManager needs an enabled
 *    listener component).
 * 2. Turn-by-turn for the map panel: the navigation notification of Waze or Google Maps (next turn, street, ETA line)
 *    is forwarded to the page as host.on('nav', ...); nav ended = an empty object.
 *    Google Maps puts the turn in it (title "110 m", text = street, sub text "7 min · 3.4 km · 21:52 ETA") and the turn
 *    arrow as the large icon, sent as "icon" (a PNG data URL). Waze (checked 2026-10-27) only posts
 *    "Running. Tap to open.", with no turn details, even while navigating.
 */
public class MediaListener extends NotificationListenerService {
    static final String[] NAV_APPS = {"com.waze", "com.google.android.apps.maps"};

    @Override
    public void onListenerConnected() {
        DriveLog.add(this, "notification access connected");
        Power.attach(this, this);   // charger out / in (Power.java), also while accessibility is off
        try {
            for (StatusBarNotification n : getActiveNotifications()) onNotificationPosted(n);
        } catch (Exception ignored) { }
    }

    @Override
    public void onListenerDisconnected() { DriveLog.add(this, "notification access disconnected"); Power.detach(this); }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isNav(sbn.getPackageName())) return;
        Bundle x = sbn.getNotification().extras;
        if (x == null) return;
        try {
            JSONObject o = new JSONObject().put("pkg", sbn.getPackageName())
                    .put("title", str(x.getCharSequence(Notification.EXTRA_TITLE)))
                    .put("text", str(x.getCharSequence(Notification.EXTRA_TEXT)))
                    .put("sub", str(x.getCharSequence(Notification.EXTRA_SUB_TEXT)))
                    .put("big", str(x.getCharSequence(Notification.EXTRA_BIG_TEXT)))
                    .put("ongoing", (sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0)
                    .put("icon", png(sbn.getNotification().getLargeIcon()));
            if (o.optString("title").isEmpty() && o.optString("text").isEmpty()) return;
            Bridge.nav(o);
        } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (isNav(sbn.getPackageName()) && (sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            Bridge.nav(new JSONObject());
        }
    }

    private static boolean isNav(String pkg) {
        for (String p : NAV_APPS) if (p.equals(pkg)) return true;
        return false;
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString(); }

    /** The turn arrow (a small bitmap) as a data URL; "" if there is none. */
    private String png(Icon icon) {
        if (icon == null) return "";
        try {
            Drawable d = icon.loadDrawable(this);
            if (d == null) return "";
            int w = Math.max(1, Math.min(d.getIntrinsicWidth(), 192)), h = Math.max(1, Math.min(d.getIntrinsicHeight(), 192));
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, w, h);
            d.draw(new Canvas(b));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
