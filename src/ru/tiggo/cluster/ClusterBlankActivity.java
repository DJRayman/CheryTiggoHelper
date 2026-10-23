package ru.tiggo.cluster;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

/**
 * Пустой чёрный экран для приборки. Если на display 1 нет своего окна, Android показывает
 * там копию основного экрана, — так приборка «дублировала» Навигатор с основного экрана.
 */
public class ClusterBlankActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View black = new View(this);
        black.setBackgroundColor(Color.BLACK);
        setContentView(black);
    }
}
