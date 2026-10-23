package ru.tiggo.cluster;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Настройки в device-protected storage: доступны до разблокировки (LOCKED_BOOT_COMPLETED). */
final class Prefs {
    static final String DEFAULT_CLUSTER_APP = "ru.yandex.yandexnavi";

    private final SharedPreferences sp;

    Prefs(Context ctx) {
        sp = ctx.getApplicationContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** Приложение для приборной панели (display 1); пусто — «не задано», приборку не трогаем. */
    String clusterApp() { return sp.getString("cluster_app", DEFAULT_CLUSTER_APP); }
    void setClusterApp(String pkg) { sp.edit().putString("cluster_app", pkg).apply(); }

    /**
     * Что сейчас на приборке: при автозапуске — clusterApp, потом — выбранное вручную.
     * Его и переносят ответы приборки RequestNaviAreaDisplay.
     */
    String currentClusterApp() { return sp.getString("current_cluster_app", clusterApp()); }
    void setCurrentClusterApp(String pkg) { sp.edit().putString("current_cluster_app", pkg).apply(); }

    /** Приложение для основного экрана (display 0); пусто — не запускать. */
    String mainApp() { return sp.getString("main_app", ""); }
    void setMainApp(String pkg) { sp.edit().putString("main_app", pkg).apply(); }

    /** Приложения, которые запускаются при старте (музыка и т.п.), по порядку. */
    List<String> autostartApps() {
        String s = sp.getString("autostart_apps", "");
        return s.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }
    void setAutostartApps(List<String> pkgs) {
        sp.edit().putString("autostart_apps", String.join(",", pkgs)).apply();
    }

    boolean bootEnabled() { return sp.getBoolean("boot", true); }
    void setBootEnabled(boolean v) { sp.edit().putBoolean("boot", v).apply(); }

    /** Гасить фоновый TurboDog: иначе его карта перекрывает приложение на приборке. */
    boolean suppressTurboDog() { return sp.getBoolean("suppress_turbodog", true); }
    void setSuppressTurboDog(boolean v) { sp.edit().putBoolean("suppress_turbodog", v).apply(); }

    /** Когда приборка закрывает зону навигации — переносить приложение на основной экран. */
    boolean returnToMain() { return sp.getBoolean("return_to_main", true); }
    void setReturnToMain(boolean v) { sp.edit().putBoolean("return_to_main", v).apply(); }

    /** Действие на удержание кнопки руля: пусто — ничего. */
    static final String ACTION_ALICE = "alice";
    /** Префикс действия «запустить приложение»: «app:ru.yandex.music». */
    static final String ACTION_APP = "app:";

    /**
     * Действие на удержание кнопки руля (keyCode). Для MODE по умолчанию — Алиса: так было,
     * пока вместо выбора стоял переключатель «mode_long_alice».
     */
    String keyAction(int keyCode) {
        String key = "key_action_" + keyCode;
        if (sp.contains(key)) return sp.getString(key, "");
        if (keyCode == ClusterService.KEYCODE_MODE && sp.getBoolean("mode_long_alice", true)) {
            return ACTION_ALICE;
        }
        return "";
    }
    void setKeyAction(int keyCode, String action) {
        sp.edit().putString("key_action_" + keyCode, action).apply();
    }

    /** Fullscreen.OFF / SELECTED / ALL. */
    int fullscreenMode() { return sp.getInt("fullscreen_mode", Fullscreen.OFF); }
    void setFullscreenMode(int v) { sp.edit().putInt("fullscreen_mode", v).apply(); }

    List<String> fullscreenApps() {
        String s = sp.getString("fullscreen_apps", "");
        return s.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }
    void setFullscreenApps(List<String> pkgs) {
        sp.edit().putString("fullscreen_apps", String.join(",", pkgs)).apply();
    }

    /** Пауза после загрузки перед запуском приложений, мс. */
    int bootDelayMs() { return sp.getInt("boot_delay_ms", 4000); }
    void setBootDelayMs(int v) { sp.edit().putInt("boot_delay_ms", v).apply(); }
}
