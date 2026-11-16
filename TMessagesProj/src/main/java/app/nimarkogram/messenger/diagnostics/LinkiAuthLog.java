package app.nimarkogram.messenger.diagnostics;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * A tiny in-memory journal of the last sign-in steps.
 *
 * Login gives no feedback when auth.sendCode never answers, and logcat is not
 * available on the phone, so the journal is shown on the sign-in screen and also
 * copied to Download/LinkiGram-Legacy-2.6/auth.log, which any file manager can reach.
 */
public final class LinkiAuthLog {

    private static final int LIMIT = 120;
    private static final String FOLDER = "LinkiGram-Legacy-2.6";
    private static final String FILE_NAME = "auth.log";
    private static final ArrayDeque<String> lines = new ArrayDeque<>(LIMIT);
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private LinkiAuthLog() {
    }

    public static void add(String message) {
        if (message == null) return;
        synchronized (lines) {
            if (lines.size() >= LIMIT) lines.removeFirst();
            lines.addLast(TIME.format(new Date()) + "  " + message);
        }
    }

    public static void clear() {
        synchronized (lines) {
            lines.clear();
        }
    }

    /** Oldest first. */
    public static List<String> all() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    /** Newest lines first. */
    public static List<String> snapshot(int count) {
        ArrayList<String> out = new ArrayList<>();
        synchronized (lines) {
            Iterator<String> it = lines.descendingIterator();
            while (it.hasNext() && out.size() < count) out.add(it.next());
        }
        return out;
    }

    public static String text() {
        StringBuilder sb = new StringBuilder();
        for (String line : all()) sb.append(line).append('\n');
        return sb.toString();
    }

    /**
     * Copies the journal to Download/LinkiGram-Legacy-2.6/auth.log so it can be sent
     * without a computer. Uses MediaStore on Android 10+, a plain file below that.
     */
    public static void dumpToDownloads(Context context) {
        if (context == null) return;
        final String body = text();
        if (body.isEmpty()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri target = null;
                try (Cursor c = context.getContentResolver().query(collection, new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                        new String[]{FILE_NAME, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER}, null)) {
                    if (c != null && c.moveToFirst()) {
                        target = android.content.ContentUris.withAppendedId(collection, c.getLong(0));
                    }
                } catch (Throwable ignored) {
                }
                if (target == null) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
                    target = context.getContentResolver().insert(collection, values);
                }
                if (target != null) {
                    try (OutputStream os = context.getContentResolver().openOutputStream(target, "wt")) {
                        if (os != null) os.write(body.getBytes("UTF-8"));
                    }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(new File(dir, FILE_NAME), false)) {
                    fos.write(body.getBytes("UTF-8"));
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
