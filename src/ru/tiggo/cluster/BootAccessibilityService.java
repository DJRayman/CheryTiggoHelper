package ru.tiggo.cluster;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * Прошивка Desay при загрузке запускает по BOOT_COMPLETED только приложения из своего
 * белого списка («Skip starting app, white list not found»). Включённые accessibility-сервисы
 * система поднимает сама, в обход этого списка, — через них и делаем автозапуск.
 *
 * Заодно сервис отвечает, видно ли приложение на основном экране: на Android 9 список окон
 * accessibility содержит только display 0.
 */
public class BootAccessibilityService extends AccessibilityService {
    /** Подключение позже этого срока после загрузки — перезапуск процесса, а не включение машины. */
    private static final long BOOT_WINDOW_MS = 3 * 60 * 1000;

    private static volatile BootAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        instance = this;
        boolean afterBoot = SystemClock.elapsedRealtime() < BOOT_WINDOW_MS;
        EventLog.i("Accessibility-сервис подключён" + (afterBoot ? " после загрузки" : ""));
        if (afterBoot && new Prefs(this).bootEnabled()) {
            ClusterService.start(this, ClusterService.ACTION_BOOT);
        } else {
            ClusterService.start(this, null);
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /**
     * Есть ли у pkg окно приложения на основном экране. Список окон берём в момент вопроса:
     * отслеживание по событиям врало — события склеиваются, их шлют и тосты, и запуск на приборке.
     * Если сервис не подключён (например, его временно отключил uiautomator), отвечаем «да»:
     * лишний рабочий стол на основном экране лучше, чем карта, оставшаяся на двух экранах.
     */
    static boolean isOnMainScreen(String pkg) {
        BootAccessibilityService service = instance;
        if (service == null) return true;
        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows.isEmpty()) return true;
        for (AccessibilityWindowInfo w : windows) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            CharSequence p = root.getPackageName();
            root.recycle();
            if (p != null && pkg.contentEquals(p)) return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
