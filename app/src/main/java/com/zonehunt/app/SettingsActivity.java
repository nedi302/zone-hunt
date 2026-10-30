package com.zonehunt.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.radiobutton.MaterialRadioButton;

/**
 * Small settings screen. Currently it contains the app language, which is
 * persisted by AppCompat (Android 12 and below) or by the platform per-app
 * locale API (Android 13+). Choosing "System default" clears the override.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String[] LANGUAGE_TAGS = {"", "en", "de", "es", "fr"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean applying;
    private MaterialRadioButton[] languageButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RadioGroup group = findViewById(R.id.languageGroup);
        languageButtons = new MaterialRadioButton[]{
                findViewById(R.id.langSystem),
                findViewById(R.id.langEnglish),
                findViewById(R.id.langGerman),
                findViewById(R.id.langSpanish),
                findViewById(R.id.langFrench)
        };

        String current = primaryLanguage(AppCompatDelegate.getApplicationLocales().toLanguageTags());
        int checked = 0;
        for (int i = 1; i < LANGUAGE_TAGS.length; i++) {
            if (LANGUAGE_TAGS[i].equals(current)) {
                checked = i;
                break;
            }
        }
        group.check(languageButtons[checked].getId());

        for (int i = 0; i < languageButtons.length; i++) {
            final String tag = LANGUAGE_TAGS[i];
            languageButtons[i].setOnClickListener(v -> chooseLanguage(tag));
        }
    }

    private void chooseLanguage(String tag) {
        if (applying) {
            return;
        }
        String current = primaryLanguage(AppCompatDelegate.getApplicationLocales().toLanguageTags());
        if (tag.equals(current)) {
            return;
        }
        applying = true;
        if (tag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }
        // AppCompat and the platform usually recreate the activity themselves.
        // The delayed call only covers devices where this does not happen.
        handler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                recreate();
            }
        }, 150);
    }

    private static String primaryLanguage(String tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        int comma = tags.indexOf(',');
        String first = comma >= 0 ? tags.substring(0, comma) : tags;
        int separator = first.indexOf('-');
        if (separator < 0) {
            separator = first.indexOf('_');
        }
        return separator >= 0 ? first.substring(0, separator) : first;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
