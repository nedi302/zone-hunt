package com.zonehunt.app;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

import com.zonehunt.app.model.GameSettings;
import com.zonehunt.app.model.LobbySnapshot;
import com.zonehunt.app.net.GameClient;
import com.zonehunt.app.util.UiUtils;

public class CreateGameActivity extends AppCompatActivity implements GameClient.Listener {

    public static final String EXTRA_EDIT_LOBBY = "edit_lobby";

    private SeekBar sliderRadius;
    private SeekBar sliderShrink;
    private SeekBar sliderRound;
    private SeekBar sliderPing;
    private SeekBar sliderGrace;
    private SeekBar sliderHide;
    private SeekBar sliderZombieConversion;
    private SeekBar sliderZombieSense;
    private SeekBar sliderAssassinRadius;
    private SeekBar sliderIceThawRadius;
    private SeekBar sliderIceThawHold;
    private SeekBar sliderIceMelt;
    private TextView valueRadius;
    private TextView valueShrink;
    private TextView valueRound;
    private TextView valuePing;
    private TextView valueGrace;
    private TextView valueHide;
    private TextView valueZombieConversion;
    private TextView valueZombieSense;
    private TextView valueAssassinRadius;
    private TextView valueIceThawRadius;
    private TextView valueIceThawHold;
    private TextView valueIceMelt;
    private TextView txtModeDescription;
    private ChipGroup modeChips;
    private View modeSettingsCard;
    private View zombieSettings;
    private View assassinSettings;
    private View iceSettings;
    private SwitchMaterial switchTeamLive;
    private SwitchMaterial switchShop;
    private SwitchMaterial switchAssassinPingOnly;
    private String currentMode = GameSettings.MODE_CLASSIC;
    private View overlay;
    private TextView overlayStatus;
    private boolean connecting;
    private boolean launchedLobby;
    private boolean editMode;
    private String playerName;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_game);

        playerName = getIntent().getStringExtra("player_name");
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_LOBBY, false);
        GameClient client = ((App) getApplication()).client();
        if (editMode) {
            LobbySnapshot lobby = client.getLobby();
            if (lobby == null || !client.isHost()) {
                finish();
                return;
            }
            if (playerName == null || playerName.trim().length() < 2) {
                playerName = client.getPlayerName();
            }
        }
        if (playerName == null || playerName.trim().length() < 2) {
            finish();
            return;
        }
        playerName = playerName.trim();

        sliderRadius = findViewById(R.id.sliderRadius);
        sliderShrink = findViewById(R.id.sliderShrink);
        sliderRound = findViewById(R.id.sliderRound);
        sliderPing = findViewById(R.id.sliderPing);
        sliderGrace = findViewById(R.id.sliderGrace);
        sliderHide = findViewById(R.id.sliderHide);
        sliderZombieConversion = findViewById(R.id.sliderZombieConversion);
        sliderZombieSense = findViewById(R.id.sliderZombieSense);
        sliderAssassinRadius = findViewById(R.id.sliderAssassinRadius);
        sliderIceThawRadius = findViewById(R.id.sliderIceThawRadius);
        sliderIceThawHold = findViewById(R.id.sliderIceThawHold);
        sliderIceMelt = findViewById(R.id.sliderIceMelt);
        valueRadius = findViewById(R.id.valueRadius);
        valueShrink = findViewById(R.id.valueShrink);
        valueRound = findViewById(R.id.valueRound);
        valuePing = findViewById(R.id.valuePing);
        valueGrace = findViewById(R.id.valueGrace);
        valueHide = findViewById(R.id.valueHide);
        valueZombieConversion = findViewById(R.id.valueZombieConversion);
        valueZombieSense = findViewById(R.id.valueZombieSense);
        valueAssassinRadius = findViewById(R.id.valueAssassinRadius);
        valueIceThawRadius = findViewById(R.id.valueIceThawRadius);
        valueIceThawHold = findViewById(R.id.valueIceThawHold);
        valueIceMelt = findViewById(R.id.valueIceMelt);
        txtModeDescription = findViewById(R.id.txtModeDescription);
        modeChips = findViewById(R.id.modeChips);
        modeSettingsCard = findViewById(R.id.modeSettingsCard);
        zombieSettings = findViewById(R.id.zombieSettings);
        assassinSettings = findViewById(R.id.assassinSettings);
        iceSettings = findViewById(R.id.iceSettings);
        switchTeamLive = findViewById(R.id.switchTeamLive);
        switchShop = findViewById(R.id.switchShop);
        switchAssassinPingOnly = findViewById(R.id.switchAssassinPingOnly);
        overlay = findViewById(R.id.connectingOverlay);
        overlayStatus = findViewById(R.id.txtOverlayStatus);

        findViewById(R.id.btnBack).setOnClickListener(v -> confirmClose());
        findViewById(R.id.btnCreateLobby).setOnClickListener(v -> onPrimaryClick());

        setupSliders();
        setupModeChips();
        if (editMode) {
            configureEditMode(client.getLobby());
        } else {
            applyModeVisibility();
        }

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
            if (fine) {
                startCreateFlow();
            } else {
                showPermissionDialog();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmClose();
            }
        });
    }

    private void setupSliders() {
        switchAssassinPingOnly.setChecked(true);
        sliderRadius.setProgress(5);
        sliderShrink.setProgress(5);
        sliderRound.setProgress(5);
        sliderPing.setProgress(2);
        sliderGrace.setProgress(4);
        sliderHide.setProgress(4);
        sliderZombieConversion.setProgress(2);
        sliderZombieSense.setProgress(1);
        sliderAssassinRadius.setProgress(3);
        sliderIceThawRadius.setProgress(2);
        sliderIceThawHold.setProgress(2);
        sliderIceMelt.setProgress(4);

        bindSlider(sliderRadius, valueRadius, p -> String.format(Locale.getDefault(), "%d m", 100 + p * 50));
        bindSlider(sliderShrink, valueShrink, p -> p == 0
                ? getString(R.string.game_static)
                : String.format(Locale.getDefault(), "%d m/min", p));
        bindSlider(sliderRound, valueRound, p -> String.format(Locale.getDefault(), "%d min", 5 + p * 5));
        bindSlider(sliderPing, valuePing, p -> String.format(Locale.getDefault(), "%d s", 15 + p * 15));
        bindSlider(sliderGrace, valueGrace, p -> String.format(Locale.getDefault(), "%d s", 10 + p * 5));
        bindSlider(sliderHide, valueHide, p -> String.format(Locale.getDefault(), "%d s", 5 + p * 5));
        bindSlider(sliderZombieConversion, valueZombieConversion,
                p -> String.format(Locale.getDefault(), "%d s", 5 + p * 5));
        bindSlider(sliderZombieSense, valueZombieSense,
                p -> String.format(Locale.getDefault(), "%d s", 15 + p * 15));
        bindSlider(sliderAssassinRadius, valueAssassinRadius,
                p -> String.format(Locale.getDefault(), "%d m", 10 + p * 5));
        bindSlider(sliderIceThawRadius, valueIceThawRadius,
                p -> String.format(Locale.getDefault(), "%d m", 5 + p * 5));
        bindSlider(sliderIceThawHold, valueIceThawHold,
                p -> String.format(Locale.getDefault(), "%d s", 3 + p));
        bindSlider(sliderIceMelt, valueIceMelt,
                p -> String.format(Locale.getDefault(), "%d s", 20 + p * 10));
    }

    private void setupModeChips() {
        if (modeChips == null) {
            return;
        }
        modeChips.removeAllViews();
        String[] modes = {
                GameSettings.MODE_CLASSIC,
                GameSettings.MODE_ZOMBIE,
                GameSettings.MODE_ASSASSIN,
                GameSettings.MODE_ICE
        };
        int[] labels = {
                R.string.mode_classic,
                R.string.mode_zombie,
                R.string.mode_assassin,
                R.string.mode_ice
        };
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            Chip chip = new Chip(this);
            chip.setText(labels[i]);
            chip.setTag(mode);
            chip.setCheckable(true);
            chip.setChecked(mode.equals(currentMode));
            chip.setTextSize(12f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipStartPadding(UiUtils.dp(this, 10));
            chip.setChipEndPadding(UiUtils.dp(this, 10));
            chip.setChipCornerRadius(UiUtils.dp(this, 100));
            chip.setChipStrokeWidth(UiUtils.dp(this, 1));
            chip.setChipBackgroundColor(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{
                            ContextCompat.getColor(this, R.color.lime_soft),
                            Color.argb(22, 215, 255, 95)
                    }));
            chip.setTextColor(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{
                            ContextCompat.getColor(this, R.color.lime),
                            ContextCompat.getColor(this, R.color.text_soft)
                    }));
            chip.setOnClickListener(v -> selectMode(mode));
            modeChips.addView(chip);
        }
    }

    private void selectMode(String mode) {
        currentMode = GameSettings.normalizeMode(mode);
        applyModeVisibility();
    }

    private void applyModeVisibility() {
        boolean zombie = GameSettings.MODE_ZOMBIE.equals(currentMode);
        boolean assassin = GameSettings.MODE_ASSASSIN.equals(currentMode);
        boolean ice = GameSettings.MODE_ICE.equals(currentMode);
        if (zombieSettings != null) {
            zombieSettings.setVisibility(zombie ? View.VISIBLE : View.GONE);
        }
        if (assassinSettings != null) {
            assassinSettings.setVisibility(assassin ? View.VISIBLE : View.GONE);
        }
        if (iceSettings != null) {
            iceSettings.setVisibility(ice ? View.VISIBLE : View.GONE);
        }
        if (modeSettingsCard != null) {
            modeSettingsCard.setVisibility(zombie || assassin || ice
                    ? View.VISIBLE : View.GONE);
        }
        if (txtModeDescription != null) {
            int description = R.string.mode_classic_desc;
            if (zombie) {
                description = R.string.mode_zombie_desc;
            } else if (assassin) {
                description = R.string.mode_assassin_desc;
            } else if (ice) {
                description = R.string.mode_ice_desc;
            }
            txtModeDescription.setText(description);
        }
    }

    private void configureEditMode(LobbySnapshot lobby) {
        TextView kicker = findViewById(R.id.txtCreateKicker);
        TextView title = findViewById(R.id.txtCreateTitle);
        TextView sub = findViewById(R.id.txtCreateSub);
        kicker.setText(R.string.create_edit_title);
        title.setText(R.string.create_edit_headline);
        sub.setText(R.string.create_edit_sub);
        ((TextView) findViewById(R.id.btnCreateLobby)).setText(R.string.create_save_settings);
        TextView footnote = findViewById(R.id.txtCreateFootnote);
        if (footnote != null) {
            footnote.setText(R.string.create_edit_footnote);
        }
        GameSettings settings = lobby == null || lobby.settings == null
                ? new GameSettings() : lobby.settings;
        currentMode = GameSettings.normalizeMode(settings.mode);
        setupModeChips();
        sliderRadius.setProgress(progressFor(settings.initialRadiusMeters, 100, 50, 28));
        sliderShrink.setProgress(progressFor(settings.shrinkMetersPerMinute, 0, 1, 30));
        sliderRound.setProgress(progressFor(settings.roundMinutes, 5, 5, 23));
        sliderPing.setProgress(progressFor(settings.pingIntervalSeconds, 15, 15, 11));
        sliderGrace.setProgress(progressFor(settings.graceSeconds, 10, 5, 17));
        sliderHide.setProgress(progressFor(settings.hideSeconds, 5, 5, 23));
        sliderZombieConversion.setProgress(progressFor(settings.zombieConversionSeconds, 5, 5, 11));
        sliderZombieSense.setProgress(progressFor(settings.zombieSenseIntervalSeconds, 15, 15, 7));
        sliderAssassinRadius.setProgress(progressFor(settings.assassinKillRadiusMeters, 10, 5, 10));
        sliderIceThawRadius.setProgress(progressFor(settings.iceThawRadiusMeters, 5, 5, 7));
        sliderIceThawHold.setProgress(progressFor(settings.iceThawHoldSeconds, 3, 1, 12));
        sliderIceMelt.setProgress(progressFor(settings.iceMeltSeconds, 20, 10, 10));
        switchTeamLive.setChecked(settings.teamLive);
        switchShop.setChecked(settings.shopEnabled);
        switchAssassinPingOnly.setChecked(settings.assassinTargetPingOnly);
        applyModeVisibility();
    }

    private static int progressFor(int value, int base, int step, int max) {
        return Math.max(0, Math.min(max, Math.round((value - base) / (float) step)));
    }

    private void onPrimaryClick() {
        if (editMode) {
            saveLobbySettings();
        } else {
            startCreateFlow();
        }
    }

    private void saveLobbySettings() {
        GameClient client = ((App) getApplication()).client();
        if (!client.isHost() || client.getLobby() == null) {
            Toast.makeText(this, R.string.create_lobby_inactive, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        client.updateLobbySettings(readSettings());
        Toast.makeText(this, R.string.create_settings_updated, Toast.LENGTH_SHORT).show();
        finish();
    }

    private interface Formatter {
        String format(int progress);
    }

    private void bindSlider(SeekBar seekBar, TextView valueView, Formatter formatter) {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueView.setText(formatter.format(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        seekBar.setOnSeekBarChangeListener(listener);
        listener.onProgressChanged(seekBar, seekBar.getProgress(), false);
    }

    private GameSettings readSettings() {
        GameSettings s = new GameSettings();
        s.initialRadiusMeters = 100 + sliderRadius.getProgress() * 50;
        s.shrinkMetersPerMinute = sliderShrink.getProgress();
        s.roundMinutes = 5 + sliderRound.getProgress() * 5;
        s.pingIntervalSeconds = 15 + sliderPing.getProgress() * 15;
        s.graceSeconds = 10 + sliderGrace.getProgress() * 5;
        s.hideSeconds = 5 + sliderHide.getProgress() * 5;
        s.teamLive = switchTeamLive.isChecked();
        s.shopEnabled = switchShop.isChecked();
        s.mode = GameSettings.normalizeMode(currentMode);
        s.zombieConversionSeconds = 5 + sliderZombieConversion.getProgress() * 5;
        s.zombieSenseIntervalSeconds = 15 + sliderZombieSense.getProgress() * 15;
        s.assassinKillRadiusMeters = 10 + sliderAssassinRadius.getProgress() * 5;
        s.assassinTargetPingOnly = switchAssassinPingOnly.isChecked();
        s.iceThawRadiusMeters = 5 + sliderIceThawRadius.getProgress() * 5;
        s.iceThawHoldSeconds = 3 + sliderIceThawHold.getProgress();
        s.iceMeltSeconds = 20 + sliderIceMelt.getProgress() * 10;
        return s;
    }

    private void startCreateFlow() {
        if (connecting) {
            return;
        }
        if (!hasLocationPermission()) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        if (!GameClient.isLocationEnabled(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.location_off_title)
                    .setMessage(R.string.location_off_message)
                    .setPositiveButton(R.string.permission_settings, (d, w) -> {
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        connecting = true;
        overlay.setVisibility(View.VISIBLE);
        overlayStatus.setText(R.string.connecting);
        ((App) getApplication()).client().setListener(this);
        ((App) getApplication()).client().createGame(playerName, readSettings(), this);
        timeoutHandler.postDelayed(() -> {
            if (connecting && !launchedLobby && !isFinishing()) {
                connecting = false;
                overlay.setVisibility(View.GONE);
                ((App) getApplication()).client().shutdown();
                showError(getString(R.string.create_connect_timeout));
            }
        }, 22000);
    }

    @Override
    public void onLobbyChanged(LobbySnapshot snapshot) {
        if (editMode || snapshot == null || launchedLobby || isFinishing()) {
            return;
        }
        if (snapshot.hasPlayer(((App) getApplication()).client().getPlayerId())) {
            launchedLobby = true;
            connecting = false;
            timeoutHandler.removeCallbacksAndMessages(null);
            overlayStatus.setText(R.string.create_lobby_ready);
            Intent i = new Intent(this, LobbyActivity.class);
            startActivity(i);
            finish();
        }
    }

    @Override
    public void onError(String message) {
        if (isFinishing() || launchedLobby) {
            return;
        }
        connecting = false;
        overlay.setVisibility(View.GONE);
        showError(message);
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (connecting && overlay.getVisibility() == View.VISIBLE) {
            overlayStatus.setText(connected
                    ? getString(R.string.create_preparing)
                    : getString(R.string.game_reconnecting));
        }
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.create_connection_failed_title)
                .setMessage(message)
                .setPositiveButton(R.string.retry, (d, w) -> startCreateFlow())
                .setNegativeButton(R.string.cancel, (d, w) -> finish())
                .show();
    }

    private void confirmClose() {
        if (editMode) {
            finish();
            return;
        }
        if (connecting) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.create_cancel_connection_title)
                    .setMessage(R.string.create_cancel_connection_message)
                    .setPositiveButton(R.string.leave, (d, w) -> {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        ((App) getApplication()).client().shutdown();
                        finish();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            finish();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_message)
                .setPositiveButton(R.string.permission_settings, (d, w) -> {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        timeoutHandler.removeCallbacksAndMessages(null);
        if (!editMode) {
            ((App) getApplication()).client().clearListener(this);
        }
        super.onDestroy();
    }
}
