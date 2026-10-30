package com.zonehunt.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.zonehunt.app.net.GameClient;
import com.zonehunt.app.util.PlayerStats;
import com.zonehunt.app.util.ReplayStore;
import com.zonehunt.app.util.UiUtils;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "zonenjagd";
    private static final String PREF_NAME = "player_name";

    private TextInputLayout nameLayout;
    private TextInputEditText inputName;
    private TextView btnReplays;
    private TextView txtTotalKm;
    private TextView txtTotalKcal;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private Runnable pendingAction;
    private String appliedLocaleTag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appliedLocaleTag = currentLanguageTag();

        nameLayout = findViewById(R.id.nameLayout);
        inputName = findViewById(R.id.inputName);
        btnReplays = findViewById(R.id.btnReplays);
        txtTotalKm = findViewById(R.id.txtTotalKm);
        txtTotalKcal = findViewById(R.id.txtTotalKcal);
        btnReplays.setOnClickListener(v ->
                startActivity(new Intent(this, ReplayListActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String savedName = prefs.getString(PREF_NAME, "");
        inputName.setText(savedName);
        if (savedName.isEmpty()) {
            inputName.requestFocus();
        }

        inputName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                nameLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fine = hasFineLocation()
                            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    if (fine) {
                        runWithLocationServiceCheck();
                    } else {
                        showPermissionDialog();
                    }
                });

        findViewById(R.id.btnCreate).setOnClickListener(v -> beginWithPermissions(() -> openCreate()));
        findViewById(R.id.btnJoin).setOnClickListener(v -> beginWithPermissions(() -> openJoin()));

        configureSteps();
    }

    private void configureSteps() {
        setStep(findViewById(R.id.stepOne), "1",
                getString(R.string.main_step1_title), getString(R.string.main_step1_text));
        setStep(findViewById(R.id.stepTwo), "2",
                getString(R.string.main_step2_title), getString(R.string.main_step2_text));
        setStep(findViewById(R.id.stepThree), "3",
                getString(R.string.main_step3_title), getString(R.string.main_step3_text));
    }

    private void setStep(View root, String number, String title, String text) {
        if (root == null) {
            return;
        }
        ((TextView) root.findViewById(R.id.stepNumber)).setText(number);
        ((TextView) root.findViewById(R.id.stepTitle)).setText(title);
        ((TextView) root.findViewById(R.id.stepText)).setText(text);
    }

    private void beginWithPermissions(Runnable action) {
        if (!validateName()) {
            return;
        }
        UiUtils.hideKeyboard(inputName);
        persistName();
        pendingAction = action;
        boolean fine = hasFineLocation();
        boolean notifications = hasNotificationPermission();
        if (fine && notifications) {
            runWithLocationServiceCheck();
        } else {
            List<String> permissions = new ArrayList<>();
            if (!fine) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (!notifications) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            permissionLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void runWithLocationServiceCheck() {
        if (!GameClient.isLocationEnabled(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.location_off_title)
                    .setMessage(R.string.location_off_message)
                    .setPositiveButton(R.string.permission_settings, (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        Runnable action = pendingAction;
        pendingAction = null;
        if (action != null) {
            action.run();
        }
    }

    private void openCreate() {
        Intent i = new Intent(this, CreateGameActivity.class);
        i.putExtra("player_name", inputName.getText() == null ? "" : inputName.getText().toString().trim());
        startActivity(i);
    }

    private void openJoin() {
        Intent i = new Intent(this, JoinGameActivity.class);
        i.putExtra("player_name", inputName.getText() == null ? "" : inputName.getText().toString().trim());
        startActivity(i);
    }

    private boolean validateName() {
        String n = inputName.getText() == null ? "" : inputName.getText().toString().trim();
        if (n.length() < 2) {
            nameLayout.setError(getString(R.string.main_name_too_short));
            inputName.requestFocus();
            return false;
        }
        if (n.length() > 20) {
            nameLayout.setError(getString(R.string.main_name_too_long));
            return false;
        }
        return true;
    }

    private void persistName() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_NAME, inputName.getText() == null ? "" : inputName.getText().toString().trim())
                .apply();
    }

    private boolean hasFineLocation() {
        return androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_message)
                .setPositiveButton(R.string.permission_settings, (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String localeTag = currentLanguageTag();
        if (!localeTag.equals(appliedLocaleTag)) {
            appliedLocaleTag = localeTag;
            recreate();
            return;
        }
        if (btnReplays != null) {
            int count = ReplayStore.count(this);
            btnReplays.setText(count == 0
                    ? getString(R.string.main_replays)
                    : getString(R.string.main_replays_count, count));
        }
        if (txtTotalKm != null) {
            txtTotalKm.setText(PlayerStats.formatKilometers(this,
                    PlayerStats.totalDistanceMeters(this)) + " km");
        }
        if (txtTotalKcal != null) {
            txtTotalKcal.setText(PlayerStats.totalCalories(this) + " kcal");
        }
    }

    @Override
    protected void onDestroy() {
        pendingAction = null;
        super.onDestroy();
    }

    private String currentLanguageTag() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        String tags = locales.isEmpty() ? "" : locales.toLanguageTags();
        if (tags.isEmpty()) {
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
}
