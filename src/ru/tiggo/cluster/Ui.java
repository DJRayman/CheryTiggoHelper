package ru.tiggo.cluster;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Оформление экрана настроек кодом, без XML и AndroidX. Цвета — под штатную тёмную
 * оболочку Desay с бирюзовым акцентом. Экран 1920×720 при плотности 160: 1 dp = 1 px.
 */
final class Ui {
    static final int BG = 0xFF0B0F14;
    static final int SURFACE = 0xFF151B23;
    static final int SURFACE_HIGH = 0xFF1E2733;
    static final int DIVIDER = 0xFF26303C;
    static final int ACCENT = 0xFF2EC4D6;
    static final int ON_ACCENT = 0xFF04161A;
    static final int TEXT = 0xFFE8EEF4;
    static final int TEXT_DIM = 0xFF8A97A6;

    private final Context ctx;
    private final float density;

    Ui(Context ctx) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    int dp(float v) { return Math.round(v * density); }

    GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    /** Фон с откликом на нажатие. */
    Drawable pressable(int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x332EC4D6), rounded(color, radiusDp),
                rounded(0xFFFFFFFF, radiusDp));
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return tv;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Заголовок страницы с пояснением. */
    View pageHeader(String title, String subtitle) {
        LinearLayout l = column();
        l.addView(text(title, 30, TEXT, true));
        TextView sub = text(subtitle, 16, TEXT_DIM, false);
        sub.setPadding(0, dp(6), 0, dp(20));
        l.addView(sub);
        return l;
    }

    /** Карточка: подпись секции и содержимое. Строки внутри разделяются линиями. */
    LinearLayout card(String title) {
        LinearLayout card = column();
        card.setBackground(rounded(SURFACE, 18));
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (title != null) {
            TextView t = text(title.toUpperCase(), 13, ACCENT, true);
            t.setLetterSpacing(0.08f);
            t.setPadding(dp(16), dp(10), dp(16), dp(4));
            card.addView(t);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(16);
        card.setLayoutParams(lp);
        return card;
    }

    View divider() {
        View v = new View(ctx);
        v.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        v.setLayoutParams(lp);
        return v;
    }

    /** Строка: заголовок, пояснение и произвольный элемент справа. */
    LinearLayout item(Drawable icon, String title, String description, View trailing) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(72));
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        if (icon != null) {
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
            ilp.rightMargin = dp(16);
            r.addView(iv, ilp);
        }
        LinearLayout texts = column();
        TextView t = text(title, 18, TEXT, false);
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(t);
        if (description != null) {
            TextView d = text(description, 14, TEXT_DIM, false);
            d.setPadding(0, dp(3), 0, 0);
            texts.addView(d);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (trailing != null) {
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.leftMargin = dp(16);
            r.addView(trailing, tlp);
        }
        return r;
    }

    interface Toggle { void set(boolean v); }

    /** Строка с переключателем; нажатие по всей строке переключает его. */
    LinearLayout switchItem(String title, String description, boolean value, Toggle toggle) {
        Switch sw = new Switch(ctx);
        sw.setChecked(value);
        sw.setThumbTintList(new ColorStateList(
                new int[][] {{android.R.attr.state_checked}, {}}, new int[] {ACCENT, 0xFFB0BAC5}));
        sw.setTrackTintList(new ColorStateList(
                new int[][] {{android.R.attr.state_checked}, {}}, new int[] {0x882EC4D6, 0xFF3A4554}));
        sw.setOnCheckedChangeListener((v, checked) -> toggle.set(checked));
        // Штатный Switch мелковат для экрана в машине; вся строка всё равно нажимается.
        sw.setScaleX(1.4f);
        sw.setScaleY(1.4f);
        LinearLayout r = item(null, title, description, sw);
        r.setPadding(dp(16), dp(12), dp(28), dp(12));
        r.setBackground(pressable(SURFACE, 12));
        r.setOnClickListener(v -> sw.toggle());
        return r;
    }

    /** Строка, ведущая к выбору: справа текущее значение и стрелка. */
    LinearLayout navItem(Drawable icon, String title, String value, View.OnClickListener click) {
        TextView chevron = text("›", 30, TEXT_DIM, false);
        LinearLayout r = item(icon, title, value, chevron);
        r.setBackground(pressable(SURFACE, 12));
        r.setOnClickListener(click);
        return r;
    }

    /** Строка выбора одного варианта из нескольких: кружок слева, отмеченный — с точкой. */
    LinearLayout choiceItem(String title, String description, boolean selected,
                            View.OnClickListener click) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setSize(dp(26), dp(26));
        ring.setStroke(dp(2), selected ? ACCENT : TEXT_DIM);
        ring.setColor(SURFACE);
        Drawable mark = ring;
        if (selected) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(ACCENT);
            android.graphics.drawable.LayerDrawable layers =
                    new android.graphics.drawable.LayerDrawable(new Drawable[] {ring, dot});
            layers.setLayerInset(1, dp(7), dp(7), dp(7), dp(7));
            mark = layers;
        }
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(mark);
        LinearLayout r = item(null, title, description, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(26), dp(26));
        lp.rightMargin = dp(20);
        r.addView(iv, 0, lp);
        r.setBackground(pressable(SURFACE, 12));
        r.setOnClickListener(click);
        return r;
    }

    /** Полоса заполнения 0..1; цвет — акцент, при высокой загрузке — тёплый. */
    View progressBar(float fraction) {
        int fill = fraction > 0.85f ? 0xFFE8774A : ACCENT;
        GradientDrawable track = rounded(SURFACE_HIGH, 6);
        GradientDrawable bar = rounded(fill, 6);
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(
                bar, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        clip.setLevel(Math.round(Math.max(0f, Math.min(1f, fraction)) * 10000));
        View v = new View(ctx);
        v.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[] {track, clip}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(12));
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(16);
        v.setLayoutParams(lp);
        return v;
    }

    /** Значок-заглушка для строк без приложения, чтобы текст стоял в одну линию. */
    Drawable placeholderIcon() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(SURFACE_HIGH);
        d.setStroke(dp(2), DIVIDER);
        return d;
    }

    TextView primaryButton(String label, View.OnClickListener click) {
        TextView b = text(label, 17, ON_ACCENT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(28), 0, dp(28), 0);
        b.setMinHeight(dp(56));
        b.setBackground(pressable(ACCENT, 28));
        b.setOnClickListener(click);
        return b;
    }

    TextView secondaryButton(String label, View.OnClickListener click) {
        TextView b = text(label, 17, TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(28), 0, dp(28), 0);
        b.setMinHeight(dp(56));
        GradientDrawable outline = rounded(SURFACE_HIGH, 28);
        outline.setStroke(dp(1), DIVIDER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x332EC4D6), outline, null));
        b.setOnClickListener(click);
        return b;
    }

    /** Ряд кнопок с отступами между ними. */
    LinearLayout buttonRow(View... buttons) {
        LinearLayout r = row();
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = dp(12);
            r.addView(buttons[i], lp);
        }
        return r;
    }

    /** Поясняющий текст внутри карточки. */
    TextView note(String s) {
        TextView t = text(s, 15, TEXT_DIM, false);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(dp(16), dp(8), dp(16), dp(12));
        return t;
    }
}
