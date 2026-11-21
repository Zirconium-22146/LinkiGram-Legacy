package app.nimarkogram.messenger.utils;

import android.os.Build;
import android.os.Process;
import android.os.SystemClock;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * How long the launch path really takes, written where we can read it.
 *
 * <p>Guessing which part of the startup is expensive is how apps get slower, so the
 * interesting points along the way drop a timestamp here and the file lands in the
 * app's own media directory ({@code Android/media/&lt;package&gt;/diagnostics/startup.txt}).
 * Timestamps count from process start, not from the first call, so they include the
 * work the system does before {@code Application.onCreate}.
 *
 * <p>Costs one list append per mark and one small file write, off the main thread.
 */
public final class StartupTrace {

    private static final ArrayList<String> marks = new ArrayList<>();
    private static final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "startup-trace");
        thread.setDaemon(true);
        return thread;
    });
    private static final long origin = processStart();

    private StartupTrace() {
    }

    private static long processStart() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                long start = Process.getStartElapsedRealtime();
                if (start > 0) {
                    return start;
                }
            }
        } catch (Throwable ignore) {
        }
        return SystemClock.elapsedRealtime();
    }

    public static void mark(String name) {
        long now = SystemClock.elapsedRealtime();
        synchronized (marks) {
            marks.add(String.format(Locale.US, "%6d ms  %s", now - origin, name));
        }
        try {
            writer.submit(StartupTrace::write);
        } catch (Throwable ignore) {
        }
    }

    private static void write() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("started ")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                    .append('\n');
            synchronized (marks) {
                for (int i = 0; i < marks.size(); i++) {
                    sb.append(marks.get(i)).append('\n');
                }
            }
            File dir = null;
            try {
                File[] dirs = ApplicationLoader.applicationContext.getExternalMediaDirs();
                if (dirs != null && dirs.length > 0 && dirs[0] != null) {
                    dir = new File(dirs[0], "diagnostics");
                }
            } catch (Throwable ignore) {
            }
            if (dir == null) {
                dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "diagnostics");
            }
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                return;
            }
            try (FileWriter out = new FileWriter(new File(dir, "startup.txt"), false)) {
                out.write(sb.toString());
            }
        } catch (Throwable t) {
            FileLog.e("startup-trace: " + t);
        }
    }
}
