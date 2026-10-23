package ru.tiggo.cluster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** LOCKED_BOOT_COMPLETED приходит раньше BOOT_COMPLETED; сервис запускает автозапуск один раз. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        EventLog.i("Получено " + intent.getAction());
        if (new Prefs(context).bootEnabled()) {
            ClusterService.start(context, ClusterService.ACTION_BOOT);
        }
    }
}
