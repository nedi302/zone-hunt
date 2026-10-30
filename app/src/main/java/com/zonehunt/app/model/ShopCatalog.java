package com.zonehunt.app.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.zonehunt.app.R;

/**
 * The items that can be bought with survival coins during a round.
 *
 * Every game mode has its own three items. Prices are defined for a 30 minute
 * round and scale linearly with the round length. Coins are earned at a fixed
 * rate of one coin per five seconds, so a purchase costs roughly the same share
 * of a round regardless of its length.
 */
public final class ShopCatalog {

    public static final int SECONDS_PER_COIN = 5;
    public static final int PRICE_BASE_MINUTES = 30;

    public static final String ITEM_FAKE_MARKER = "FAKE_MARKER";
    public static final String ITEM_GHOST = "GHOST";
    public static final String ITEM_RADAR = "RADAR";

    public static final String ITEM_ZOMBIE_DECOY = "ZOMBIE_DECOY";
    public static final String ITEM_ZOMBIE_CAMO = "ZOMBIE_CAMO";
    public static final String ITEM_ZOMBIE_SENSE = "ZOMBIE_SENSE";

    public static final String ITEM_ASSASSIN_TRACK = "ASSASSIN_TRACK";
    public static final String ITEM_ASSASSIN_SHADOW = "ASSASSIN_SHADOW";
    public static final String ITEM_ASSASSIN_RADAR = "ASSASSIN_RADAR";

    public static final String ITEM_ICE_DECOY = "ICE_DECOY";
    public static final String ITEM_ICE_TEA = "ICE_TEA";
    public static final String ITEM_ICE_THERMAL = "ICE_THERMAL";

    public static final class Item {
        public final String id;
        public final int nameRes;
        public final int descriptionRes;
        /** Empty for everyone, otherwise the required player role. */
        public final String role;
        public final int basePrice;

        private Item(String id, int nameRes, int descriptionRes, String role, int basePrice) {
            this.id = id;
            this.nameRes = nameRes;
            this.descriptionRes = descriptionRes;
            this.role = role;
            this.basePrice = basePrice;
        }

        public boolean isHiderOnly() {
            return "HIDER".equals(role);
        }

        public boolean isSeekerOnly() {
            return "SEEKER".equals(role);
        }

        public boolean isZombieOnly() {
            return "ZOMBIE".equals(role);
        }

        public boolean isAssassinOnly() {
            return "ASSASSIN".equals(role);
        }
    }

    public static final Item FAKE_MARKER = new Item(
            ITEM_FAKE_MARKER,
            R.string.shop_item_decoy_name,
            R.string.shop_item_decoy_description,
            "HIDER",
            90
    );

    public static final Item GHOST = new Item(
            ITEM_GHOST,
            R.string.shop_item_ghost_name,
            R.string.shop_item_ghost_description,
            "HIDER",
            180
    );

    public static final Item RADAR = new Item(
            ITEM_RADAR,
            R.string.shop_item_radar_name,
            R.string.shop_item_radar_description,
            "SEEKER",
            150
    );

    public static final Item ZOMBIE_DECOY = new Item(
            ITEM_ZOMBIE_DECOY,
            R.string.shop_item_zombie_decoy_name,
            R.string.shop_item_zombie_decoy_description,
            "HIDER",
            90
    );

    public static final Item ZOMBIE_CAMO = new Item(
            ITEM_ZOMBIE_CAMO,
            R.string.shop_item_zombie_camo_name,
            R.string.shop_item_zombie_camo_description,
            "HIDER",
            180
    );

    public static final Item ZOMBIE_SENSE = new Item(
            ITEM_ZOMBIE_SENSE,
            R.string.shop_item_zombie_sense_name,
            R.string.shop_item_zombie_sense_description,
            "ZOMBIE",
            150
    );

    public static final Item ASSASSIN_TRACK = new Item(
            ITEM_ASSASSIN_TRACK,
            R.string.shop_item_assassin_track_name,
            R.string.shop_item_assassin_track_description,
            "ASSASSIN",
            90
    );

    public static final Item ASSASSIN_SHADOW = new Item(
            ITEM_ASSASSIN_SHADOW,
            R.string.shop_item_assassin_shadow_name,
            R.string.shop_item_assassin_shadow_description,
            "ASSASSIN",
            180
    );

    public static final Item ASSASSIN_RADAR = new Item(
            ITEM_ASSASSIN_RADAR,
            R.string.shop_item_assassin_radar_name,
            R.string.shop_item_assassin_radar_description,
            "ASSASSIN",
            150
    );

    public static final Item ICE_DECOY = new Item(
            ITEM_ICE_DECOY,
            R.string.shop_item_ice_decoy_name,
            R.string.shop_item_ice_decoy_description,
            "HIDER",
            90
    );

    public static final Item ICE_TEA = new Item(
            ITEM_ICE_TEA,
            R.string.shop_item_ice_tea_name,
            R.string.shop_item_ice_tea_description,
            "HIDER",
            150
    );

