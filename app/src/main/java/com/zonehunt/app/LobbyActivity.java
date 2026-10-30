package com.zonehunt.app;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import com.zonehunt.app.model.GameSettings;
import com.zonehunt.app.model.LobbySnapshot;
import com.zonehunt.app.model.Player;
import com.zonehunt.app.net.GameClient;
import com.zonehunt.app.util.Geo;
import com.zonehunt.app.util.UiUtils;

public class LobbyActivity extends AppCompatActivity implements GameClient.Listener {

    private GameClient client;
    private TextView txtCode;
    private TextView txtRole;
    private TextView txtPlayerCount;
    private TextView txtLobbyStatus;
    private TextView txtSeekerValue;
    private TextView txtStartHint;
    private TextView txtGuestHint;
    private TextView txtLobbyInfo;
    private TextView txtLobbyMode;
    private TextView txtLobbyModeInfo;
    private LinearLayout playerList;
    private LinearLayout hostControls;
    private LinearLayout guestWaiting;
    private ChipGroup settingsChips;
    private ChipGroup seekerChips;
    private TextView btnRandomSeeker;
    private View btnStart;
    private boolean gameLaunched;
    private boolean permissionAsked;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        client = ((App) getApplication()).client();
        if (client.getLobby() == null || !client.hasActiveSession()) {
            finish();
            return;
        }
        client.setListener(this);

        txtCode = findViewById(R.id.txtCode);
        txtRole = findViewById(R.id.txtRole);
        txtPlayerCount = findViewById(R.id.txtPlayerCount);
        txtLobbyStatus = findViewById(R.id.txtLobbyStatus);
        txtSeekerValue = findViewById(R.id.txtSeekerValue);
        txtStartHint = findViewById(R.id.txtStartHint);
        txtGuestHint = findViewById(R.id.txtGuestHint);
        txtLobbyInfo = findViewById(R.id.txtLobbyInfo);
        txtLobbyMode = findViewById(R.id.txtLobbyMode);
        txtLobbyModeInfo = findViewById(R.id.txtLobbyModeInfo);
        playerList = findViewById(R.id.playerList);
        hostControls = findViewById(R.id.hostControls);
        guestWaiting = findViewById(R.id.guestWaiting);
        settingsChips = findViewById(R.id.settingsChips);
        seekerChips = findViewById(R.id.seekerChips);
        btnRandomSeeker = findViewById(R.id.btnRandomSeeker);
        btnStart = findViewById(R.id.btnStart);

