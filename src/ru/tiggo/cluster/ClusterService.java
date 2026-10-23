package ru.tiggo.cluster;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.KeyEvent;

/**
 * Держит приложение на приборке Chery Tiggo 8 Pro Max (Desay SV, Android 9).
 *
 * Приборка показывает display 1, только пока у неё открыта «зона навигации».
 * Её открывает системный com.desaysv.mapservice, когда получает от навигации
 * broadcast turbodog.navigation.system.message (CODE=207); ответ приборки приходит
 * как desay.broadcast.map.meter.interaction с RequestNaviAreaDisplay=1|0.
 * Фоновый TurboDog на этот ответ рисует свою карту поверх display 1 — его гасим.
 */
public class ClusterService extends Service {
    static final String ACTION_BOOT = "ru.tiggo.cluster.BOOT";
    static final String ACTION_SHOW_CLUSTER = "ru.tiggo.cluster.SHOW_CLUSTER";
    static final String ACTION_SHOW_MAIN = "ru.tiggo.cluster.SHOW_MAIN";
    /** Для ACTION_SHOW_CLUSTER: какое приложение вывести на приборку. */
    static final String EXTRA_PACKAGE = "pkg";

    private static final String NAVI_TO_SYSTEM = "turbodog.navigation.system.message";
    private static final String METER_INTERACTION = "desay.broadcast.map.meter.interaction";
    private static final String CAR_LAN = "SVCarLanManager";
    private static final String TURBODOG = "com.astrob.turbodog";
    private static final String YANDEX_NAVI = "ru.yandex.yandexnavi";
    private static final int CLUSTER_DISPLAY = 1;
    private static final int MAIN_DISPLAY = 0;
    /** Коды прошивки Desay, в SDK их нет: MODE (scanCode 779), голосовой помощник (793). */
    static final int KEYCODE_MODE = 297;
    static final int KEYCODE_VR = 292;
    /** 4-й повтор — примерно 0,8 с удержания. */
    private static final int LONG_PRESS_REPEATS = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private boolean bootSequenceStarted;
    private int lastArea = -1;
    private long lastAreaAt;
    private KeyPolicyClient keyClient;
    /** downTime нажатия, на котором действие уже выполнено: одно удержание — один вызов. */
    private long longPressDownTime = -1;

