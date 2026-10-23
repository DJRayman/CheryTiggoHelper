package ru.tiggo.cluster;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

/**
 * Скрытие штатных приложений через DevicePolicyManager.setApplicationHidden. Скрытое
 * приложение система останавливает и убирает из меню, но не удаляет — возвращается тем же
 * вызовом. Работает, только если TiggoHelper — владелец устройства:
 *   adb shell dpm set-device-owner ru.tiggo.cluster/.AdminReceiver
 * (Android разрешает это, только пока на устройстве нет учётных записей.)
 */
final class AppControl {
    /** Пакет, подпись и пояснение переключателя на вкладке «Система». */
    static final String[][] STOCK_APPS = {
            {"com.ivi.cerence", "Голосовой помощник",
                    "Cerence: ~380 МБ памяти и треть ядра в простое. Кнопка помощника на руле "
                            + "перестанет работать; Wi-Fi открывается кнопкой выше"},
            {"com.google.android.projection.sink", "Android Auto", null},
            {"com.desaysv.vehicle.carplayapp", "Apple CarPlay", null},
    };

    private AppControl() {}

    private static DevicePolicyManager dpm(Context ctx) {
        return (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    private static ComponentName admin(Context ctx) {
        return new ComponentName(ctx, AdminReceiver.class);
    }

    static boolean isDeviceOwner(Context ctx) {
        return dpm(ctx).isDeviceOwnerApp(ctx.getPackageName());
    }

    static boolean isEnabled(Context ctx, String pkg) {
        return !dpm(ctx).isApplicationHidden(admin(ctx), pkg);
    }

    /** false — система отказала (например, пакет защищён). */
    static boolean setEnabled(Context ctx, String pkg, boolean enabled) {
        boolean ok = dpm(ctx).setApplicationHidden(admin(ctx), pkg, !enabled);
        EventLog.i((enabled ? "Возвращено: " : "Скрыто: ") + pkg + (ok ? "" : " — отказ системы"));
        if (ok && CERENCE.equals(pkg)) setVoiceEngine(enabled);
        return ok;
    }

    private static final String CERENCE = "com.ivi.cerence";

    /**
     * Движок распознавания Cerence — нативный natp (~260 МБ), его запускает init, а не
     * приложение, поэтому скрытие пакета его не останавливает. В init.g6sh.rc на
     * persist.sys.start.natp=1 запускается start_natp, на =0 — stop_natp (срабатывает и
     * на повторную запись того же значения). При загрузке natp кто-то запускает напрямую
     * (start start_natp), поэтому после загрузки ClusterService вызывает это снова.
     */
    static void setVoiceEngine(boolean running) {
        try {
            Class.forName("android.os.SystemProperties")
                    .getMethod("set", String.class, String.class)
                    .invoke(null, "persist.sys.start.natp", running ? "1" : "0");
            EventLog.i("natp: " + (running ? "запуск" : "остановка"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            EventLog.i("natp: не удалось изменить свойство: " + e);
        }
    }

    /** После загрузки: если Cerence отключён, гасим запущенный при загрузке natp. */
    static void stopVoiceEngineIfDisabled(Context ctx) {
        if (isDeviceOwner(ctx) && !isEnabled(ctx, CERENCE)) setVoiceEngine(false);
    }

    /** Возвращает все скрытые приложения и снимает права владельца устройства. */
    static void releaseOwnership(Context ctx) {
        for (String[] app : STOCK_APPS) {
            if (!isEnabled(ctx, app[0])) setEnabled(ctx, app[0], true);
        }
        dpm(ctx).clearDeviceOwnerApp(ctx.getPackageName());
        EventLog.i("Права владельца устройства сняты");
    }
}
