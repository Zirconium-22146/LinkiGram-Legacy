package app.nimarkogram.messenger.diagnostics;

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
 * available on the phone, so the diagnostics screen reads from here instead of
 * asking the user to plug the device into a computer.
 */
public final class LinkiAuthLog {

    private static final int LIMIT = 120;
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

    /** Newest lines first. */
    public static List<String> snapshot(int count) {
        ArrayList<String> out = new ArrayList<>();
        synchronized (lines) {
            Iterator<String> it = lines.descendingIterator();
            while (it.hasNext() && out.size() < count) out.add(it.next());
        }
        return out;
    }
}
