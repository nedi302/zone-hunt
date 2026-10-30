package com.zonehunt.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.zonehunt.app.model.ChatMessage;
import com.zonehunt.app.model.DecoyMarker;
import com.zonehunt.app.model.GameRuntime;
import com.zonehunt.app.model.GameSettings;
import com.zonehunt.app.model.LobbySnapshot;
import com.zonehunt.app.model.Player;
import com.zonehunt.app.model.ShopCatalog;
import com.zonehunt.app.model.ZoneGeometry;
import com.zonehunt.app.net.GameClient;
import com.zonehunt.app.util.Geo;
import com.zonehunt.app.util.PlayerStats;
import com.zonehunt.app.util.UiUtils;
import com.zonehunt.app.view.PingMarkerDrawable;
import com.zonehunt.app.view.PingPulseOverlay;
import com.zonehunt.app.view.PlayerPinDrawable;
import com.zonehunt.app.view.ZoneOverlay;

public class GameActivity extends AppCompatActivity implements GameClient.Listener {

    private static final int MENU_HELP = 10;
    private static final int MENU_END = 11;
    private static final int MENU_LEAVE = 12;
    private static final int MENU_OVERVIEW = 13;

    private GameClient client;
    private GameRuntime runtime;
    private MapView map;
    private ZoneOverlay zoneOverlay;
    private Marker ownMarker;
    private PingPulseOverlay pingPulseOverlay;
    private final Map<String, Marker> playerMarkers = new HashMap<>();
    private final Map<String, Marker> decoyMapMarkers = new HashMap<>();
    private final Map<String, View> playerChips = new HashMap<>();
    private String playerSignature = "";
    private MapEventsOverlay mapEventsOverlay;

    private TextView txtRole;
    private TextView txtConnection;
    private TextView txtTimer;
    private TextView txtZone;
    private TextView txtZoneSub;
    private TextView txtZoneNow;
    private TextView txtZoneNext;
    private TextView txtPingLabel;
    private TextView txtPingTimer;
    private TextView txtPingSub;
    private TextView txtAlive;
    private TextView txtAliveSub;
    private TextView pingBanner;
    private TextView warningBanner;
    private TextView connectionBanner;
    private LinearLayout hidePhaseBanner;
    private TextView txtHidePhaseTimer;
    private TextView txtHidePhaseSub;
    private TextView chatBadge;
    private View btnChat;
    private View btnOverview;
    private LinearLayout seekerAction;
    private LinearLayout hiderCodePanel;
    private LinearLayout outPanel;
    private LinearLayout targetPanel;
    private LinearLayout statePanel;
    private TextView txtTargetName;
    private TextView txtTargetDistance;
    private MaterialButton btnKill;
    private TextView txtStateTitle;
    private TextView txtStateInfo;
    private ImageView imgStateIcon;
    private TextView txtCatchActionLabel;
    private TextView txtCatchActionSub;
    private LinearLayout playerStrip;
    private TextView txtCatchCode;
    private TextView txtCodeHint;
    private TextView txtOutReason;
    private TextView txtAttribution;
    private View btnCatch;
    private MaterialButton btnRevealCode;
    private TextView btnStartDummy;
    private ImageView btnRevealIcon;
    private HorizontalScrollView playerScroll;

    private View introOverlay;
    private TextView introRole;
    private TextView introTitle;
    private TextView introText;
    private ImageView introIcon;
    private View countdownOverlay;
    private TextView txtCountdown;
    private TextView txtCountdownLabel;
    private TextView txtCountdownSub;
    private View endOverlay;
    private TextView endBadge;
    private TextView endTitle;
    private TextView endReason;
    private TextView endStatOneValue;
    private TextView endStatTwoValue;
    private TextView endStatTwoLabel;
    private TextView endStatThreeValue;
    private TextView endStatThreeLabel;
    private TextView endStatFourValue;
    private TextView endStatFiveValue;
    private MaterialButton btnEndReplay;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable tickRunnable;
    private Runnable introHideRunnable;
    private Runnable countdownRunnable;
    private boolean markersRevealed;
    private boolean radarWasActive;
    private boolean codeVisible;
    private boolean endShown;
    private boolean introShown;
    private boolean countdownDone;
    private boolean placingDecoy;
    private ShopCatalog.Item pendingDecoyItem;
    private boolean senseWasActive;
    private long codeHideAt;
    private long pingBannerHideAt;
    private long outsideSinceElapsed = -1;
    private int lastWarningStage = -1;
    private boolean zoneCentered;
    private boolean initialLocationFocus;
    private boolean permissionAsked;
    private boolean locationOffDialogShown;
    private ActivityResultLauncher<String[]> permissionLauncher;

    private AlertDialog chatDialog;
    private ScrollView chatScroll;
    private LinearLayout chatMessageList;
    private TextView txtChatEmpty;
    private EditText inputChat;
    private TextView btnChatAll;
    private TextView btnChatTeam;
    private TextView txtChatTeamHint;
    private String chatScope = ChatMessage.SCOPE_ALL;
    private boolean chatOpen;
    private int unreadChat;

    private AlertDialog overviewDialog;
    private TextView overviewOutTitle;
    private TextView overviewOutEmpty;
    private LinearLayout overviewOutList;
    private TextView overviewAliveTitle;
    private LinearLayout overviewAliveList;

    private LinearLayout shopRow;
    private TextView txtCoins;
    private TextView txtShopStatus;
    private MaterialButton btnShop;
    private AlertDialog shopDialog;
    private LinearLayout shopItemList;
    private TextView shopCoinsLabel;
    private String shopDialogSignature = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        client = ((App) getApplication()).client();
        runtime = client.getRuntime();
        if (runtime == null) {
            finish();
            return;
        }
        client.setListener(this);

        bindViews();
        setupPermissions();
        setupMap();
        setupActions();

        Player me = client.getMe();
        if (me == null) {
            finish();
            return;
        }
        renderRole(me);
        updatePlayerStrip(true);
        refreshMarkersVisibility();
        client.startLocationTracking();

