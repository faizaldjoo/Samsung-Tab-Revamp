package me.faizal.carnative;

import android.app.Notification;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Notification access, as in the WebView app: its existence lets Tablet read and control other apps' media sessions,
 * and it forwards the nav app's notification for the map's turn card. Google Maps puts the turn in it (title "110 m",
 * text = street, sub text "7 min · 3.4 km · 21:52 ETA") and the turn arrow as the large icon. Waze only posts
 * "Running. Tap to open.", with no turn details (checked 2026-10-27).
 */
public class MediaListener extends NotificationListenerService {
    static final String[] NAV_APPS = {"com.waze", "com.google.android.apps.maps"};

    @Override
    public void onListenerConnected() {
        try {
            for (StatusBarNotification n : getActiveNotifications()) onNotificationPosted(n);
        } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isNav(sbn.getPackageName())) return;
        Bundle x = sbn.getNotification().extras;
        if (x == null) return;
        Tablet.Nav n = new Tablet.Nav();
        n.pkg = sbn.getPackageName();
        n.title = str(x.getCharSequence(Notification.EXTRA_TITLE));
        n.text = str(x.getCharSequence(Notification.EXTRA_TEXT));
        n.sub = str(x.getCharSequence(Notification.EXTRA_SUB_TEXT));
        n.big = str(x.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (n.title.isEmpty() && n.text.isEmpty()) return;
        n.icon = bitmap(sbn.getNotification().getLargeIcon());
        Tablet.nav(n);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (isNav(sbn.getPackageName()) && (sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) Tablet.nav(null);
    }

    private static boolean isNav(String pkg) {
        for (String p : NAV_APPS) if (p.equals(pkg)) return true;
        return false;
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString(); }

    private Bitmap bitmap(Icon icon) {
        if (icon == null) return null;
        try {
            Drawable d = icon.loadDrawable(this);
            if (d == null) return null;
            int w = Math.max(1, Math.min(d.getIntrinsicWidth(), 192)), h = Math.max(1, Math.min(d.getIntrinsicHeight(), 192));
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, w, h);
            d.draw(new Canvas(b));
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
