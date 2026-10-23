package ru.tiggo.cluster;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String[] TABS = {"Экраны", "Полный экран", "Автозапуск", "Процессы",
            "Кнопки руля", "Система", "Журнал"};
    private static final int TAB_FULLSCREEN = 1;
    private static final int TAB_PROCESSES = 3;
    private static final int TAB_KEYS = 4;
    private static final int TAB_SYSTEM = 5;
    private static final int TAB_LOG = 6;

    /** Приложение из меню запуска: подпись, пакет, значок. */
    private static final class App {
        final String label;
        final String pkg;
        final Drawable icon;

        App(String label, String pkg, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.icon = icon;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<App> apps = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private Prefs prefs;
    private Ui ui;
    private FrameLayout content;
    private int currentTab;
    private TextView logView;

    private final Runnable logRefresher = new Runnable() {
        @Override
        public void run() {
            if (logView == null) return;
            String log = EventLog.dump();
            logView.setText(log.isEmpty() ? "Событий пока нет" : log);
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        ui = new Ui(this);
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        loadLaunchableApps();
        setContentView(buildRoot());
        currentTab = getPreferences(MODE_PRIVATE).getInt("tab", 0);
        showTab(currentTab);
        // Сервис должен жить, чтобы слышать ответы приборки, даже без автозапуска.
        ClusterService.start(this, null);
    }

    /** Карточка памяти на вкладке «Процессы»; перерисовывается раз в 3 с. */
    private LinearLayout memoryCard;

    private final Runnable memoryRefresher = new Runnable() {
        @Override
        public void run() {
            if (memoryCard == null) return;
            fillMemoryCard(memoryCard);
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(logRefresher);
        handler.removeCallbacks(memoryRefresher);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentTab == TAB_LOG) logRefresher.run();
        // Список фоновых меняется, пока нас не видно: при возврате строим вкладку заново.
        if (currentTab == TAB_PROCESSES) showTab(TAB_PROCESSES);
    }

    private void loadLaunchableApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        found.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        List<String> seen = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<ResolveInfo> unique = new ArrayList<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            labels.add(ri.loadLabel(pm).toString());
            unique.add(ri);
        }
        // Разные приложения с одним названием (Яндекс Навигатор и NavPP — оба «Навигатор»)
        // различаем по пакету во всех списках.
        for (int i = 0; i < unique.size(); i++) {
            String label = labels.get(i);
            String pkg = unique.get(i).activityInfo.packageName;
            if (labels.indexOf(label) != labels.lastIndexOf(label)) label += " · " + pkg;
            apps.add(new App(label, pkg, unique.get(i).loadIcon(pm)));
        }
    }

    private App findApp(String pkg) {
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    // ---------- каркас: панель вкладок слева, страница справа ----------

    private View buildRoot() {
        LinearLayout root = ui.row();
        root.setGravity(Gravity.NO_GRAVITY);
        root.setBackgroundColor(Ui.BG);

        LinearLayout rail = ui.column();
        rail.setPadding(ui.dp(20), ui.dp(28), ui.dp(12), ui.dp(20));
        TextView title = ui.text("TiggoHelper", 26, Ui.TEXT, true);
        title.setPadding(ui.dp(16), 0, 0, ui.dp(4));
        rail.addView(title);
        TextView version = ui.text("Chery Tiggo 8 Pro Max", 13, Ui.TEXT_DIM, false);
        version.setPadding(ui.dp(16), 0, 0, ui.dp(24));
        rail.addView(version);
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            TextView tab = ui.text(TABS[i], 18, Ui.TEXT_DIM, true);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(ui.dp(16), 0, ui.dp(16), 0);
            tab.setMinHeight(ui.dp(56));
            tab.setOnClickListener(v -> showTab(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = ui.dp(6);
            rail.addView(tab, lp);
            tabViews.add(tab);
        }
        root.addView(rail, new LinearLayout.LayoutParams(ui.dp(280), -1));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1f));
        return root;
    }

    private void showTab(int index) {
        currentTab = index;
        getPreferences(MODE_PRIVATE).edit().putInt("tab", index).apply();
        for (int i = 0; i < tabViews.size(); i++) {
            boolean selected = i == index;
            TextView tab = tabViews.get(i);
            tab.setTextColor(selected ? Ui.ACCENT : Ui.TEXT_DIM);
            tab.setBackground(selected ? ui.rounded(Ui.SURFACE_HIGH, 14) : ui.pressable(Ui.BG, 14));
        }
        handler.removeCallbacks(logRefresher);
        handler.removeCallbacks(memoryRefresher);
        logView = null;
        memoryCard = null;
        content.removeAllViews();
        View page;
        switch (index) {
            case 0: page = screensPage(); break;
            case 1: page = fullscreenPage(); break;
            case 2: page = autostartPage(); break;
            case 3: page = processesPage(); break;
            case 4: page = keysPage(); break;
            case 5: page = systemPage(); break;
            default: page = logPage(); break;
        }
        content.addView(page);
        if (index == TAB_LOG) logRefresher.run();
        if (index == TAB_PROCESSES) memoryRefresher.run();
    }

    /** Прокручиваемая страница с полями; в неё кладутся заголовок и карточки. */
    private LinearLayout page(ScrollView[] holder) {
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(24), ui.dp(28), ui.dp(40), ui.dp(28));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        holder[0] = scroll;
        return body;
    }

    // ---------- вкладки ----------

    private View screensPage() {
        ScrollView[] holder = new ScrollView[1];
        LinearLayout body = page(holder);
        body.addView(ui.pageHeader("Экраны",
                "Что показывать на приборной панели и на основном экране"));

        LinearLayout apps = ui.card("Приложения");
        apps.addView(appChooser("Приборная панель при запуске", prefs.clusterApp(), "Не задано", pkg -> {
            prefs.setClusterApp(pkg);
            showTab(0);
        }));
        apps.addView(ui.divider());
        apps.addView(appChooser("Основной экран при запуске", prefs.mainApp(), "Не запускать", pkg -> {
            prefs.setMainApp(pkg);
            showTab(0);
        }));
        body.addView(apps);

        LinearLayout actions = ui.card("Сейчас");
        boolean clusterReady = ((DisplayManager) getSystemService(DISPLAY_SERVICE)).getDisplays().length > 1;
        actions.addView(ui.note(clusterReady
                ? "Дисплей приборки подключён."
                : "Дисплей приборки пока не обнаружен — он появляется через несколько секунд после включения."));
        // Выбор только запоминается; на приборку приложение уходит по кнопке.
        String manual = manualClusterApp();
        actions.addView(appChooser("Приложение для приборки", manual, null, pkg -> {
            getPreferences(MODE_PRIVATE).edit().putString("manual_cluster_app", pkg).apply();
            showTab(0);
        }));
        actions.addView(ui.buttonRow(
                ui.primaryButton("Показать на приборке", v -> {
                    if (!manual.isEmpty()) ClusterService.showOnCluster(this, manual);
                }),
                ui.secondaryButton("Вернуть на основной экран",
                        v -> ClusterService.start(this, ClusterService.ACTION_SHOW_MAIN))));
        body.addView(actions);

        LinearLayout behavior = ui.card("Поведение");
        behavior.addView(ui.switchItem("Переносить на основной экран",
                "Когда приборка закрывает карту (например, при выключении зажигания)",
                prefs.returnToMain(), prefs::setReturnToMain));
        body.addView(behavior);
        return holder[0];
    }

    /** Выбранное во «Сейчас»; пока ничего не выбирали — то, что на приборке. */
    private String manualClusterApp() {
        return getPreferences(MODE_PRIVATE).getString("manual_cluster_app", prefs.currentClusterApp());
    }

    private View fullscreenPage() {
        ScrollView[] holder = new ScrollView[1];
        LinearLayout body = page(holder);
        body.addView(ui.pageHeader("Полный экран",
                "Скрывает левый док и правую панель. Вернуть их — свайп от края экрана"));

        if (!Fullscreen.canApply(this)) {
            LinearLayout warn = ui.card("Нужно разрешение");
            warn.addView(ui.note("Приложению не выдано WRITE_SECURE_SETTINGS. Подключите "
                    + "магнитолу к компьютеру и запустите install.sh — он выдаст разрешение."));
            body.addView(warn);
        }

        int mode = prefs.fullscreenMode();
        LinearLayout modes = ui.card("Режим");
        String[][] options = {
                {"Выключен", "Панели видны всегда"},
                {"Для выбранных приложений", "Отметьте приложения в списке ниже"},
                {"Для всех приложений", "Кроме рабочего стола: на нём док остаётся"},
        };
        for (int i = 0; i < options.length; i++) {
            final int value = i;
            if (i > 0) modes.addView(ui.divider());
            modes.addView(ui.choiceItem(options[i][0], options[i][1], mode == i, v -> {
                prefs.setFullscreenMode(value);
                Fullscreen.apply(this, value, prefs.fullscreenApps());
                showTab(TAB_FULLSCREEN);
            }));
        }
        body.addView(modes);

        if (mode == Fullscreen.SELECTED) {
            LinearLayout list = ui.card("Приложения");
            List<String> chosen = prefs.fullscreenApps();
            boolean first = true;
            for (App app : apps) {
                if (!first) list.addView(ui.divider());
                first = false;
                list.addView(checkRow(app, chosen.contains(app.pkg), checked -> {
                    List<String> current = prefs.fullscreenApps();
                    if (checked) {
                        if (!current.contains(app.pkg)) current.add(app.pkg);
                    } else {
                        current.remove(app.pkg);
                    }
                    prefs.setFullscreenApps(current);
                    Fullscreen.apply(this, Fullscreen.SELECTED, current);
                }));
            }
            body.addView(list);
        }
        return holder[0];
    }

    private View autostartPage() {
        ScrollView[] holder = new ScrollView[1];
        LinearLayout body = page(holder);
        body.addView(ui.pageHeader("Автозапуск",
                "Что запускать после загрузки магнитолы"));

        LinearLayout main = ui.card(null);
        main.addView(ui.switchItem("Автозапуск при включении",
                "Сначала фоновые приложения, затем основной экран, затем приборка",
                prefs.bootEnabled(), prefs::setBootEnabled));
        body.addView(main);

        LinearLayout list = ui.card("Фоновые приложения");
        list.addView(ui.note("Отмеченные приложения запускаются по очереди на основном экране. "
                + "Приложение приборки здесь отмечать не нужно."));
        List<String> chosen = prefs.autostartApps();
        boolean first = true;
        for (App app : apps) {
            if (!first) list.addView(ui.divider());
            first = false;
            list.addView(checkRow(app, chosen.contains(app.pkg), checked -> {
                List<String> current = prefs.autostartApps();
                if (checked) {
                    if (!current.contains(app.pkg)) current.add(app.pkg);
                } else {
                    current.remove(app.pkg);
                }
                prefs.setAutostartApps(current);
            }));
        }
        body.addView(list);
        return holder[0];
    }

    /** Строка приложения с галочкой; нажатие по всей строке переключает её. */
    private View checkRow(App app, boolean checked, Ui.Toggle toggle) {
        CheckBox cb = new CheckBox(this);
        cb.setChecked(checked);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        cb.setClickable(false);
        LinearLayout row = ui.item(app.icon, app.label, null, cb);
        row.setBackground(ui.pressable(Ui.SURFACE, 12));
        row.setOnClickListener(v -> {
            cb.setChecked(!cb.isChecked());
            toggle.set(cb.isChecked());
        });
        return row;
    }

    private View processesPage() {
        ScrollView[] holder = new ScrollView[1];
        LinearLayout body = page(holder);
        body.addView(ui.pageHeader("Процессы",
                "Оперативная память и приложения, оставшиеся работать в фоне"));

        memoryCard = ui.card("Оперативная память");
        fillMemoryCard(memoryCard);
        body.addView(memoryCard);

        if (!BackgroundApps.hasAccess(this)) {
            LinearLayout warn = ui.card("Нужен доступ");
            warn.addView(ui.note("Приложению не выдан доступ к статистике использования — без "
                    + "него список фоновых приложений недоступен. Подключите магнитолу к "
                    + "компьютеру и запустите install.sh."));
            body.addView(warn);
            return holder[0];
        }

        List<BackgroundApps.Entry> running = BackgroundApps.list(this);
        LinearLayout list = ui.card("В фоне");
        if (running.isEmpty()) {
            list.addView(ui.note("Фоновых приложений нет."));
        } else {
            list.addView(ui.note("Приложения, открытые с момента включения и сейчас свёрнутые. "
                    + "Системные приложения Desay не показываются. Играющую музыку система "
                    + "не даст завершить."));
            String cluster = prefs.currentClusterApp();
            java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
            for (BackgroundApps.Entry entry : running) {
                list.addView(ui.divider());
                App app = findApp(entry.pkg);
                String label = app != null ? app.label : entry.pkg;
                String opened = "открывалось в " + time.format(new java.util.Date(entry.lastUsed));
                // Перенос на приборку система считает уходом в фон, но приложение работает.
                boolean onCluster = entry.pkg.equals(cluster);
                View stop = onCluster ? null : ui.secondaryButton("Завершить", v -> {
                    BackgroundApps.stop(this, entry.pkg);
                    handler.postDelayed(() -> showTab(TAB_PROCESSES), 500);
                });
                list.addView(ui.item(app != null ? app.icon : ui.placeholderIcon(), label,
                        onCluster ? "На приборке · " + opened : opened, stop));
            }
            list.addView(ui.buttonRow(ui.primaryButton("Завершить все, кроме приборки", v -> {
                for (BackgroundApps.Entry entry : running) {
                    if (!entry.pkg.equals(cluster)) BackgroundApps.stop(this, entry.pkg);
                }
                handler.postDelayed(() -> showTab(TAB_PROCESSES), 500);
            })));
        }
        body.addView(list);
        return holder[0];
    }

    private void fillMemoryCard(LinearLayout card) {
        // Заголовок карточки — первый ребёнок; остальное перерисовываем.
        while (card.getChildCount() > 1) card.removeViewAt(1);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        float used = 1f - (float) mi.availMem / mi.totalMem;
        String summary = String.format(java.util.Locale.US, "%s из %s занято · свободно %s",
                gb(mi.totalMem - mi.availMem), gb(mi.totalMem), gb(mi.availMem));
        TextView big = ui.text(Math.round(used * 100) + " %", 34, Ui.TEXT, true);
        big.setPadding(ui.dp(16), ui.dp(6), ui.dp(16), 0);
        card.addView(big);
        card.addView(ui.note(summary + (mi.lowMemory ? " · памяти мало" : "")));
        card.addView(ui.progressBar(used));
    }

    private static String gb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f ГБ", bytes / (1024f * 1024f * 1024f));
    }

    private View keysPage() {
        ScrollView[] holder = new ScrollView[1];
        LinearLayout body = page(holder);
        body.addView(ui.pageHeader("Кнопки руля",
                "Дополнительные действия на кнопках мультифункционального руля"));

        LinearLayout mode = ui.card("Кнопка MODE");
        mode.addView(keyActionChooser(ClusterService.KEYCODE_MODE));
        mode.addView(ui.note("Короткое нажатие по-прежнему переключает источник звука."));
        body.addView(mode);

        LinearLayout vr = ui.card("Кнопка голосового помощника");
        vr.addView(keyActionChooser(ClusterService.KEYCODE_VR));
        vr.addView(ui.note("Короткое нажатие вызывает штатный помощник, если он не отключён "
                + "во вкладке «Система»."));
        body.addView(vr);

        LinearLayout info = ui.card("Как это работает");
        info.addView(ui.note("Держите кнопку около секунды — выполнится выбранное действие. "
                + "Штатное действие кнопки отменить нельзя, поэтому свои действия назначаются "
                + "на удержание. Кнопки громкости система обрабатывает сама — до приложения они "
                + "не доходят."));
        body.addView(info);
        return holder[0];
    }

    /** Строка «Удержание: …» с выбором действия: ничего, Алиса или любое приложение. */
    private View keyActionChooser(int keyCode) {
        String action = prefs.keyAction(keyCode);
        App yandex = findApp("ru.yandex.yandexnavi");
        Drawable aliceIcon = yandex != null ? yandex.icon : ui.placeholderIcon();
        String value;
        Drawable icon;
        if (action.equals(Prefs.ACTION_ALICE)) {
            value = "Алиса";
            icon = aliceIcon;
        } else if (action.startsWith(Prefs.ACTION_APP)) {
            App app = findApp(action.substring(Prefs.ACTION_APP.length()));
            value = app != null ? app.label : action.substring(Prefs.ACTION_APP.length());
            icon = app != null ? app.icon : ui.placeholderIcon();
        } else {
            value = "Ничего";
            icon = ui.placeholderIcon();
        }
        return ui.navItem(icon, "Удержание", value, v -> {
            // В пункте списка pkg хранит само действие: "", "alice" или "app:<пакет>".
            List<App> items = new ArrayList<>();
            items.add(new App("Ничего", "", ui.placeholderIcon()));
            items.add(new App("Алиса", Prefs.ACTION_ALICE, aliceIcon));
            for (App app : apps) items.add(new App(app.label, Prefs.ACTION_APP + app.pkg, app.icon));
            pickFrom("Удержание кнопки", items, chosen -> {
                prefs.setKeyAction(keyCode, chosen);
                showTab(TAB_KEYS);
            });
        });
    }

    private View systemPage() {
        ScrollView[] holder = new ScrollView[1];
        LinearLayout body = page(holder);
        body.addView(ui.pageHeader("Система", "Быстрый доступ и служебные настройки"));

        LinearLayout quick = ui.card("Быстрый доступ");
        quick.addView(ui.navItem(null, "Wi-Fi",
                "В штатных настройках этого раздела нет — открывается только отсюда",
                v -> openWifiSettings()));
        body.addView(quick);

        LinearLayout stock = ui.card("Штатные приложения");
        if (!AppControl.isDeviceOwner(this)) {
            stock.addView(ui.note("Чтобы отключать штатные приложения, TiggoHelper нужно один раз "
                    + "назначить владельцем устройства с компьютера. Android разрешает это, только "
                    + "пока на магнитоле нет учётных записей: выйдите из аккаунта Яндекса, выполните "
                    + "«adb shell dpm set-device-owner ru.tiggo.cluster/.AdminReceiver» "
                    + "и войдите снова."));
        } else {
            for (int i = 0; i < AppControl.STOCK_APPS.length; i++) {
                String[] app = AppControl.STOCK_APPS[i];
                if (i > 0) stock.addView(ui.divider());
                // После переключения перерисовываем: если система отказала, переключатель
                // вернётся к фактическому состоянию.
                stock.addView(ui.switchItem(app[1], app[2], AppControl.isEnabled(this, app[0]),
                        enabled -> {
                            AppControl.setEnabled(this, app[0], enabled);
                            handler.post(() -> showTab(TAB_SYSTEM));
                        }));
            }
            stock.addView(ui.buttonRow(ui.secondaryButton("Снять права владельца", v -> {
                AppControl.releaseOwnership(this);
                showTab(TAB_SYSTEM);
            })));
            stock.addView(ui.note("Перед удалением TiggoHelper нажмите «Снять права владельца»: "
                    + "скрытые приложения вернутся, а приложение можно будет удалить."));
        }
        body.addView(stock);

        LinearLayout service = ui.card("Штатная навигация");
        service.addView(ui.switchItem("Гасить фоновый TurboDog",
                "Иначе его карта перекрывает приложение на приборке. "
                        + "Если TurboDog отключён в системе, ничего не делает",
                prefs.suppressTurboDog(), prefs::setSuppressTurboDog));
        body.addView(service);
        return holder[0];
    }

    private View logPage() {
        LinearLayout pageView = ui.column();
        pageView.setPadding(ui.dp(24), ui.dp(28), ui.dp(40), ui.dp(28));
        pageView.addView(ui.pageHeader("Журнал", "Последние события сервиса, обновляется сам"));
        LinearLayout card = ui.card(null);
        logView = ui.text("", 14, Ui.TEXT, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setLineSpacing(ui.dp(3), 1f);
        logView.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        logView.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        pageView.addView(card);
        return pageView;
    }

    // ---------- действия ----------

    private interface PackageChoice { void chosen(String pkg); }

    /** noneLabel — подпись пустого выбора в начале списка; null — пустой выбор не предлагать. */
    private View appChooser(String title, String selected, String noneLabel, PackageChoice choice) {
        App app = findApp(selected);
        String value = app != null ? app.label
                : (selected.isEmpty() ? (noneLabel != null ? noneLabel : "Не выбрано") : selected);
        return ui.navItem(app != null ? app.icon : ui.placeholderIcon(), title, value,
                v -> pickApp(title, noneLabel, choice));
    }

    private void pickApp(String title, String noneLabel, PackageChoice choice) {
        List<App> items = new ArrayList<>();
        if (noneLabel != null) items.add(new App(noneLabel, "", ui.placeholderIcon()));
        items.addAll(apps);
        pickFrom(title, items, choice);
    }

    /** Диалог выбора из списка; в choice приходит pkg выбранного пункта. */
    private void pickFrom(String title, List<App> items, PackageChoice choice) {
        // Свой диалог: штатная тема Desay даёт серый фон и тёмный заголовок, плохо читается.
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = ui.column();
        box.setBackground(ui.rounded(Ui.SURFACE, 22));
        box.setPadding(ui.dp(8), ui.dp(20), ui.dp(8), ui.dp(12));
        TextView header = ui.text(title, 22, Ui.TEXT, true);
        header.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(12));
        box.addView(header);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(false);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                App a = items.get(position);
                LinearLayout row = ui.item(a.icon, a.label, null, null);
                row.setBackground(ui.pressable(Ui.SURFACE, 12));
                return row;
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            choice.chosen(items.get(position).pkg);
        });
        box.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout buttons = ui.row();
        buttons.setGravity(Gravity.END);
        buttons.addView(ui.buttonRow(ui.secondaryButton("Отмена", v -> dialog.dismiss())));
        box.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(box);
        Window w = dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(ui.dp(760), ui.dp(640));
        dialog.show();
    }

    /** Тот же интент, что шлёт голосовой помощник; CLEAR_TASK — чтобы открылся именно Wi-Fi. */
    private void openWifiSettings() {
        Intent wifi = new Intent(Settings.ACTION_WIFI_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(wifi);
        } catch (ActivityNotFoundException e) {
            EventLog.i("Настройки Wi-Fi не найдены");
        }
    }
}
