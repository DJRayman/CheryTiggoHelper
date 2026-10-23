package ru.tiggo.cluster;

import android.content.Context;
import android.provider.Settings;

import java.util.List;

/**
 * Полноэкранный режим через системную настройку policy_control (Android 9, удалена в 11):
 * «immersive.full=pkg1,pkg2» скрывает панели у этих пакетов, «*» — у всех, «-pkg» — исключение.
 * У Desay «навигационная панель» — левый док с роботом (288 px), «статус-бар» — полоса справа.
 * Панели возвращаются свайпом от края. Нужен WRITE_SECURE_SETTINGS (pm grant из install.sh).
 */
final class Fullscreen {
    static final int OFF = 0;
    static final int SELECTED = 1;
    static final int ALL = 2;

    private static final String KEY = "policy_control";
    /** В режиме «для всех» рабочий стол оставляем с доком: на нём кнопки «Домой», музыка, телефон. */
    private static final String LAUNCHER = "com.desaysv.launcher";

    private Fullscreen() {}

    static boolean canApply(Context ctx) {
        return ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Записывает режим в систему; false — нет разрешения. */
    static boolean apply(Context ctx, int mode, List<String> apps) {
        String value;
        if (mode == ALL) {
            value = "immersive.full=*,-" + LAUNCHER;
        } else if (mode == SELECTED && !apps.isEmpty()) {
            value = "immersive.full=" + String.join(",", apps);
        } else {
            value = null;
        }
        try {
            Settings.Global.putString(ctx.getContentResolver(), KEY, value);
            EventLog.i("Полный экран: " + (value != null ? value : "выключен"));
            return true;
        } catch (SecurityException e) {
            EventLog.i("Полный экран: нет разрешения WRITE_SECURE_SETTINGS");
            return false;
        }
    }
}
