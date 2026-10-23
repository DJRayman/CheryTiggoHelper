package ru.tiggo.cluster;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Фоновые приложения по статистике использования: что выходило на передний план с момента
 * включения магнитолы и сейчас не на экране. Чужие процессы обычному приложению на Android 9
 * не видны, поэтому это лучшее приближение к «запущенным». Доступ к статистике выдаётся
 * из adb: appops set … GET_USAGE_STATS allow (install.sh).
 */
final class BackgroundApps {
    static final class Entry {
        final String pkg;
        final long lastUsed;

        Entry(String pkg, long lastUsed) {
            this.pkg = pkg;
            this.lastUsed = lastUsed;
        }
    }

    /** Когда приложение завершили отсюда: пока его снова не откроют, в списке не показываем. */
    private static final Map<String, Long> stoppedAt = new HashMap<>();

    private BackgroundApps() {}

    static boolean hasAccess(Context ctx) {
        AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                ctx.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /** Пользовательские приложения в фоне, недавно использованные — первыми. */
    static List<Entry> list(Context ctx) {
        long now = System.currentTimeMillis();
        long bootTime = now - SystemClock.elapsedRealtime();
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents events = usm.queryEvents(bootTime, now);

        // Список строится, когда на основном экране мы сами, поэтому всё остальное — в фоне.
        // Флаги «на переднем плане» из событий и accessibility здесь врут: уход прежнего
        // приложения в фон фиксируется уже после нашего onResume.
        Map<String, Long> lastForeground = new HashMap<>();
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForeground.put(e.getPackageName(), e.getTimeStamp());
            }
        }

        PackageManager pm = ctx.getPackageManager();
        List<Entry> result = new ArrayList<>();
        for (Map.Entry<String, Long> item : lastForeground.entrySet()) {
            String pkg = item.getKey();
            if (pkg.equals(ctx.getPackageName())) continue;
            Long stopped = stoppedAt.get(pkg);
            if (stopped != null && stopped >= item.getValue()) continue;
            if (isSystem(pm, pkg)) continue;
            result.add(new Entry(pkg, item.getValue()));
        }
        result.sort((a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        return result;
    }

    /** Завершает фоновые процессы приложения (работает, только пока оно не на экране). */
    static void stop(Context ctx, String pkg) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(pkg);
        stoppedAt.put(pkg, System.currentTimeMillis());
        EventLog.i("Завершено: " + pkg);
    }

    private static boolean isSystem(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException ex) {
            return true;
        }
    }
}