    private final BroadcastReceiver meterReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int area = parseNaviArea(intent.getExtras());
            if (area < 0) return;
            // mapservice дублирует каждое событие 3–4 раза в пределах ~0,5 с.
            long now = SystemClock.elapsedRealtime();
            if (area == lastArea && now - lastAreaAt < 3000) return;
            lastArea = area;
            lastAreaAt = now;
            if (area == 1) {
                EventLog.i("Приборка открыла зону навигации");
                showOnCluster();
            } else if (area == 0) {
                EventLog.i("Приборка закрыла зону навигации");
                if (prefs.returnToMain()) showOnMain();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        startForeground(1, buildNotification());
        // Ответ приборки шлётся неявным broadcast'ом: на Android 9 его получает
        // только динамический receiver живого процесса, не статический из манифеста.
        IntentFilter filter = new IntentFilter(METER_INTERACTION);
        filter.addAction(CAR_LAN);
        registerReceiver(meterReceiver, filter);
        EventLog.i("Сервис запущен");

        // Кнопки руля приходят в общей очереди key_policy: ответ там игнорируется, поэтому
        // штатное действие отменить нельзя — только добавить своё. Первое место в очереди
        // («pre_handle_appmode») занято svcarinfo. Громкость сюда не доходит вовсе.
        keyClient = new KeyPolicyClient(getPackageName(), getPackageName(), (event, slot) -> {
            handler.post(() -> onSteeringKey(event));
            return false;
        });
        EventLog.i("key_policy: регистрация " + (keyClient.register() ? "отправлена" : "не удалась"));

        // Пустой display 1 показывает копию основного экрана — закрываем его сразу,
        // не дожидаясь событий приборки.
        if (prefs.currentClusterApp().isEmpty() && findDisplay(CLUSTER_DISPLAY) != null) {
            showBlankOnCluster();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_BOOT.equals(action)) {
            if (!bootSequenceStarted) {
                bootSequenceStarted = true;
                handler.postDelayed(this::runBootSequence, prefs.bootDelayMs());
                // natp стартует на ~16-й секунде загрузки; второй раз — на случай задержки.
                for (long delay : new long[] {20000, 40000}) {
                    handler.postDelayed(() -> AppControl.stopVoiceEngineIfDisabled(this), delay);
                }
            }
        } else if (ACTION_SHOW_CLUSTER.equals(action)) {
            String pkg = intent.getStringExtra(EXTRA_PACKAGE);
            if (pkg != null) prefs.setCurrentClusterApp(pkg);
            if (prefs.currentClusterApp().isEmpty()) return START_STICKY;
            requestNaviArea();
            showOnCluster();
        } else if (ACTION_SHOW_MAIN.equals(action)) {
            showOnMain();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        keyClient.unregister();
        unregisterReceiver(meterReceiver);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Действие на удержание MODE и кнопки помощника. Короткое нажатие у обеих забирает
     * штатный обработчик (MODE — источник звука), а отпускание короткого нажатия до нас не
     * доходит, поэтому таймер «нажал — отпустил» не годится. Удержание узнаём по повторам:
     * их шлёт только зажатая кнопка (~через 0,5 с, затем каждые ~75 мс).
     */
    private void onSteeringKey(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KEYCODE_MODE && code != KEYCODE_VR) return;
        if (event.getAction() != KeyEvent.ACTION_DOWN
                || event.getRepeatCount() < LONG_PRESS_REPEATS
                || event.getDownTime() == longPressDownTime) {
            return;
        }
        String action = prefs.keyAction(code);
        if (action.isEmpty()) return;
        longPressDownTime = event.getDownTime();
        EventLog.i("Удержание " + (code == KEYCODE_MODE ? "MODE" : "помощника") + " → " + action);
        if (action.equals(Prefs.ACTION_ALICE)) {
            openAlice();
        } else if (action.startsWith(Prefs.ACTION_APP)) {
            launch(action.substring(Prefs.ACTION_APP.length()), MAIN_DISPLAY);
        }
    }

    private void openAlice() {
        Intent alice = new Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://ask_alice"))
                .setPackage(YANDEX_NAVI)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(alice);
        } catch (ActivityNotFoundException e) {
            EventLog.i("Яндекс Навигатор не установлен");
        }
    }

    /** Автозапуск: фоновые приложения, затем основной экран, затем приборка. */
    private void runBootSequence() {
        EventLog.i("Автозапуск");
        // Одна задача не может быть на двух дисплеях: приложение приборки на основной
        // экран не запускаем, иначе оно там и останется.
        String cluster = prefs.clusterApp();
        prefs.setCurrentClusterApp(cluster);
        long t = 0;
        for (String pkg : prefs.autostartApps()) {
            if (pkg.equals(cluster)) continue;
            handler.postDelayed(() -> launch(pkg, MAIN_DISPLAY), t);
            t += 1500;
        }
        String main = prefs.mainApp();
        if (!main.isEmpty() && !main.equals(cluster)) {
            handler.postDelayed(() -> launch(main, MAIN_DISPLAY), t);
            t += 1500;
        }
        handler.postDelayed(() -> {
            // Без приложения для приборки зону карты не просим, а на display 1 ставим
            // чёрный экран — иначе там отражался бы основной экран.
            if (!cluster.isEmpty()) requestNaviArea();
            waitForClusterDisplay(15);
        }, t);
    }

    /** display 1 появляется не сразу после загрузки — ждём до attempts секунд. */
    private void waitForClusterDisplay(int attempts) {
        if (findDisplay(CLUSTER_DISPLAY) != null) {
            showOnCluster();
        } else if (attempts > 0) {
            handler.postDelayed(() -> waitForClusterDisplay(attempts - 1), 1000);
        } else {
            EventLog.i("Дисплей приборки так и не появился");
        }
    }

    /** То же, что шлёт TurboDog при старте: mapservice просит приборку открыть зону навигации. */
    private void requestNaviArea() {
        Intent ping = new Intent(NAVI_TO_SYSTEM);
        ping.putExtra("CODE", 207);
        ping.putExtra("DATA", 0);
        sendBroadcast(ping);
        EventLog.i("Запрос зоны навигации (CODE=207)");
    }

