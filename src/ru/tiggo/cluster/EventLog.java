package ru.tiggo.cluster;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Журнал событий: в logcat (тег ClusterLauncher) и в память для экрана настроек. */
final class EventLog {
    private static final String TAG = "ClusterLauncher";
    private static final int MAX = 200;
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private EventLog() {}

    static synchronized void i(String msg) {
        Log.i(TAG, msg);
        lines.addFirst(fmt.format(new Date()) + "  " + msg);
        while (lines.size() > MAX) lines.removeLast();
    }

    static synchronized String dump() {
        return String.join("\n", lines);
    }
}
