package me.faizal.carhome;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * The drive log (user, 2026-10-30): one line per event, so what happened on a drive can be read afterwards instead of
 * guessed at a desk. Power in / out and what was done about it, the services coming and going, the app shown or hidden,
 * and the pages' own events (screensaver, driving, errors) through Bridge.log.
 * Written only when something happens, nothing runs in between. Kept in the app's external files folder, so it can
 * also be read with adb pull /sdcard/Android/data/me.faizal.carhome/files/drivelog.txt; trimmed to the newest half
 * once it passes 256 KB (a few thousand lines).
 */
final class DriveLog {
    private static final long MAX = 256 * 1024;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
    private static File file;

    private DriveLog() { }

    private static File file(Context c) {
        if (file == null) {
            File dir = c.getExternalFilesDir(null);
            file = new File(dir != null ? dir : c.getFilesDir(), "drivelog.txt");
        }
        return file;
    }

    static synchronized void add(Context c, String what) {
        try {
            File f = file(c.getApplicationContext());
            if (f.length() > MAX) trim(f);
            try (FileOutputStream out = new FileOutputStream(f, true)) {
                out.write((TIME.format(new Date()) + "  " + what.replace('\n', ' ') + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }

    /** The newest n lines, oldest first. */
    static synchronized String tail(Context c, int n) {
        ArrayDeque<String> lines = new ArrayDeque<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(file(c.getApplicationContext())))) {
            for (String l; (l = r.readLine()) != null; ) { lines.addLast(l); if (lines.size() > n) lines.removeFirst(); }
        } catch (Exception ignored) { }
        return String.join("\n", lines);
    }

    /** Keep the newest half, from a line start. */
    private static void trim(File f) throws Exception {
        byte[] keep;
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long from = r.length() / 2;
            r.seek(from);
            r.readLine();                                  // skip the cut line
            keep = new byte[(int) (r.length() - r.getFilePointer())];
            r.readFully(keep);
        }
        try (FileOutputStream out = new FileOutputStream(f, false)) { out.write(keep); }
    }
}
