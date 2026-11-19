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
    private static final String STEPS_NAME = "steps.txt";

    /** Steps of the current process, newest last: a native abort leaves no stack trace at all. */
    private static final StringBuilder steps = new StringBuilder();

    private static volatile boolean installed;
    private static volatile Context appContext;

    private LegacyCrashLog() {
    }

    public static synchronized void install(Context context) {
        if (installed || context == null) {
            return;
        }
        final Context app = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        appContext = app;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                step("crash in " + (thread != null ? thread.getName() : "?") + ": " + throwable);
                write(app, thread, throwable);
            } catch (Throwable ignored) {
                // a crash reporter must never crash itself
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
        installed = true;
        step("launch " + BuildVars.BUILD_VERSION_STRING + " (code " + NimarkoConfig.VERSION_CODE + ")"
                + " diag=3 targets=media,download,internal");
    }

    /**
     * Keeps a breadcrumb of the current process in Download/.../steps.txt. Written eagerly, so the
     * last written step survives a crash that kills the process without an uncaught exception
     * (a native abort of the blur engine, for instance).
     */
    public static void step(String what) {
        synchronized (steps) {
            steps.append(new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()))
                 .append("  ").append(what).append('\n');
            final byte[] bytes = steps.toString().getBytes();
            final Context context = appContext;
            if (context != null) {
                write(context, STEPS_NAME, bytes);
            }
        }
    }

    /** Writes the trace of an exception caught on purpose, where the default handler would not see it. */
    public static void logThrowable(String where, Throwable t) {
        final Context context = appContext;
        if (context == null) {
            return;
        }
        write(context, where + ".txt", t);
    }

    private static void write(Context context, Thread thread, Throwable t) {
        write(context, FILE_NAME, buildReport(thread, t, null));
    }

    private static byte[] buildReport(Thread thread, Throwable t, String place) {
        final StringWriter buffer = new StringWriter();
        final PrintWriter out = new PrintWriter(buffer);
        out.println("=== LinkiGram Legacy crash ===");
        out.println("Time: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(new Date()));
        out.println("Thread: " + (thread != null ? thread.getName() : "(unknown)"));
        out.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        out.println("App: " + NimarkoConfig.APP_NAME + " " + BuildVars.BUILD_VERSION_STRING
                + " (" + NimarkoConfig.VERSION_CODE + ")");
        if (place != null) {
            out.println("Place: " + place);
        }
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

        return buffer.toString().getBytes();
    }

    private static void write(Context context, String fileName, Throwable t) {
        write(context, fileName, buildReport(Thread.currentThread(), t, fileName));
    }

    private static void write(Context context, String fileName, byte[] bytes) {
        // The app media folder needs no permission and no MediaStore at all: on Android 10+ it is
        // the only external place the app may always write to, so it goes first. The Download
        // copy is kept for the file manager, the internal one as the last resort.
        final boolean media = writeToMediaDir(context, fileName, bytes);
        boolean download = writeWithMediaStore(context, fileName, bytes);
        if (!download) {
            download = writeWithFile(fileName, bytes);
        }
        final boolean internal = writeToInternalDir(context, fileName, bytes);
        if (!media && !download && !internal) {
            Log.e(TAG, "nowhere to write " + fileName);
        }
    }

    private static boolean writeToMediaDir(Context context, String fileName, byte[] bytes) {
        try {
            final File[] dirs = context.getExternalMediaDirs();
            if (dirs == null || dirs.length == 0 || dirs[0] == null) {
                return false;
            }
            final File dir = new File(dirs[0], "diagnostics");
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            final File file = new File(dir, fileName);
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(bytes);
            }
            Log.e(TAG, "written " + file);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "media dir write failed", e);
            return false;
        }
    }

    private static boolean writeToInternalDir(Context context, String fileName, byte[] bytes) {
        try {
            final File file = new File(context.getFilesDir(), fileName);
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(bytes);
            }
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "internal write failed", e);
            return false;
        }
    }

    private static boolean writeWithMediaStore(Context context, String fileName, byte[] bytes) {
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
                        new String[]{fileName, relativePath});
            } catch (Throwable ignored) {
            }
            final ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
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

    private static boolean writeWithFile(String fileName, byte[] bytes) {
        try {
            final File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            final File file = new File(dir, fileName);
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(bytes);
            }
            Log.e(TAG, "written " + file);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "file write failed", e);
            return false;
        }
    }
}