    public static final Item ICE_THERMAL = new Item(
            ITEM_ICE_THERMAL,
            R.string.shop_item_ice_thermal_name,
            R.string.shop_item_ice_thermal_description,
            "SEEKER",
            150
    );

    public static final List<Item> CLASSIC_ITEMS = Collections.unmodifiableList(
            Arrays.asList(FAKE_MARKER, GHOST, RADAR));

    public static final List<Item> ZOMBIE_ITEMS = Collections.unmodifiableList(
            Arrays.asList(ZOMBIE_DECOY, ZOMBIE_CAMO, ZOMBIE_SENSE));

    public static final List<Item> ASSASSIN_ITEMS = Collections.unmodifiableList(
            Arrays.asList(ASSASSIN_TRACK, ASSASSIN_SHADOW, ASSASSIN_RADAR));

    public static final List<Item> ICE_ITEMS = Collections.unmodifiableList(
            Arrays.asList(ICE_DECOY, ICE_TEA, ICE_THERMAL));

    public static final List<Item> ITEMS = Collections.unmodifiableList(Arrays.asList(
            FAKE_MARKER, GHOST, RADAR,
            ZOMBIE_DECOY, ZOMBIE_CAMO, ZOMBIE_SENSE,
            ASSASSIN_TRACK, ASSASSIN_SHADOW, ASSASSIN_RADAR,
            ICE_DECOY, ICE_TEA, ICE_THERMAL));

    private ShopCatalog() {
    }

    public static List<Item> itemsFor(GameSettings settings) {
        String mode = settings == null ? GameSettings.MODE_CLASSIC : settings.mode;
        if (GameSettings.MODE_ZOMBIE.equals(mode)) {
            return ZOMBIE_ITEMS;
        }
        if (GameSettings.MODE_ASSASSIN.equals(mode)) {
            return ASSASSIN_ITEMS;
        }
        if (GameSettings.MODE_ICE.equals(mode)) {
            return ICE_ITEMS;
        }
        return CLASSIC_ITEMS;
    }

    public static Item find(String id) {
        if (id == null) {
            return null;
        }
        for (Item item : ITEMS) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isDecoy(Item item) {
        return item != null && (ITEM_FAKE_MARKER.equals(item.id)
                || ITEM_ZOMBIE_DECOY.equals(item.id)
                || ITEM_ASSASSIN_TRACK.equals(item.id)
                || ITEM_ICE_DECOY.equals(item.id));
    }

    public static boolean isGhost(Item item) {
        return item != null && (ITEM_GHOST.equals(item.id)
                || ITEM_ZOMBIE_CAMO.equals(item.id)
                || ITEM_ASSASSIN_SHADOW.equals(item.id));
    }

    public static boolean isRadar(Item item) {
        return item != null && (ITEM_RADAR.equals(item.id)
                || ITEM_ZOMBIE_SENSE.equals(item.id)
                || ITEM_ASSASSIN_RADAR.equals(item.id)
                || ITEM_ICE_THERMAL.equals(item.id));
    }

    public static boolean isThaw(Item item) {
        return item != null && ITEM_ICE_TEA.equals(item.id);
    }

    public static boolean canUse(Item item, Player player) {
        if (item == null || player == null || !player.alive) {
            return false;
        }
        if (isThaw(item)) {
            return player.isHider() && !player.infected;
        }
        if (player.frozen) {
            return false;
        }
        if ("HIDER".equals(item.role)) {
            return player.isHider() && !player.infected;
        }
        if ("SEEKER".equals(item.role)) {
            return player.isSeeker();
        }
        if ("ZOMBIE".equals(item.role)) {
            return player.isZombie();
        }
        if ("ASSASSIN".equals(item.role)) {
            return player.isAssassin();
        }
        return true;
    }

    public static int roleLabelRes(Item item) {
        if (item != null && "HIDER".equals(item.role)) {
            return R.string.shop_role_hider_only;
        }
        if (item != null && "SEEKER".equals(item.role)) {
            return R.string.shop_role_seeker_only;
        }
        if (item != null && "ZOMBIE".equals(item.role)) {
            return R.string.shop_role_zombie_only;
        }
        if (item != null && "ASSASSIN".equals(item.role)) {
            return R.string.shop_role_assassin_only;
        }
        return 0;
    }

    public static int priceFor(GameSettings settings, Item item) {
        if (item == null) {
            return 0;
        }
        int minutes = settings == null ? 30
                : GameSettings.clamp(settings.roundMinutes, 5, 120);
        double scaled = item.basePrice * (minutes / (double) PRICE_BASE_MINUTES);
        int rounded = (int) Math.round(scaled / 5.0) * 5;
        return Math.max(5, rounded);
    }

    public static int priceFor(GameSettings settings, String itemId) {
        return priceFor(settings, find(itemId));
    }

    /**
     * Total coins a player can earn by surviving an entire round.
     */
    public static int coinsForRound(GameSettings settings) {
        int minutes = settings == null ? 30
                : GameSettings.clamp(settings.roundMinutes, 5, 120);
        return Math.max(1, minutes * 60 / SECONDS_PER_COIN);
    }
}
