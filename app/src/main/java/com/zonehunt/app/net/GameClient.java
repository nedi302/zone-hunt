package com.zonehunt.app.net;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.zonehunt.app.R;
import com.zonehunt.app.model.ChatMessage;
import com.zonehunt.app.model.DecoyMarker;
import com.zonehunt.app.model.GameReplay;
import com.zonehunt.app.model.GameRuntime;
import com.zonehunt.app.model.GameSettings;
import com.zonehunt.app.model.LobbySnapshot;
import com.zonehunt.app.model.Player;
import com.zonehunt.app.model.ShopCatalog;
import com.zonehunt.app.util.Geo;
import com.zonehunt.app.util.Hash;
import com.zonehunt.app.util.NotificationHelper;
import com.zonehunt.app.util.PlayerStats;
import com.zonehunt.app.util.ReplayStore;

/**
 * The complete multiplayer state machine.
 *
 * Transport is MQTT over TLS against a public broker. A host publishes a retained
 * lobby document under a 4 digit code. During a round, only small JSON events are
 * exchanged. The clients derive the shrinking circle locally from the start time.
 *
 * This is intentionally serverless: it keeps the project runnable without handing out
 * backend credentials. For a commercial release, replace the broker with your own
 * authenticated endpoint (see README).
 */
public class GameClient {

    private static final String TAG = "GameClient";
    public static final String BROKER_URI = "ssl://broker.emqx.io:8883";
    private static final String TOPIC_ROOT = "zonenjagd/v1/";
    private static final int QOS_STATE = 1;
    private static final int QOS_HEARTBEAT = 0;
    private static final long HEARTBEAT_MS = 4000L;
    private static final long LOBBY_KEEPALIVE_MS = 8000L;
    private static final long LOBBY_MAX_AGE_MS = 90_000L;
    private static final long SHOP_DECOY_DURATION_MS = 45_000L;
    private static final long SHOP_RADAR_DURATION_MS = 10_000L;
    private static final long SHOP_GHOST_PING_BUFFER_MS = 2_000L;
    private static final int[] ZONE_STAGES = {90, 80, 70, 60, 50, 40, 30, 20, 10};

    public interface Listener {
        default void onLobbyChanged(LobbySnapshot snapshot) {
        }

        default void onGameStarted() {
        }

        default void onGameEnded(String reason, String winner) {
        }

        default void onPing() {
        }

        default void onPlayersChanged() {
        }

        default void onSelfEliminated(String reason) {
        }

        default void onError(String message) {
        }

        default void onConnectionChanged(boolean connected) {
        }

        default void onLocationChanged(LocationPoint point) {
        }

        default void onHuntStarted() {
        }

        default void onChatMessage(ChatMessage message) {
        }

        default void onShopChanged() {
        }

        /** Zombie mode: the local player was turned into a zombie. */
        default void onSelfInfected(String byName) {
        }

        /** Ice mode: the local player was frozen. */
        default void onSelfFrozen(String byName) {
        }

        /** Ice mode: the local player was thawed. */
        default void onSelfThawed(String byName) {
        }

        /** A teammate thawed another player. */
        default void onTeammateThawed(String victimName, String byName) {
        }
    }

    public static class LocationPoint {
        public final double lat;
        public final double lng;
        public final float accuracy;
        public final long elapsedMs;

        public LocationPoint(double lat, double lng, float accuracy, long elapsedMs) {
            this.lat = lat;
            this.lng = lng;
            this.accuracy = accuracy;
            this.elapsedMs = elapsedMs;
        }
    }

    public static class CatchResult {
        public final boolean success;
        public final String message;

        CatchResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static class ShopResult {
        public final boolean success;
        public final String message;

        ShopResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler gameHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SecureRandom random = new SecureRandom();
    private final Object stateLock = new Object();
    private final Object subscriptionLock = new Object();
    private final Map<String, Player> livePlayers = new LinkedHashMap<>();
    private final Set<String> subscriptions = new LinkedHashSet<>();

    private final String playerId = UUID.randomUUID().toString();

    private volatile Listener listener;
    private volatile MqttClient mqtt;
    private volatile boolean connected;
    private volatile boolean host;
    private volatile boolean activeSession;
    private volatile boolean joinRequested;
    private volatile boolean admitted;
    private volatile String name = "";
    private volatile String code = "";
    private volatile String gameId = "";
    private volatile LobbySnapshot lobby;
    private volatile GameRuntime runtime;
    private volatile int generation;
    private volatile boolean shuttingDown;

    private volatile String myCatchCode = "";
    private volatile String myCatchHash = "";
    private volatile LocationPoint myLocation;
    private volatile long lastPingElapsed = 0;
    private final Object statsLock = new Object();
    private double roundDistanceMeters = 0;
    private double pendingStatDistance = 0;
    private long lastStatsSaveElapsed = 0;
    private LocationPoint distanceAnchor;
    private boolean statsFlushed;
    /**
     * Positions captured at the most recent ping. They stay frozen until the
     * next ping so that every player sees the same revealed snapshot.
     */
    private volatile List<Player> pingSnapshot = Collections.emptyList();

    private final Object replayLock = new Object();
    private volatile GameReplay replay;
    private volatile String replayId = "";
    private volatile boolean replaySaved;

    private Runnable heartbeatRunnable;
    private Runnable hostPingRunnable;
    private Runnable hostEndRunnable;
    private Runnable lobbyKeepaliveRunnable;
    private Runnable hidePhaseRunnable;
    private Runnable zoneWatchRunnable;
    private Runnable pingWatchRunnable;

    private final Object chatLock = new Object();
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final Set<String> chatMessageIds = new HashSet<>();
    private int lastZonePercent = 100;
    private boolean huntStartNotified;

    private final Object shopLock = new Object();
    private long shopAliveAccumMs;
    private long shopLastTickElapsed;
    private int shopCoinsSpent;
    private final Map<String, Long> shopGhostUntilHuntMs = new LinkedHashMap<>();
    private final Map<String, Long> shopRadarUntilHuntMs = new LinkedHashMap<>();
    private final Map<String, DecoyMarker> shopDecoys = new LinkedHashMap<>();
    /** Ice mode: when the current thaw attempt started, keyed by frozen player. */
    private final Map<String, Long> iceThawHoldStartHuntMs = new LinkedHashMap<>();
    private Runnable iceWatchRunnable;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private volatile boolean locationTracking;

    public GameClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getGameId() {
        return gameId;
    }

    public boolean isHost() {
        return host;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean hasActiveSession() {
        return activeSession;
    }

    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void clearListener(Listener l) {
        if (listener == l) {
            listener = null;
        }
    }

    public LobbySnapshot getLobby() {
        synchronized (stateLock) {
            return lobby == null ? null : lobby.copy();
        }
    }

    public GameRuntime getRuntime() {
        return runtime;
    }

    public List<Player> getPlayers() {
        synchronized (stateLock) {
            List<Player> copy = new ArrayList<>(livePlayers.size());
            for (Player p : livePlayers.values()) {
                copy.add(p.copy());
            }
            return copy;
        }
    }

    public Player getMe() {
        synchronized (stateLock) {
            Player p = livePlayers.get(playerId);
            if (p != null) {
                return p.copy();
            }
            if (lobby != null) {
                Player lobbyPlayer = lobby.findPlayer(playerId);
                if (lobbyPlayer != null) {
                    return lobbyPlayer.copy();
                }
            }
            return null;
        }
    }

    public Player findPlayer(String id) {
        synchronized (stateLock) {
            Player p = livePlayers.get(id);
            if (p == null && lobby != null) {
                p = lobby.findPlayer(id);
            }
            return p == null ? null : p.copy();
        }
    }

    public LocationPoint getMyLocation() {
        return myLocation;
    }

    public String getMyCatchCode() {
        return myCatchCode;
    }

    public long getLastPingElapsed() {
        return lastPingElapsed;
    }

    /**
     * Frozen positions from the last ping. Empty until the first ping of the
     * round. The returned list is immutable.
     */
    public List<Player> getPingSnapshot() {
        return pingSnapshot;
    }

    public double getRoundDistanceMeters() {
        synchronized (statsLock) {
            return roundDistanceMeters;
        }
    }

    public int getRoundCalories() {
        return PlayerStats.caloriesForMeters(getRoundDistanceMeters());
    }

    // ---------------------------------------------------------------------
    // Shop
    // ---------------------------------------------------------------------

    public boolean isShopEnabled() {
        GameRuntime rt = runtime;
        return rt != null && !rt.ended && rt.lobby != null && rt.lobby.settings != null
                && rt.lobby.settings.shopEnabled;
    }

    /**
     * Coins earned by surviving. One coin per five seconds; the balance only
     * grows while the local player is alive.
     */
    public int getCoinsEarned() {
        updateSurvivalCoins();
        synchronized (shopLock) {
            return (int) (shopAliveAccumMs / (ShopCatalog.SECONDS_PER_COIN * 1000L));
        }
    }

    public int getCoinBalance() {
        updateSurvivalCoins();
        synchronized (shopLock) {
            int earned = (int) (shopAliveAccumMs / (ShopCatalog.SECONDS_PER_COIN * 1000L));
            return Math.max(0, earned - shopCoinsSpent);
        }
    }

    private void updateSurvivalCoins() {
        GameRuntime rt = runtime;
        Player me = getMe();
        long now = SystemClock.elapsedRealtime();
        synchronized (shopLock) {
            long last = shopLastTickElapsed;
            shopLastTickElapsed = now;
            if (rt == null || rt.ended || me == null || !me.alive || last <= 0L) {
                return;
            }
            long delta = now - last;
            if (delta > 0L) {
                shopAliveAccumMs += delta;
            }
        }
    }

    private void resetShopState() {
        synchronized (shopLock) {
            shopAliveAccumMs = 0;
            shopCoinsSpent = 0;
            shopLastTickElapsed = SystemClock.elapsedRealtime();
            shopGhostUntilHuntMs.clear();
            shopRadarUntilHuntMs.clear();
            shopDecoys.clear();
            iceThawHoldStartHuntMs.clear();
        }
    }

    public long getMyGhostRemainingMs() {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended) {
            return 0;
        }
        long hunt = rt.huntingElapsedMs();
        synchronized (shopLock) {
            Long until = shopGhostUntilHuntMs.get(playerId);
            return until == null ? 0 : Math.max(0, until - hunt);
        }
    }

    /** Radar is only shown to the team that bought it. */
    public long getRadarRemainingMs() {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || !canUseRevealRole()) {
            return 0;
        }
        long hunt = rt.huntingElapsedMs();
        synchronized (shopLock) {
            Long until = shopRadarUntilHuntMs.get(playerId);
            return until == null ? 0 : Math.max(0, until - hunt);
        }
    }

    private boolean canUseRevealRole() {
        GameRuntime rt = runtime;
        if (rt == null || rt.lobby == null || rt.lobby.settings == null) {
            return false;
        }
        Player me = getMe();
        if (me == null || !me.alive) {
            return false;
        }
        GameSettings settings = rt.lobby.settings;
        if (settings.isZombie()) {
            return me.isZombie();
        }
        if (settings.isAssassin()) {
            return me.isAssassin();
        }
        return me.isSeeker();
    }

    /**
     * True while a radar-like shop effect may show the given player live. The
     * caller still has to check the effect duration.
     */
    public boolean isRadarRevealing(Player p) {
        if (p == null || !isRadarActive()) {
            return false;
        }
        GameRuntime rt = runtime;
        if (rt == null || rt.lobby == null || rt.lobby.settings == null) {
            return false;
        }
        GameSettings settings = rt.lobby.settings;
        if (settings.isZombie()) {
            return p.isHider() && !p.infected;
        }
        if (settings.isAssassin()) {
            Player me = getMe();
            if (me != null && p.id.equals(me.targetId)) {
                return true;
            }
            return !settings.assassinTargetPingOnly;
        }
        return p.isHider();
    }

    /**
     * Zombies briefly sense every survivor at a fixed interval. This is a
     * purely local reveal derived from the shared clock, so it works without
     * extra traffic.
     */
    public boolean isZombieSenseActive() {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || rt.inHidePhase() || rt.lobby == null
                || rt.lobby.settings == null || !rt.lobby.settings.isZombie()) {
            return false;
        }
        Player me = getMe();
        if (me == null || !me.alive || !me.isZombie()) {
            return false;
        }
        long interval = Math.max(15, rt.lobby.settings.zombieSenseIntervalSeconds) * 1000L;
        long phase = rt.huntingElapsedMs() % interval;
        return phase < 4000L;
    }

    public boolean isRadarActive() {
        return getRadarRemainingMs() > 0;
    }