    private void showOnCluster() {
        String pkg = prefs.currentClusterApp();
        if (pkg.isEmpty()) {
            showBlankOnCluster();
            return;
        }
        if (BootAccessibilityService.isOnMainScreen(pkg)) {
            // Android 9 переносит существующую задачу на другой дисплей, только если она
            // не на переднем плане: сначала убираем её с основного экрана рабочим столом.
            EventLog.i(pkg + " на основном экране — убираю рабочим столом");
            launchHome(MAIN_DISPLAY);
            handler.postDelayed(() -> launch(pkg, CLUSTER_DISPLAY), 500);
        } else {
            launch(pkg, CLUSTER_DISPLAY);
        }
        // TurboDog рисует поверх после ответа приборки, который приходит с задержкой.
        if (prefs.suppressTurboDog()) {
            for (long delay : new long[] {1000, 3000, 6000}) {
                handler.postDelayed(this::stopTurboDog, delay);
            }
        }
    }

    /**
     * Переносим приложение с приборки на основной экран. Сначала закрываем его на приборке
     * чёрным экраном: Android 9 переносит задачу на другой дисплей, только если она не на
     * переднем плане, а пустая приборка показала бы копию основного экрана.
     */
    private void showOnMain() {
        String pkg = prefs.currentClusterApp();
        if (pkg.isEmpty()) return;
        showBlankOnCluster();
        handler.postDelayed(() -> launch(pkg, MAIN_DISPLAY), 300);
    }

    private void showBlankOnCluster() {
        Intent blank = new Intent(this, ClusterBlankActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startOnDisplay(blank, CLUSTER_DISPLAY);
        EventLog.i("Приборка: чёрный экран");
    }

    private void stopTurboDog() {
        try {
            if (!getPackageManager().getApplicationInfo(TURBODOG, 0).enabled) return;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return;
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        am.killBackgroundProcesses(TURBODOG);
        EventLog.i("killBackgroundProcesses(TurboDog)");
    }

    private void launch(String pkg, int displayIndex) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            EventLog.i("Не установлено: " + pkg);
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startOnDisplay(intent, displayIndex);
        EventLog.i("Запуск " + pkg + " на display " + displayIndex);
    }

    private void launchHome(int displayIndex) {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startOnDisplay(home, displayIndex);
    }

    private void startOnDisplay(Intent intent, int displayIndex) {
        Display display = findDisplay(displayIndex);
        if (display == null) {
            startActivity(intent);
            return;
        }
        Bundle options = ActivityOptions.makeBasic()
                .setLaunchDisplayId(display.getDisplayId()).toBundle();
        startActivity(intent, options);
    }

    private Display findDisplay(int index) {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();
        return index < displays.length ? displays[index] : null;
    }

    /** RequestNaviAreaDisplay приходит то int-полем, то внутри JSON-строки. */
    static int parseNaviArea(Bundle extras) {
        if (extras == null) return -1;
        for (String key : new String[] {"RequestNaviAreaDisplay", "map_area", "map_status"}) {
            Object v = extras.get(key);
            if (v instanceof Integer) return (Integer) v;
        }
        String s = extras.toString();
        if (s.contains("RequestNaviAreaDisplay=1") || s.contains("\"RequestNaviAreaDisplay\":1")) return 1;
        if (s.contains("RequestNaviAreaDisplay=0") || s.contains("\"RequestNaviAreaDisplay\":0")) return 0;
        return -1;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                "cluster", "TiggoHelper", NotificationManager.IMPORTANCE_MIN));
        return new Notification.Builder(this, "cluster")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("TiggoHelper работает")
                .build();
    }

    static void start(Context ctx, String action) {
        Intent i = new Intent(ctx, ClusterService.class).setAction(action);
        ctx.startForegroundService(i);
    }

    static void showOnCluster(Context ctx, String pkg) {
        Intent i = new Intent(ctx, ClusterService.class).setAction(ACTION_SHOW_CLUSTER)
                .putExtra(EXTRA_PACKAGE, pkg);
        ctx.startForegroundService(i);
    }
}