        findViewById(R.id.btnLeave).setOnClickListener(v -> confirmLeave());
        findViewById(R.id.btnCopyCode).setOnClickListener(v -> copyCode());
        findViewById(R.id.seekerRow).setOnClickListener(v -> chooseSeeker());
        findViewById(R.id.btnEditSettings).setOnClickListener(v -> editSettings());
        btnRandomSeeker.setOnClickListener(v -> client.rollRandomSeeker());
        btnStart.setOnClickListener(v -> tryStart());

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
            if (fine) {
                client.startLocationTracking();
            } else {
                showPermissionDialog();
            }
            updateStartState();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave();
            }
        });

        ensureLocation();
        render(client.getLobby());
    }

    private void ensureLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            client.startLocationTracking();
        } else if (!permissionAsked) {
            permissionAsked = true;
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @Override
    public void onLocationChanged(GameClient.LocationPoint point) {
        updateStartState();
    }

    @Override
    public void onLobbyChanged(LobbySnapshot snapshot) {
        if (gameLaunched || snapshot == null) {
            return;
        }
        if (LobbySnapshot.STATE_ENDED.equals(snapshot.state)) {
            showLobbyClosed();
            return;
        }
        render(snapshot);
    }

    @Override
    public void onGameStarted() {
        if (gameLaunched) {
            return;
        }
        gameLaunched = true;
        client.clearListener(this);
        startActivity(new Intent(this, GameActivity.class));
        finish();
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (gameLaunched) {
            return;
        }
        txtLobbyStatus.setText(connected
                ? statusForLobby(client.getLobby())
                : getString(R.string.game_reconnecting));
        updateStartState();
    }

    @Override
    public void onError(String message) {
        if (gameLaunched || isFinishing()) {
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        btnStart.setEnabled(true);
        if (btnStart instanceof TextView) {
            ((TextView) btnStart).setText(R.string.lobby_start);
        }
        updateStartState();
    }

    private void render(LobbySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        txtCode.setText(snapshot.code);
        boolean host = client.isHost();
        txtRole.setText(host ? getString(R.string.host) : getString(R.string.lobby_guest_badge));
        if (host) {
            UiUtils.pill(txtRole, R.drawable.bg_pill_lime_soft, R.color.lime);
        } else {
            UiUtils.pill(txtRole, R.drawable.bg_pill_mint_soft, R.color.mint);
        }
        txtPlayerCount.setText(snapshot.players.size() + " / 20");
        txtLobbyStatus.setText(statusForLobby(snapshot));

        GameSettings settings = snapshot.settings == null
                ? new GameSettings() : snapshot.settings;
        String mode = GameSettings.normalizeMode(settings.mode);
        boolean classic = GameSettings.MODE_CLASSIC.equals(mode);
        if (txtLobbyMode != null) {
            txtLobbyMode.setText(getString(R.string.lobby_mode_prefix,
                    getString(modeLabelRes(mode))));
        }
        if (txtLobbyModeInfo != null) {
            txtLobbyModeInfo.setText(modeInfoRes(mode));
        }
        setSeekerControlsVisible(classic);

        hostControls.setVisibility(host ? View.VISIBLE : View.GONE);
        guestWaiting.setVisibility(host ? View.GONE : View.VISIBLE);

        renderPlayers(snapshot);
        if (classic) {
            renderSeeker(snapshot);
            renderSeekerChips(snapshot);
        }
        renderChips(snapshot);

        int hideSeconds = settings.hideSeconds;
        if (txtGuestHint != null) {
            txtGuestHint.setText(classic
                    ? getString(R.string.lobby_guest_hint_seconds, hideSeconds)
                    : getString(modeInfoRes(mode)));
        }
        if (txtLobbyInfo != null) {
            txtLobbyInfo.setText(getString(R.string.lobby_info_seconds, hideSeconds));
        }

        if (host) {
            updateStartState();
        }
    }

    private String statusForLobby(LobbySnapshot snapshot) {
        if (snapshot == null) {
            return getString(R.string.lobby_title);
        }
        if (!client.isConnected()) {
            return getString(R.string.game_reconnecting);
        }
        if (snapshot.players.size() < 2) {
            return client.isHost()
                    ? getString(R.string.lobby_waiting_players)
                    : getString(R.string.lobby_status_wait_host);
        }
        return client.isHost()
                ? getString(R.string.lobby_status_ready_start)
                : getString(R.string.lobby_status_wait_start);
    }

    private void renderPlayers(LobbySnapshot snapshot) {
        playerList.removeAllViews();
        boolean classic = isClassicMode(snapshot);
        for (Player p : snapshot.players) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_lobby_player, playerList, false);
            TextView avatar = row.findViewById(R.id.avatar);
            TextView name = row.findViewById(R.id.txtName);
            TextView meta = row.findViewById(R.id.txtMeta);
            TextView badge = row.findViewById(R.id.txtBadge);

            avatar.setText(p.initials());
            name.setText(p.name);
            boolean isHost = p.id.equals(snapshot.hostId);
            boolean isMe = p.id.equals(client.getPlayerId());
            boolean isPicked = classic && snapshot.isSeekerSelected(p.id);
            if (isHost) {
                meta.setText(isMe
                        ? getString(R.string.lobby_host_you)
                        : getString(R.string.host));
            } else {
                meta.setText(isMe
                        ? getString(R.string.lobby_you_ready)
                        : getString(R.string.lobby_ready));
            }
            if (isPicked) {
                UiUtils.circleBackground(avatar,
                        ContextCompat.getColor(this, R.color.orange),
                        ContextCompat.getColor(this, R.color.orange_soft));
                badge.setVisibility(View.VISIBLE);
                badge.setText(R.string.game_role_seeker);
                UiUtils.pill(badge, R.drawable.bg_pill_orange_soft, R.color.orange);
            } else if (isHost) {
                UiUtils.circleBackground(avatar,
                        ContextCompat.getColor(this, R.color.lime),
                        ContextCompat.getColor(this, R.color.lime_soft));
                badge.setVisibility(View.VISIBLE);
                badge.setText(R.string.host);
                UiUtils.pill(badge, R.drawable.bg_pill_lime_soft, R.color.lime);
            } else {
                UiUtils.circleBackground(avatar,
                        ContextCompat.getColor(this, R.color.line),
                        ContextCompat.getColor(this, R.color.surface_3));
                if (isMe) {
                    badge.setVisibility(View.VISIBLE);
                    badge.setText(R.string.you);
                    UiUtils.pill(badge, R.drawable.bg_pill_mint_soft, R.color.mint);
                } else {
                    badge.setVisibility(View.GONE);
                }
            }
            playerList.addView(row);
        }
    }

    private void renderSeeker(LobbySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        List<String> ids = snapshot.seekerIds();
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            Player p = snapshot.findPlayer(id);
            if (p != null) {
                names.add(p.name + (p.id.equals(client.getPlayerId())
                        ? getString(R.string.lobby_you_suffix) : ""));
            }
        }
        if (!names.isEmpty()) {
            String joined = String.join(", ", names);
            boolean random = "RANDOM".equals(snapshot.seekerMode);
            if (names.size() > 1) {
                txtSeekerValue.setText(random
                        ? getString(R.string.lobby_random_seekers, names.size(), joined)
                        : getString(R.string.lobby_seekers_count, names.size(), joined));
            } else {
                txtSeekerValue.setText(random
                        ? getString(R.string.lobby_random_prefix) + joined
                        : joined);
            }
            btnRandomSeeker.setText(R.string.lobby_reroll);
        } else {
            txtSeekerValue.setText(R.string.lobby_not_rolled);
            btnRandomSeeker.setText(R.string.lobby_roll_random);
        }
    }

    private void renderSeekerChips(LobbySnapshot snapshot) {
        if (seekerChips == null || snapshot == null) {
            return;
        }
        seekerChips.removeAllViews();
        boolean host = client.isHost();
        for (Player p : snapshot.players) {
            Chip chip = new Chip(this);
            chip.setText(p.name + (p.id.equals(client.getPlayerId())
                    ? getString(R.string.lobby_you_suffix) : ""));
            chip.setTag(p.id);
            chip.setCheckable(true);
            chip.setChecked(snapshot.isSeekerSelected(p.id));
            chip.setEnabled(host);
            chip.setTextSize(12f);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipStartPadding(UiUtils.dp(this, 10));
            chip.setChipEndPadding(UiUtils.dp(this, 10));
            chip.setChipCornerRadius(UiUtils.dp(this, 100));
            chip.setChipStrokeWidth(UiUtils.dp(this, 1));
            chip.setCheckedIconResource(R.drawable.ic_check);
            chip.setCheckedIconVisible(true);
            chip.setCheckedIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.orange)));
            chip.setChipBackgroundColor(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{
                            ContextCompat.getColor(this, R.color.orange_soft),
                            Color.argb(22, 215, 255, 95)
                    }));
            chip.setTextColor(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{
                            ContextCompat.getColor(this, R.color.orange),
                            ContextCompat.getColor(this, R.color.text_soft)
                    }));
            chip.setOnCheckedChangeListener((button, checked) -> handleSeekerChipChange());
            seekerChips.addView(chip);
        }
        TextView tip = findViewById(R.id.txtSeekerTip);
        if (tip != null) {
            String selected = snapshot.seekerIds().isEmpty()
                    ? getString(R.string.lobby_no_selection)
                    : getString(R.string.lobby_selected_prefix) + seekerNames(snapshot) + ".";
            tip.setText(selected + " " + getString(R.string.lobby_multi_hint));
        }
    }

    private void handleSeekerChipChange() {
        if (!client.isHost()) {
            return;
        }
        LobbySnapshot snapshot = client.getLobby();
        if (snapshot == null) {
            return;
        }
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < seekerChips.getChildCount(); i++) {
            View child = seekerChips.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked() && child.getTag() != null) {
                selected.add((String) child.getTag());
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.lobby_pick_seeker, Toast.LENGTH_SHORT).show();
            renderSeekerChips(snapshot);
            return;
        }
        if (selected.size() >= snapshot.players.size()) {
            Toast.makeText(this, R.string.lobby_keep_hider, Toast.LENGTH_SHORT).show();
            renderSeekerChips(snapshot);
            return;
        }
        client.setSeekerChoice("PLAYER", selected);
    }

    private String seekerNames(LobbySnapshot snapshot) {
        List<String> names = new ArrayList<>();
        for (String id : snapshot.seekerIds()) {
            Player p = snapshot.findPlayer(id);
            if (p != null) {
                names.add(p.id.equals(client.getPlayerId()) ? getString(R.string.you) : p.name);
            }
        }
        return String.join(", ", names);
    }

    private void renderChips(LobbySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        settingsChips.removeAllViews();
        GameSettings settings = snapshot.settings == null
                ? new GameSettings() : snapshot.settings;
        String mode = GameSettings.normalizeMode(settings.mode);
        addChip(getString(R.string.lobby_zone_prefix)
                + Geo.formatDistance(settings.initialRadiusMeters));
        addChip(settings.shrinkMetersPerMinute == 0
                ? getString(R.string.lobby_zone_static)
                : "−" + settings.shrinkMetersPerMinute + " m/min");
        addChip(settings.roundMinutes + getString(R.string.lobby_round_suffix));
        addChip(getString(R.string.lobby_ping_every)
                + settings.pingIntervalSeconds + " s");
        addChip(settings.graceSeconds + getString(R.string.lobby_grace_suffix));
        addChip(settings.hideSeconds + getString(R.string.lobby_hide_suffix));
        if (GameSettings.MODE_ZOMBIE.equals(mode)) {
            addChip(getString(R.string.lobby_zombie_conversion_chip,
                    settings.zombieConversionSeconds));
            addChip(getString(R.string.lobby_zombie_sense_chip,
                    settings.zombieSenseIntervalSeconds));
        } else if (GameSettings.MODE_ASSASSIN.equals(mode)) {
            addChip(getString(R.string.lobby_assassin_radius_chip,
                    settings.assassinKillRadiusMeters));
            addChip(settings.assassinTargetPingOnly
                    ? getString(R.string.lobby_assassin_ping_only)
                    : getString(R.string.lobby_assassin_ping_all));
        } else if (GameSettings.MODE_ICE.equals(mode)) {
            addChip(getString(R.string.lobby_ice_thaw_chip,
                    settings.iceThawRadiusMeters));
            addChip(getString(R.string.lobby_ice_melt_chip,
                    settings.iceMeltSeconds));
        }
        if (settings.teamLive) {
            addChip(getString(R.string.lobby_team_live));
        }
        if (settings.shopEnabled) {
            addChip(getString(R.string.lobby_shop_active));
        }
    }

    private boolean isClassicMode(LobbySnapshot snapshot) {
        if (snapshot == null || snapshot.settings == null) {
            return true;
        }
        return GameSettings.MODE_CLASSIC.equals(
                GameSettings.normalizeMode(snapshot.settings.mode));
    }

    private int modeLabelRes(String mode) {
        if (GameSettings.MODE_ZOMBIE.equals(mode)) {
            return R.string.mode_zombie;
        }
        if (GameSettings.MODE_ASSASSIN.equals(mode)) {
            return R.string.mode_assassin;
        }
        if (GameSettings.MODE_ICE.equals(mode)) {
            return R.string.mode_ice;
        }
        return R.string.mode_classic;
    }

    private int modeInfoRes(String mode) {
        if (GameSettings.MODE_ZOMBIE.equals(mode)) {
            return R.string.lobby_mode_info_zombie;
        }
        if (GameSettings.MODE_ASSASSIN.equals(mode)) {
            return R.string.lobby_mode_info_assassin;
        }
        if (GameSettings.MODE_ICE.equals(mode)) {
            return R.string.lobby_mode_info_ice;
        }
        return R.string.lobby_mode_info_classic;
    }

    private void setSeekerControlsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        int[] ids = {R.id.seekerRow, R.id.txtSeekerLabel, R.id.seekerChips,
                R.id.btnRandomSeeker, R.id.txtSeekerTip};
        for (int id : ids) {
            View view = findViewById(id);
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    private void addChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setTextSize(11f);
        chip.setTextColor(ContextCompat.getColor(this, R.color.text_soft));
        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.argb(22, 215, 255, 95)));
        chip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.line)));
        chip.setChipStrokeWidth(UiUtils.dp(this, 1));
        chip.setChipCornerRadius(UiUtils.dp(this, 100));
        chip.setCheckable(false);
        chip.setClickable(false);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipStartPadding(UiUtils.dp(this, 10));
        chip.setChipEndPadding(UiUtils.dp(this, 10));
        settingsChips.addView(chip);
    }

    private void updateStartState() {
        if (!client.isHost()) {
            return;
        }
        LobbySnapshot snapshot = client.getLobby();
        int count = snapshot == null ? 0 : snapshot.players.size();
        boolean hasLocation = client.getMyLocation() != null;
        boolean ready = count >= 2 && hasLocation && client.isConnected() && !gameLaunched;
        btnStart.setEnabled(ready);
        if (count < 2) {
            txtStartHint.setText(R.string.lobby_need_two);
        } else if (!hasLocation) {
            txtStartHint.setText(R.string.lobby_gps_searching);
        } else if (!client.isConnected()) {
            txtStartHint.setText(R.string.game_reconnecting);
        } else {
            int hideSeconds = snapshot == null || snapshot.settings == null
                    ? 25 : snapshot.settings.hideSeconds;
            txtStartHint.setText(getString(R.string.lobby_ready_hide, hideSeconds));
        }
    }

    private void editSettings() {
        if (!client.isHost()) {
            return;
        }
        Intent intent = new Intent(this, CreateGameActivity.class);
        intent.putExtra("player_name", client.getPlayerName());
        intent.putExtra(CreateGameActivity.EXTRA_EDIT_LOBBY, true);
        startActivity(intent);
    }

    private void copyCode() {
        LobbySnapshot s = client.getLobby();
        if (s != null) {
            UiUtils.copyToClipboard(this, getString(R.string.lobby_code_share), s.code);
            Toast.makeText(this, getString(R.string.lobby_code_copied, s.code), Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseSeeker() {
        if (!client.isHost()) {
            return;
        }
        LobbySnapshot snapshot = client.getLobby();
        if (snapshot == null || snapshot.players.isEmpty()) {
            return;
        }
        final List<Player> players = new ArrayList<>(snapshot.players);
        String[] labels = new String[players.size()];
        final boolean[] checked = new boolean[players.size()];
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            labels[i] = p.name + (p.id.equals(client.getPlayerId())
                    ? getString(R.string.lobby_you_suffix) : "");
            checked[i] = snapshot.isSeekerSelected(p.id);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lobby_dialog_who_seeks)
                .setMessage(R.string.lobby_dialog_multi)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.lobby_dialog_apply, (dialog, which) -> {
                    List<String> selected = new ArrayList<>();
                    for (int i = 0; i < players.size(); i++) {
                        if (checked[i]) {
                            selected.add(players.get(i).id);
                        }
                    }
                    if (selected.isEmpty()) {
                        Toast.makeText(this, R.string.lobby_dialog_pick_or_random,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int maxSeekers = Math.max(1, players.size() - 1);
                    while (selected.size() > maxSeekers) {
                        selected.remove(selected.size() - 1);
                    }
                    client.setSeekerChoice("PLAYER", selected);
                })
                .setNeutralButton(R.string.random, (dialog, which) -> client.rollRandomSeeker())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void tryStart() {
        if (!client.isHost() || !client.isConnected()) {
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ensureLocation();
            return;
        }
        if (client.getMyLocation() == null) {
            Toast.makeText(this, R.string.lobby_gps_wait, Toast.LENGTH_SHORT).show();
            return;
        }
        btnStart.setEnabled(false);
        ((TextView) btnStart).setText(R.string.lobby_starting);
        if (!client.startGame()) {
            ((TextView) btnStart).setText(R.string.lobby_start);
            updateStartState();
        }
    }

    private void confirmLeave() {
        if (gameLaunched) {
            return;
        }
        boolean host = client.isHost();
        new MaterialAlertDialogBuilder(this)
                .setTitle(host
                        ? getString(R.string.lobby_close_title)
                        : getString(R.string.lobby_leave_title))
                .setMessage(host
                        ? getString(R.string.lobby_close_message)
                        : getString(R.string.lobby_leave_message))
                .setPositiveButton(R.string.leave, (d, w) -> {
                    client.leaveCurrentSession();
                    goHome();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showLobbyClosed() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lobby_closed_title)
                .setMessage(R.string.lobby_closed_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    client.shutdown();
                    goHome();
                })
                .show();
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
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
        client.clearListener(this);
        super.onDestroy();
    }
}