    public long getMyDecoyRemainingMs() {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended) {
            return 0;
        }
        long hunt = rt.huntingElapsedMs();
        long remaining = 0;
        synchronized (shopLock) {
            for (DecoyMarker decoy : shopDecoys.values()) {
                if (playerId.equals(decoy.ownerId) && decoy.untilHuntMs > hunt) {
                    remaining = Math.max(remaining, decoy.untilHuntMs - hunt);
                }
            }
        }
        return remaining;
    }

    public List<DecoyMarker> getActiveDecoyMarkers() {
        List<DecoyMarker> result = new ArrayList<>();
        GameRuntime rt = runtime;
        if (rt == null || rt.ended) {
            return result;
        }
        long hunt = rt.huntingElapsedMs();
        synchronized (shopLock) {
            Iterator<Map.Entry<String, DecoyMarker>> it = shopDecoys.entrySet().iterator();
            while (it.hasNext()) {
                DecoyMarker decoy = it.next().getValue();
                if (decoy.untilHuntMs <= hunt) {
                    it.remove();
                } else {
                    result.add(decoy.copy());
                }
            }
        }
        return result;
    }

    /**
     * Buys an item. The fake marker needs the map position where the decoy
     * should appear; other items ignore the coordinates.
     */
    public ShopResult buyShopItem(String itemId, double lat, double lng) {
        GameRuntime rt = runtime;
        ShopCatalog.Item item = ShopCatalog.find(itemId);
        if (rt == null || rt.ended) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_round_over));
        }
        if (rt.lobby == null || rt.lobby.settings == null || !rt.lobby.settings.shopEnabled) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_disabled));
        }
        if (rt.inHidePhase()) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_not_started));
        }
        if (item == null || !ShopCatalog.itemsFor(rt.lobby.settings).contains(item)) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_unknown_item));
        }
        if (!connected) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_no_connection));
        }
        Player me = getMe();
        if (me == null || !me.alive) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_out));
        }
        boolean tea = ShopCatalog.isThaw(item);
        if (me.frozen && !tea) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_frozen));
        }
        if (tea && !me.frozen) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_tea_not_frozen));
        }
        if (!ShopCatalog.canUse(item, me)) {
            return new ShopResult(false, appContext.getString(ShopCatalog.roleLabelRes(item)));
        }
        if (ShopCatalog.isDecoy(item) && !isValidCoordinate(lat, lng)) {
            return new ShopResult(false, appContext.getString(R.string.shop_error_decoy_tap));
        }

        updateSurvivalCoins();
        long hunt = rt.huntingElapsedMs();
        int price = ShopCatalog.priceFor(rt.lobby.settings, item);
        String effectId = UUID.randomUUID().toString();
        long untilHuntMs = 0;
        double effectLat = lat;
        double effectLng = lng;

        synchronized (shopLock) {
            int balance = (int) (shopAliveAccumMs
                    / (ShopCatalog.SECONDS_PER_COIN * 1000L)) - shopCoinsSpent;
            if (balance < price) {
                return new ShopResult(false, appContext.getString(R.string.shop_error_coins,
                        price, Math.max(0, balance)));
            }
            if (ShopCatalog.isGhost(item)) {
                Long active = shopGhostUntilHuntMs.get(playerId);
                if (active != null && active > hunt) {
                    return new ShopResult(false, appContext.getString(R.string.shop_error_ghost_active));
                }
                long interval = Math.max(10, rt.lobby.settings.pingIntervalSeconds) * 1000L;
                long nextPing = ((hunt / interval) + 1L) * interval;
                untilHuntMs = nextPing + SHOP_GHOST_PING_BUFFER_MS;
            } else if (ShopCatalog.isRadar(item)) {
                Long active = shopRadarUntilHuntMs.get(playerId);
                if (active != null && active > hunt) {
                    return new ShopResult(false, appContext.getString(R.string.shop_error_radar_active));
                }
                untilHuntMs = hunt + SHOP_RADAR_DURATION_MS;
            } else if (ShopCatalog.isDecoy(item)) {
                untilHuntMs = hunt + SHOP_DECOY_DURATION_MS;
            }
            shopCoinsSpent += price;
            if (ShopCatalog.isDecoy(item)) {
                addDecoyLocked(effectId, playerId, me.name, effectLat, effectLng, untilHuntMs);
            } else if (ShopCatalog.isGhost(item)) {
                shopGhostUntilHuntMs.put(playerId, untilHuntMs);
            } else if (ShopCatalog.isRadar(item)) {
                shopRadarUntilHuntMs.put(playerId, untilHuntMs);
            }
        }

        if (tea) {
            synchronized (stateLock) {
                Player p = livePlayers.get(playerId);
                if (p != null) {
                    p.frozen = false;
                    p.frozenSinceHuntMs = 0;
                }
            }
            synchronized (shopLock) {
                iceThawHoldStartHuntMs.remove(playerId);
            }
            try {
                JSONObject event = baseEvent("THAW");
                event.put("victimId", playerId);
                event.put("byId", playerId);
                publishEvent(event, true);
            } catch (JSONException ignored) {
            }
            notifyPlayersChanged();
            notifyShopChanged();
            return new ShopResult(true, appContext.getString(R.string.shop_tea_thawed));
        }

        try {
            JSONObject event = baseEvent("SHOP");
            event.put("item", item.id);
            event.put("effectId", effectId);
            event.put("untilHuntMs", untilHuntMs);
            if (ShopCatalog.isDecoy(item)) {
                event.put("lat", effectLat);
                event.put("lng", effectLng);
            }
            publishEvent(event, true);
        } catch (JSONException ignored) {
        }

        notifyShopChanged();
        return new ShopResult(true, shopSuccessMessage(item));
    }

    private String shopSuccessMessage(ShopCatalog.Item item) {
        if (ShopCatalog.isDecoy(item)) {
            if (ShopCatalog.ITEM_ZOMBIE_DECOY.equals(item.id)) {
                return appContext.getString(R.string.shop_zombie_decoy_placed);
            }
            if (ShopCatalog.ITEM_ASSASSIN_TRACK.equals(item.id)) {
                return appContext.getString(R.string.shop_assassin_track_placed);
            }
            if (ShopCatalog.ITEM_ICE_DECOY.equals(item.id)) {
                return appContext.getString(R.string.shop_ice_decoy_placed);
            }
            return appContext.getString(R.string.shop_decoy_placed);
        }
        if (ShopCatalog.isGhost(item)) {
            if (ShopCatalog.ITEM_ZOMBIE_CAMO.equals(item.id)) {
                return appContext.getString(R.string.shop_zombie_camo_active);
            }
            if (ShopCatalog.ITEM_ASSASSIN_SHADOW.equals(item.id)) {
                return appContext.getString(R.string.shop_assassin_shadow_active);
            }
            return appContext.getString(R.string.shop_ghost_active_message);
        }
        if (ShopCatalog.ITEM_ZOMBIE_SENSE.equals(item.id)) {
            return appContext.getString(R.string.shop_zombie_sense_active);
        }
        if (ShopCatalog.ITEM_ASSASSIN_RADAR.equals(item.id)) {
            return appContext.getString(R.string.shop_assassin_target_radar_active);
        }
        if (ShopCatalog.ITEM_ICE_THERMAL.equals(item.id)) {
            return appContext.getString(R.string.shop_ice_thermal_active);
        }
        return appContext.getString(R.string.shop_radar_active_message);
    }

    private static boolean isValidCoordinate(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng)
                && Math.abs(lat) <= 90.0 && Math.abs(lng) <= 180.0
                && !(lat == 0.0 && lng == 0.0);
    }

    private void addDecoyLocked(String id, String ownerId, String ownerName,
                                double lat, double lng, long untilHuntMs) {
        DecoyMarker decoy = new DecoyMarker();
        decoy.id = id;
        decoy.ownerId = ownerId;
        decoy.ownerName = ownerName;
        decoy.lat = lat;
        decoy.lng = lng;
        decoy.untilHuntMs = untilHuntMs;
        shopDecoys.put(id, decoy);
    }

    private void handleShopUse(String sender, JSONObject o) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || sender == null || sender.isEmpty()
                || sender.equals(playerId)
                || rt.lobby == null || rt.lobby.settings == null
                || !rt.lobby.settings.shopEnabled) {
            return;
        }
        ShopCatalog.Item item = ShopCatalog.find(o.optString("item", ""));
        if (item == null || !ShopCatalog.itemsFor(rt.lobby.settings).contains(item)
                || ShopCatalog.isThaw(item)) {
            return;
        }
        Player owner;
        synchronized (stateLock) {
            Player p = livePlayers.get(sender);
            owner = p == null ? null : p.copy();
        }
        if (owner == null || !owner.alive || owner.frozen || !ShopCatalog.canUse(item, owner)) {
            return;
        }
        long hunt = rt.huntingElapsedMs();
        long untilHuntMs = o.optLong("untilHuntMs", 0);
        if (untilHuntMs <= hunt) {
            return;
        }
        String effectId = o.optString("effectId", sender + "-" + o.optLong("ts", 0));
        synchronized (shopLock) {
            if (ShopCatalog.isDecoy(item)) {
                double lat = o.optDouble("lat", Double.NaN);
                double lng = o.optDouble("lng", Double.NaN);
                if (!isValidCoordinate(lat, lng)) {
                    return;
                }
                untilHuntMs = Math.min(untilHuntMs, hunt + SHOP_DECOY_DURATION_MS + 5000L);
                addDecoyLocked(effectId, sender, owner.name, lat, lng, untilHuntMs);
            } else if (ShopCatalog.isGhost(item)) {
                shopGhostUntilHuntMs.put(sender, untilHuntMs);
            } else if (ShopCatalog.isRadar(item)) {
                shopRadarUntilHuntMs.put(sender, untilHuntMs);
            }
        }
        notifyShopChanged();
    }

    private boolean isPlayerGhosted(String id, long huntElapsedMs) {
        synchronized (shopLock) {
            Long until = shopGhostUntilHuntMs.get(id);
            return until != null && until > huntElapsedMs;
        }
    }

    private void resetRoundStats() {
        synchronized (statsLock) {
            roundDistanceMeters = 0;
            pendingStatDistance = 0;
            lastStatsSaveElapsed = SystemClock.elapsedRealtime();
            distanceAnchor = null;
            statsFlushed = false;
        }
    }

    /**
     * Accumulates walked distance from filtered GPS fixes. Very inaccurate
     * fixes, tiny jitter and impossible jumps (for example after a tunnel) are
     * ignored so the shown value stays useful.
     */
    private void trackDistance(LocationPoint point) {
        if (point == null) {
            return;
        }
        double saveNow;
        synchronized (statsLock) {
            if (statsFlushed || point.accuracy > 60f) {
                return;
            }
            LocationPoint anchor = distanceAnchor;
            if (anchor == null) {
                distanceAnchor = point;
                return;
            }
            long gapMs = point.elapsedMs - anchor.elapsedMs;
            if (gapMs <= 0L) {
                distanceAnchor = point;
                return;
            }
            double delta = Geo.distanceMeters(anchor.lat, anchor.lng, point.lat, point.lng);
            if (gapMs > 30_000L || delta / (gapMs / 1000.0) > 10.0) {
                // Signal was lost or the fix jumped: restart from here.
                distanceAnchor = point;
                return;
            }
            if (delta < 2.0) {
                return;
            }
            roundDistanceMeters += delta;
            pendingStatDistance += delta;
            distanceAnchor = point;
            long now = SystemClock.elapsedRealtime();
            if (now - lastStatsSaveElapsed < 5000L) {
                return;
            }
            lastStatsSaveElapsed = now;
            saveNow = pendingStatDistance;
            pendingStatDistance = 0;
        }
        PlayerStats.addDistance(appContext, saveNow);
    }

    /** Persists the current round distance exactly once. */
    private void flushRoundStats() {
        double saveNow;
        synchronized (statsLock) {
            if (statsFlushed) {
                return;
            }
            statsFlushed = true;
            saveNow = pendingStatDistance;
            pendingStatDistance = 0;
        }
        PlayerStats.addDistance(appContext, saveNow);
    }

    /**
     * True when the host enabled live team positions and the local player is
     * still alive. The local player may then see teammates moving live.
     */
    public boolean isTeamLiveActive() {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || rt.lobby.settings == null || !rt.lobby.settings.teamLive) {
            return false;
        }
        synchronized (stateLock) {
            Player me = livePlayers.get(playerId);
            return me != null && me.alive;
        }
    }

    public boolean isMyRoleSeeker() {
        synchronized (stateLock) {
            Player me = livePlayers.get(playerId);
            return me != null && me.isSeeker();
        }
    }

    public List<ChatMessage> getChatMessages() {
        synchronized (chatLock) {
            return new ArrayList<>(chatMessages);
        }
    }

    /**
     * Sends a chat line. scope is either ALL or TEAM. Team messages are only
     * accepted by clients whose role matches the sender role.
     */
    public boolean sendChat(String rawText, String scope) {
        GameRuntime rt = runtime;
        Player me = getMe();
        if (rt == null || rt.ended || me == null || !connected) {
            return false;
        }
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return false;
        }
        if (text.length() > 300) {
            text = text.substring(0, 300);
        }
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID().toString();
        message.senderId = playerId;
        message.senderName = me.name;
        message.role = me.teamKey();
        message.scope = ChatMessage.SCOPE_TEAM.equals(scope)
                ? ChatMessage.SCOPE_TEAM : ChatMessage.SCOPE_ALL;
        message.text = text;
        message.timestamp = System.currentTimeMillis();
        addChat(message);
        try {
            JSONObject event = baseEvent("CHAT");
            event.put("msgId", message.id);
            event.put("name", message.senderName);
            event.put("role", message.role);
            event.put("scope", message.scope);
            event.put("text", message.text);
            publishEvent(event, true);
        } catch (JSONException ignored) {
        }
        return true;
    }

    private void addChat(ChatMessage message) {
        if (message == null || message.id == null || message.id.isEmpty()) {
            return;
        }
        synchronized (chatLock) {
            if (!chatMessageIds.add(message.id)) {
                return;
            }
            chatMessages.add(message);
            while (chatMessages.size() > 200) {
                ChatMessage removed = chatMessages.remove(0);
                chatMessageIds.remove(removed.id);
            }
        }
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onChatMessage(message);
            }
        });
    }

    private void resetChat() {
        synchronized (chatLock) {
            chatMessages.clear();
            chatMessageIds.clear();
        }
    }

    public boolean hasFineLocationPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static LobbySnapshot createPreviewLobby(String code) {
        LobbySnapshot s = new LobbySnapshot();
        s.code = code;
        return s;
    }

    // ---------------------------------------------------------------------
    // Session creation / connection
    // ---------------------------------------------------------------------

    public void createGame(String playerName, GameSettings settings, Listener l) {
        int gen = prepareNewSession(l, true, playerName);
        code = randomCode();
        gameId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        LobbySnapshot snap = new LobbySnapshot();
        snap.code = code;
        snap.gameId = gameId;
        snap.hostId = playerId;
        snap.hostName = playerName;
        snap.state = LobbySnapshot.STATE_LOBBY;
        snap.createdAt = now;
        snap.updatedAt = now;
        snap.settings = settings.copy();

        Player me = new Player();
        me.id = playerId;
        me.name = playerName;
        me.role = "";
        snap.players.add(me);
        synchronized (stateLock) {
            lobby = snap;
            livePlayers.clear();
            livePlayers.put(playerId, me.copy());
        }

        io.execute(() -> {
            disconnectQuietly();
            if (gen != generation) {
                return;
            }
            try {
                connectBlocking(gen);
                if (gen != generation) {
                    disconnectQuietly();
                    return;
                }
                subscribeBlocking(eventTopic());
                LobbySnapshot copy = getLobby();
                if (copy != null) {
                    publishLobbySnapshotBlocking(copy, true);
                }
                activeSession = true;
                startHostLobbyKeepalive();
                notifyConnection(true);
                notifyLobby(copy);
            } catch (Exception e) {
                Log.w(TAG, "createGame failed", e);
                if (gen == generation) {
                    connected = false;
                    notifyError(appContext.getString(R.string.error_connection_create));
                }
            }
        });
    }

    public void joinGame(String playerName, String joinCode, Listener l) {
        int gen = prepareNewSession(l, false, playerName);
        code = joinCode == null ? "" : joinCode.trim();
        gameId = "";
        activeSession = true;

        io.execute(() -> {
            disconnectQuietly();
            if (gen != generation) {
                return;
            }
            try {
                connectBlocking(gen);
                if (gen != generation) {
                    disconnectQuietly();
                    return;
                }
                subscribeBlocking(lobbyTopic());
                notifyConnection(true);
            } catch (Exception e) {
                Log.w(TAG, "joinGame failed", e);
                if (gen == generation) {
                    connected = false;
                    notifyError(appContext.getString(R.string.error_connection_join));
                }
            }
        });
    }

    private int prepareNewSession(Listener l, boolean asHost, String playerName) {
        int gen = ++generation;
        stopSchedulers();
        listener = l;
        host = asHost;
        name = playerName == null ? "" : playerName.trim();
        connected = false;
        activeSession = false;
        joinRequested = false;
        admitted = false;
        shuttingDown = false;
        runtime = null;
        lobby = null;
        code = "";
        gameId = "";
        myCatchCode = "";
        myCatchHash = "";
        lastPingElapsed = 0;
        pingSnapshot = Collections.emptyList();
        resetRoundStats();
        resetShopState();
        huntStartNotified = false;
        lastZonePercent = 100;
        resetChat();
        NotificationHelper.cancelGameNotifications(appContext);
        synchronized (replayLock) {
            replay = null;
            replayId = "";
            replaySaved = false;
        }
        synchronized (stateLock) {
            livePlayers.clear();
        }
        synchronized (subscriptionLock) {
            subscriptions.clear();
        }
        return gen;
    }

    private String randomCode() {
        return String.format(java.util.Locale.GERMANY, "%04d", 1000 + random.nextInt(9000));
    }

    private void connectBlocking(int gen) throws MqttException {
        String clientId = "zj-" + playerId.replace("-", "").substring(0, 18) + "-" + (random.nextInt(9000) + 1000);
        MqttClient client = new MqttClient(BROKER_URI, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(12);
        options.setKeepAliveInterval(30);
        options.setMaxInflight(100);

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (gen != generation) {
                    return;
                }
                connected = true;
                notifyConnection(true);
                List<String> topics;
                synchronized (subscriptionLock) {
                    topics = new ArrayList<>(subscriptions);
                }
                for (String topic : topics) {
                    io.execute(() -> {
                        if (gen == generation && connected) {
                            try {
                                subscribeBlocking(topic);
                            } catch (MqttException ignored) {
                            }
                        }
                    });
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                if (gen != generation) {
                    return;
                }
                connected = false;
                notifyConnection(false);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (gen != generation) {
                    return;
                }
                try {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    if (lobbyTopic().equals(topic)) {
                        handleLobbyMessage(payload);
                    } else if (eventTopic().equals(topic)) {
                        handleEventPayload(payload);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "messageArrived failed", e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        client.connect(options);
        mqtt = client;
        connected = true;
    }

    private void subscribeBlocking(String topic) throws MqttException {
        MqttClient client = mqtt;
        if (client == null || !client.isConnected()) {
            return;
        }
        client.subscribe(topic, QOS_STATE);
        synchronized (subscriptionLock) {
            subscriptions.add(topic);
        }
    }

    private void disconnectQuietly() {
        connected = false;
        MqttClient client = mqtt;
        mqtt = null;
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnectForcibly(150, 500);
            }
        } catch (Exception ignored) {
        }
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------------
    // Lobby
    // ---------------------------------------------------------------------

    private void handleLobbyMessage(String payload) {
        if (host) {
            return;
        }
        LobbySnapshot snapshot;
        try {
            snapshot = LobbySnapshot.fromJson(new JSONObject(payload));
        } catch (JSONException e) {
            return;
        }
        if (snapshot.code == null || !snapshot.code.equals(code) || snapshot.gameId.isEmpty()) {
            return;
        }

        if (!joinRequested) {
            long age = System.currentTimeMillis() - snapshot.updatedAt;
            if (LobbySnapshot.STATE_LOBBY.equals(snapshot.state) && snapshot.updatedAt > 0 && age > LOBBY_MAX_AGE_MS) {
                notifyError(appContext.getString(R.string.error_lobby_inactive));
                return;
            }
            if (!LobbySnapshot.STATE_LOBBY.equals(snapshot.state)) {
                notifyError(appContext.getString(R.string.error_game_started));
                return;
            }
            synchronized (stateLock) {
                gameId = snapshot.gameId;
                lobby = snapshot;
            }
            joinRequested = true;
            int gen = generation;
            io.execute(() -> {
                if (gen != generation) {
                    return;
                }
                try {
                    subscribeBlocking(eventTopic());
                    publishJoinBlocking();
                } catch (Exception e) {
                    notifyError(appContext.getString(R.string.error_join_failed));
                }
            });
            return;
        }

        if (!admitted) {
            if (LobbySnapshot.STATE_LOBBY.equals(snapshot.state)) {
                if (snapshot.hasPlayer(playerId)) {
                    admitted = true;
                    synchronized (stateLock) {
                        gameId = snapshot.gameId;
                        lobby = snapshot;
                        seedLiveFromLobby(snapshot);
                    }
                    notifyLobby(snapshot.copy());
                }
            } else if (LobbySnapshot.STATE_RUNNING.equals(snapshot.state)) {
                notifyError(appContext.getString(R.string.error_started_before_join));
            } else if (LobbySnapshot.STATE_ENDED.equals(snapshot.state)) {
                notifyError(appContext.getString(R.string.error_lobby_closed));
            }
            return;
        }

        if (LobbySnapshot.STATE_LOBBY.equals(snapshot.state)) {
            synchronized (stateLock) {
                lobby = snapshot;
                seedLiveFromLobby(snapshot);
            }
            notifyLobby(snapshot.copy());
        } else if (LobbySnapshot.STATE_ENDED.equals(snapshot.state) && runtime == null) {
            notifyError(appContext.getString(R.string.error_lobby_closed));
        }
    }

    private void handleJoinEvent(String pid, String playerName) {
        if (!host) {
            return;
        }
        LobbySnapshot copy;
        synchronized (stateLock) {
            if (lobby == null || !LobbySnapshot.STATE_LOBBY.equals(lobby.state)) {
                return;
            }
            if (pid == null || pid.isEmpty() || pid.equals(playerId) || lobby.hasPlayer(pid)) {
                return;
            }
            if (lobby.players.size() >= GameSettings.MAX_PLAYERS) {
                return;
            }
            Player p = new Player();
            p.id = pid;
            p.name = cleanName(playerName);
            lobby.players.add(p);
            lobby.updatedAt = System.currentTimeMillis();
            copy = lobby.copy();
        }
        publishLobbySnapshot(copy);
        notifyLobby(copy);
    }

    private void handleLeaveEvent(String pid, boolean fromGame) {
        if (!host) {
            return;
        }
        LobbySnapshot copy = null;
        Player left = null;
        synchronized (stateLock) {
            if (lobby == null) {
                return;
            }
            if (!fromGame && LobbySnapshot.STATE_LOBBY.equals(lobby.state)) {
                lobby.players.removeIf(p -> p.id.equals(pid));
                List<String> selected = lobby.seekerIds();
                if (selected.remove(pid)) {
                    lobby.setSeekerIds(selected);
                    lobby.seekerMode = selected.isEmpty() ? "RANDOM" : "PLAYER";
                }
                lobby.updatedAt = System.currentTimeMillis();
                copy = lobby.copy();
            } else if (runtime != null && !runtime.ended) {
                left = livePlayers.get(pid);
                if (left != null && left.alive) {
                    left.alive = false;
                    left.eliminatedReason = "VERLASSEN";
                }
            }
        }
        if (copy != null) {
            publishLobbySnapshot(copy);
            notifyLobby(copy);
        }
        if (left != null) {
            GameSettings settings = runtime == null || runtime.lobby == null
                    ? null : runtime.lobby.settings;
            if (settings != null && settings.isAssassin()) {
                synchronized (stateLock) {
                    repairAssassinTargetsLocked();
                }
                publishAssassinTargets();
            }
            try {
                JSONObject event = baseEvent("OUT");
                event.put("victimId", left.id);
                event.put("reason", "VERLASSEN");
                publishEvent(event, true);
            } catch (JSONException ignored) {
            }
            notifyPlayersChanged();
            hostCheckEnd();
        }
    }

    public void setSeekerChoice(String mode, List<String> selectedIds) {
        if (!host) {
            return;
        }
        LobbySnapshot copy;
        synchronized (stateLock) {
            if (lobby == null || !LobbySnapshot.STATE_LOBBY.equals(lobby.state)) {
                return;
            }
            List<String> clean = new ArrayList<>();
            if ("PLAYER".equals(mode) && selectedIds != null) {
                for (String id : selectedIds) {
                    if (lobby.hasPlayer(id) && !clean.contains(id)) {
                        clean.add(id);
                    }
                }
            }
            int maxSeekers = Math.max(1, lobby.players.size() - 1);
            while (clean.size() > maxSeekers) {
                clean.remove(clean.size() - 1);
            }
            if (clean.isEmpty()) {
                lobby.seekerMode = "RANDOM";
                lobby.setSeekerIds(new ArrayList<>());
            } else {
                lobby.seekerMode = "PLAYER";
                lobby.setSeekerIds(clean);
            }
            lobby.updatedAt = System.currentTimeMillis();
            copy = lobby.copy();
        }
        publishLobbySnapshot(copy);
        notifyLobby(copy);
    }

    public void setSeekerChoice(String mode, String selectedId) {
        List<String> ids = new ArrayList<>();
        if (selectedId != null && !selectedId.isEmpty()) {
            ids.add(selectedId);
        }
        setSeekerChoice(mode, ids);
    }

    public void rollRandomSeeker() {
        if (!host) {
            return;
        }
        LobbySnapshot copy;
        synchronized (stateLock) {
            if (lobby == null || !LobbySnapshot.STATE_LOBBY.equals(lobby.state) || lobby.players.isEmpty()) {
                return;
            }
            int maxSeekers = Math.max(1, lobby.players.size() - 1);
            int count = Math.min(Math.max(1, lobby.seekerIds().size()), maxSeekers);
            String previousKey = idKey(lobby.seekerIds());
            List<String> chosen = new ArrayList<>();
            for (int attempt = 0; attempt < 20; attempt++) {
                List<String> pool = new ArrayList<>();
                for (Player p : lobby.players) {
                    pool.add(p.id);
                }
                Collections.shuffle(pool, random);
                chosen.clear();
                for (int i = 0; i < count && i < pool.size(); i++) {
                    chosen.add(pool.get(i));
                }
                if (!idKey(chosen).equals(previousKey)) {
                    break;
                }
            }
            lobby.seekerMode = "RANDOM";
            lobby.setSeekerIds(chosen);
            lobby.updatedAt = System.currentTimeMillis();
            copy = lobby.copy();
        }
        publishLobbySnapshot(copy);
        notifyLobby(copy);
    }

    /**
     * Applies changed rules to an existing lobby. Only the host may edit and
     * the round must not have started yet.
     */
    public void updateLobbySettings(GameSettings settings) {
        if (!host || settings == null) {
            return;
        }
        LobbySnapshot copy;
        synchronized (stateLock) {
            if (lobby == null || !LobbySnapshot.STATE_LOBBY.equals(lobby.state)) {
                return;
            }
            lobby.settings = settings.copy();
            lobby.updatedAt = System.currentTimeMillis();
            copy = lobby.copy();
        }
        publishLobbySnapshot(copy);
        notifyLobby(copy);
    }

    private static String idKey(List<String> ids) {
        List<String> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        StringBuilder builder = new StringBuilder();
        for (String id : sorted) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    /**
     * Builds a deterministic ring of secret targets from the player list and
     * the game id. Every client that receives the start snapshot sees the same
     * assignment.
     */
    private void assignAssassinTargetsLocked(List<Player> players) {
        List<String> ids = new ArrayList<>();
        for (Player p : players) {
            if (p.id != null && !p.id.isEmpty()) {
                ids.add(p.id);
            }
        }
        if (ids.size() < 2) {
            for (Player p : players) {
                p.targetId = "";
            }
            return;
        }
        Collections.shuffle(ids, new Random((gameId == null ? "" : gameId).hashCode()));
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            String target = ids.get((i + 1) % ids.size());
            for (Player p : players) {
                if (id.equals(p.id)) {
                    p.targetId = target;
                    break;
                }
            }
        }
    }

    /**
     * Repairs missing, dead or self-referencing assassin targets. A killer
     * inherits the target of the eliminated player. The repair is
     * deterministic so all devices converge even if a TARGETS event is lost.
     */
    private boolean repairAssassinTargetsLocked() {
        List<Player> alive = new ArrayList<>();
        for (Player p : livePlayers.values()) {
            if (p.alive && p.isAssassin()) {
                alive.add(p);
            }
        }
        if (alive.size() < 2) {
            boolean changed = false;
            for (Player p : alive) {
                if (!p.targetId.isEmpty()) {
                    p.targetId = "";
                    changed = true;
                }
            }
            return changed;
        }
        alive.sort((a, b) -> a.id.compareTo(b.id));
        boolean changed = false;
        for (Player p : alive) {
            String target = p.targetId;
            Set<String> seen = new HashSet<>();
            while (target != null && !target.isEmpty() && seen.add(target)) {
                Player next = livePlayers.get(target);
                if (next == null) {
                    target = "";
                    break;
                }
                if (next.alive) {
                    break;
                }
                target = next.targetId;
            }
            if (target == null || target.isEmpty() || target.equals(p.id)) {
                int index = alive.indexOf(p);
                target = alive.get((index + 1) % alive.size()).id;
            }
            if (!target.equals(p.targetId)) {
                p.targetId = target;
                changed = true;
            }
        }
        return changed;
    }

    private JSONObject assassinTargetsJsonLocked() throws JSONException {
        JSONObject targets = new JSONObject();
        for (Player p : livePlayers.values()) {
            if (p.isAssassin() && p.alive) {
                targets.put(p.id, p.targetId == null ? "" : p.targetId);
            }
        }
        return targets;
    }

    private void publishAssassinTargets() {
        if (!host) {
            return;
        }
        JSONObject targets;
        synchronized (stateLock) {
            try {
                targets = assassinTargetsJsonLocked();
            } catch (JSONException e) {
                return;
            }
        }
        try {
            JSONObject event = baseEvent("TARGETS");
            event.put("targets", targets);
            publishEvent(event, true);
        } catch (JSONException ignored) {
        }
    }

    private void handleTargetsEvent(JSONObject o) {
        if (host) {
            return;
        }
        JSONObject targets = o.optJSONObject("targets");
        if (targets == null) {
            return;
        }
        synchronized (stateLock) {
            Iterator<String> keys = targets.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                Player p = livePlayers.get(id);
                if (p != null) {
                    p.targetId = targets.optString(id, "");
                }
            }
        }
        notifyPlayersChanged();
    }

    public boolean startGame() {
        if (!host || !connected) {
            notifyError(appContext.getString(R.string.error_no_connection));
            return false;
        }
        if (myLocation == null) {
            notifyError(appContext.getString(R.string.error_gps_missing_join));
            return false;
        }

        LobbySnapshot finalLobby;
        GameRuntime rt;
        synchronized (stateLock) {
            if (lobby == null || !LobbySnapshot.STATE_LOBBY.equals(lobby.state) || runtime != null) {
                return false;
            }
            if (lobby.players.size() < 2) {
                notifyError(appContext.getString(R.string.error_need_second_player));
                return false;
            }

            GameSettings activeSettings = lobby.settings == null
                    ? new GameSettings() : lobby.settings;
            String mode = GameSettings.normalizeMode(activeSettings.mode);
            List<String> chosen = new ArrayList<>();
            if (GameSettings.MODE_ASSASSIN.equals(mode)) {
                for (Player p : lobby.players) {
                    p.role = "ASSASSIN";
                    p.alive = true;
                    p.connected = true;
                    p.eliminatedReason = "";
                    p.infected = false;
                    p.frozen = false;
                    p.kills = 0;
                    p.infectedAtHuntMs = 0;
                    p.frozenSinceHuntMs = 0;
                }
                assignAssassinTargetsLocked(lobby.players);
                lobby.seekerMode = "RANDOM";
                lobby.setSeekerIds(chosen);
            } else {
                if (GameSettings.MODE_CLASSIC.equals(mode)) {
                    for (String id : lobby.seekerIds()) {
                        if (lobby.hasPlayer(id) && !chosen.contains(id)) {
                            chosen.add(id);
                        }
                    }
                    int maxSeekers = Math.max(1, lobby.players.size() - 1);
                    while (chosen.size() > maxSeekers) {
                        chosen.remove(chosen.size() - 1);
                    }
                    if (chosen.isEmpty()) {
                        chosen.add(lobby.players.get(random.nextInt(lobby.players.size())).id);
                    }
                } else {
                    // Zombie and ice rounds start with exactly one hunter.
                    chosen.add(lobby.players.get(random.nextInt(lobby.players.size())).id);
                }
                for (Player p : lobby.players) {
                    p.role = chosen.contains(p.id) ? "SEEKER" : "HIDER";
                    p.alive = true;
                    p.connected = true;
                    p.eliminatedReason = "";
                    p.infected = false;
                    p.frozen = false;
                    p.kills = 0;
                    p.infectedAtHuntMs = 0;
                    p.frozenSinceHuntMs = 0;
                    p.targetId = "";
                }
                lobby.seekerMode = "PLAYER";
                lobby.setSeekerIds(chosen);
            }
            lobby.settings.mode = mode;
            lobby.state = LobbySnapshot.STATE_RUNNING;
            lobby.updatedAt = System.currentTimeMillis();
            finalLobby = lobby.copy();

            livePlayers.clear();
            for (Player p : finalLobby.players) {
                livePlayers.put(p.id, p.copy());
            }
            Player me = livePlayers.get(playerId);
            if (me != null && me.isHider()) {
                generateMyCatchCode();
            } else {
                myCatchCode = "";
                myCatchHash = "";
            }
            resetChat();
            huntStartNotified = false;
            lastZonePercent = 100;
            resetRoundStats();
            resetShopState();
            rt = new GameRuntime(finalLobby, myLocation.lat, myLocation.lng, SystemClock.elapsedRealtime());
            runtime = rt;
            lobby = finalLobby.copy();
        }

        stopLobbyKeepalive();
        startReplay(finalLobby, rt);
        startHeartbeat();
        startPhaseWatchers(rt);
        startHostTimers(rt);
        notifyGameStarted();

        final int gen = generation;
        final LobbySnapshot snapshotForNetwork = finalLobby;
        final GameRuntime runtimeForNetwork = rt;
        io.execute(() -> {
            if (gen != generation) {
                return;
            }
            try {
                JSONObject event = baseEvent("START");
                event.put("snapshot", snapshotForNetwork.toJson());
                event.put("centerLat", runtimeForNetwork.centerLat);
                event.put("centerLng", runtimeForNetwork.centerLng);
                publishLobbySnapshotBlocking(snapshotForNetwork, true);
                publishJsonBlocking(eventTopic(), event, false, QOS_STATE);
            } catch (Exception e) {
                notifyError(appContext.getString(R.string.error_start_failed));
            }
        });
        return true;
    }

    private void startHostTimers(GameRuntime rt) {
        stopHostTimers();
        long interval = Math.max(10, rt.lobby.settings.pingIntervalSeconds) * 1000L;
        hostPingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!host || !activeSession || runtime == null || runtime.ended) {
                    return;
                }
                if (runtime.inHidePhase()) {
                    gameHandler.postDelayed(this, Math.max(1000L, runtime.hideRemainingMs()));
                    return;
                }
                if (connected) {
                    try {
                        JSONObject event = baseEvent("PING");
                        event.put("radius", runtime.currentRadiusMeters());
                        publishEvent(event, true);
                    } catch (JSONException ignored) {
                    }
                }
                // The broker normally echoes the event back, but the host
                // should never miss its own ping if that echo is delayed.
                deliverPing();
                gameHandler.postDelayed(this, interval);
            }
        };
        long firstPing = Math.max(0, rt.hideRemainingMs()) + interval;
        gameHandler.postDelayed(hostPingRunnable, firstPing);

        long remaining = Math.max(1000, Math.max(0, rt.hideRemainingMs()) + rt.remainingMs());
        hostEndRunnable = () -> {
            if (host && runtime != null && !runtime.ended) {
                finishHostTimeUp();
            }
        };
        gameHandler.postDelayed(hostEndRunnable, remaining);
        startIceWatcher(rt);
    }

    private void stopHostTimers() {
        if (hostPingRunnable != null) {
            gameHandler.removeCallbacks(hostPingRunnable);
            hostPingRunnable = null;
        }
        if (hostEndRunnable != null) {
            gameHandler.removeCallbacks(hostEndRunnable);
            hostEndRunnable = null;
        }
        if (iceWatchRunnable != null) {
            gameHandler.removeCallbacks(iceWatchRunnable);
            iceWatchRunnable = null;
        }
    }

    private void startHostLobbyKeepalive() {
        stopLobbyKeepalive();
        lobbyKeepaliveRunnable = new Runnable() {
            @Override
            public void run() {
                if (!host || !activeSession) {
                    return;
                }
                LobbySnapshot copy;
                synchronized (stateLock) {
                    if (lobby == null || !LobbySnapshot.STATE_LOBBY.equals(lobby.state)) {
                        return;
                    }
                    lobby.updatedAt = System.currentTimeMillis();
                    copy = lobby.copy();
                }
                publishLobbySnapshot(copy);
                mainHandler.postDelayed(this, LOBBY_KEEPALIVE_MS);
            }
        };
        mainHandler.postDelayed(lobbyKeepaliveRunnable, LOBBY_KEEPALIVE_MS);
    }

    private void stopLobbyKeepalive() {
        if (lobbyKeepaliveRunnable != null) {
            mainHandler.removeCallbacks(lobbyKeepaliveRunnable);
            lobbyKeepaliveRunnable = null;
        }
    }

    // ---------------------------------------------------------------------
    // Game event handling
    // ---------------------------------------------------------------------

    private void handleEventPayload(String payload) {
        JSONObject o;
        try {
            o = new JSONObject(payload);
        } catch (JSONException e) {
            return;
        }
        String type = o.optString("type", "");
        String sender = o.optString("pid", "");
        GameRuntime rt = runtime;
        switch (type) {
            case "JOIN":
                handleJoinEvent(sender, o.optString("name", appContext.getString(R.string.game_player_name_placeholder)));
                break;
            case "LEAVE":
                handleLeaveEvent(sender, o.optBoolean("inGame", false));
                break;
            case "START":
                if (!host) {
                    handleStartEvent(o);
                }
                break;
            case "HB":
                handleHeartbeatEvent(sender, o);
                break;
            case "PING":
                deliverPing();
                break;
            case "CHAT":
                handleChatEvent(sender, o);
                break;
            case "SHOP":
                handleShopUse(sender, o);
                break;
            case "CATCH":
                handleCatchEvent(sender, o.optString("victimId", ""),
                        o.optLong("sinceHuntMs", 0));
                break;
            case "KILL":
                handleAssassinKillEvent(o.optString("killerId", sender),
                        o.optString("victimId", ""));
                break;
            case "THAW":
                handleThawEvent(o.optString("victimId", ""), o.optString("byId", sender));
                break;
            case "TARGETS":
                handleTargetsEvent(o);
                break;
            case "OUT":
                handleElimination(sender, o.optString("victimId", ""), o.optString("reason", "ZONE"));
                break;
            case "END":
                handleEndEvent(o);
                break;
            case "LOBBY_CLOSED":
                if (runtime == null) {
                    notifyError(appContext.getString(R.string.error_host_closed));
                }
                break;
            default:
                break;
        }
    }

    private void handleStartEvent(JSONObject o) {
        if (runtime != null) {
            return;
        }
        JSONObject snapshotJson = o.optJSONObject("snapshot");
        if (snapshotJson == null) {
            return;
        }
        LobbySnapshot snapshot;
        try {
            snapshot = LobbySnapshot.fromJson(snapshotJson);
        } catch (JSONException e) {
            return;
        }
        double lat = o.optDouble("centerLat", Double.NaN);
        double lng = o.optDouble("centerLng", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            return;
        }

        synchronized (stateLock) {
            lobby = snapshot;
            livePlayers.clear();
            pingSnapshot = Collections.emptyList();
            for (Player p : snapshot.players) {
                livePlayers.put(p.id, p.copy());
            }
            Player me = livePlayers.get(playerId);
            if (me != null && me.isHider()) {
                generateMyCatchCode();
            } else {
                myCatchCode = "";
                myCatchHash = "";
            }
            resetRoundStats();
            resetShopState();
            runtime = new GameRuntime(snapshot, lat, lng, SystemClock.elapsedRealtime());
        }
        startReplay(snapshot, runtime);
        resetChat();
        huntStartNotified = false;
        lastZonePercent = 100;
        startHeartbeat();
        startPhaseWatchers(runtime);
        notifyGameStarted();
    }

    private void handleHeartbeatEvent(String sender, JSONObject o) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || sender == null || sender.isEmpty()) {
            return;
        }
        boolean becameOut = false;
        boolean stateChanged = false;
        String reason = "";
        synchronized (stateLock) {
            Player p = livePlayers.get(sender);
            if (p == null) {
                return;
            }
            p.lastSeenElapsed = SystemClock.elapsedRealtime();
            p.connected = true;
            if (o.has("lat") && o.has("lng")) {
                p.lat = o.optDouble("lat");
                p.lng = o.optDouble("lng");
                p.accuracy = (float) o.optDouble("acc", 0);
                p.hasLocation = true;
                if (!sender.equals(playerId)) {
                    recordReplaySample(sender, rt.elapsedMs(), p.lat, p.lng);
                }
            }
            String hash = o.optString("hash", "");
            if (!hash.isEmpty()) {
                p.catchHash = hash;
            }
            boolean infected = o.optBoolean("infected", p.infected);
            if (infected != p.infected) {
                p.infected = infected;
                stateChanged = true;
            }
            boolean frozen = o.optBoolean("frozen", p.frozen);
            if (frozen != p.frozen) {
                p.frozen = frozen;
                if (frozen) {
                    p.frozenSinceHuntMs = o.optLong("frozenSince", rt.huntingElapsedMs());
                } else {
                    p.frozenSinceHuntMs = 0;
                }
                stateChanged = true;
            } else if (frozen && o.has("frozenSince")) {
                p.frozenSinceHuntMs = o.optLong("frozenSince", p.frozenSinceHuntMs);
            }
            int kills = o.optInt("kills", p.kills);
            if (kills != p.kills) {
                p.kills = kills;
                stateChanged = true;
            }
            if (o.has("infectedAt")) {
                p.infectedAtHuntMs = o.optLong("infectedAt", p.infectedAtHuntMs);
            }
            if (p.alive && !o.optBoolean("alive", true)) {
                p.alive = false;
                p.eliminatedReason = "AUSGESCHIEDEN";
                becameOut = true;
                reason = p.eliminatedReason;
            }
            if (!p.alive && p.eliminatedReason.isEmpty()) {
                p.eliminatedReason = "AUSGESCHIEDEN";
            }
        }
        if (becameOut || stateChanged) {
            GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
            if (settings != null && settings.isAssassin()) {
                boolean repaired;
                synchronized (stateLock) {
                    repaired = repairAssassinTargetsLocked();
                }
                if (repaired && host) {
                    publishAssassinTargets();
                }
            }
            notifyPlayersChanged();
        }
        if (host && (becameOut || stateChanged)) {
            hostCheckEnd();
        }
    }

    private void handleChatEvent(String sender, JSONObject o) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended) {
            return;
        }
        String text = o.optString("text", "").trim();
        String msgId = o.optString("msgId", "");
        String senderId = o.optString("senderId", sender);
        if (text.isEmpty() || msgId.isEmpty() || senderId.isEmpty()) {
            return;
        }
        if (text.length() > 300) {
            text = text.substring(0, 300);
        }
        String scope = ChatMessage.SCOPE_TEAM.equals(o.optString("scope", ""))
                ? ChatMessage.SCOPE_TEAM : ChatMessage.SCOPE_ALL;
        String role = o.optString("role", "");
        String senderName = o.optString("name", appContext.getString(R.string.game_player_name_placeholder));
        synchronized (stateLock) {
            Player p = livePlayers.get(senderId);
            if (p != null) {
                role = p.teamKey();
                if (p.name != null && !p.name.isEmpty()) {
                    senderName = p.name;
                }
            }
        }
        Player me = getMe();
        if (ChatMessage.SCOPE_TEAM.equals(scope)) {
            if (me == null || !me.teamKey().equals(role)) {
                return;
            }
        }
        ChatMessage message = new ChatMessage();
        message.id = msgId;
        message.senderId = senderId;
        message.senderName = senderName;
        message.role = role;
        message.scope = scope;
        message.text = text;
        message.timestamp = o.optLong("ts", System.currentTimeMillis());
        addChat(message);
    }

    private void handleElimination(String sender, String victimId, String reason) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || victimId == null || victimId.isEmpty()) {
            return;
        }
        boolean changed = false;
        boolean self = victimId.equals(playerId);
        boolean assassinMode = false;
        synchronized (stateLock) {
            Player p = livePlayers.get(victimId);
            if (p != null && p.alive) {
                p.alive = false;
                p.eliminatedReason = reason;
                p.connected = true;
                p.frozen = false;
                p.frozenSinceHuntMs = 0;
                changed = true;
            }
            GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
            assassinMode = settings != null && settings.isAssassin();
            if (changed && assassinMode) {
                repairAssassinTargetsLocked();
            }
        }
        if (!changed) {
            return;
        }
        notifyPlayersChanged();
        if (self) {
            notifySelfEliminated(reason);
        }
        if (host) {
            if (assassinMode) {
                publishAssassinTargets();
            }
            hostCheckEnd();
        }
    }

    private void handleEndEvent(JSONObject o) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended) {
            return;
        }
        String reason = o.optString("reason", "ENDE");
        String winner = o.optString("winner", "");
        synchronized (stateLock) {
            rt.ended = true;
            rt.endReason = reason;
            rt.winner = winner;
            if (lobby != null) {
                lobby.state = LobbySnapshot.STATE_ENDED;
                lobby.updatedAt = System.currentTimeMillis();
            }
        }
        stopHeartbeat();
        stopPhaseWatchers();
        finalizeReplay(reason, winner);
        notifyGameEnded(reason, winner);
    }

    /**
     * Applies a catch according to the current mode. Classic eliminations keep
     * the old path, zombie catches turn the victim and ice catches freeze it.
     */
    private void handleCatchEvent(String sender, String victimId, long sinceHuntMs) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || victimId == null || victimId.isEmpty()) {
            return;
        }
        GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
        if (settings == null) {
            return;
        }
        if (settings.isClassic()) {
            handleElimination(sender, victimId, "GEFANGEN");
            return;
        }
        boolean changed = false;
        boolean self = victimId.equals(playerId);
        String catcher = playerName(sender);
        if (settings.isZombie()) {
            synchronized (stateLock) {
                Player victim = livePlayers.get(victimId);
                if (victim == null || !victim.alive || victim.isSeeker() || victim.infected) {
                    return;
                }
                victim.infected = true;
                victim.infectedAtHuntMs = sinceHuntMs > 0 ? sinceHuntMs : rt.huntingElapsedMs();
                victim.frozen = false;
                victim.frozenSinceHuntMs = 0;
                changed = true;
            }
            if (changed) {
                notifyPlayersChanged();
                if (self) {
                    notifySelfInfected(catcher);
                }
            }
        } else if (settings.isIce()) {
            synchronized (stateLock) {
                Player victim = livePlayers.get(victimId);
                if (victim == null || !victim.alive || !victim.isHider()
                        || victim.infected || victim.frozen) {
                    return;
                }
                victim.frozen = true;
                victim.frozenSinceHuntMs = sinceHuntMs > 0
                        ? sinceHuntMs : rt.huntingElapsedMs();
                changed = true;
            }
            if (changed) {
                notifyPlayersChanged();
                if (self) {
                    notifySelfFrozen(catcher);
                }
            }
        }
        if (changed && host) {
            hostCheckEnd();
        }
    }

    private void handleThawEvent(String victimId, String byId) {
        if (victimId == null || victimId.isEmpty()) {
            return;
        }
        applyThaw(victimId, byId, true);
    }

    private boolean applyThaw(String victimId, String byId, boolean announce) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || victimId == null || victimId.isEmpty()) {
            return false;
        }
        synchronized (stateLock) {
            Player p = livePlayers.get(victimId);
            if (p == null || !p.alive || !p.frozen) {
                return false;
            }
            p.frozen = false;
            p.frozenSinceHuntMs = 0;
        }
        synchronized (shopLock) {
            iceThawHoldStartHuntMs.remove(victimId);
        }
        if (announce) {
            notifyPlayersChanged();
            if (victimId.equals(playerId)) {
                notifySelfThawed(playerName(byId));
            } else if (byId != null && byId.equals(playerId)) {
                notifyTeammateThawed(playerName(victimId), playerName(byId));
            }
        }
        return true;
    }

    private boolean applyMelt(String victimId) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended) {
            return false;
        }
        boolean self = victimId != null && victimId.equals(playerId);
        synchronized (stateLock) {
            Player p = livePlayers.get(victimId);
            if (p == null || !p.alive || !p.frozen) {
                return false;
            }
            p.frozen = false;
            p.frozenSinceHuntMs = 0;
            p.alive = false;
            p.eliminatedReason = "ERFROREN";
        }
        synchronized (shopLock) {
            iceThawHoldStartHuntMs.remove(victimId);
        }
        notifyPlayersChanged();
        if (self) {
            notifySelfEliminated("ERFROREN");
        }
        return true;
    }

    private void handleAssassinKillEvent(String killerId, String victimId) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || victimId == null || victimId.isEmpty()
                || killerId == null || killerId.isEmpty() || killerId.equals(victimId)) {
            return;
        }
        GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
        if (settings == null || !settings.isAssassin()) {
            return;
        }
        boolean self = victimId.equals(playerId);
        synchronized (stateLock) {
            Player victim = livePlayers.get(victimId);
            if (victim == null || !victim.alive) {
                return;
            }
            victim.alive = false;
            victim.eliminatedReason = "ATTENTAT";
            victim.frozen = false;
            victim.frozenSinceHuntMs = 0;
            Player killer = livePlayers.get(killerId);
            if (killer != null && killer.alive && killer.isAssassin()) {
                killer.kills += 1;
            }
            repairAssassinTargetsLocked();
        }
        notifyPlayersChanged();
        if (self) {
            notifySelfEliminated("ATTENTAT");
        }
        if (host) {
            publishAssassinTargets();
            hostCheckEnd();
        }
    }

    private void handleJoinRequestFromGuest(int ignored) {
        // Kept as a placeholder so the event switch stays easy to extend.
    }

    // ---------------------------------------------------------------------
    // Replay recording
    // ---------------------------------------------------------------------

    private void startReplay(LobbySnapshot snapshot, GameRuntime rt) {
        if (snapshot == null || rt == null) {
            return;
        }
        GameReplay recording = new GameReplay();
        recording.id = "replay-" + System.currentTimeMillis() + "-" + (1000 + random.nextInt(9000));
        recording.gameId = snapshot.gameId;
        recording.code = snapshot.code;
        recording.startedAt = rt.startedWallMs;
        recording.centerLat = rt.centerLat;
        recording.centerLng = rt.centerLng;
        recording.initialRadiusMeters = snapshot.settings.initialRadiusMeters;
        recording.shrinkMetersPerMinute = snapshot.settings.shrinkMetersPerMinute;
        recording.hidePhaseMs = rt.hidePhaseMs;
        recording.roundDurationMs = snapshot.settings.roundDurationMs();
        recording.mode = snapshot.settings.mode;
        recording.zoneSeed = rt.zoneGeometry.seed;
        for (Player p : snapshot.players) {
            recording.addPlayer(p.id, p.name, p.role);
        }
        LocationPoint location = myLocation;
        if (location != null) {
            recording.addSample(playerId, rt.elapsedMs(), location.lat, location.lng);
        }
        synchronized (replayLock) {
            replay = recording;
            replayId = "";
            replaySaved = false;
        }
    }

    private void recordReplaySample(String pid, long elapsedMs, double lat, double lng) {
        GameReplay recording = replay;
        if (recording != null) {
            recording.addSample(pid, elapsedMs, lat, lng);
        }
    }

    /**
     * Saves the local recording once. Safe to call repeatedly from end paths.
     */
    public String finalizeReplay(String reason, String winner) {
        flushRoundStats();
        synchronized (replayLock) {
            if (replaySaved) {
                return replayId;
            }
            GameReplay recording = replay;
            if (recording == null) {
                return "";
            }
            if (!recording.hasData()) {
                replaySaved = true;
                return "";
            }
            GameRuntime rt = runtime;
            long elapsed = rt == null ? 0 : rt.elapsedMs();
            recording.durationMs = Math.max(1000, Math.max(elapsed, recording.maxSampleTime()));
            recording.endedAt = System.currentTimeMillis();
            if (recording.startedAt <= 0) {
                recording.startedAt = recording.endedAt - recording.durationMs;
            }
            recording.endReason = reason == null ? "" : reason;
            recording.winner = winner == null ? "" : winner;
            try {
                replayId = ReplayStore.save(appContext, recording);
                replaySaved = true;
                return replayId;
            } catch (Exception e) {
                Log.w(TAG, "Replay konnte nicht gespeichert werden", e);
                return "";
            }
        }
    }

    public String ensureReplaySaved(String reason, String winner) {
        return finalizeReplay(reason, winner);
    }

    public String getReplayId() {
        return replayId;
    }

    public boolean hasReplayData() {
        GameReplay recording = replay;
        return recording != null && recording.hasData();
    }

    // ---------------------------------------------------------------------
    // Direct player actions
    // ---------------------------------------------------------------------

    public CatchResult submitCatch(String targetId, String enteredCode) {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended) {
            return new CatchResult(false, appContext.getString(R.string.error_round_over));
        }
        if (rt.inHidePhase()) {
            return new CatchResult(false, appContext.getString(R.string.error_hide_running));
        }
        GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
        boolean zombieMode = settings != null && settings.isZombie();
        boolean iceMode = settings != null && settings.isIce();
        Player me = getMe();
        if (me == null || (zombieMode ? !me.isZombie() : !me.isSeeker())) {
            return new CatchResult(false, appContext.getString(zombieMode
                    ? R.string.error_only_zombie : R.string.error_only_seeker));
        }
        if (!me.alive) {
            return new CatchResult(false, appContext.getString(R.string.error_you_out));
        }
        if (me.frozen) {
            return new CatchResult(false, appContext.getString(R.string.error_frozen));
        }
        if (zombieMode && me.infected && me.infectedAtHuntMs > 0) {
            long protection = Math.max(5, settings.zombieConversionSeconds) * 1000L;
            long remaining = protection - (rt.huntingElapsedMs() - me.infectedAtHuntMs);
            if (remaining > 0) {
                int seconds = (int) Math.ceil(remaining / 1000.0);
                return new CatchResult(false,
                        appContext.getString(R.string.error_zombie_protected, seconds));
            }
        }
        if (targetId == null || targetId.equals(playerId)) {
            return new CatchResult(false, appContext.getString(R.string.error_choose_hider));
        }
        String cleanCode = enteredCode == null ? "" : enteredCode.replaceAll("\\D", "");
        if (cleanCode.length() != 4) {
            return new CatchResult(false, appContext.getString(R.string.error_enter_catch));
        }
        Player target;
        synchronized (stateLock) {
            target = livePlayers.get(targetId);
            if (target == null) {
                return new CatchResult(false, appContext.getString(R.string.error_player_not_found));
            }
            target = target.copy();
        }
        if (!target.isHider() || target.infected) {
            return new CatchResult(false, appContext.getString(R.string.error_not_hider));
        }
        if (!target.alive) {
            return new CatchResult(false, appContext.getString(R.string.error_already_out, target.name));
        }
        if (iceMode && target.frozen) {
            return new CatchResult(false, appContext.getString(R.string.error_already_out, target.name));
        }
        if (target.catchHash == null || target.catchHash.isEmpty()) {
            return new CatchResult(false, appContext.getString(R.string.error_no_catch_code));
        }
        if (!Hash.matches(cleanCode, targetId, target.catchHash)) {
            return new CatchResult(false, appContext.getString(R.string.error_wrong_catch_code, target.name));
        }
        if (!connected) {
            return new CatchResult(false, appContext.getString(R.string.error_catch_no_connection));
        }

        long nowHunt = rt.huntingElapsedMs();
        handleCatchEvent(playerId, targetId, nowHunt);
        try {
            JSONObject event = baseEvent("CATCH");
            event.put("victimId", targetId);
            event.put("sinceHuntMs", nowHunt);
            publishEvent(event, true);
        } catch (JSONException ignored) {
        }

        int messageRes = R.string.notify_caught;
        if (zombieMode) {
            messageRes = R.string.notify_infected;
        } else if (iceMode) {
            messageRes = R.string.notify_frozen;
        }
        return new CatchResult(true, appContext.getString(messageRes, target.name));
    }

    /**
     * Assassin mode: eliminates the current secret target when the local
     * player is close enough. The new target is inherited from the victim.
     */
    public CatchResult submitAssassinKill() {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended) {
            return new CatchResult(false, appContext.getString(R.string.error_round_over));
        }
        if (rt.inHidePhase()) {
            return new CatchResult(false, appContext.getString(R.string.error_hide_running));
        }
        GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
        if (settings == null || !settings.isAssassin()) {
            return new CatchResult(false, appContext.getString(R.string.error_only_assassin));
        }
        Player me = getMe();
        if (me == null || !me.isAssassin() || !me.alive) {
            return new CatchResult(false, appContext.getString(R.string.error_you_out));
        }
        if (me.frozen) {
            return new CatchResult(false, appContext.getString(R.string.error_frozen));
        }
        if (me.targetId == null || me.targetId.isEmpty()) {
            return new CatchResult(false, appContext.getString(R.string.error_no_target));
        }
        Player target;
        synchronized (stateLock) {
            target = livePlayers.get(me.targetId);
            if (target == null || !target.alive) {
                return new CatchResult(false, appContext.getString(R.string.game_target_none));
            }
            target = target.copy();
        }
        if (!target.hasLocation) {
            return new CatchResult(false, appContext.getString(R.string.game_kill_no_position));
        }
        LocationPoint location = myLocation;
        if (location == null) {
            return new CatchResult(false, appContext.getString(R.string.error_gps_missing_join));
        }
        double distance = Geo.distanceMeters(location.lat, location.lng, target.lat, target.lng);
        int radius = Math.max(10, settings.assassinKillRadiusMeters);
        if (distance > radius) {
            String distanceText = Geo.formatDistance((int) Math.round(distance));
            String radiusText = Geo.formatDistance(radius);
            return new CatchResult(false, appContext.getString(
                    R.string.game_kill_too_far, distanceText, radiusText));
        }
        if (!connected) {
            return new CatchResult(false, appContext.getString(R.string.error_catch_no_connection));
        }

        String targetName = target.name;
        String newTargetName = "";
        int aliveAfter;
        synchronized (stateLock) {
            Player victim = livePlayers.get(target.id);
            if (victim == null || !victim.alive) {
                return new CatchResult(false,
                        appContext.getString(R.string.error_already_out, targetName));
            }
            Player killer = livePlayers.get(playerId);
            if (killer == null || !killer.alive) {
                return new CatchResult(false, appContext.getString(R.string.error_you_out));
            }
            victim.alive = false;
            victim.eliminatedReason = "ATTENTAT";
            victim.frozen = false;
            victim.frozenSinceHuntMs = 0;
            killer.kills += 1;
            repairAssassinTargetsLocked();
            Player updated = livePlayers.get(playerId);
            if (updated != null && updated.targetId != null && !updated.targetId.isEmpty()) {
                Player next = livePlayers.get(updated.targetId);
                newTargetName = next == null ? "" : next.name;
            }
            aliveAfter = 0;
            for (Player p : livePlayers.values()) {
                if (p.alive && p.isAssassin()) {
                    aliveAfter++;
                }
            }
        }
        notifyPlayersChanged();
        try {
            JSONObject event = baseEvent("KILL");
            event.put("killerId", playerId);
            event.put("victimId", target.id);
            publishEvent(event, true);
        } catch (JSONException ignored) {
        }
        if (host) {
            publishAssassinTargets();
            hostCheckEnd();
        }

        String message;
        if (aliveAfter <= 1) {
            message = appContext.getString(R.string.game_kill_last, targetName);
        } else if (!newTargetName.isEmpty()) {
            message = appContext.getString(R.string.game_kill_success, targetName, newTargetName);
        } else {
            message = appContext.getString(R.string.game_target_none);
        }
        return new CatchResult(true, message);
    }

    public boolean eliminateSelfByZone() {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || rt.inHidePhase()) {
            return false;
        }
        Player me;
        synchronized (stateLock) {
            me = livePlayers.get(playerId);
            if (me == null || !me.alive) {
                return false;
            }
            me.alive = false;
            me.eliminatedReason = "ZONE";
        }
        try {
            JSONObject event = baseEvent("OUT");
            event.put("victimId", playerId);
            event.put("reason", "ZONE");
            publishEvent(event, true);
        } catch (JSONException ignored) {
        }
        notifyPlayersChanged();
        notifySelfEliminated("ZONE");
        if (host) {
            GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
            if (settings != null && settings.isAssassin()) {
                synchronized (stateLock) {
                    repairAssassinTargetsLocked();
                }
                publishAssassinTargets();
            }
            hostCheckEnd();
        }
        return true;
    }

    public void endGameAsHost() {
        if (!host) {
            return;
        }
        finishHostGame("HOST_BEENDET", "");
    }

    private void finishHostGame(String reason, String winner) {
        if (!host) {
            return;
        }
        LobbySnapshot endedSnapshot;
        GameRuntime rt = runtime;
        synchronized (stateLock) {
            if (rt == null || rt.ended) {
                return;
            }
            rt.ended = true;
            rt.endReason = reason;
            rt.winner = winner;
            if (lobby != null) {
                lobby.state = LobbySnapshot.STATE_ENDED;
                lobby.updatedAt = System.currentTimeMillis();
                endedSnapshot = lobby.copy();
            } else {
                endedSnapshot = rt.lobby.copy();
                endedSnapshot.state = LobbySnapshot.STATE_ENDED;
                endedSnapshot.updatedAt = System.currentTimeMillis();
            }
        }
        stopHostTimers();
        stopHeartbeat();
        stopPhaseWatchers();
        finalizeReplay(reason, winner);
        notifyGameEnded(reason, winner);
        final int gen = generation;
        final LobbySnapshot snapshotForNetwork = endedSnapshot;
        io.execute(() -> {
            if (gen != generation) {
                return;
            }
            try {
                JSONObject event = baseEvent("END");
                event.put("reason", reason);
                event.put("winner", winner);
                publishJsonBlocking(eventTopic(), event, false, QOS_STATE);
                publishLobbySnapshotBlocking(snapshotForNetwork, true);
            } catch (Exception ignored) {
            }
        });
    }

    private void hostCheckEnd() {
        if (!host || runtime == null || runtime.ended) {
            return;
        }
        mainHandler.postDelayed(() -> {
            GameRuntime rt = runtime;
            if (!host || rt == null || rt.ended) {
                return;
            }
            GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
            if (settings != null && settings.isZombie()) {
                if (countAliveZombies() == 0) {
                    finishHostGame("ZOMBIES_RAUS", "SURVIVORS");
                } else if (countAliveSurvivors() == 0) {
                    finishHostGame("ALLE_INFIZIERT", "ZOMBIES");
                }
                return;
            }
            if (settings != null && settings.isIce()) {
                if (countAliveSeekers() == 0) {
                    finishHostGame("SUCHER_RAUS", "HIDERS");
                } else if (countActiveHiders() == 0) {
                    finishHostGame("ALLE_EINGEFROREN", "ICE");
                }
                return;
            }
            if (settings != null && settings.isAssassin()) {
                String last = lastAliveAssassin();
                if (countAliveAssassins() <= 1) {
                    finishHostGame("LETZTER_ATTENTAETER",
                            last.isEmpty() ? "" : "ASSASSIN:" + last);
                }
                return;
            }
            int aliveSeekers = countAliveSeekers();
            int aliveHiders = countAliveHiders();
            if (aliveSeekers == 0) {
                finishHostGame("SUCHER_RAUS", "HIDERS");
            } else if (aliveHiders == 0) {
                finishHostGame("ALLE_RAUS", "SEEKER");
            }
        }, 120);
    }

    private void finishHostTimeUp() {
        GameRuntime rt = runtime;
        if (!host || rt == null || rt.ended) {
            return;
        }
        GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
        if (settings != null && settings.isZombie()) {
            boolean survivorsLeft = countAliveSurvivors() > 0;
            finishHostGame("ZEIT_ZOMBIE", survivorsLeft ? "SURVIVORS" : "ZOMBIES");
            return;
        }
        if (settings != null && settings.isIce()) {
            boolean hidersLeft = countActiveHiders() > 0;
            finishHostGame("ZEIT_EIS", hidersLeft ? "HIDERS" : "ICE");
            return;
        }
        if (settings != null && settings.isAssassin()) {
            String leader = leadingAssassin();
            finishHostGame("ZEIT_ATTENTAETER",
                    leader.isEmpty() ? "" : "ASSASSIN:" + leader);
            return;
        }
        finishHostGame("ZEIT", countAliveHiders() > 0 ? "HIDERS" : "SEEKER");
    }

    /**
     * Host-side watchdog for ice mode. Freezes melt after the configured time
     * and active teammates thaw them after staying close long enough.
     */
    private void startIceWatcher(GameRuntime rt) {
        if (!host || rt == null || rt.lobby == null || rt.lobby.settings == null
                || !rt.lobby.settings.isIce()) {
            return;
        }
        iceWatchRunnable = new Runnable() {
            @Override
            public void run() {
                GameRuntime current = runtime;
                if (!host || current == null || current != rt || current.ended) {
                    return;
                }
                tickIceWatcher(current);
                gameHandler.postDelayed(this, 1000L);
            }
        };
        gameHandler.postDelayed(iceWatchRunnable, 1000L);
    }

    private void tickIceWatcher(GameRuntime rt) {
        GameSettings settings = rt.lobby == null ? null : rt.lobby.settings;
        if (settings == null || !settings.isIce()) {
            return;
        }
        long hunt = rt.huntingElapsedMs();
        long now = SystemClock.elapsedRealtime();
        long meltMs = Math.max(20, settings.iceMeltSeconds) * 1000L;
        long holdMs = Math.max(3, settings.iceThawHoldSeconds) * 1000L;
        int radius = Math.max(5, settings.iceThawRadiusMeters);
        List<Player> snapshot = getPlayers();
        List<String> melted = new ArrayList<>();
        Map<String, String> thawedBy = new LinkedHashMap<>();
        synchronized (shopLock) {
            Set<String> stillFrozen = new HashSet<>();
            for (Player p : snapshot) {
                if (!p.alive || !p.frozen || !p.isHider()) {
                    continue;
                }
                if (hunt - p.frozenSinceHuntMs >= meltMs) {
                    melted.add(p.id);
                    iceThawHoldStartHuntMs.remove(p.id);
                    continue;
                }
                stillFrozen.add(p.id);
                Player helper = nearestThawHelper(snapshot, p, radius);
                if (helper == null) {
                    iceThawHoldStartHuntMs.remove(p.id);
                    continue;
                }
                Long started = iceThawHoldStartHuntMs.get(p.id);
                if (started == null) {
                    iceThawHoldStartHuntMs.put(p.id, now);
                    started = now;
                }
                if (now - started >= holdMs) {
                    thawedBy.put(p.id, helper.id);
                    iceThawHoldStartHuntMs.remove(p.id);
                }
            }
            iceThawHoldStartHuntMs.keySet().retainAll(stillFrozen);
        }
        for (String id : melted) {
            if (!applyMelt(id)) {
                continue;
            }
            try {
                JSONObject event = baseEvent("OUT");
                event.put("victimId", id);
                event.put("reason", "ERFROREN");
                publishEvent(event, true);
            } catch (JSONException ignored) {
            }
        }
        for (Map.Entry<String, String> entry : thawedBy.entrySet()) {
            if (!applyThaw(entry.getKey(), entry.getValue(), true)) {
                continue;
            }
            try {
                JSONObject event = baseEvent("THAW");
                event.put("victimId", entry.getKey());
                event.put("byId", entry.getValue());
                publishEvent(event, true);
            } catch (JSONException ignored) {
            }
        }
        if (!melted.isEmpty() || !thawedBy.isEmpty()) {
            hostCheckEnd();
        }
    }

    private Player nearestThawHelper(List<Player> players, Player frozen, int radius) {
        if (frozen == null || !frozen.hasLocation) {
            return null;
        }
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player p : players) {
            if (p.id.equals(frozen.id) || !p.alive || p.frozen || p.infected
                    || !p.isHider() || !p.hasLocation) {
                continue;
            }
            double distance = Geo.distanceMeters(frozen.lat, frozen.lng, p.lat, p.lng);
            if (distance <= radius && distance < bestDistance) {
                bestDistance = distance;
                best = p;
            }
        }
        return best;
    }

    private int countAliveSeekers() {
        synchronized (stateLock) {
            int n = 0;
            for (Player p : livePlayers.values()) {
                if (p.isSeeker() && p.alive) {
                    n++;
                }
            }
            return n;
        }
    }

    private int countAliveHiders() {
        synchronized (stateLock) {
            int n = 0;
            for (Player p : livePlayers.values()) {
                if (p.isHider() && p.alive) {
                    n++;
                }
            }
            return n;
        }
    }

    private int countAliveSurvivors() {
        synchronized (stateLock) {
            int n = 0;
            for (Player p : livePlayers.values()) {
                if (p.isSurvivor() && p.alive) {
                    n++;
                }
            }
            return n;
        }
    }

    private int countAliveZombies() {
        synchronized (stateLock) {
            int n = 0;
            for (Player p : livePlayers.values()) {
                if (p.isZombie() && p.alive) {
                    n++;
                }
            }
            return n;
        }
    }

    private int countActiveHiders() {
        synchronized (stateLock) {
            int n = 0;
            for (Player p : livePlayers.values()) {
                if (p.isHider() && !p.infected && p.alive && !p.frozen) {
                    n++;
                }
            }
            return n;
        }
    }

    private int countAliveAssassins() {
        synchronized (stateLock) {
            int n = 0;
            for (Player p : livePlayers.values()) {
                if (p.isAssassin() && p.alive) {
                    n++;
                }
            }
            return n;
        }
    }

    private String lastAliveAssassin() {
        synchronized (stateLock) {
            for (Player p : livePlayers.values()) {
                if (p.isAssassin() && p.alive) {
                    return p.id;
                }
            }
            return "";
        }
    }

    private String leadingAssassin() {
        synchronized (stateLock) {
            Player best = null;
            for (Player p : livePlayers.values()) {
                if (!p.isAssassin() || !p.alive) {
                    continue;
                }
                if (best == null || p.kills > best.kills
                        || (p.kills == best.kills && p.id.compareTo(best.id) < 0)) {
                    best = p;
                }
            }
            return best == null ? "" : best.id;
        }
    }

    // ---------------------------------------------------------------------
    // Heartbeat / ping
    // ---------------------------------------------------------------------

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (!activeSession || runtime == null) {
                    return;
                }
                publishHeartbeat();
                gameHandler.postDelayed(this, HEARTBEAT_MS);
            }
        };
        publishHeartbeat();
        gameHandler.postDelayed(heartbeatRunnable, HEARTBEAT_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatRunnable != null) {
            gameHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
    }

    private void startPhaseWatchers(GameRuntime rt) {
        stopPhaseWatchers();
        huntStartNotified = false;
        lastZonePercent = 100;
        if (rt == null) {
            return;
        }
        long hideRemaining = rt.hideRemainingMs();
        if (hideRemaining > 0) {
            hidePhaseRunnable = () -> {
                GameRuntime current = runtime;
                if (current != rt || current.ended || huntStartNotified) {
                    return;
                }
                notifyHuntStarted();
            };
            gameHandler.postDelayed(hidePhaseRunnable, hideRemaining + 100L);
        } else {
            notifyHuntStarted();
        }

        zoneWatchRunnable = new Runnable() {
            @Override
            public void run() {
                GameRuntime current = runtime;
                if (current == null || current != rt || current.ended) {
                    return;
                }
                if (current.lobby.settings.shrinkMetersPerMinute > 0 && !current.inHidePhase()) {
                    int initial = Math.max(1, current.lobby.settings.initialRadiusMeters);
                    int radius = current.currentRadiusMeters();
                    int crossed = 100;
                    for (int stage : ZONE_STAGES) {
                        if (radius * 100 <= initial * stage) {
                            crossed = stage;
                        }
                    }
                    if (crossed < lastZonePercent) {
                        lastZonePercent = crossed;
                        NotificationHelper.zoneShrinking(appContext, radius, crossed);
                    }
                }
                gameHandler.postDelayed(this, 5000L);
            }
        };
        gameHandler.postDelayed(zoneWatchRunnable, 5000L);

        // Fallback for lost PING events: every device can reveal the current
        // positions on its own when the expected ping window has passed.
        pingWatchRunnable = new Runnable() {
            @Override
            public void run() {
                GameRuntime current = runtime;
                if (current == null || current != rt || current.ended) {
                    return;
                }
                if (!current.inHidePhase()) {
                    long interval = Math.max(10,
                            current.lobby.settings.pingIntervalSeconds) * 1000L;
                    long now = SystemClock.elapsedRealtime();
                    long since = lastPingElapsed > 0
                            ? now - lastPingElapsed
                            : current.huntingElapsedMs();
                    if (since >= interval + 4000L) {
                        deliverPing();
                    }
                }
                gameHandler.postDelayed(this, 1000L);
            }
        };
        gameHandler.postDelayed(pingWatchRunnable, 1000L);
    }

    private void notifyHuntStarted() {
        if (huntStartNotified) {
            return;
        }
        huntStartNotified = true;
        Player me = getMe();
        boolean hunter = me != null && (me.isSeeker() || me.isZombie());
        NotificationHelper.huntStarted(appContext, hunter);
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onHuntStarted();
            }
        });
    }

    private void stopPhaseWatchers() {
        if (hidePhaseRunnable != null) {
            gameHandler.removeCallbacks(hidePhaseRunnable);
            hidePhaseRunnable = null;
        }
        if (zoneWatchRunnable != null) {
            gameHandler.removeCallbacks(zoneWatchRunnable);
            zoneWatchRunnable = null;
        }
        if (pingWatchRunnable != null) {
            gameHandler.removeCallbacks(pingWatchRunnable);
            pingWatchRunnable = null;
        }
    }

    private void publishHeartbeat() {
        final int gen = generation;
        io.execute(() -> {
            if (gen != generation) {
                return;
            }
            GameRuntime rt = runtime;
            if (rt == null || rt.ended || !connected) {
                return;
            }
            try {
                JSONObject event = baseEvent("HB");
                LocationPoint location = myLocation;
                if (location != null) {
                    event.put("lat", location.lat);
                    event.put("lng", location.lng);
                    event.put("acc", location.accuracy);
                }
                event.put("alive", isAliveLocal());
                if (!myCatchHash.isEmpty()) {
                    event.put("hash", myCatchHash);
                }
                Player meState;
                synchronized (stateLock) {
                    Player p = livePlayers.get(playerId);
                    meState = p == null ? null : p.copy();
                }
                if (meState != null) {
                    if (meState.infected) {
                        event.put("infected", true);
                        event.put("infectedAt", meState.infectedAtHuntMs);
                    }
                    if (meState.frozen) {
                        event.put("frozen", true);
                        event.put("frozenSince", meState.frozenSinceHuntMs);
                    }
                    if (meState.kills > 0) {
                        event.put("kills", meState.kills);
                    }
                    if (meState.targetId != null && !meState.targetId.isEmpty()) {
                        event.put("targetId", meState.targetId);
                    }
                }
                publishJsonBlocking(eventTopic(), event, false, QOS_STATE);
            } catch (Exception ignored) {
            }
        });
    }

    private boolean isAliveLocal() {
        synchronized (stateLock) {
            Player me = livePlayers.get(playerId);
            return me == null || me.alive;
        }
    }

    private void generateMyCatchCode() {
        myCatchCode = String.format(java.util.Locale.GERMANY, "%04d", 1000 + random.nextInt(9000));
        myCatchHash = Hash.sha256(myCatchCode + "|" + playerId);
    }

    // ---------------------------------------------------------------------
    // MQTT helpers
    // ---------------------------------------------------------------------

    private JSONObject baseEvent(String type) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", type);
        o.put("pid", playerId);
        o.put("ts", System.currentTimeMillis());
        return o;
    }

    private void publishEvent(JSONObject event, boolean reliable) {
        publishJson(eventTopic(), event, false, reliable ? QOS_STATE : QOS_HEARTBEAT);
    }

    private void publishLobbySnapshot(LobbySnapshot snapshot) {
        final int gen = generation;
        io.execute(() -> {
            if (gen != generation) {
                return;
            }
            try {
                publishLobbySnapshotBlocking(snapshot, true);
            } catch (Exception e) {
                notifyError(appContext.getString(R.string.error_lobby_update));
            }
        });
    }

    private void publishLobbySnapshotBlocking(LobbySnapshot snapshot, boolean retained) throws MqttException, JSONException {
        publishJsonBlocking(lobbyTopic(), snapshot.toJson(), retained, QOS_STATE);
    }

    private void publishJoinBlocking() throws MqttException, JSONException {
        JSONObject event = baseEvent("JOIN");
        event.put("name", name);
        publishJsonBlocking(eventTopic(), event, false, QOS_STATE);
    }

    private void publishJson(JSONObject payload, boolean retained, int qos) {
        publishJson(eventTopic(), payload, retained, qos);
    }

    private void publishJson(String topic, JSONObject payload, boolean retained, int qos) {
        int gen = generation;
        io.execute(() -> {
            if (gen != generation) {
                return;
            }
            try {
                publishJsonBlocking(topic, payload, retained, qos);
            } catch (Exception e) {
                Log.w(TAG, "publish failed", e);
            }
        });
    }

    private void publishJsonBlocking(String topic, JSONObject payload, boolean retained, int qos)
            throws MqttException {
        MqttClient client = mqtt;
        if (client == null || !client.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        message.setRetained(retained);
        client.publish(topic, message);
    }

    private String lobbyTopic() {
        return TOPIC_ROOT + "lobby/" + code;
    }

    private String eventTopic() {
        return TOPIC_ROOT + "game/" + gameId + "/events";
    }

    // ---------------------------------------------------------------------
    // Location tracking
    // ---------------------------------------------------------------------

    public boolean startLocationTracking() {
        if (!hasFineLocationPermission()) {
            return false;
        }
        if (locationTracking) {
            return true;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::startLocationTracking);
            return true;
        }
        try {
            locationManager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                return false;
            }
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        updateLocation(location);
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };
            boolean any = false;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, 2000L, 0f, locationListener, Looper.getMainLooper());
                        any = true;
                    }
                    Location last = locationManager.getLastKnownLocation(provider);
                    if (last != null) {
                        updateLocation(last);
                    }
                } catch (SecurityException | IllegalArgumentException ignored) {
                }
            }
            locationTracking = any;
            return any;
        } catch (Exception e) {
            Log.w(TAG, "location setup failed", e);
            return false;
        }
    }

    private void updateLocation(Location location) {
        long fixAgeMs = (SystemClock.elapsedRealtimeNanos()
                - location.getElapsedRealtimeNanos()) / 1_000_000L;
        if (location.getElapsedRealtimeNanos() > 0 && fixAgeMs > 90_000L) {
            // A cached last-known fix from another place would otherwise
            // become the zone centre or a wild ping position.
            return;
        }
        LocationPoint point = new LocationPoint(location.getLatitude(), location.getLongitude(),
                location.getAccuracy(), SystemClock.elapsedRealtime());
        myLocation = point;
        GameRuntime rt = runtime;
        if (rt != null && !rt.ended) {
            trackDistance(point);
            recordReplaySample(playerId, rt.elapsedMs(), point.lat, point.lng);
        }
        Listener l = listener;
        if (l != null) {
            mainHandler.post(() -> {
                Listener current = listener;
                if (current != null) {
                    current.onLocationChanged(point);
                }
            });
        }
    }

    public void stopLocationTracking() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {
            }
        }
        locationManager = null;
        locationListener = null;
        locationTracking = false;
    }

    // ---------------------------------------------------------------------
    // Leaving
    // ---------------------------------------------------------------------

    /**
     * Announces leaving and disconnects. Use this from the lobby or while playing.
     */
    public void leaveCurrentSession() {
        if (!activeSession && mqtt == null) {
            shutdown();
            return;
        }
        final boolean wasHost = host;
        final GameRuntime rt = runtime;
        final boolean runningGame = rt != null && !rt.ended;
        final boolean inGame = rt != null;
        final String reason = runningGame ? "HOST_BEENDET" : "LOBBY_CLOSED";
        LobbySnapshot ended = null;

        if (wasHost) {
            synchronized (stateLock) {
                if (lobby != null) {
                    lobby.state = LobbySnapshot.STATE_ENDED;
                    lobby.updatedAt = System.currentTimeMillis();
                    ended = lobby.copy();
                }
            }
        }

        if (rt != null) {
            finalizeReplay(wasHost ? "HOST_BEENDET" : "VERLASSEN", "");
        }

        activeSession = false;
        listener = null;
        ++generation;
        stopSchedulers();
        stopHeartbeat();
        stopLocationTracking();
        NotificationHelper.cancelGameNotifications(appContext);

        final LobbySnapshot snapshotToPublish = ended;
        final int gen = generation;

        io.execute(() -> {
            try {
                MqttClient client = mqtt;
                if (client != null && client.isConnected()) {
                    if (wasHost) {
                        if (snapshotToPublish != null) {
                            publishLobbySnapshotBlocking(snapshotToPublish, true);
                        }
                        if (runningGame) {
                            JSONObject event = new JSONObject();
                            event.put("type", "END");
                            event.put("pid", playerId);
                            event.put("ts", System.currentTimeMillis());
                            event.put("reason", reason);
                            event.put("winner", "");
                            publishJsonBlocking(eventTopic(), event, false, QOS_STATE);
                        } else {
                            JSONObject event = new JSONObject();
                            event.put("type", "LOBBY_CLOSED");
                            event.put("pid", playerId);
                            event.put("ts", System.currentTimeMillis());
                            publishJsonBlocking(eventTopic(), event, false, QOS_STATE);
                        }
                    } else {
                        JSONObject event = new JSONObject();
                        event.put("type", "LEAVE");
                        event.put("pid", playerId);
                        event.put("ts", System.currentTimeMillis());
                        event.put("inGame", inGame);
                        publishJsonBlocking(eventTopic(), event, false, QOS_STATE);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                disconnectQuietly();
            }
        });
    }

    /**
     * Silent disconnect, e.g. after the result screen.
     */
    public void shutdown() {
        ++generation;
        activeSession = false;
        listener = null;
        stopSchedulers();
        stopHeartbeat();
        stopLocationTracking();
        io.execute(this::disconnectQuietly);
    }

    private void stopSchedulers() {
        stopHostTimers();
        stopLobbyKeepalive();
        stopPhaseWatchers();
        if (heartbeatRunnable != null) {
            gameHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
    }

    // ---------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------

    private void notifyLobby(LobbySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        final LobbySnapshot copy = snapshot.copy();
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onLobbyChanged(copy);
            }
        });
    }

    private void notifyGameStarted() {
        Player me = getMe();
        GameRuntime rt = runtime;
        int hideSeconds = rt == null ? 25 : (int) (rt.hidePhaseMs / 1000L);
        NotificationHelper.gameStarted(appContext, me != null && (me.isSeeker() || me.isZombie()), hideSeconds);
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onGameStarted();
            }
        });
    }

    private void notifyGameEnded(String reason, String winner) {
        String title = winnerNotificationTitle(winner);
        NotificationHelper.gameEnded(appContext, title, endNotificationText(reason, winner));
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onGameEnded(reason, winner);
            }
        });
    }

    private String winnerNotificationTitle(String winner) {
        String value = winner == null ? "" : winner;
        if ("SEEKER".equals(value)) {
            return appContext.getString(R.string.game_winner_seeker);
        }
        if ("HIDERS".equals(value)) {
            return appContext.getString(R.string.game_winners_hiders);
        }
        if ("ZOMBIES".equals(value)) {
            return appContext.getString(R.string.game_winner_zombies);
        }
        if ("SURVIVORS".equals(value)) {
            return appContext.getString(R.string.game_winner_survivors);
        }
        if ("ICE".equals(value)) {
            return appContext.getString(R.string.game_winner_ice_hunter);
        }
        if (value.startsWith("ASSASSIN:")) {
            String name = playerName(value.substring("ASSASSIN:".length()));
            return appContext.getString(R.string.game_winner_assassin,
                    name.isEmpty() ? "?" : name);
        }
        return appContext.getString(R.string.replay_round_over);
    }

    private String endNotificationText(String reason, String winner) {
        if (reason == null) {
            return appContext.getString(R.string.end_default);
        }
        switch (reason) {
            case "ALLE_RAUS":
                return appContext.getString(R.string.replay_end_all_out);
            case "ZEIT":
                return appContext.getString(R.string.replay_end_time);
            case "SUCHER_RAUS":
                return appContext.getString(R.string.replay_end_seekers_out);
            case "HOST_BEENDET":
                return appContext.getString(R.string.replay_end_host);
            case "HOST_VERLASSEN":
                return appContext.getString(R.string.replay_end_host_left);
            case "ALLE_INFIZIERT":
                return appContext.getString(R.string.end_all_infected);
            case "ZOMBIES_RAUS":
                return appContext.getString(R.string.end_zombie_out);
            case "ALLE_EINGEFROREN":
                return appContext.getString(R.string.end_all_frozen);
            case "LETZTER_ATTENTAETER":
                return appContext.getString(R.string.end_last_assassin);
            case "ZEIT_ZOMBIE":
                return appContext.getString("ZOMBIES".equals(winner)
                        ? R.string.end_time_zombies : R.string.end_time_survivors);
            case "ZEIT_EIS":
                return appContext.getString("ICE".equals(winner)
                        ? R.string.end_time_ice : R.string.end_time_hiders_ice);
            case "ZEIT_ATTENTAETER":
                return appContext.getString(R.string.end_time_assassin);
            default:
                return appContext.getString(R.string.end_default);
        }
    }

    private synchronized void deliverPing() {
        GameRuntime rt = runtime;
        if (rt == null || rt.ended || rt.inHidePhase()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        // Local trigger and broker echo can arrive almost together.
        if (lastPingElapsed > 0 && now - lastPingElapsed < 1500L) {
            return;
        }
        lastPingElapsed = now;
        capturePingSnapshot();
        notifyPing();
    }

    /**
     * Freezes the currently known positions. Only alive players with a fix are
     * included, so markers of eliminated players disappear immediately.
     */
    private void capturePingSnapshot() {
        List<Player> snapshot = new ArrayList<>();
        GameRuntime rt = runtime;
        long hunt = rt == null ? 0 : rt.huntingElapsedMs();
        GameSettings settings = rt == null || rt.lobby == null ? null : rt.lobby.settings;
        synchronized (stateLock) {
            Player me = livePlayers.get(playerId);
            boolean targetOnly = settings != null && settings.isAssassin()
                    && settings.assassinTargetPingOnly;
            for (Player p : livePlayers.values()) {
                if (!p.alive || !p.hasLocation || isPlayerGhosted(p.id, hunt)) {
                    continue;
                }
                if (targetOnly && me != null && !p.id.equals(me.id)
                        && !p.id.equals(me.targetId)) {
                    continue;
                }
                snapshot.add(p.copy());
            }
        }
        pingSnapshot = Collections.unmodifiableList(snapshot);
    }

    private void notifyPing() {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onPing();
            }
        });
    }

    private void notifyShopChanged() {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onShopChanged();
            }
        });
    }

    private void notifyPlayersChanged() {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onPlayersChanged();
            }
        });
    }

    private void notifySelfEliminated(String reason) {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onSelfEliminated(reason);
            }
        });
    }

    private void notifySelfInfected(String byName) {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onSelfInfected(byName);
            }
        });
    }

    private void notifySelfFrozen(String byName) {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onSelfFrozen(byName);
            }
        });
    }

    private void notifySelfThawed(String byName) {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onSelfThawed(byName);
            }
        });
    }

    private void notifyTeammateThawed(String victimName, String byName) {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onTeammateThawed(victimName, byName);
            }
        });
    }

    private String playerName(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        synchronized (stateLock) {
            Player p = livePlayers.get(id);
            return p == null ? "" : p.name;
        }
    }

    private void notifyError(String message) {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onError(message);
            }
        });
    }

    private void notifyConnection(boolean isConnected) {
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onConnectionChanged(isConnected);
            }
        });
    }

    private void notifyLobbyWithoutCopy(LobbySnapshot snapshot) {
        notifyLobby(snapshot);
    }

    private void seedLiveFromLobby(LobbySnapshot snapshot) {
        livePlayers.clear();
        for (Player p : snapshot.players) {
            livePlayers.put(p.id, p.copy());
        }
    }

    private String cleanName(String raw) {
        String fallback = appContext.getString(R.string.game_player_name_placeholder);
        String clean = raw == null ? fallback : raw.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            clean = fallback;
        }
        return clean.length() > 20 ? clean.substring(0, 20) : clean;
    }

    public static boolean isLocationEnabled(Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return false;
            }
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }
}