        if (runtime.ended) {
            showEndOverlay(runtime.endReason, runtime.winner);
        } else if (!me.alive) {
            showOutState(me.eliminatedReason);
        } else {
            boolean introAvailable = !PlayerStats.hasSeenIntro(this, client.getPlayerName());
            long elapsed = runtime.elapsedMs();
            if (elapsed < 3600L) {
                startRoundCountdown(() -> {
                    if (introAvailable && !isFinishing()) {
                        showIntro(me);
                    }
                });
            } else if (introAvailable && elapsed < 60000L) {
                showIntro(me);
            }
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (placingDecoy) {
                    cancelDecoyPlacement(true);
                } else if (endShown) {
                    exitToHome(true);
                } else {
                    confirmLeave();
                }
            }
        });

        tickRunnable = new Runnable() {
            @Override
            public void run() {
                tick();
                ui.postDelayed(this, 1000L);
            }
        };
        ui.post(tickRunnable);
    }

    private void bindViews() {
        map = findViewById(R.id.map);
        txtRole = findViewById(R.id.txtRole);
        txtConnection = findViewById(R.id.txtConnection);
        txtTimer = findViewById(R.id.txtTimer);
        txtZone = findViewById(R.id.txtZone);
        txtZoneSub = findViewById(R.id.txtZoneSub);
        txtZoneNow = findViewById(R.id.txtZoneNow);
        txtZoneNext = findViewById(R.id.txtZoneNext);
        txtPingLabel = findViewById(R.id.txtPingLabel);
        txtPingTimer = findViewById(R.id.txtPingTimer);
        txtPingSub = findViewById(R.id.txtPingSub);
        txtAlive = findViewById(R.id.txtAlive);
        txtAliveSub = findViewById(R.id.txtAliveSub);
        pingBanner = findViewById(R.id.pingBanner);
        warningBanner = findViewById(R.id.warningBanner);
        connectionBanner = findViewById(R.id.connectionBanner);
        hidePhaseBanner = findViewById(R.id.hidePhaseBanner);
        txtHidePhaseTimer = findViewById(R.id.txtHidePhaseTimer);
        txtHidePhaseSub = findViewById(R.id.txtHidePhaseSub);
        chatBadge = findViewById(R.id.chatBadge);
        btnChat = findViewById(R.id.btnChat);
        btnOverview = findViewById(R.id.btnOverview);
        seekerAction = findViewById(R.id.seekerAction);
        hiderCodePanel = findViewById(R.id.hiderCodePanel);
        outPanel = findViewById(R.id.outPanel);
        targetPanel = findViewById(R.id.targetPanel);
        statePanel = findViewById(R.id.statePanel);
        txtTargetName = findViewById(R.id.txtTargetName);
        txtTargetDistance = findViewById(R.id.txtTargetDistance);
        btnKill = findViewById(R.id.btnKill);
        txtStateTitle = findViewById(R.id.txtStateTitle);
        txtStateInfo = findViewById(R.id.txtStateInfo);
        imgStateIcon = findViewById(R.id.imgStateIcon);
        txtCatchActionLabel = findViewById(R.id.txtCatchActionLabel);
        txtCatchActionSub = findViewById(R.id.txtCatchActionSub);
        playerStrip = findViewById(R.id.playerStrip);
        playerScroll = findViewById(R.id.playerStrip).getParent() instanceof HorizontalScrollView
                ? (HorizontalScrollView) playerStrip.getParent() : null;
        txtCatchCode = findViewById(R.id.txtCatchCode);
        txtCodeHint = findViewById(R.id.txtCodeHint);
        txtOutReason = findViewById(R.id.txtOutReason);
        txtAttribution = findViewById(R.id.txtAttribution);
        btnCatch = findViewById(R.id.btnCatch);
        btnRevealCode = findViewById(R.id.btnRevealCode);
        btnRevealIcon = null;
        introOverlay = findViewById(R.id.introOverlay);
        introRole = findViewById(R.id.introRole);
        introTitle = findViewById(R.id.introTitle);
        introText = findViewById(R.id.introText);
        introIcon = findViewById(R.id.introIcon);
        countdownOverlay = findViewById(R.id.countdownOverlay);
        txtCountdown = findViewById(R.id.txtCountdown);
        txtCountdownLabel = findViewById(R.id.txtCountdownLabel);
        txtCountdownSub = findViewById(R.id.txtCountdownSub);
        endOverlay = findViewById(R.id.endOverlay);
        endBadge = findViewById(R.id.endBadge);
        endTitle = findViewById(R.id.endTitle);
        endReason = findViewById(R.id.endReason);
        endStatOneValue = findViewById(R.id.endStatOneValue);
        endStatTwoValue = findViewById(R.id.endStatTwoValue);
        endStatTwoLabel = findViewById(R.id.endStatTwoLabel);
        endStatThreeValue = findViewById(R.id.endStatThreeValue);
        endStatThreeLabel = findViewById(R.id.endStatThreeLabel);
        endStatFourValue = findViewById(R.id.endStatFourValue);
        endStatFiveValue = findViewById(R.id.endStatFiveValue);
        btnEndReplay = findViewById(R.id.btnEndReplay);
        shopRow = findViewById(R.id.shopRow);
        txtCoins = findViewById(R.id.txtCoins);
        txtShopStatus = findViewById(R.id.txtShopStatus);
        btnShop = findViewById(R.id.btnShop);
    }

    private void setupPermissions() {
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
            if (fine) {
                client.startLocationTracking();
            } else {
                showPermissionDialog();
            }
            updateConnectionUi();
        });
        ensureLocationPermission();
        if (!GameClient.isLocationEnabled(this) && !locationOffDialogShown) {
            locationOffDialogShown = true;
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
        }
    }

    private void ensureLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            client.startLocationTracking();
        } else if (!permissionAsked) {
            permissionAsked = true;
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);
        map.setTilesScaledToDpi(true);
        map.setMinZoomLevel(3.0);
        map.setMaxZoomLevel(20.0);
        map.getController().setZoom(16.5);
        GeoPoint center = new GeoPoint(runtime.centerLat, runtime.centerLng);
        map.getController().setCenter(center);

        zoneOverlay = new ZoneOverlay(getResources().getDisplayMetrics().density);
        ZoneGeometry.Frame frame = runtime.zoneFrame();
        zoneOverlay.setZones(new GeoPoint(frame.current.lat, frame.current.lng),
                frame.current.radiusMeters,
                new GeoPoint(frame.next.lat, frame.next.lng),
                frame.next.radiusMeters);
        map.getOverlays().add(zoneOverlay);

        pingPulseOverlay = new PingPulseOverlay(getResources().getDisplayMetrics().density);
        map.getOverlays().add(pingPulseOverlay);

        mapEventsOverlay = new MapEventsOverlay(this, new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (!placingDecoy || p == null) {
                    return false;
                }
                placeDecoyAt(p.getLatitude(), p.getLongitude());
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        });
        map.getOverlays().add(mapEventsOverlay);

        // Own pin first so that other players' ping pins stay readable on
        // top of it when two players are close together.
        createOwnMarker();
        createPlayerMarkers();
        map.invalidate();

        map.postDelayed(() -> {
            if (isFinishing() || map == null) {
                return;
            }
            fitZoneInView();
        }, 650L);

        txtAttribution.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright")));
            } catch (Exception ignored) {
            }
        });
    }

    private void fitZoneInView() {
        if (zoneCentered || runtime == null) {
            return;
        }
        zoneCentered = true;
        int radius = Math.max(120, runtime.lobby.settings.initialRadiusMeters);
        double latOff = Geo.latitudeOffset(radius * 1.45);
        double lonOff = Geo.longitudeOffset(runtime.centerLat, radius * 1.45);
        BoundingBox box = new BoundingBox(
                runtime.centerLat + latOff,
                runtime.centerLng + lonOff,
                runtime.centerLat - latOff,
                runtime.centerLng - lonOff
        );
        map.zoomToBoundingBox(box, false);
        map.invalidate();
    }

    private void createOwnMarker() {
        ownMarker = new Marker(map);
        ownMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        ownMarker.setIcon(new PlayerPinDrawable(getResources().getDisplayMetrics().density,
                getString(R.string.game_you_marker), ContextCompat.getColor(this, R.color.blue), true));
        ownMarker.setEnabled(false);
        ownMarker.setOnMarkerClickListener((marker, mapView) -> {
            if (placingDecoy) {
                placeDecoyAt(marker.getPosition().getLatitude(), marker.getPosition().getLongitude());
            } else {
                Toast.makeText(this, R.string.game_you_marker_desc, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        map.getOverlays().add(ownMarker);
        GameClient.LocationPoint p = client.getMyLocation();
        if (p != null) {
            ownMarker.setPosition(new GeoPoint(p.lat, p.lng));
            ownMarker.setEnabled(true);
        }
    }

    private void createPlayerMarkers() {
        playerMarkers.clear();
        List<Player> players = client.getPlayers();
        for (Player p : players) {
            if (p.id.equals(client.getPlayerId())) {
                continue;
            }
            Marker marker = new Marker(map);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setRelatedObject(p.id);
            marker.setTitle(p.name);
            marker.setOnMarkerClickListener((m, mapView) -> {
                Player latest = client.findPlayer((String) m.getRelatedObject());
                if (latest != null) {
                    String role = getString(roleLowerRes(latest, modeSettings()));
                    boolean live = latest.alive && usesLivePosition(latest);
                    Toast.makeText(this, latest.name + " · " + role
                            + (live ? getString(R.string.game_live_suffix)
                            : getString(R.string.game_ping_suffix)),
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            marker.setIcon(new PingMarkerDrawable(getResources().getDisplayMetrics().density,
                    p.initials(), markerColor(p)));
            marker.setPosition(new GeoPoint(runtime.centerLat, runtime.centerLng));
            marker.setEnabled(false);
            playerMarkers.put(p.id, marker);
            map.getOverlays().add(marker);
        }
    }

    private int markerColor(Player p) {
        GameSettings settings = modeSettings();
        if (settings.isZombie()) {
            return ContextCompat.getColor(this,
                    p.isZombie() ? R.color.orange : R.color.mint);
        }
        if (settings.isIce()) {
            if (p.frozen) {
                return ContextCompat.getColor(this, R.color.blue);
            }
            return ContextCompat.getColor(this,
                    p.isSeeker() ? R.color.orange : R.color.mint);
        }
        if (settings.isAssassin()) {
            Player me = client.getMe();
            boolean target = me != null && me.targetId != null
                    && me.targetId.equals(p.id);
            return ContextCompat.getColor(this,
                    target ? R.color.orange : R.color.mint);
        }
        return ContextCompat.getColor(this,
                p.isSeeker() ? R.color.orange : R.color.mint);
    }

    private void setupActions() {
        findViewById(R.id.btnRecenter).setOnClickListener(v -> recenterMap());
        findViewById(R.id.btnMenu).setOnClickListener(this::showMenu);
        btnChat.setOnClickListener(v -> showChat());
        btnOverview.setOnClickListener(v -> showPlayerOverview());
        btnCatch.setOnClickListener(v -> openCatchDialog());
        btnKill.setOnClickListener(v -> confirmAssassinKill());
        btnRevealCode.setOnClickListener(v -> toggleCatchCode());
        if (btnShop != null) {
            btnShop.setOnClickListener(v -> showShop());
        }
        if (txtShopStatus != null) {
            // Only used to cancel the decoy placement. Opening the shop from
            // here caused an accidental reopen when the shop dialog was
            // dismissed by a tap on this row.
            txtShopStatus.setOnClickListener(v -> {
                if (placingDecoy) {
                    cancelDecoyPlacement(true);
                }
            });
        }
        findViewById(R.id.btnIntroDismiss).setOnClickListener(v -> hideIntro());
        introOverlay.setOnClickListener(v -> hideIntro());
        findViewById(R.id.btnEndBack).setOnClickListener(v -> exitToHome(true));
        btnEndReplay.setOnClickListener(v -> openReplay());
    }

    private void renderRole(Player me) {
        if (me == null || runtime == null) {
            return;
        }
        GameSettings settings = modeSettings();
        boolean zombie = settings.isZombie();
        boolean assassin = settings.isAssassin();
        boolean ice = settings.isIce();

        int roleRes = roleUpperRes(me, settings);
        int pillBg = R.drawable.bg_pill_mint_soft;
        int pillColor = R.color.mint;
        if (assassin) {
            pillBg = R.drawable.bg_pill_lime_soft;
            pillColor = R.color.lime;
        } else if (zombie && me.isZombie()) {
            pillBg = R.drawable.bg_pill_orange_soft;
            pillColor = R.color.orange;
        } else if (isHunter(me)) {
            pillBg = R.drawable.bg_pill_orange_soft;
            pillColor = R.color.orange;
        } else if (ice && me.frozen) {
            pillBg = R.drawable.bg_pill_neutral;
            pillColor = R.color.blue;
        }
        txtRole.setText(roleRes);
        UiUtils.pill(txtRole, pillBg, pillColor);

        if (zombie) {
            txtCatchActionLabel.setText(R.string.game_zombie_found);
            txtCatchActionSub.setText(R.string.game_zombie_enter);
        } else if (ice) {
            txtCatchActionLabel.setText(R.string.game_ice_found);
            txtCatchActionSub.setText(R.string.game_ice_enter);
        } else {
            txtCatchActionLabel.setText(R.string.game_found_someone);
            txtCatchActionSub.setText(R.string.game_enter_catch);
        }
        if (!isHunter(me) && !assassin) {
            if (!codeVisible) {
                txtCatchCode.setText("••••");
                txtCodeHint.setText(zombie
                        ? R.string.game_survivor_objective : R.string.game_code_hint);
            }
        }
        updateActionPanels(me);
    }

    private GameSettings modeSettings() {
        if (runtime == null || runtime.lobby == null || runtime.lobby.settings == null) {
            return new GameSettings();
        }
        return runtime.lobby.settings;
    }

    /** Mode-aware hunter flag: zombie, ice hunter, assassin or classic seeker. */
    private boolean isHunter(Player p) {
        if (p == null) {
            return false;
        }
        GameSettings settings = modeSettings();
        if (settings.isZombie()) {
            return p.isZombie();
        }
        if (settings.isAssassin()) {
            return p.isAssassin();
        }
        return p.isSeeker();
    }

    private int roleUpperRes(Player p, GameSettings settings) {
        if (p == null) {
            return R.string.game_role_hider;
        }
        if (settings.isAssassin()) {
            return R.string.game_role_assassin;
        }
        if (settings.isZombie()) {
            if (p.isZombie()) {
                return p.infected ? R.string.game_role_infected : R.string.game_role_zombie;
            }
            return R.string.game_role_survivor;
        }
        if (settings.isIce()) {
            if (p.isSeeker()) {
                return R.string.game_role_ice_hunter;
            }
            return p.frozen ? R.string.game_role_frozen : R.string.game_role_hider;
        }
        return p.isSeeker() ? R.string.game_role_seeker : R.string.game_role_hider;
    }

    private int roleLowerRes(Player p, GameSettings settings) {
        if (p == null) {
            return R.string.game_role_hider_lower;
        }
        if (settings.isAssassin()) {
            return R.string.game_role_assassin_lower;
        }
        if (settings.isZombie()) {
            if (p.isZombie()) {
                return p.infected
                        ? R.string.game_role_infected : R.string.game_role_zombie_lower;
            }
            return R.string.game_role_survivor_lower;
        }
        if (settings.isIce()) {
            if (p.isSeeker()) {
                return R.string.game_role_ice_hunter_lower;
            }
            return p.frozen
                    ? R.string.game_role_frozen_lower : R.string.game_role_hider_lower;
        }
        return p.isSeeker()
                ? R.string.game_role_seeker_lower : R.string.game_role_hider_lower;
    }

    private int roleColorRes(Player p, GameSettings settings) {
        if (settings.isAssassin()) {
            return R.color.lime;
        }
        if (settings.isZombie()) {
            return p != null && p.isZombie() ? R.color.orange : R.color.mint;
        }
        if (settings.isIce()) {
            if (p != null && p.isSeeker()) {
                return R.color.orange;
            }
            return p != null && p.frozen ? R.color.blue : R.color.mint;
        }
        return p != null && p.isSeeker() ? R.color.orange : R.color.mint;
    }

    private int rolePillRes(Player p, GameSettings settings) {
        int color = roleColorRes(p, settings);
        if (color == R.color.orange) {
            return R.drawable.bg_pill_orange_soft;
        }
        if (color == R.color.blue) {
            return R.drawable.bg_pill_neutral;
        }
        if (color == R.color.lime) {
            return R.drawable.bg_pill_lime_soft;
        }
        return R.drawable.bg_pill_mint_soft;
    }

    private int catchButtonLabel() {
        GameSettings settings = modeSettings();
        if (settings.isZombie()) {
            return R.string.game_infect;
        }
        if (settings.isIce()) {
            return R.string.game_freeze;
        }
        return R.string.game_catch;
    }

    /**
     * Shows exactly the action, code, target or state panel that matches the
     * local player's current role and condition.
     */
    private void updateActionPanels(Player me) {
        if (me == null || runtime == null) {
            return;
        }
        GameSettings settings = modeSettings();
        boolean ended = runtime.ended;
        boolean alive = me.alive;
        boolean frozen = me.frozen;
        boolean zombie = settings.isZombie();
        boolean assassin = settings.isAssassin();

        boolean catchAllowed = alive && !ended && !frozen && !assassin
                && (zombie ? me.isZombie() : me.isSeeker());
        boolean codeAllowed = alive && !ended && !frozen && !assassin
                && (zombie ? me.isSurvivor() : me.isHider());
        boolean targetAllowed = alive && !ended && assassin;
        boolean stateAllowed = alive && !ended
                && (frozen || (zombie && me.infected));

        seekerAction.setVisibility(catchAllowed ? View.VISIBLE : View.GONE);
        hiderCodePanel.setVisibility(codeAllowed ? View.VISIBLE : View.GONE);
        targetPanel.setVisibility(targetAllowed ? View.VISIBLE : View.GONE);
        statePanel.setVisibility(stateAllowed ? View.VISIBLE : View.GONE);
        outPanel.setVisibility(!alive && !ended ? View.VISIBLE : View.GONE);

        if (catchAllowed && btnCatch instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) btnCatch;
            button.setText(runtime.inHidePhase()
                    ? getString(R.string.locked) : getString(catchButtonLabel()));
            button.setEnabled(!runtime.inHidePhase());
        }
        if (codeAllowed && !codeVisible) {
            txtCodeHint.setText(zombie
                    ? R.string.game_survivor_objective : R.string.game_code_hint);
        }
        if (!codeAllowed && codeVisible) {
            hideCatchCode();
        }
    }

    private void updateModePanels(Player me) {
        if (me == null || runtime == null) {
            return;
        }
        GameSettings settings = modeSettings();
        updateActionPanels(me);
        if (settings.isAssassin()) {
            updateTargetPanel(me);
        }
        updateStatePanel(me, settings);
        boolean sense = client.isZombieSenseActive();
        if (sense && !senseWasActive && !endShown) {
            Toast.makeText(this, R.string.game_zombie_sense_banner, Toast.LENGTH_SHORT).show();
        }
        senseWasActive = sense;
    }

    private void updateTargetPanel(Player me) {
        if (txtTargetName == null || txtTargetDistance == null || btnKill == null) {
            return;
        }
        Player target = me.targetId == null || me.targetId.isEmpty()
                ? null : client.findPlayer(me.targetId);
        if (target == null || !target.alive) {
            txtTargetName.setText(R.string.game_target_none);
            txtTargetDistance.setText(R.string.game_target_waiting);
            btnKill.setEnabled(false);
            return;
        }
        txtTargetName.setText(target.name);
        GameClient.LocationPoint loc = client.getMyLocation();
        Player located = null;
        if (client.isRadarRevealing(target) && isFreshLive(target)) {
            located = target;
        } else {
            for (Player pinged : client.getPingSnapshot()) {
                if (pinged.id.equals(target.id)) {
                    located = pinged;
                    break;
                }
            }
        }
        if (loc != null && located != null) {
            double distance = Geo.distanceMeters(loc.lat, loc.lng, located.lat, located.lng);
            txtTargetDistance.setText(getString(R.string.game_target_away,
                    Geo.formatDistance((int) Math.round(distance))));
        } else {
            txtTargetDistance.setText(R.string.game_target_waiting);
        }
        btnKill.setEnabled(true);
    }

    private void updateStatePanel(Player me, GameSettings settings) {
        if (txtStateTitle == null || txtStateInfo == null) {
            return;
        }
        long hunt = runtime.huntingElapsedMs();
        if (me.frozen) {
            txtStateTitle.setText(R.string.game_frozen_title);
            txtStateTitle.setTextColor(ContextCompat.getColor(this, R.color.blue));
            if (imgStateIcon != null) {
                imgStateIcon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.blue)));
            }
            long meltMs = Math.max(20, settings.iceMeltSeconds) * 1000L;
            long left = Math.max(0, meltMs - (hunt - me.frozenSinceHuntMs));
            String text = getString(R.string.game_frozen_text,
                    settings.iceThawRadiusMeters, settings.iceThawHoldSeconds,
                    Geo.formatClock(left));
            if (settings.shopEnabled) {
                text += "\n" + getString(R.string.game_frozen_shop_hint);
            }
            txtStateInfo.setText(text);
        } else if (settings.isZombie() && me.infected) {
            txtStateTitle.setText(R.string.game_role_infected);
            txtStateTitle.setTextColor(ContextCompat.getColor(this, R.color.orange));
            if (imgStateIcon != null) {
                imgStateIcon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.orange)));
            }
            long protection = Math.max(5, settings.zombieConversionSeconds) * 1000L;
            long left = protection - (hunt - me.infectedAtHuntMs);
            if (left > 0) {
                txtStateInfo.setText(getString(R.string.game_infected_banner,
                        (int) ((left + 999) / 1000)));
            } else {
                txtStateInfo.setText(R.string.game_zombie_banner);
            }
        }
    }

    private void confirmAssassinKill() {
        Player me = client.getMe();
        if (me == null || !me.alive || me.targetId == null || me.targetId.isEmpty()) {
            Toast.makeText(this, R.string.error_no_target, Toast.LENGTH_SHORT).show();
            return;
        }
        Player target = client.findPlayer(me.targetId);
        if (target == null || !target.alive) {
            Toast.makeText(this, R.string.game_target_none, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game_kill_title)
                .setMessage(getString(R.string.game_kill_message, target.name))
                .setPositiveButton(R.string.game_kill, (d, w) -> {
                    GameClient.CatchResult result = client.submitAssassinKill();
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                    if (result.success) {
                        UiUtils.vibrate(this, 0, 90, 60, 160);
                        updatePlayerStrip(true);
                        updateModePanels(client.getMe());
                        refreshMarkersVisibility();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startRoundCountdown(Runnable onFinished) {
        if (countdownOverlay == null || countdownDone) {
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }
        countdownDone = true;
        countdownOverlay.setVisibility(View.VISIBLE);
        countdownOverlay.setAlpha(1f);
        final Runnable[] hide = new Runnable[1];
        hide[0] = () -> {
            countdownOverlay.setVisibility(View.GONE);
            if (onFinished != null) {
                onFinished.run();
            }
        };
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (runtime == null || isFinishing() || countdownOverlay == null) {
                    return;
                }
                long elapsed = runtime.elapsedMs();
                if (elapsed < 1000L) {
                    showCountdownNumber("3", getString(R.string.game_countdown_ready),
                            getString(R.string.game_countdown_here));
                    ui.postDelayed(this, 100L);
                } else if (elapsed < 2000L) {
                    showCountdownNumber("2", getString(R.string.game_countdown_ready),
                            getString(R.string.game_countdown_eyes));
                    ui.postDelayed(this, 100L);
                } else if (elapsed < 3000L) {
                    showCountdownNumber("1", getString(R.string.game_countdown_ready),
                            getString(R.string.game_countdown_sub));
                    ui.postDelayed(this, 100L);
                } else {
                    showCountdownNumber(getString(R.string.game_countdown_go),
                            getString(R.string.game_countdown_hunt_on),
                            getString(R.string.game_countdown_good_luck));
                    ui.postDelayed(hide[0], 450L);
                }
            }
        };
        countdownRunnable.run();
    }

    private void showCountdownNumber(String number, String label, String sub) {
        if (txtCountdown == null) {
            return;
        }
        txtCountdown.setText(number);
        txtCountdown.setTextSize(number.length() > 1 ? 56f : 150f);
        txtCountdownLabel.setText(label);
        txtCountdownSub.setText(sub);
        txtCountdown.setScaleX(0.82f);
        txtCountdown.setScaleY(0.82f);
        txtCountdown.animate().scaleX(1f).scaleY(1f).setDuration(180L).start();
    }

    private void hideCountdown() {
        if (countdownRunnable != null) {
            ui.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        if (countdownOverlay != null) {
            countdownOverlay.setVisibility(View.GONE);
        }
    }

    private void showIntro(Player me) {
        if (PlayerStats.hasSeenIntro(this, client.getPlayerName())) {
            return;
        }
        PlayerStats.markIntroSeen(this, client.getPlayerName());
        introShown = true;
        GameSettings settings = modeSettings();
        long hideSeconds = runtime.hidePhaseMs / 1000L;
        int roleRes;
        int titleRes;
        int textRes;
        int iconRes;
        int colorRes;
        if (settings.isZombie()) {
            titleRes = R.string.mode_zombie;
            if (me.isZombie()) {
                roleRes = R.string.game_role_zombie;
                textRes = R.string.game_zombie_objective;
                iconRes = R.drawable.ic_search;
                colorRes = R.color.orange;
            } else {
                roleRes = R.string.game_role_survivor;
                textRes = R.string.game_survivor_objective;
                iconRes = R.drawable.ic_visibility;
                colorRes = R.color.mint;
            }
        } else if (settings.isAssassin()) {
            roleRes = R.string.game_role_assassin;
            titleRes = R.string.mode_assassin;
            textRes = R.string.game_assassin_objective;
            iconRes = R.drawable.ic_radar;
            colorRes = R.color.lime;
        } else if (settings.isIce()) {
            titleRes = R.string.mode_ice;
            if (me.isSeeker()) {
                roleRes = R.string.game_role_ice_hunter;
                textRes = R.string.game_ice_hunter_objective;
                iconRes = R.drawable.ic_search;
                colorRes = R.color.orange;
            } else {
                roleRes = R.string.game_role_hider;
                textRes = R.string.game_ice_objective;
                iconRes = R.drawable.ic_visibility;
                colorRes = R.color.mint;
            }
        } else {
            roleRes = me.isSeeker()
                    ? R.string.game_intro_role_seeker : R.string.game_intro_role_hider;
            titleRes = me.isSeeker()
                    ? R.string.game_intro_title_seeker : R.string.game_intro_title_hider;
            textRes = me.isSeeker()
                    ? R.string.game_intro_text_seeker : R.string.game_intro_text_hider;
            iconRes = me.isSeeker() ? R.drawable.ic_search : R.drawable.ic_visibility;
            colorRes = me.isSeeker() ? R.color.orange : R.color.mint;
        }
        introRole.setText(roleRes);
        introRole.setTextColor(ContextCompat.getColor(this, colorRes));
        introTitle.setText(titleRes);
        String text = getString(textRes);
        if (!settings.isClassic()) {
            text += "\n\n" + getString(R.string.game_intro_head_start, hideSeconds);
        }
        introText.setText(text + shopIntroHint());
        introIcon.setImageResource(iconRes);
        introIcon.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, colorRes)));
        introOverlay.setVisibility(View.VISIBLE);
        introOverlay.setAlpha(0f);
        introOverlay.animate().alpha(1f).setDuration(220).start();
        if (introHideRunnable != null) {
            ui.removeCallbacks(introHideRunnable);
        }
        introHideRunnable = this::hideIntro;
        ui.postDelayed(introHideRunnable, 9000L);
    }

    private String shopIntroHint() {
        if (runtime == null || runtime.lobby.settings == null
                || !runtime.lobby.settings.shopEnabled) {
            return "";
        }
        GameSettings settings = runtime.lobby.settings;
        if (settings.isZombie()) {
            return getString(R.string.game_intro_shop_zombie);
        }
        if (settings.isAssassin()) {
            return getString(R.string.game_intro_shop_assassin);
        }
        if (settings.isIce()) {
            return getString(R.string.game_intro_shop_ice);
        }
        return getString(R.string.game_intro_shop_suffix);
    }

    private void hideIntro() {
        if (!introShown) {
            return;
        }
        introShown = false;
        if (introHideRunnable != null) {
            ui.removeCallbacks(introHideRunnable);
            introHideRunnable = null;
        }
        introOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (introOverlay != null) {
                introOverlay.setVisibility(View.GONE);
            }
        }).start();
    }

    private void tick() {
        if (runtime == null || isFinishing()) {
            return;
        }
        Player me = client.getMe();
        if (me == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long elapsed = runtime.elapsedMs();
        long remaining = runtime.remainingMs();
        txtTimer.setText(Geo.formatClock(remaining));
        txtTimer.setTextColor(ContextCompat.getColor(this, R.color.text));
        if (remaining < 5 * 60_000L && remaining > 0) {
            txtTimer.setTextColor(ContextCompat.getColor(this, R.color.orange));
        } else if (remaining == 0) {
            txtTimer.setTextColor(ContextCompat.getColor(this, R.color.red));
        }

        ZoneGeometry.Frame zoneFrame = runtime.zoneFrame();
        updateZoneUi(zoneFrame);
        updateHidePhaseUi(me);
        updateTopCounts();
        updatePingUi(now);
        updateOutsideState(me, now, zoneFrame);
        updateConnectionUi();
        updatePlayerStrip(false);
        updateShopUi(me, now);
        updateModePanels(me);
        boolean reveal = client.isRadarActive() || client.isZombieSenseActive();
        if (client.isTeamLiveActive() || reveal) {
            refreshMarkerPositions();
        } else if (radarWasActive) {
            refreshMarkersVisibility();
        }
        radarWasActive = reveal;
        refreshDecoyMarkers();

        if (pingBanner.getVisibility() == View.VISIBLE && now > pingBannerHideAt) {
            pingBanner.setVisibility(View.GONE);
        }
        if (codeVisible && now > codeHideAt) {
            hideCatchCode();
        }
        if (!endShown && runtime.ended) {
            showEndOverlay(runtime.endReason, runtime.winner);
        }
        if (!endShown && !runtime.ended
                && elapsed > runtime.hidePhaseMs + runtime.durationMs + 10000L) {
            runtime.ended = true;
            runtime.endReason = timeoutReason();
            runtime.winner = computeLocalWinner();
            client.finalizeReplay(runtime.endReason, runtime.winner);
            showEndOverlay(runtime.endReason, runtime.winner);
        }
    }

    private String timeoutReason() {
        GameSettings settings = modeSettings();
        if (settings.isZombie()) {
            return "ZEIT_ZOMBIE";
        }
        if (settings.isIce()) {
            return "ZEIT_EIS";
        }
        if (settings.isAssassin()) {
            return "ZEIT_ATTENTAETER";
        }
        return "ZEIT";
    }

    private void updateZoneUi(ZoneGeometry.Frame frame) {
        if (frame == null) {
            return;
        }
        int shrink = runtime.lobby.settings.shrinkMetersPerMinute;
        int radius = frame.current.radiusMeters;
        boolean stays = shrink == 0
                || frame.next.radiusMeters >= frame.current.radiusMeters;
        txtZone.setText(Geo.formatDistance(radius));
        txtZoneSub.setText(shrink == 0 ? getString(R.string.game_static)
                : stays ? getString(R.string.game_endzone_reached)
                : "−" + shrink + " m/min");

        String nextText;
        if (stays) {
            nextText = getString(frame.lastStage
                            ? R.string.game_endzone_label
                            : R.string.game_next_label)
                    + " · " + Geo.formatDistance(frame.next.radiusMeters)
                    + getString(R.string.game_stays);
        } else {
            long until = frame.remainingMs();
            if (runtime.inHidePhase()) {
                until += runtime.hideRemainingMs();
            }
            if (frame.lastStage) {
                nextText = getString(R.string.game_zone_endzone,
                        Geo.formatDistance(frame.next.radiusMeters))
                        + (until > 0 ? " · " + Geo.formatClock(until) : "");
            } else {
                nextText = getString(R.string.game_zone_next_prefix,
                        Geo.formatDistance(frame.next.radiusMeters))
                        + " · " + Geo.formatClock(until);
            }
        }
        if (txtZoneNow != null) {
            txtZoneNow.setText(getString(R.string.game_zone_now_prefix,
                    Geo.formatDistance(radius)));
        }
        if (txtZoneNext != null) {
            txtZoneNext.setText(nextText);
        }

        if (zoneOverlay != null) {
            zoneOverlay.setZones(
                    new GeoPoint(frame.current.lat, frame.current.lng), radius,
                    new GeoPoint(frame.next.lat, frame.next.lng), frame.next.radiusMeters);
            map.invalidate();
        }
    }

    private void updateHidePhaseUi(Player me) {
        if (hidePhaseBanner == null) {
            return;
        }
        boolean hide = runtime.inHidePhase();
        hidePhaseBanner.setVisibility(hide ? View.VISIBLE : View.GONE);
        if (hide) {
            long remaining = runtime.hideRemainingMs();
            long seconds = (remaining + 999) / 1000;
            txtHidePhaseTimer.setText(Geo.formatClock(remaining));
            txtHidePhaseSub.setText(isHunter(me)
                    ? getString(R.string.game_wait_start, seconds)
                    : getString(R.string.game_wait_hide, seconds));
            if (btnCatch instanceof MaterialButton) {
                MaterialButton button = (MaterialButton) btnCatch;
                button.setEnabled(false);
                button.setText(R.string.locked);
            }
        } else if (!runtime.ended && btnCatch instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) btnCatch;
            button.setEnabled(true);
            button.setText(catchButtonLabel());
        }
    }

    private void updateTopCounts() {
        List<Player> players = client.getPlayers();
        GameSettings settings = modeSettings();
        int alive = 0;
        int totalHiders = 0;
        int aliveHiders = 0;
        int activeHiders = 0;
        int totalSurvivors = 0;
        int aliveSurvivors = 0;
        for (Player p : players) {
            if (p.alive) {
                alive++;
            }
            if (p.isHider()) {
                totalHiders++;
                if (p.alive) {
                    aliveHiders++;
                }
                if (p.alive && !p.frozen) {
                    activeHiders++;
                }
                if (!p.infected) {
                    totalSurvivors++;
                    if (p.alive) {
                        aliveSurvivors++;
                    }
                }
            }
        }
        txtAlive.setText(String.valueOf(alive));
        if (settings.isZombie()) {
            txtAliveSub.setText(getString(R.string.game_survivors_progress,
                    aliveSurvivors, totalSurvivors));
        } else if (settings.isIce()) {
            txtAliveSub.setText(getString(R.string.game_active_progress,
                    activeHiders, totalHiders));
        } else if (settings.isAssassin()) {
            txtAliveSub.setText(getString(R.string.game_alive_progress, alive, players.size()));
        } else {
            txtAliveSub.setText(getString(R.string.game_hiders_progress,
                    aliveHiders, totalHiders));
        }
    }

    private void updatePingUi(long now) {
        if (runtime.ended) {
            txtPingLabel.setText(R.string.game_round_over);
            txtPingTimer.setText("--:--");
            txtPingSub.setText(R.string.game_map_stays);
            return;
        }
        if (runtime.inHidePhase()) {
            txtPingLabel.setText(R.string.game_hide_phase);
            txtPingTimer.setText(Geo.formatClock(runtime.hideRemainingMs()));
            txtPingSub.setText(R.string.game_pings_after);
            txtPingLabel.setTextColor(ContextCompat.getColor(this, R.color.orange));
            txtPingTimer.setTextColor(ContextCompat.getColor(this, R.color.orange));
            return;
        }
        if (!client.getPingSnapshot().isEmpty()) {
            long pingAt = client.getLastPingElapsed();
            boolean fresh = pingAt > 0 && now - pingAt < 5500L;
            txtPingLabel.setText(fresh ? R.string.game_ping : R.string.game_next_ping_label);
            txtPingTimer.setText(Geo.formatClock(nextPingInMs()));
            txtPingSub.setText(R.string.game_positions_frozen);
            txtPingLabel.setTextColor(ContextCompat.getColor(this,
                    fresh ? R.color.orange : R.color.muted));
            txtPingTimer.setTextColor(ContextCompat.getColor(this,
                    fresh ? R.color.orange : R.color.text_soft));
            return;
        }
        txtPingLabel.setText(R.string.game_next_ping_label);
        txtPingLabel.setTextColor(ContextCompat.getColor(this, R.color.muted));
        txtPingTimer.setText(Geo.formatClock(nextPingInMs()));
        txtPingSub.setText(R.string.game_visible_until_next);
        txtPingTimer.setTextColor(ContextCompat.getColor(this, R.color.text_soft));
    }

    private long nextPingInMs() {
        long interval = Math.max(10, runtime.lobby.settings.pingIntervalSeconds) * 1000L;
        long elapsed = runtime.huntingElapsedMs();
        long until = interval - (elapsed % interval);
        return until <= 0 ? interval : until;
    }

    private void setZoneDanger(boolean danger) {
        if (zoneOverlay != null) {
            zoneOverlay.setDanger(danger);
            map.invalidate();
        }
    }

    private void updateOutsideState(Player me, long now, ZoneGeometry.Frame frame) {
        if (runtime.ended || !me.alive || runtime.inHidePhase()) {
            warningBanner.setVisibility(View.GONE);
            setZoneDanger(false);
            outsideSinceElapsed = -1;
            return;
        }
        GameClient.LocationPoint loc = client.getMyLocation();
        if (loc == null) {
            warningBanner.setVisibility(View.GONE);
            setZoneDanger(false);
            outsideSinceElapsed = -1;
            return;
        }
        boolean stale = now - loc.elapsedMs > 25000L;
        int radius = frame.current.radiusMeters;
        double distance = Geo.distanceMeters(loc.lat, loc.lng, frame.current.lat, frame.current.lng);
        double tolerance = Math.min(22.0, Math.max(6.0, loc.accuracy * 0.5));
        boolean outside = distance > radius + tolerance;

        if (stale) {
            outsideSinceElapsed = -1;
            warningBanner.setVisibility(View.GONE);
            setZoneDanger(false);
            return;
        }
        if (!outside) {
            outsideSinceElapsed = -1;
            lastWarningStage = -1;
            warningBanner.setVisibility(View.GONE);
            setZoneDanger(false);
            return;
        }

        if (outsideSinceElapsed < 0) {
            outsideSinceElapsed = now;
            lastWarningStage = -1;
            UiUtils.vibrate(this, 0, 90, 70, 90);
        }
        long outMs = now - outsideSinceElapsed;
        long graceMs = runtime.lobby.settings.graceSeconds * 1000L;
        long left = Math.max(0, graceMs - outMs);
        long secondsLeft = (left + 999) / 1000;
        warningBanner.setText(secondsLeft > 0
                ? getString(R.string.game_back_in_zone, secondsLeft)
                : getString(R.string.game_eliminating));
        warningBanner.setVisibility(View.VISIBLE);
        setZoneDanger(true);

        int stage = secondsLeft <= 5 ? 5 : secondsLeft <= 10 ? 10 : 30;
        if (stage != lastWarningStage) {
            lastWarningStage = stage;
            if (secondsLeft <= 10 && secondsLeft > 0) {
                UiUtils.vibrate(this, 0, 60, 50, 60);
            }
        }
        if (left <= 0) {
            if (client.eliminateSelfByZone()) {
                UiUtils.vibrate(this, 0, 180, 90, 180);
                showOutState("ZONE");
            }
            warningBanner.setVisibility(View.GONE);
        }
    }

    private void updateConnectionUi() {
        boolean connected = client.isConnected();
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        GameClient.LocationPoint loc = client.getMyLocation();
        boolean hasLocation = loc != null && SystemClock.elapsedRealtime() - loc.elapsedMs < 25000L;
        txtConnection.setText(connected ? R.string.game_live : R.string.game_offline);
        UiUtils.pill(txtConnection,
                connected ? R.drawable.bg_pill_mint_soft : R.drawable.bg_pill_red_soft,
                connected ? R.color.mint : R.color.red);
        if (!connected) {
            connectionBanner.setText(R.string.game_reconnecting);
            connectionBanner.setVisibility(View.VISIBLE);
        } else if (!hasPermission) {
            connectionBanner.setText(R.string.game_gps_no_access);
            connectionBanner.setVisibility(View.VISIBLE);
        } else if (!hasLocation && runtime.elapsedMs() > 12000L) {
            connectionBanner.setText(R.string.game_gps_searching);
            connectionBanner.setVisibility(View.VISIBLE);
        } else {
            connectionBanner.setVisibility(View.GONE);
        }
    }

    private void updatePlayerStrip(boolean force) {
        List<Player> players = client.getPlayers();
        StringBuilder signature = new StringBuilder();
        for (Player p : players) {
            signature.append(p.id).append(':').append(p.name).append(':')
                    .append(p.role).append(':').append(p.alive).append(':')
                    .append(p.infected).append(':').append(p.frozen).append(':')
                    .append(p.kills).append(':').append(p.targetId).append(':')
                    .append(p.eliminatedReason).append(';');
        }
        String sig = signature.toString();
        if (force || !sig.equals(playerSignature)) {
            playerSignature = sig;
            buildPlayerStrip(players);
        }
        long now = SystemClock.elapsedRealtime();
        for (Player p : players) {
            View chip = playerChips.get(p.id);
            if (chip == null) {
                continue;
            }
            TextView status = chip.findViewById(R.id.status);
            View dot = chip.findViewById(R.id.onlineDot);
            if (!p.alive) {
                status.setText(R.string.game_out_short);
                dot.setVisibility(View.GONE);
            } else if (p.frozen) {
                status.setText(R.string.game_role_frozen_lower);
                dot.setVisibility(View.VISIBLE);
            } else if (p.infected) {
                status.setText(R.string.game_role_infected);
                dot.setVisibility(View.VISIBLE);
            } else if (p.id.equals(client.getPlayerId())) {
                status.setText(R.string.you);
                dot.setVisibility(client.getMyLocation() != null ? View.VISIBLE : View.GONE);
            } else if (!p.hasLocation) {
                status.setText(R.string.game_no_ping);
                dot.setVisibility(View.GONE);
            } else {
                long posAge = positionAgeMs(p, now);
                status.setText(posAge < 0
                        ? getString(R.string.game_no_ping)
                        : Geo.ago(this, posAge));
                // The dot reflects the connection, not the age of the frozen
                // ping position. Opponents stay online between pings.
                long seenAge = Math.max(0, now - p.lastSeenElapsed);
                dot.setVisibility(seenAge < 15000L ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void buildPlayerStrip(List<Player> players) {
        playerChips.clear();
        playerStrip.removeAllViews();
        GameSettings settings = modeSettings();
        for (Player p : players) {
            View chip = LayoutInflater.from(this).inflate(R.layout.item_game_player, playerStrip, false);
            TextView avatar = chip.findViewById(R.id.avatar);
            TextView name = chip.findViewById(R.id.name);
            TextView status = chip.findViewById(R.id.status);
            View dot = chip.findViewById(R.id.onlineDot);
            avatar.setText(p.initials());
            name.setText(p.id.equals(client.getPlayerId()) ? getString(R.string.you) : p.name);
            int stroke;
            int fill;
            int textColor;
            if (!p.alive) {
                stroke = ContextCompat.getColor(this, R.color.line);
                fill = ContextCompat.getColor(this, R.color.surface);
                textColor = ContextCompat.getColor(this, R.color.muted);
                status.setTextColor(ContextCompat.getColor(this, R.color.muted));
            } else {
                int colorRes = roleColorRes(p, settings);
                stroke = ContextCompat.getColor(this, colorRes);
                fill = ContextCompat.getColor(this, softColorRes(colorRes));
                textColor = stroke;
                status.setTextColor(ContextCompat.getColor(this, R.color.muted));
            }
            if (p.id.equals(client.getPlayerId()) && p.alive) {
                stroke = Color.WHITE;
            }
            UiUtils.circleBackground(avatar, stroke, fill);
            avatar.setTextColor(textColor);
            name.setTextColor(ContextCompat.getColor(this, p.alive ? R.color.text_soft : R.color.muted));
            dot.setVisibility(p.alive && (p.id.equals(client.getPlayerId()) || p.hasLocation)
                    ? View.VISIBLE : View.GONE);
            chip.setTag(p.id);
            playerChips.put(p.id, chip);
            playerStrip.addView(chip);
        }
    }

    private int softColorRes(int colorRes) {
        if (colorRes == R.color.orange) {
            return R.color.orange_soft;
        }
        if (colorRes == R.color.mint) {
            return R.color.mint_soft;
        }
        if (colorRes == R.color.blue) {
            return R.color.blue_soft;
        }
        if (colorRes == R.color.lime) {
            return R.color.lime_soft;
        }
        return R.color.surface_2;
    }

    private void refreshMarkerPositions() {
        if (placingDecoy) {
            hidePlayerMarkers();
            hideDecoyMarkers();
            map.invalidate();
            return;
        }
        List<Player> snapshot = client.getPingSnapshot();
        Player me = client.getMe();
        boolean teamLive = client.isTeamLiveActive() && me != null && me.alive;
        boolean reveal = isRevealActive(me);
        if (snapshot.isEmpty() && !teamLive && !reveal) {
            markersRevealed = false;
            hidePlayerMarkers();
            refreshDecoyMarkers();
            return;
        }
        markersRevealed = true;
        Map<String, Player> live = new HashMap<>();
        for (Player p : client.getPlayers()) {
            live.put(p.id, p);
        }
        // Hide everything first, then show only allowed positions. This also
        // removes markers for players who were caught since the last ping.
        for (Marker marker : playerMarkers.values()) {
            marker.setEnabled(false);
        }
        Set<String> shown = new HashSet<>();
        for (Player pinged : snapshot) {
            if (pinged.id.equals(client.getPlayerId())) {
                continue;
            }
            Marker marker = playerMarkers.get(pinged.id);
            if (marker == null) {
                continue;
            }
            Player current = live.get(pinged.id);
            if (current == null || !current.alive) {
                continue;
            }
            marker.setTitle(pinged.name);
            // Frozen ping position is the default. Teammates move live when
            // the host enabled team visibility; a radar effect or the zombie
            // horde sense reveals the matching targets briefly.
            if (revealShows(me, current) && isFreshLive(current)) {
                marker.setPosition(new GeoPoint(current.lat, current.lng));
            } else if (teamLive && teamLiveEligible(me, current) && isFreshLive(current)) {
                marker.setPosition(new GeoPoint(current.lat, current.lng));
            } else {
                marker.setPosition(new GeoPoint(pinged.lat, pinged.lng));
            }
            marker.setEnabled(true);
            shown.add(pinged.id);
        }
        if (teamLive) {
            // During the hide phase there is no ping yet, but teammates should
            // already see each other. Eliminated teammates disappear at once.
            for (Player current : live.values()) {
                if (current.id.equals(client.getPlayerId()) || shown.contains(current.id)
                        || !current.alive || !current.hasLocation
                        || !teamLiveEligible(me, current) || !isFreshLive(current)) {
                    continue;
                }
                Marker marker = playerMarkers.get(current.id);
                if (marker == null) {
                    continue;
                }
                marker.setTitle(current.name);
                marker.setPosition(new GeoPoint(current.lat, current.lng));
                marker.setEnabled(true);
            }
        }
        if (reveal) {
            for (Player current : live.values()) {
                if (current.id.equals(client.getPlayerId()) || shown.contains(current.id)
                        || !current.alive || !current.hasLocation
                        || !revealShows(me, current) || !isFreshLive(current)) {
                    continue;
                }
                Marker marker = playerMarkers.get(current.id);
                if (marker == null) {
                    continue;
                }
                marker.setTitle(current.name);
                marker.setPosition(new GeoPoint(current.lat, current.lng));
                marker.setEnabled(true);
            }
        }
        refreshDecoyMarkers();
        map.invalidate();
    }

    private boolean sameRole(Player a, Player b) {
        if (a == null || b == null || a.role == null || b.role == null) {
            return false;
        }
        return a.role.equals(b.role);
    }

    private boolean isFreshLive(Player p) {
        if (p == null || !p.hasLocation || p.lastSeenElapsed <= 0) {
            return false;
        }
        return SystemClock.elapsedRealtime() - p.lastSeenElapsed < 15000L;
    }

    private boolean isRevealActive(Player me) {
        if (me == null || runtime == null || runtime.ended || !me.alive) {
            return false;
        }
        return client.isRadarActive() || client.isZombieSenseActive();
    }

    /** True when a radar effect or the zombie horde sense shows this player live. */
    private boolean revealShows(Player me, Player p) {
        if (me == null || p == null || !isRevealActive(me)
                || p.id.equals(client.getPlayerId())) {
            return false;
        }
        if (client.isZombieSenseActive()) {
            return me.isZombie() && p.isSurvivor();
        }
        return client.isRadarRevealing(p);
    }

    private boolean teamLiveEligible(Player me, Player p) {
        if (me == null || p == null) {
            return false;
        }
        GameSettings settings = modeSettings();
        if (settings.isAssassin() && settings.assassinTargetPingOnly
                && (me.targetId == null || !p.id.equals(me.targetId))) {
            return false;
        }
        return sameRole(me, p);
    }

    /**
     * True when the map shows this player's live heartbeat position instead of
     * the frozen position from the last ping.
     */
    private boolean usesLivePosition(Player p) {
        if (p == null || !p.alive) {
            return false;
        }
        if (p.id.equals(client.getPlayerId())) {
            return true;
        }
        Player me = client.getMe();
        if (revealShows(me, p) && isFreshLive(p)) {
            return true;
        }
        return client.isTeamLiveActive() && me != null && me.alive
                && teamLiveEligible(me, p) && isFreshLive(p);
    }

    /**
     * Age of the position currently displayed for this player. Teammates move
     * live when team-live is enabled; every other marker is frozen at the last
     * ping. Returns -1 when there is no position to show yet.
     */
    private long positionAgeMs(Player p, long now) {
        if (p == null || !p.alive) {
            return -1;
        }
        if (p.id.equals(client.getPlayerId())) {
            return 0;
        }
        if (usesLivePosition(p)) {
            return Math.max(0, now - p.lastSeenElapsed);
        }
        long pingAt = client.getLastPingElapsed();
        if (pingAt > 0 && isInPingSnapshot(p.id)) {
            return Math.max(0, now - pingAt);
        }
        return -1;
    }

    private boolean isInPingSnapshot(String playerId) {
        for (Player pinged : client.getPingSnapshot()) {
            if (pinged.id.equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    private void refreshMarkersVisibility() {
        Player me = client.getMe();
        boolean reveal = isRevealActive(me);
        if (client.getPingSnapshot().isEmpty()
                && !(client.isTeamLiveActive() && me != null && me.alive) && !reveal) {
            markersRevealed = false;
            hidePlayerMarkers();
            refreshDecoyMarkers();
        } else {
            markersRevealed = true;
            refreshMarkerPositions();
        }
    }

    private void hidePlayerMarkers() {
        for (Marker marker : playerMarkers.values()) {
            marker.setEnabled(false);
        }
        if (map != null) {
            map.invalidate();
        }
    }

    private void hideDecoyMarkers() {
        for (Marker marker : decoyMapMarkers.values()) {
            marker.setEnabled(false);
        }
    }

    private void refreshDecoyMarkers() {
        if (map == null || runtime == null) {
            return;
        }
        Player me = client.getMe();
        boolean activePlayer = me != null && me.alive && !runtime.ended && !placingDecoy;
        boolean hunter = activePlayer && isHunter(me);
        List<DecoyMarker> active;
        if (hunter) {
            active = client.getActiveDecoyMarkers();
        } else if (activePlayer) {
            // Hiders only see their own decoy, but it stays visible for the
            // whole lifetime so the buyer can watch it on the map.
            active = new ArrayList<>();
            for (DecoyMarker decoy : client.getActiveDecoyMarkers()) {
                if (decoy.ownerId != null && decoy.ownerId.equals(client.getPlayerId())) {
                    active.add(decoy);
                }
            }
        } else {
            active = Collections.emptyList();
        }
        Set<String> activeIds = new HashSet<>();
        for (DecoyMarker decoy : active) {
            activeIds.add(decoy.id);
            boolean own = decoy.ownerId != null && decoy.ownerId.equals(client.getPlayerId());
            Marker marker = decoyMapMarkers.get(decoy.id);
            if (marker == null) {
                marker = new Marker(map);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marker.setRelatedObject(decoy.id);
                marker.setIcon(new PingMarkerDrawable(getResources().getDisplayMetrics().density,
                        decoy.initials(), ContextCompat.getColor(this,
                        own ? R.color.lime : R.color.mint)));
                marker.setOnMarkerClickListener((m, mapView) -> {
                    DecoyMarker latest = findDecoy((String) m.getRelatedObject());
                    if (latest != null) {
                        boolean mine = latest.ownerId != null
                                && latest.ownerId.equals(client.getPlayerId());
                        Player owner = latest.ownerId == null
                                ? null : client.findPlayer(latest.ownerId);
                        String role = owner == null
                                ? getString(R.string.game_role_hider_lower)
                                : getString(roleLowerRes(owner, modeSettings()));
                        String label = mine
                                ? getString(R.string.game_decoy_own)
                                : latest.ownerName + " · " + role
                                + getString(R.string.game_ping_suffix);
                        Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
                decoyMapMarkers.put(decoy.id, marker);
                map.getOverlays().add(marker);
            }
            marker.setTitle(own ? getString(R.string.game_decoy_own) : decoy.ownerName);
            marker.setPosition(new GeoPoint(decoy.lat, decoy.lng));
            marker.setEnabled(true);
        }
        Iterator<Map.Entry<String, Marker>> it = decoyMapMarkers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Marker> entry = it.next();
            if (!activeIds.contains(entry.getKey())) {
                entry.getValue().setEnabled(false);
                map.getOverlays().remove(entry.getValue());
                it.remove();
            }
        }
        map.invalidate();
    }

    private DecoyMarker findDecoy(String id) {
        if (id == null) {
            return null;
        }
        for (DecoyMarker decoy : client.getActiveDecoyMarkers()) {
            if (id.equals(decoy.id)) {
                return decoy;
            }
        }
        return null;
    }

    private void onNewPing() {
        pingBanner.setText(R.string.game_ping_banner_long);
        pingBanner.setVisibility(View.VISIBLE);
        pingBannerHideAt = SystemClock.elapsedRealtime() + 5500L;
        refreshMarkerPositions();
        showPingPulses();
        UiUtils.vibrate(this, 0, 55, 45, 55);
    }

    private void showPingPulses() {
        if (pingPulseOverlay == null) {
            return;
        }
        List<PingPulseOverlay.PulsePoint> points = new ArrayList<>();
        for (Player p : client.getPingSnapshot()) {
            int color = p.id.equals(client.getPlayerId())
                    ? ContextCompat.getColor(this, R.color.blue)
                    : markerColor(p);
            points.add(new PingPulseOverlay.PulsePoint(p.lat, p.lng, color));
        }
        pingPulseOverlay.show(points);
        map.invalidate();
    }

    private void toggleCatchCode() {
        if (codeVisible) {
            hideCatchCode();
            return;
        }
        String code = client.getMyCatchCode();
        if (code == null || code.isEmpty()) {
            Toast.makeText(this, R.string.game_catch_code_preparing, Toast.LENGTH_SHORT).show();
            return;
        }
        codeVisible = true;
        codeHideAt = SystemClock.elapsedRealtime() + 30000L;
        txtCatchCode.setText(code);
        txtCodeHint.setText(R.string.game_code_visible_30);
        btnRevealCode.setText(R.string.hide);
        btnRevealCode.setIconResource(R.drawable.ic_visibility_off);
        UiUtils.vibrate(this, 0, 35);
    }

    private void hideCatchCode() {
        codeVisible = false;
        txtCatchCode.setText("••••");
        txtCodeHint.setText(modeSettings().isZombie()
                ? R.string.game_survivor_objective : R.string.game_code_hint);
        btnRevealCode.setText(R.string.game_show);
        btnRevealCode.setIconResource(R.drawable.ic_visibility);
    }

    // ---------------------------------------------------------------------
    // Shop
    // ---------------------------------------------------------------------

    private void updateShopUi(Player me, long now) {
        if (shopRow == null) {
            return;
        }
        boolean visible = client.isShopEnabled() && me != null && me.alive && !runtime.ended;
        shopRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            if (placingDecoy) {
                placingDecoy = false;
            }
            pendingDecoyItem = null;
            dismissShopDialog();
            return;
        }
        txtCoins.setText(String.valueOf(client.getCoinBalance()));
        boolean hidePhase = runtime.inHidePhase();
        btnShop.setEnabled(!hidePhase);
        btnShop.setText(hidePhase ? R.string.shop_later : R.string.game_shop);

        if (placingDecoy) {
            txtShopStatus.setText(R.string.game_decoy_place_hint);
        } else if (hidePhase) {
            txtShopStatus.setText(R.string.game_shop_opens);
        } else {
            List<String> effects = new ArrayList<>();
            GameSettings settings = modeSettings();
            long radar = client.getRadarRemainingMs();
            if (radar > 0) {
                int radarPrefix = settings.isZombie() ? R.string.game_sense_prefix
                        : settings.isIce() ? R.string.game_thermal_prefix
                        : R.string.game_radar_prefix;
                effects.add(getString(radarPrefix) + Geo.formatClock(radar));
            }
            long ghost = client.getMyGhostRemainingMs();
            if (ghost > 0) {
                int ghostPrefix = settings.isAssassin()
                        ? R.string.game_shadow_prefix : R.string.game_camo_prefix;
                effects.add(getString(ghostPrefix) + Geo.formatClock(ghost));
            }
            long decoy = client.getMyDecoyRemainingMs();
            if (decoy > 0) {
                effects.add(getString(R.string.game_decoy_prefix) + Geo.formatClock(decoy));
            }
            if (effects.isEmpty()) {
                txtShopStatus.setText(R.string.game_shop_status);
            } else {
                txtShopStatus.setText(String.join(" · ", effects)
                        + getString(R.string.game_shop_active_suffix));
            }
        }
        if (shopDialog != null && shopDialog.isShowing()) {
            renderShopDialog();
        }
    }

    private void showShop() {
        if (!client.isShopEnabled() || runtime == null || runtime.ended) {
            return;
        }
        Player me = client.getMe();
        if (me == null || !me.alive) {
            Toast.makeText(this, R.string.game_out_no_buy,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (runtime.inHidePhase()) {
            Toast.makeText(this, R.string.game_shop_opens,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (shopDialog != null && shopDialog.isShowing()) {
            renderShopDialog();
            return;
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_shop, null, false);
        shopCoinsLabel = content.findViewById(R.id.shopCoins);
        shopItemList = content.findViewById(R.id.shopItemList);
        shopDialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setNegativeButton(R.string.close, null)
                .create();
        shopDialog.setOnDismissListener(d -> {
            shopDialog = null;
            shopItemList = null;
            shopCoinsLabel = null;
            shopDialogSignature = "";
        });
        shopDialog.show();
        shopDialogSignature = "";
        renderShopDialog();
    }

    private void renderShopDialog() {
        if (shopDialog == null || !shopDialog.isShowing()
                || shopItemList == null || shopCoinsLabel == null || runtime == null) {
            return;
        }
        Player me = client.getMe();
        GameRuntime rt = runtime;
        int balance = client.getCoinBalance();
        List<ShopCatalog.Item> items = shopItemsFor(me, rt);
        StringBuilder signature = new StringBuilder();
        signature.append(balance).append('|').append(rt.inHidePhase()).append('|');
        boolAppend(signature, me != null && me.isHider());
        boolAppend(signature, me != null && me.isSeeker());
        boolAppend(signature, me != null && me.isZombie());
        boolAppend(signature, me != null && me.frozen);
        boolAppend(signature, me != null && me.isAssassin());
        boolAppend(signature, me != null && me.infected);
        for (ShopCatalog.Item item : items) {
            boolAppend(signature, ShopCatalog.canUse(item, me));
            boolAppend(signature, isShopItemActive(item));
        }
        if (shopItemList.getChildCount() > 0
                && signature.toString().equals(shopDialogSignature)) {
            return;
        }
        shopDialogSignature = signature.toString();
        shopCoinsLabel.setText(getResources().getQuantityString(R.plurals.coins,
                balance, balance));
        shopItemList.removeAllViews();

        for (ShopCatalog.Item item : items) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_shop_offer, shopItemList, false);
            ImageView icon = row.findViewById(R.id.shopIcon);
            TextView name = row.findViewById(R.id.shopName);
            TextView description = row.findViewById(R.id.shopDescription);
            TextView price = row.findViewById(R.id.shopPrice);
            MaterialButton buy = row.findViewById(R.id.btnShopBuy);

            icon.setImageResource(shopIcon(item));
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                    item.isSeekerOnly() || item.isZombieOnly()
                            ? R.color.orange : R.color.lime)));
            name.setText(item.nameRes);
            description.setText(item.descriptionRes);
            int priceValue = ShopCatalog.priceFor(rt.lobby.settings, item);
            price.setText(getResources().getQuantityString(R.plurals.coins,
                    priceValue, priceValue));
            price.setTextColor(ContextCompat.getColor(this,
                    balance >= priceValue ? R.color.lime : R.color.muted));

            boolean roleOk = ShopCatalog.canUse(item, me);
            boolean active = isShopItemActive(item);
            boolean affordable = balance >= priceValue;
            boolean hidePhase = rt.inHidePhase();
            boolean enabled;
            String label;
            if (!roleOk) {
                label = getString(ShopCatalog.roleLabelRes(item));
                enabled = false;
            } else if (active) {
                label = getString(R.string.active);
                enabled = false;
            } else if (hidePhase) {
                label = getString(R.string.later);
                enabled = false;
            } else if (!affordable) {
                label = getString(R.string.shop_too_little);
                enabled = false;
            } else {
                label = getString(R.string.shop_buy);
                enabled = true;
            }
            buy.setText(label);
            buy.setEnabled(enabled);
            if (enabled) {
                buy.setOnClickListener(v -> buyShopItem(item));
            }
            shopItemList.addView(row);
        }
    }

    private List<ShopCatalog.Item> shopItemsFor(Player me, GameRuntime rt) {
        List<ShopCatalog.Item> items = ShopCatalog.itemsFor(
                rt == null || rt.lobby == null ? null : rt.lobby.settings);
        if (me != null && me.frozen) {
            List<ShopCatalog.Item> thawItems = new ArrayList<>();
            for (ShopCatalog.Item item : items) {
                if (ShopCatalog.isThaw(item)) {
                    thawItems.add(item);
                }
            }
            return thawItems;
        }
        return items;
    }

    private static void boolAppend(StringBuilder sb, boolean value) {
        sb.append(value ? '1' : '0');
    }

    private boolean isShopItemActive(ShopCatalog.Item item) {
        if (ShopCatalog.isGhost(item)) {
            return client.getMyGhostRemainingMs() > 0;
        }
        if (ShopCatalog.isRadar(item)) {
            return client.getRadarRemainingMs() > 0;
        }
        return false;
    }

    private int shopIcon(ShopCatalog.Item item) {
        if (ShopCatalog.isDecoy(item)) {
            return R.drawable.ic_location_pin;
        }
        if (ShopCatalog.isRadar(item)) {
            return R.drawable.ic_radar;
        }
        if (ShopCatalog.isThaw(item)) {
            return R.drawable.ic_visibility;
        }
        return R.drawable.ic_visibility_off;
    }

    private void buyShopItem(ShopCatalog.Item item) {
        if (item == null || runtime == null) {
            return;
        }
        if (ShopCatalog.isDecoy(item)) {
            beginDecoyPlacement(item);
            return;
        }
        GameClient.ShopResult result = client.buyShopItem(item.id, 0, 0);
        if (!result.success) {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
        } else {
            UiUtils.vibrate(this, 0, 45, 40, 90);
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        }
        renderShopDialog();
        updateShopUi(client.getMe(), SystemClock.elapsedRealtime());
    }

    private void beginDecoyPlacement(ShopCatalog.Item item) {
        if (runtime == null || runtime.ended || !client.isShopEnabled()
                || item == null || !ShopCatalog.isDecoy(item)) {
            return;
        }
        if (runtime.inHidePhase()) {
            Toast.makeText(this, R.string.game_shop_opens,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Player me = client.getMe();
        if (me == null || !me.alive) {
            return;
        }
        if (!ShopCatalog.canUse(item, me)) {
            Toast.makeText(this, getString(ShopCatalog.roleLabelRes(item)),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int price = ShopCatalog.priceFor(runtime.lobby.settings, item);
        int balance = client.getCoinBalance();
        if (balance < price) {
            Toast.makeText(this, getString(R.string.game_too_few_coins, price, balance),
                    Toast.LENGTH_LONG).show();
            return;
        }
        GameClient.LocationPoint location = client.getMyLocation();
        if (location == null) {
            Toast.makeText(this, R.string.game_gps_missing,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        pendingDecoyItem = item;
        placingDecoy = true;
        dismissShopDialog();
        hidePlayerMarkers();
        hideDecoyMarkers();
        map.getController().animateTo(new GeoPoint(location.lat, location.lng));
        map.getController().setZoom(17.0);
        Toast.makeText(this, R.string.game_decoy_tap,
                Toast.LENGTH_LONG).show();
        updateShopUi(me, SystemClock.elapsedRealtime());
    }

    private void placeDecoyAt(double lat, double lng) {
        if (!placingDecoy) {
            return;
        }
        placingDecoy = false;
        ShopCatalog.Item item = pendingDecoyItem;
        pendingDecoyItem = null;
        GameClient.ShopResult result = client.buyShopItem(
                item == null ? ShopCatalog.ITEM_FAKE_MARKER : item.id, lat, lng);
        if (result.success) {
            UiUtils.vibrate(this, 0, 55, 50, 120);
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        }
        refreshMarkersVisibility();
        updateShopUi(client.getMe(), SystemClock.elapsedRealtime());
    }

    private void cancelDecoyPlacement(boolean announce) {
        if (!placingDecoy) {
            return;
        }
        placingDecoy = false;
        pendingDecoyItem = null;
        refreshMarkersVisibility();
        updateShopUi(client.getMe(), SystemClock.elapsedRealtime());
        if (announce) {
            Toast.makeText(this, R.string.game_placement_cancelled, Toast.LENGTH_SHORT).show();
        }
    }

    private void dismissShopDialog() {
        if (shopDialog != null) {
            shopDialog.dismiss();
            shopDialog = null;
        }
        shopItemList = null;
        shopCoinsLabel = null;
    }

    private void showOutState(String reason) {
        if (runtime.ended || endShown) {
            return;
        }
        seekerAction.setVisibility(View.GONE);
        hiderCodePanel.setVisibility(View.GONE);
        targetPanel.setVisibility(View.GONE);
        statePanel.setVisibility(View.GONE);
        outPanel.setVisibility(View.VISIBLE);
        placingDecoy = false;
        pendingDecoyItem = null;
        dismissShopDialog();
        if (shopRow != null) {
            shopRow.setVisibility(View.GONE);
        }
        String message;
        if ("ZONE".equals(reason)) {
            message = getString(R.string.out_reason_zone);
        } else if ("GEFANGEN".equals(reason)) {
            GameSettings settings = modeSettings();
            message = getString(settings.isZombie()
                    ? R.string.out_reason_zombie
                    : settings.isIce()
                    ? R.string.out_reason_ice
                    : R.string.out_reason_caught);
        } else if ("ERFROREN".equals(reason)) {
            message = getString(R.string.out_reason_frozen);
        } else if ("ATTENTAT".equals(reason)) {
            message = getString(R.string.out_reason_assassin);
        } else if ("VERLASSEN".equals(reason)) {
            message = getString(R.string.out_reason_left);
        } else {
            message = getString(R.string.out_reason_default);
        }
        txtOutReason.setText(message + getString(R.string.out_watch_suffix));
        hideCatchCode();
    }

    private void openCatchDialog() {
        Player me = client.getMe();
        GameSettings settings = modeSettings();
        boolean zombie = settings.isZombie();
        boolean ice = settings.isIce();
        if (me == null || (zombie ? !me.isZombie() : !me.isSeeker()) || !me.alive) {
            return;
        }
        if (me.frozen) {
            Toast.makeText(this, R.string.error_frozen, Toast.LENGTH_SHORT).show();
            return;
        }
        if (zombie && me.infected && me.infectedAtHuntMs > 0) {
            long protection = Math.max(5, settings.zombieConversionSeconds) * 1000L;
            long left = protection - (runtime.huntingElapsedMs() - me.infectedAtHuntMs);
            if (left > 0) {
                Toast.makeText(this, getString(R.string.error_zombie_protected,
                        (int) ((left + 999) / 1000)), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (runtime.inHidePhase()) {
            Toast.makeText(this, getString(R.string.game_hide_time_left,
                            ((runtime.hideRemainingMs() + 999) / 1000)),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        List<Player> players = client.getPlayers();
        List<Player> targets = new ArrayList<>();
        for (Player p : players) {
            if (p.id.equals(client.getPlayerId())) {
                continue;
            }
            boolean candidate = zombie ? p.isSurvivor()
                    : ice ? (p.isHider() && !p.frozen) : p.isHider();
            if (candidate && p.alive) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) {
            int emptyRes = zombie ? R.string.game_no_survivors_left
                    : ice ? R.string.game_no_active_hiders_left
                    : R.string.game_no_hiders_left;
            Toast.makeText(this, emptyRes, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        long now = SystemClock.elapsedRealtime();
        for (Player p : targets) {
            long age = positionAgeMs(p, now);
            labels.add(p.name + " · " + (age < 0
                    ? getString(R.string.game_no_ping)
                    : Geo.ago(this, age)));
        }
        int titleRes = zombie ? R.string.game_who_turn
                : ice ? R.string.game_who_freeze : R.string.game_who_found;
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setItems(labels.toArray(new String[0]), (dialog, which)
                        -> showCatchCodeDialog(targets.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCatchCodeDialog(Player target) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_catch_code, null, false);
        EditText input = content.findViewById(R.id.inputCatchCode);
        TextView error = content.findViewById(R.id.txtCatchError);
        TextView prompt = content.findViewById(R.id.txtCatchPrompt);
        prompt.setText(getString(R.string.game_shows_code, target.name));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                error.setVisibility(View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                String digits = s.toString().replaceAll("\\D", "");
                if (!digits.equals(s.toString())) {
                    input.setText(digits);
                    input.setSelection(digits.length());
                }
            }
        });

        GameSettings settings = modeSettings();
        int positiveRes = settings.isZombie() ? R.string.game_infect
                : settings.isIce() ? R.string.game_freeze : R.string.game_catch;
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game_enter_catch_code)
                .setView(content)
                .setPositiveButton(positiveRes, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = input.getText().toString().replaceAll("\\D", "");
                GameClient.CatchResult result = client.submitCatch(target.id, code);
                if (result.success) {
                    dialog.dismiss();
                    UiUtils.vibrate(this, 0, 80, 60, 160);
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                    updatePlayerStrip(true);
                    updateModePanels(client.getMe());
                    refreshMarkersVisibility();
                } else {
                    error.setText(result.message);
                    error.setVisibility(View.VISIBLE);
                    UiUtils.vibrate(this, 0, 45);
                }
            });
            input.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.show();
    }

    private void recenterMap() {
        GameClient.LocationPoint p = client.getMyLocation();
        GeoPoint target;
        if (p != null) {
            target = new GeoPoint(p.lat, p.lng);
            map.getController().setZoom(17.0);
        } else {
            ZoneGeometry.Frame frame = runtime.zoneFrame();
            target = new GeoPoint(frame.current.lat, frame.current.lng);
        }
        map.getController().animateTo(target);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, MENU_OVERVIEW, 0, getString(R.string.game_player_overview));
        menu.getMenu().add(0, MENU_HELP, 1, getString(R.string.game_help_rules));
        if (client.isHost() && !runtime.ended) {
            menu.getMenu().add(0, MENU_END, 2, getString(R.string.game_end_round));
        }
        menu.getMenu().add(0, MENU_LEAVE, 3, getString(R.string.game_leave_game));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_OVERVIEW) {
                showPlayerOverview();
                return true;
            }
            if (item.getItemId() == MENU_HELP) {
                showHelp();
                return true;
            }
            if (item.getItemId() == MENU_END) {
                confirmHostEnd();
                return true;
            }
            if (item.getItemId() == MENU_LEAVE) {
                confirmLeave();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showHelp() {
        long hideSeconds = runtime == null ? 25 : runtime.hidePhaseMs / 1000L;
        StringBuilder message = new StringBuilder(getString(R.string.game_help_body, hideSeconds));
        if (runtime != null && runtime.lobby.settings != null && runtime.lobby.settings.shopEnabled) {
            int fake = ShopCatalog.priceFor(runtime.lobby.settings, ShopCatalog.FAKE_MARKER);
            int ghost = ShopCatalog.priceFor(runtime.lobby.settings, ShopCatalog.GHOST);
            int radar = ShopCatalog.priceFor(runtime.lobby.settings, ShopCatalog.RADAR);
            message.append(getString(R.string.game_shop_help, fake, ghost, radar));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game_how_it_works)
                .setMessage(message.toString())
                .setPositiveButton(R.string.game_got_it, null)
                .show();
    }

    private void confirmHostEnd() {
        if (!client.isHost()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game_end_round_title)
                .setMessage(R.string.game_end_round_message)
                .setPositiveButton(R.string.game_end, (d, w) -> client.endGameAsHost())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmLeave() {
        boolean host = client.isHost();
        new MaterialAlertDialogBuilder(this)
                .setTitle(host ? R.string.game_leave_title : R.string.game_leave_title_short)
                .setMessage(host
                        ? R.string.game_leave_message_host
                        : R.string.game_leave_message_guest)
                .setPositiveButton(R.string.leave, (d, w) -> {
                    client.leaveCurrentSession();
                    launchHome();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openReplay() {
        String reason = runtime == null ? "" : runtime.endReason;
        String winner = runtime == null ? "" : runtime.winner;
        String id = client.ensureReplaySaved(reason, winner);
        if (id == null || id.isEmpty()) {
            Toast.makeText(this, R.string.game_no_replay, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ReplayActivity.class);
        intent.putExtra(ReplayActivity.EXTRA_REPLAY_ID, id);
        startActivity(intent);
    }

    private void showEndOverlay(String reason, String winner) {
        if (endShown) {
            return;
        }
        endShown = true;
        btnEndReplay.setVisibility(client.hasReplayData() ? View.VISIBLE : View.GONE);
        if (chatDialog != null) {
            chatDialog.dismiss();
        }
        if (overviewDialog != null) {
            overviewDialog.dismiss();
        }
        placingDecoy = false;
        pendingDecoyItem = null;
        dismissShopDialog();
        hideIntro();
        hideCountdown();
        if (codeVisible) {
            hideCatchCode();
        }
        warningBanner.setVisibility(View.GONE);
        runtime.ended = true;
        runtime.endReason = reason;
        if (winner != null && !winner.isEmpty()) {
            runtime.winner = winner;
        }

        Player me = client.getMe();
        boolean iWon = isWinningSide(winner, me);

        if ("SEEKER".equals(winner)) {
            endTitle.setText(countSeekers() > 1
                    ? R.string.game_winners_seekers
                    : R.string.game_winner_seeker);
        } else if ("HIDERS".equals(winner)) {
            endTitle.setText(R.string.game_winners_hiders);
        } else if ("ZOMBIES".equals(winner)) {
            endTitle.setText(R.string.game_winners_zombies);
        } else if ("SURVIVORS".equals(winner)) {
            endTitle.setText(R.string.game_winner_survivors);
        } else if ("ICE".equals(winner)) {
            endTitle.setText(R.string.game_winner_ice_hunter);
        } else if (winner != null && winner.startsWith("ASSASSIN:")) {
            String winnerId = winner.substring("ASSASSIN:".length());
            if (me != null && me.id.equals(winnerId)) {
                endTitle.setText(R.string.game_you_are_last);
            } else {
                String name = "";
                Player winnerPlayer = client.findPlayer(winnerId);
                if (winnerPlayer != null) {
                    name = winnerPlayer.name;
                }
                endTitle.setText(getString(R.string.game_winner_assassin,
                        name.isEmpty() ? "?" : name));
            }
        } else {
            endTitle.setText(R.string.game_round_ended);
        }
        if (winner != null && !winner.isEmpty()) {
            boolean soloWin = winner.startsWith("ASSASSIN:");
            endBadge.setText(iWon
                    ? (soloWin ? R.string.game_you_win : R.string.game_your_team_wins)
                    : R.string.game_round_ended);
            UiUtils.pill(endBadge,
                    iWon ? R.drawable.bg_pill_lime_soft : R.drawable.bg_pill_neutral,
                    iWon ? R.color.lime : R.color.text_soft);
        } else {
            endBadge.setText(R.string.game_aborted);
            UiUtils.pill(endBadge, R.drawable.bg_pill_neutral, R.color.text_soft);
        }
        endReason.setText(endReasonText(reason, winner));

        long duration = Math.min(runtime.huntingElapsedMs(), runtime.durationMs);
        if ("HOST_BEENDET".equals(reason) || "SUCHER_VERLASSEN".equals(reason)) {
            duration = runtime.elapsedMs();
        }
        GameSettings settings = modeSettings();
        List<Player> players = client.getPlayers();
        int totalHiders = 0;
        int aliveHiders = 0;
        int activeHiders = 0;
        int caught = 0;
        int infected = 0;
        int frozenNow = 0;
        int frozenGone = 0;
        int totalSurvivors = 0;
        int aliveSurvivors = 0;
        int totalAssassins = 0;
        int aliveAssassins = 0;
        for (Player p : players) {
            if (p.isHider()) {
                totalHiders++;
                if (p.alive) {
                    aliveHiders++;
                    if (!p.frozen) {
                        activeHiders++;
                    }
                }
                if ("GEFANGEN".equals(p.eliminatedReason)) {
                    caught++;
                }
                if (p.infected) {
                    infected++;
                }
                if (p.frozen) {
                    frozenNow++;
                }
                if ("ERFROREN".equals(p.eliminatedReason)) {
                    frozenGone++;
                }
                if (!p.infected) {
                    totalSurvivors++;
                    if (p.alive) {
                        aliveSurvivors++;
                    }
                }
            }
            if (p.isAssassin()) {
                totalAssassins++;
                if (p.alive) {
                    aliveAssassins++;
                }
            }
        }
        endStatOneValue.setText(Geo.formatClock(duration));
        if (settings.isZombie()) {
            endStatTwoValue.setText(aliveSurvivors + " / " + totalSurvivors);
            endStatTwoLabel.setText(R.string.game_survivors_left);
            endStatThreeValue.setText(String.valueOf(infected));
            endStatThreeLabel.setText(R.string.game_infected_stat);
        } else if (settings.isIce()) {
            endStatTwoValue.setText(activeHiders + " / " + totalHiders);
            endStatTwoLabel.setText(R.string.game_active_hiders_left);
            endStatThreeValue.setText(String.valueOf(frozenNow + frozenGone));
            endStatThreeLabel.setText(R.string.game_frozen_stat);
        } else if (settings.isAssassin()) {
            endStatTwoValue.setText(aliveAssassins + " / " + totalAssassins);
            endStatTwoLabel.setText(R.string.game_alive_label);
            endStatThreeValue.setText(String.valueOf(me == null ? 0 : me.kills));
            endStatThreeLabel.setText(R.string.game_kills);
        } else {
            endStatTwoValue.setText(aliveHiders + " / " + totalHiders);
            endStatTwoLabel.setText(R.string.game_hiders_left);
            endStatThreeValue.setText(String.valueOf(caught));
            endStatThreeLabel.setText(R.string.game_catches);
        }
        double roundMeters = client.getRoundDistanceMeters();
        endStatFourValue.setText(Geo.formatDistance((int) Math.round(roundMeters)));
        endStatFiveValue.setText(client.getRoundCalories() + getString(R.string.game_kcal_suffix));

        endOverlay.setVisibility(View.VISIBLE);
        endOverlay.setAlpha(0f);
        endOverlay.animate().alpha(1f).setDuration(260).start();
        UiUtils.vibrate(this, iWon ? new long[]{0, 120, 80, 120, 80, 220}
                : new long[]{0, 180, 90, 180});
    }

    private boolean isWinningSide(String winner, Player me) {
        if (winner == null || winner.isEmpty() || me == null) {
            return false;
        }
        switch (winner) {
            case "SEEKER":
            case "ICE":
                return me.isSeeker();
            case "HIDERS":
                return me.isHider();
            case "ZOMBIES":
                return me.isZombie();
            case "SURVIVORS":
                return me.isSurvivor();
            default:
                if (winner.startsWith("ASSASSIN:")) {
                    return me.id.equals(winner.substring("ASSASSIN:".length()));
                }
                return false;
        }
    }

    private String endReasonText(String reason, String winner) {
        if (reason == null) {
            return getString(R.string.end_default);
        }
        switch (reason) {
            case "ALLE_RAUS":
                return getString(R.string.end_all_out);
            case "ZEIT":
                return getString("HIDERS".equals(winner)
                        ? R.string.end_time_hiders : R.string.end_time_seekers);
            case "SUCHER_RAUS":
                return getString(modeSettings().isIce()
                        ? R.string.end_ice_hunter_out : R.string.end_seekers_out);
            case "SUCHER_VERLASSEN":
                return getString(modeSettings().isIce()
                        ? R.string.end_ice_hunter_left : R.string.end_seekers_left);
            case "HOST_BEENDET":
                return getString(R.string.end_host_ended);
            case "HOST_VERLASSEN":
                return getString(R.string.replay_end_host_left);
            case "ALLE_INFIZIERT":
                return getString(R.string.end_all_infected);
            case "ZOMBIES_RAUS":
                return getString(R.string.end_zombie_out);
            case "ALLE_EINGEFROREN":
                return getString(R.string.end_all_frozen);
            case "LETZTER_ATTENTAETER":
                return getString(R.string.end_last_assassin);
            case "ZEIT_ZOMBIE":
                return getString("ZOMBIES".equals(winner)
                        ? R.string.end_time_zombies : R.string.end_time_survivors);
            case "ZEIT_EIS":
                return getString("ICE".equals(winner)
                        ? R.string.end_time_ice : R.string.end_time_hiders_ice);
            case "ZEIT_ATTENTAETER":
                return getString(R.string.end_time_assassin);
            default:
                return getString(R.string.end_default);
        }
    }

    private String computeLocalWinner() {
        GameSettings settings = modeSettings();
        List<Player> players = client.getPlayers();
        int aliveSeekers = 0;
        int aliveHiders = 0;
        int aliveSurvivors = 0;
        int aliveZombies = 0;
        int activeHiders = 0;
        int aliveAssassins = 0;
        String leadingAssassin = "";
        int bestKills = -1;
        for (Player p : players) {
            if (!p.alive) {
                continue;
            }
            if (p.isSeeker()) {
                aliveSeekers++;
            }
            if (p.isHider()) {
                aliveHiders++;
                if (!p.infected) {
                    aliveSurvivors++;
                }
                if (!p.frozen) {
                    activeHiders++;
                }
            }
            if (p.isZombie()) {
                aliveZombies++;
            }
            if (p.isAssassin()) {
                aliveAssassins++;
                if (p.kills > bestKills
                        || (p.kills == bestKills && p.id.compareTo(leadingAssassin) < 0)) {
                    bestKills = p.kills;
                    leadingAssassin = p.id;
                }
            }
        }
        if (settings.isZombie()) {
            if (aliveZombies == 0) {
                return "SURVIVORS";
            }
            return aliveSurvivors == 0 ? "ZOMBIES" : "SURVIVORS";
        }
        if (settings.isIce()) {
            if (aliveSeekers == 0) {
                return "HIDERS";
            }
            return activeHiders == 0 ? "ICE" : "HIDERS";
        }
        if (settings.isAssassin()) {
            return leadingAssassin.isEmpty() ? "" : "ASSASSIN:" + leadingAssassin;
        }
        if (aliveSeekers == 0) {
            return "HIDERS";
        }
        if (aliveHiders == 0) {
            return "SEEKER";
        }
        return "HIDERS";
    }

    private int countSeekers() {
        int count = 0;
        for (Player p : client.getPlayers()) {
            if (p.isSeeker()) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------------
    // Chat
    // ---------------------------------------------------------------------

    private void showChat() {
        if (chatDialog != null && chatDialog.isShowing()) {
            return;
        }
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_chat, null, false);
        chatScroll = content.findViewById(R.id.chatScroll);
        chatMessageList = content.findViewById(R.id.chatMessageList);
        txtChatEmpty = content.findViewById(R.id.txtChatEmpty);
        inputChat = content.findViewById(R.id.inputChat);
        btnChatAll = content.findViewById(R.id.btnChatAll);
        btnChatTeam = content.findViewById(R.id.btnChatTeam);
        txtChatTeamHint = content.findViewById(R.id.txtChatTeamHint);
        TextView chatRole = content.findViewById(R.id.txtChatRole);

        Player me = client.getMe();
        GameSettings settings = modeSettings();
        int chatRoleRes = me == null
                ? R.string.game_role_hider : roleUpperRes(me, settings);
        int chatColorRes = me == null
                ? R.color.mint : roleColorRes(me, settings);
        chatRole.setText(chatRoleRes);
        UiUtils.pill(chatRole, me == null
                        ? R.drawable.bg_pill_mint_soft : rolePillRes(me, settings),
                chatColorRes);
        txtChatTeamHint.setText(chatTeamHintRes(me, settings));

        btnChatAll.setOnClickListener(v -> setChatScope(ChatMessage.SCOPE_ALL));
        btnChatTeam.setOnClickListener(v -> setChatScope(ChatMessage.SCOPE_TEAM));
        content.findViewById(R.id.btnChatSend).setOnClickListener(v -> sendChatMessage());
        inputChat.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                sendChatMessage();
                return true;
            }
            return false;
        });

        chatOpen = true;
        unreadChat = 0;
        updateChatBadge();
        setChatScope(chatScope);

        chatDialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setNegativeButton(R.string.close, null)
                .create();
        chatDialog.setOnDismissListener(d -> {
            chatOpen = false;
            chatDialog = null;
        });
        chatDialog.show();
    }

    private int chatTeamHintRes(Player me, GameSettings settings) {
        if (settings.isZombie()) {
            return me != null && me.isZombie()
                    ? R.string.chat_team_hint_zombies
                    : R.string.chat_team_hint_survivors;
        }
        if (settings.isAssassin()) {
            return R.string.chat_team_hint_assassins;
        }
        if (settings.isIce()) {
            return me != null && me.isSeeker()
                    ? R.string.chat_team_hint_seekers : R.string.chat_team_hint_hiders;
        }
        return me != null && me.isSeeker()
                ? R.string.chat_team_hint_seekers : R.string.chat_team_hint_hiders;
    }

    private void setChatScope(String scope) {
        chatScope = ChatMessage.SCOPE_TEAM.equals(scope)
                ? ChatMessage.SCOPE_TEAM : ChatMessage.SCOPE_ALL;
        if (btnChatAll != null && btnChatTeam != null) {
            boolean all = ChatMessage.SCOPE_ALL.equals(chatScope);
            btnChatAll.setBackgroundResource(all
                    ? R.drawable.bg_scope_selected : R.drawable.bg_scope_unselected);
            btnChatAll.setTextColor(ContextCompat.getColor(this,
                    all ? R.color.ink : R.color.text_soft));
            btnChatTeam.setBackgroundResource(all
                    ? R.drawable.bg_scope_unselected : R.drawable.bg_scope_selected);
            btnChatTeam.setTextColor(ContextCompat.getColor(this,
                    all ? R.color.text_soft : R.color.ink));
        }
        renderChatMessages();
    }

    private void sendChatMessage() {
        if (inputChat == null) {
            return;
        }
        String text = inputChat.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (!client.isConnected()) {
            Toast.makeText(this, R.string.chat_no_connection, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!client.sendChat(text, chatScope)) {
            Toast.makeText(this, R.string.chat_send_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        inputChat.setText("");
        renderChatMessages();
    }

    private void renderChatMessages() {
        if (chatMessageList == null) {
            return;
        }
        chatMessageList.removeAllViews();
        int shown = 0;
        for (ChatMessage message : client.getChatMessages()) {
            if (!chatScope.equals(message.scope)) {
                continue;
            }
            View row = LayoutInflater.from(this).inflate(R.layout.item_chat_message, chatMessageList, false);
            TextView sender = row.findViewById(R.id.chatSender);
            TextView scopeView = row.findViewById(R.id.chatScope);
            TextView time = row.findViewById(R.id.chatTime);
            TextView body = row.findViewById(R.id.chatText);
            boolean mine = message.senderId != null && message.senderId.equals(client.getPlayerId());
            boolean team = message.isTeam();
            sender.setText(mine ? getString(R.string.you) : (message.senderName == null || message.senderName.isEmpty()
                    ? getString(R.string.game_player_name_placeholder) : message.senderName));
            sender.setTextColor(ContextCompat.getColor(this, mine ? R.color.lime : R.color.text));
            scopeView.setText(team ? R.string.game_team_scope : R.string.game_all_scope);
            UiUtils.pill(scopeView,
                    team ? R.drawable.bg_pill_orange_soft : R.drawable.bg_pill_neutral,
                    team ? R.color.orange : R.color.muted);
            row.setBackgroundResource(team ? R.drawable.bg_chat_team : R.drawable.bg_chat_bubble);
            time.setText(DateFormat.format("HH:mm",
                    message.timestamp == 0 ? System.currentTimeMillis() : message.timestamp));
            body.setText(message.text);
            chatMessageList.addView(row);
            shown++;
        }
        if (txtChatEmpty != null) {
            txtChatEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        }
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void updateChatBadge() {
        if (chatBadge == null) {
            return;
        }
        if (unreadChat <= 0) {
            chatBadge.setVisibility(View.GONE);
            return;
        }
        chatBadge.setText(unreadChat > 9 ? "9+" : String.valueOf(unreadChat));
        chatBadge.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------------
    // Player overview
    // ---------------------------------------------------------------------

    private void showPlayerOverview() {
        if (overviewDialog != null && overviewDialog.isShowing()) {
            populatePlayerOverview();
            return;
        }
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_player_overview, null, false);
        overviewOutTitle = content.findViewById(R.id.overviewOutTitle);
        overviewOutEmpty = content.findViewById(R.id.overviewOutEmpty);
        overviewOutList = content.findViewById(R.id.overviewOutList);
        overviewAliveTitle = content.findViewById(R.id.overviewAliveTitle);
        overviewAliveList = content.findViewById(R.id.overviewAliveList);
        overviewDialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setNegativeButton(R.string.close, null)
                .create();
        overviewDialog.setOnDismissListener(d -> overviewDialog = null);
        overviewDialog.show();
        populatePlayerOverview();
    }

    private void populatePlayerOverview() {
        if (overviewOutList == null || overviewAliveList == null) {
            return;
        }
        List<Player> out = new ArrayList<>();
        List<Player> alive = new ArrayList<>();
        for (Player p : client.getPlayers()) {
            if (p.alive) {
                alive.add(p);
            } else {
                out.add(p);
            }
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        alive.sort((a, b) -> {
            if (isHunter(a) != isHunter(b)) {
                return isHunter(a) ? -1 : 1;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        overviewOutTitle.setText(getString(R.string.overview_found_count, out.size()));
        overviewAliveTitle.setText(getString(R.string.overview_alive_count, alive.size()));
        overviewOutEmpty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
        overviewOutList.removeAllViews();
        overviewAliveList.removeAllViews();
        for (Player p : out) {
            overviewOutList.addView(buildOverviewRow(p, false));
        }
        for (Player p : alive) {
            overviewAliveList.addView(buildOverviewRow(p, true));
        }
    }

    private View buildOverviewRow(Player p, boolean alive) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_lobby_player, null, false);
        TextView avatar = row.findViewById(R.id.avatar);
        TextView name = row.findViewById(R.id.txtName);
        TextView meta = row.findViewById(R.id.txtMeta);
        TextView badge = row.findViewById(R.id.txtBadge);
        boolean mine = p.id.equals(client.getPlayerId());
        LobbySnapshot snapshot = client.getLobby();
        boolean isHost = snapshot != null && p.id.equals(snapshot.hostId);

        avatar.setText(p.initials());
        name.setText(mine ? p.name + getString(R.string.lobby_you_suffix) : p.name);
        badge.setVisibility(View.VISIBLE);
        if (!alive) {
            String reason = eliminatedReasonText(p.eliminatedReason);
            meta.setText(isHost ? getString(R.string.host) + " · " + reason : reason);
            badge.setText(R.string.overview_out_badge);
            UiUtils.pill(badge, R.drawable.bg_pill_red_soft, R.color.red);
            UiUtils.circleBackground(avatar,
                    ContextCompat.getColor(this, R.color.red),
                    ContextCompat.getColor(this, R.color.red_soft));
            avatar.setTextColor(ContextCompat.getColor(this, R.color.red));
        } else {
            GameSettings settings = modeSettings();
            StringBuilder info = new StringBuilder(getString(roleLowerRes(p, settings)));
            if (p.frozen) {
                info.append(" · ").append(getString(R.string.game_role_frozen_lower));
            } else if (p.infected) {
                info.append(" · ").append(getString(R.string.game_role_infected));
            }
            if (p.isAssassin()) {
                info.append(" · ").append(getString(R.string.game_kills_progress, p.kills));
            }
            if (isHost) {
                info.append(getString(R.string.overview_host_suffix));
            }
            long posAge = positionAgeMs(p, SystemClock.elapsedRealtime());
            if (posAge < 0) {
                info.append(getString(R.string.overview_status_no_ping));
            } else if (usesLivePosition(p)) {
                info.append(getString(R.string.overview_status_live));
            } else {
                info.append(getString(R.string.overview_status_ping, Geo.ago(this, posAge)));
            }
            meta.setText(info.toString());
            int colorRes = roleColorRes(p, settings);
            int color = ContextCompat.getColor(this, colorRes);
            badge.setText(roleUpperRes(p, settings));
            UiUtils.pill(badge, rolePillRes(p, settings), colorRes);
            UiUtils.circleBackground(avatar, color,
                    ContextCompat.getColor(this, softColorRes(colorRes)));
            avatar.setTextColor(color);
        }
        return row;
    }

    private String eliminatedReasonText(String reason) {
        if ("GEFANGEN".equals(reason)) {
            return getString(R.string.overview_status_found);
        }
        if ("ZONE".equals(reason)) {
            return getString(R.string.overview_status_zone);
        }
        if ("VERLASSEN".equals(reason)) {
            return getString(R.string.overview_status_left);
        }
        if ("ERFROREN".equals(reason)) {
            return getString(R.string.overview_status_frozen);
        }
        if ("ATTENTAT".equals(reason)) {
            return getString(R.string.overview_status_assassin);
        }
        return getString(R.string.overview_status_eliminated);
    }

    @Override
    public void onPing() {
        onNewPing();
    }

    @Override
    public void onHuntStarted() {
        if (isFinishing()) {
            return;
        }
        Player me = client.getMe();
        GameSettings settings = modeSettings();
        int message;
        if (settings.isZombie()) {
            message = me != null && me.isZombie()
                    ? R.string.game_notif_hunt_zombie : R.string.game_notif_hunt_survivor;
        } else if (settings.isIce()) {
            message = me != null && me.isSeeker()
                    ? R.string.game_notif_hunt_ice_hunter : R.string.game_notif_hunt_ice_hider;
        } else if (settings.isAssassin()) {
            message = R.string.game_notif_hunt_assassin;
        } else {
            message = me != null && me.isSeeker()
                    ? R.string.game_notif_hunt_seeker : R.string.game_notif_hunt_hider;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        updateHidePhaseUi(me);
        updatePingUi(SystemClock.elapsedRealtime());
        updateModePanels(me);
    }

    @Override
    public void onChatMessage(ChatMessage message) {
        if (message == null || isFinishing()) {
            return;
        }
        boolean own = message.senderId != null && message.senderId.equals(client.getPlayerId());
        if (chatOpen) {
            if (chatScope.equals(message.scope)) {
                renderChatMessages();
            }
        } else if (!own) {
            unreadChat++;
            updateChatBadge();
        }
    }

    @Override
    public void onGameEnded(String reason, String winner) {
        showEndOverlay(reason, winner);
    }

    @Override
    public void onPlayersChanged() {
        Player me = client.getMe();
        if (me != null) {
            renderRole(me);
            updateModePanels(me);
            if (!me.alive && !endShown) {
                showOutState(me.eliminatedReason);
            }
        }
        updatePlayerStrip(true);
        updateShopUi(me, SystemClock.elapsedRealtime());
        if (overviewDialog != null && overviewDialog.isShowing()) {
            populatePlayerOverview();
        }
        if (markersRevealed || isRevealActive(me)) {
            refreshMarkerPositions();
        } else {
            refreshDecoyMarkers();
        }
    }

    @Override
    public void onShopChanged() {
        if (isFinishing()) {
            return;
        }
        updateShopUi(client.getMe(), SystemClock.elapsedRealtime());
        if (markersRevealed || client.isRadarActive() || client.isZombieSenseActive()) {
            refreshMarkerPositions();
        } else {
            refreshDecoyMarkers();
        }
        if (shopDialog != null && shopDialog.isShowing()) {
            renderShopDialog();
        }
    }

    @Override
    public void onSelfEliminated(String reason) {
        if ("ERFROREN".equals(reason)) {
            Toast.makeText(this, R.string.game_melted_toast, Toast.LENGTH_LONG).show();
        }
        showOutState(reason);
    }

    @Override
    public void onSelfInfected(String byName) {
        if (isFinishing() || endShown) {
            return;
        }
        GameSettings settings = modeSettings();
        Toast.makeText(this, getString(R.string.game_infected_banner,
                Math.max(5, settings.zombieConversionSeconds)), Toast.LENGTH_LONG).show();
        UiUtils.vibrate(this, 0, 180, 80, 180);
        Player me = client.getMe();
        if (me != null) {
            renderRole(me);
            updateModePanels(me);
        }
        updatePlayerStrip(true);
    }

    @Override
    public void onSelfFrozen(String byName) {
        if (isFinishing() || endShown) {
            return;
        }
        Toast.makeText(this, R.string.error_frozen, Toast.LENGTH_LONG).show();
        UiUtils.vibrate(this, 0, 160, 80, 160);
        Player me = client.getMe();
        if (me != null) {
            renderRole(me);
            updateModePanels(me);
        }
        updatePlayerStrip(true);
    }

    @Override
    public void onSelfThawed(String byName) {
        if (isFinishing() || endShown) {
            return;
        }
        Toast.makeText(this, R.string.game_thawed_toast, Toast.LENGTH_LONG).show();
        UiUtils.vibrate(this, 0, 45, 40, 90);
        Player me = client.getMe();
        if (me != null) {
            renderRole(me);
            updateModePanels(me);
        }
        updatePlayerStrip(true);
    }

    @Override
    public void onTeammateThawed(String victimName, String byName) {
        if (isFinishing() || endShown) {
            return;
        }
        Toast.makeText(this, getString(R.string.notify_thawed_by, byName, victimName),
                Toast.LENGTH_SHORT).show();
        updatePlayerStrip(true);
    }

    @Override
    public void onError(String message) {
        if (!isFinishing() && !endShown) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        updateConnectionUi();
        updatePlayerStrip(false);
    }

    @Override
    public void onLocationChanged(GameClient.LocationPoint point) {
        if (point == null || ownMarker == null) {
            return;
        }
        ownMarker.setPosition(new GeoPoint(point.lat, point.lng));
        ownMarker.setEnabled(true);
        if (!initialLocationFocus) {
            initialLocationFocus = true;
        }
        map.invalidate();
        updateConnectionUi();
    }

    private void showPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_title)
                .setMessage(R.string.permission_message)
                .setPositiveButton(R.string.permission_settings, (d, w) -> {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void launchHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void exitToHome(boolean shutdown) {
        if (shutdown) {
            client.shutdown();
        }
        launchHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            client.startLocationTracking();
        }
    }

    @Override
    protected void onPause() {
        if (map != null) {
            map.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (chatDialog != null) {
            chatDialog.dismiss();
            chatDialog = null;
        }
        if (overviewDialog != null) {
            overviewDialog.dismiss();
            overviewDialog = null;
        }
        dismissShopDialog();
        if (ui != null) {
            ui.removeCallbacksAndMessages(null);
        }
        if (introHideRunnable != null) {
            ui.removeCallbacks(introHideRunnable);
        }
        client.clearListener(this);
        if (pingPulseOverlay != null) {
            pingPulseOverlay.stop();
        }
        if (map != null) {
            map.onDetach();
        }
        super.onDestroy();
    }
}
