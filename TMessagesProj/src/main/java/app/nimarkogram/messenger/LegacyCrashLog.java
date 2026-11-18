package app.nimarkogram.messenger;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import org.telegram.messenger.BuildVars;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The phone is the only place where a crash of the app can be looked at: there is no adb and
 * no developer machine next to it. The last stack trace is therefore put into the delivery
 * folder of the fork, Download/LinkiGram-Legacy-2.6/crash.txt, where any file manager reaches it.
 *
 * It only writes one file, replaces the previous one each time and is not a general purpose
 * logger - this is a diagnostic aid of the legacy fork, not a feature of the client.
 */
public final class LegacyCrashLog {

    private static final String TAG = "legacy-crash";
    private static final String FOLDER = "LinkiGram-Legacy-2.6";
    private static final String FILE_NAME = "crash.txt";

    private static volatile boolean installed;

    private LegacyCrashLog() {
    }

    public static synchronized void install(Context context) {
        if (installed || context == null) {
            return;
        }
        final Context app = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                write(app, thread, throwable);
            } catch (Throwable ignored) {
                // a crash reporter must never crash itself
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
        installed = true;
    }

    private static void write(Context context, Thread thread, Throwable t) {
        final StringWriter buffer = new StringWriter();
        final PrintWriter out = new PrintWriter(buffer);
        out.println("=== LinkiGram Legacy crash ===");
        out.println("Time: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(new Date()));
        out.println("Thread: " + (thread != null ? thread.getName() : "(unknown)"));
        out.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        out.println("App: " + NimarkoConfig.APP_NAME + " " + BuildVars.BUILD_VERSION_STRING
                + " (" + NimarkoConfig.VERSION_CODE + ")");
        out.println();
        if (t != null) {
            t.printStackTrace(out);
            out.println();
            out.println("=== causes ===");
            Throwable cause = t.getCause();
            int depth = 0;
            while (cause != null && depth++ < 10) {
                cause.printStackTrace(out);
                out.println();
                cause = cause.getCause();
            }
        } else {
            out.println("(no throwable)");
        }
        out.flush();

        final byte[] bytes;
        try {
            bytes = buffer.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return;
        }

        if (writeWithMediaStore(context, bytes)) {
            return;
        }
        writeWithFile(bytes);
    }

    private static boolean writeWithMediaStore(Context context, byte[] bytes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        try {
            final ContentResolver resolver = context.getContentResolver();
            final String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/";
            final Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            try {
                resolver.delete(
                        collection,
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                        new String[]{FILE_NAME, relativePath});
            } catch (Throwable ignored) {
            }
            final ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            final Uri uri = resolver.insert(collection, values);
            if (uri == null) {
                return false;
            }
            try (OutputStream stream = resolver.openOutputStream(uri)) {
                if (stream == null) {
                    return false;
                }
                stream.write(bytes);
            }
            Log.e(TAG, "crash written to " + uri);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "MediaStore write failed", e);
            return false;
        }
    }

    private static void writeWithFile(byte[] bytes) {
        try {
            final File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            final File file = new File(dir, FILE_NAME);
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(bytes);
            }
            Log.e(TAG, "crash written to " + file);
        } catch (Throwable e) {
            Log.e(TAG, "file write failed", e);
        }
    }
}
