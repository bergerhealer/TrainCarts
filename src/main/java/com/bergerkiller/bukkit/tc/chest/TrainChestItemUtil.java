package com.bergerkiller.bukkit.tc.chest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableMember;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.BasicConfiguration;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.components.RailPath.Position;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.debug.DebugToolUtil;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.google.common.io.ByteStreams;

public class TrainChestItemUtil {
    private static final String IDENTIFIER = "Traincarts.chest";
    private static final String TITLE = "Traincarts Chest";
    private static final boolean CAN_USE_NEW_BKCL_ITEM_APIS = Common.hasCapability("Common:CommonItemStack:AddGlint");

    /** How much extra distance to look for members to auto-connect with. Emulates 'reach' */
    private static final double AUTOCONNECT_EXTRA_DISTANCE = 1.0;
    /** The reach of using the train chest to spawn where the player is looking */
    private static final double SPAWN_LOOKING_AT_REACH = 10.0;

    public static ItemStack createItem() {
        CommonItemStack item = CommonItemStack.create(Material.ENDER_CHEST, 1)
                .updateCustomData(tag -> {
                    tag.putValue("plugin", TrainCarts.plugin.getName());
                    tag.putValue("identifier", IDENTIFIER);
                    tag.putValue("name", "");
                    tag.putValue("parsed", false);
                    tag.putValue("locked", false);
                    tag.putValue("HideFlags", 1);
                })
                .hideAllAttributes();
        if (CAN_USE_NEW_BKCL_ITEM_APIS) {
            applyNewBKCLChanges(item);
        } else {
            item.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
        }
        updateTitle(item);
        return item.toBukkit();
    }

    private static void applyNewBKCLChanges(CommonItemStack item) {
        item.addGlint().mimicAsType(MaterialUtil.getFirst("PAPER", "LEGACY_PAPER"));
    }

    private static void updateTitle(CommonItemStack item) {
        String displayTitle = TITLE;
        String name = getName(item);
        if (name.isEmpty() && !isEmpty(item) && item.getCustomData().getValue("parsed", false)) {
            name = item.getCustomData().getValue("config", "");
        }
        if (!name.isEmpty()) {
            displayTitle += " (" + name + ")";
        }
        item.setCustomNameMessage(displayTitle);

        item.clearLores();
        if (isEmpty(item)) {
            item.addLore(ChatText.fromMessage(ChatColor.RED + "Empty"));
        } else if (isFiniteSpawns(item)) {
            item.addLore(ChatText.fromMessage(ChatColor.BLUE + "Single-use"));
        } else {
            item.addLore(ChatText.fromMessage(ChatColor.DARK_PURPLE + "Infinite uses"));
        }
        double speed = getSpeed(item);
        if (speed > 0.0) {
            item.addLore(ChatText.fromMessage(ChatColor.YELLOW + "Speed " + DebugToolUtil.formatNumber(speed) + "b/t"));
        }
        if (isLocked(item)) {
            item.addLore(ChatText.fromMessage(ChatColor.RED + "Locked"));
        }
    }

    public static boolean isItem(ItemStack item) {
        return isItem(CommonItemStack.of(item));
    }

    public static boolean isItem(CommonItemStack item) {
        if (!item.isEmpty() && item.hasCustomData()) {
            return IDENTIFIER.equals(item.getCustomData().getValue("identifier", ""));
        }
        return false;
    }

    public static void setFiniteSpawns(ItemStack item, boolean finite) {
        setFiniteSpawns(CommonItemStack.of(item), finite);
    }

    public static void setFiniteSpawns(CommonItemStack item, boolean finite) {
        if (isItem(item)) {
            item.updateCustomData(tag -> tag.putValue("finite", finite));
            updateTitle(item);
        }
    }

    public static void setLocked(ItemStack item, boolean locked) {
        setLocked(CommonItemStack.of(item), locked);
    }

    public static void setLocked(CommonItemStack item, boolean locked) {
        if (isItem(item)) {
            item.updateCustomData(tag -> tag.putValue("locked", locked));
            updateTitle(item);
        }
    }

    public static void setSpeed(ItemStack item, double speed) {
        setSpeed(CommonItemStack.of(item), speed);
    }

    public static void setSpeed(CommonItemStack item, double speed) {
        if (isItem(item)) {
            item.updateCustomData(tag -> tag.putValue("speed", speed));
            updateTitle(item);
        }
    }

    public static void setSpawnMessage(ItemStack item, String message) {
        setSpawnMessage(CommonItemStack.of(item), message);
    }

    public static void setSpawnMessage(CommonItemStack item, String message) {
        if (isItem(item)) {
            item.updateCustomData(tag -> tag.putValue("spawnMessage", message));
        }
    }

    public static String getSpawnMessage(ItemStack item) {
        return getSpawnMessage(CommonItemStack.of(item));
    }

    public static String getSpawnMessage(CommonItemStack item) {
        return isItem(item) ? item.getCustomData().getValue("spawnMessage", String.class, null)
                            : null;
    }

    public static boolean isLocked(ItemStack item) {
        return isLocked(CommonItemStack.of(item));
    }

    public static boolean isLocked(CommonItemStack item) {
        return isItem(item) && item.getCustomData().getValue("locked", false);
    }

    public static boolean isFiniteSpawns(ItemStack item) {
        return isFiniteSpawns(CommonItemStack.of(item));
    }

    public static boolean isFiniteSpawns(CommonItemStack item) {
        return isItem(item) && item.getCustomData().getValue("finite", false);
    }

    public static double getSpeed(ItemStack item) {
        return getSpeed(CommonItemStack.of(item));
    }

    public static double getSpeed(CommonItemStack item) {
        return isItem(item) ? item.getCustomData().getValue("speed", 0.0) : 0.0;
    }

    public static void setName(ItemStack item, String name) {
        setName(CommonItemStack.of(item), name);
    }

    public static void setName(CommonItemStack item, String name) {
        if (isItem(item)) {
            item.updateCustomData(tag -> tag.putValue("name", name));
            updateTitle(item);
        }
    }

    public static String getName(ItemStack item) {
        return getName(CommonItemStack.of(item));
    }

    public static String getName(CommonItemStack item) {
        return isItem(item) ? item.getCustomData().getValue("name", "") : "";
    }

    public static void clear(ItemStack item) {
        clear(CommonItemStack.of(item));
    }

    public static void clear(CommonItemStack item) {
        if (isItem(item)) {
            item.updateCustomData(tag -> tag.remove("config"));
            updateTitle(item);
        }
    }

    public static boolean isEmpty(ItemStack item) {
        return isEmpty(CommonItemStack.of(item));
    }

    public static boolean isEmpty(CommonItemStack item) {
        return isItem(item) && !item.getCustomData().containsKey("config");
    }

    public static void playSoundStore(Player player) {
        PlayerUtil.playSound(player, SoundEffect.PISTON_CONTRACT, 0.4f, 1.5f);
    }

    public static void playSoundSpawn(Player player) {
        PlayerUtil.playSound(player, SoundEffect.PISTON_EXTEND, 0.4f, 1.5f);
    }

    public static void store(ItemStack item, String spawnPattern) {
        store(CommonItemStack.of(item), spawnPattern);
    }

    public static void store(CommonItemStack item, String spawnPattern) {
        if (isItem(item)) {
            item.updateCustomData(tag -> {
                tag.putValue("config", spawnPattern);
                tag.putValue("parsed", true);
            });
            updateTitle(item);
        }
    }

    public static void store(ItemStack item, MinecartGroup group) {
        store(CommonItemStack.of(item), group);
    }

    public static void store(CommonItemStack item, MinecartGroup group) {
        if (group != null) {
            store(item, group.saveConfig());
        }
    }

    public static void store(ItemStack item, ConfigurationNode config) {
        store(CommonItemStack.of(item), config);
    }

    public static void store(CommonItemStack item, ConfigurationNode config) {
        if (isItem(item)) {
            item.updateCustomData(tag -> {
                byte[] compressed = new byte[0];
                try {
                    byte[] uncompressed = config.toString().getBytes("UTF-8");
                    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(uncompressed.length)) {
                        try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
                            zipStream.write(uncompressed);
                        }
                        compressed = byteStream.toByteArray();
                    }
                } catch (Throwable t) {
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Unhandled error saving item details to config", t);
                }
                tag.putValue("config", compressed);
                tag.putValue("parsed", false);
            });
            updateTitle(item);
        }
    }

    /**
     * Gets the spawnable group stored in the configuration of an item.
     * Supports both when the configuration itself, as when the train name is
     * referenced.
     *
     * @param plugin TrainCarts plugin instance
     * @param item Input train chest item
     * @return group configured in the item. Is null if the item is not a train
     *         chest item, or is empty.
     */
    public static SpawnableGroup getSpawnableGroup(TrainCarts plugin, ItemStack item) {
        return getSpawnableGroup(plugin, CommonItemStack.of(item));
    }

    /**
     * Gets the spawnable group stored in the configuration of an item.
     * Supports both when the configuration itself, as when the train name is
     * referenced.
     *
     * @param plugin TrainCarts plugin instance
     * @param item Input train chest item
     * @return group configured in the item. Is null if the item is not a train
     *         chest item, or is empty.
     */
    public static SpawnableGroup getSpawnableGroup(TrainCarts plugin, CommonItemStack item) {
        if (!isItem(item)) {
            return null;
        }
        if (isEmpty(item)) {
            return null;
        }

        // Attempt parsing the Item's configuration into a SpawnableGroup
        SpawnableGroup group;
        if (item.getCustomData().getValue("parsed", false)) {
            group = SpawnableGroup.parse(plugin, item.getCustomData().getValue("config", ""));
        } else {
            BasicConfiguration basicConfig = new BasicConfiguration();
            try {
                byte[] uncompressed = new byte[0];
                byte[] compressed = item.getCustomData().getValue("config", new byte[0]);
                if (compressed != null && compressed.length > 0) {
                    try (ByteArrayInputStream inByteStream = new ByteArrayInputStream(compressed)) {
                        try (GZIPInputStream zipStream = new GZIPInputStream(inByteStream)) {
                            uncompressed = ByteStreams.toByteArray(zipStream);
                        }
                    }
                }
                basicConfig.loadFromStream(new ByteArrayInputStream(uncompressed));
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Unhandled IO error parsing train chest configuration", ex);
                return null;
            }
            group = SpawnableGroup.fromConfig(plugin, basicConfig);
        }
        if (group.getMembers().isEmpty()) {
            return null;
        }

        return group;
    }

    public static SpawnResult spawnAtBlock(SpawnableGroup group, Block clickedBlock, SpawnOptions options) {
        if (group == null) {
            return SpawnResult.FAIL_EMPTY;
        }

        // Check not too long
        if (TCConfig.maxCartsPerTrain >= 0 && group.getMembers().size() > TCConfig.maxCartsPerTrain) {
            return SpawnResult.FAIL_TOO_LONG;
        }

        // Check not reached limit
        if (group.isExceedingSpawnLimit()) {
            return SpawnResult.FAIL_LIMIT_REACHED;
        }

        // Check clicked rails Block is actually a rail
        BlockFace orientation = FaceUtil.getDirection(options.player.getEyeLocation().getDirection());
        RailType clickedRailType = RailType.getType(clickedBlock);
        if (clickedRailType == RailType.NONE) {
            return SpawnResult.FAIL_NORAIL;
        }
        Location spawnLoc = clickedRailType.getSpawnLocation(clickedBlock, orientation);
        if (spawnLoc == null) {
            return SpawnResult.FAIL_NORAIL;
        }

        // Compute movement direction on the clicked rails using rail state
        RailState spawnStartState;
        Vector spawnDirection;
        {
            spawnStartState = new RailState();
            spawnStartState.setRailPiece(RailPiece.create(clickedRailType, clickedBlock));
            spawnStartState.setPosition(Position.fromTo(spawnLoc, spawnLoc));
            spawnStartState.setMotionVector(spawnLoc.getDirection());
            spawnStartState.initEnterDirection();
            spawnStartState.loadRailLogic().getPath().move(spawnStartState, 0.0);
            if (spawnStartState.position().motDot(options.player.getEyeLocation().getDirection()) < 0.0) {
                spawnStartState.position().invertMotion();
            }
            spawnDirection = spawnStartState.motionVector();
        }

        // Extend-behind logic
        {
            Optional<SpawnResult> behindResult = trySpawnExtendBehind(group, spawnStartState, options);
            if (behindResult.isPresent()) {
                return behindResult.get();
            }
        }

        // Check the per-world limits
        if (MinecartGroupStore.isPerWorldSpawnLimitReached(clickedBlock, group.getMembers().size())) {
            return SpawnResult.FAIL_MAX_PER_WORLD;
        }

        // Find locations to spawn at
        SpawnableGroup.SpawnLocationList locationList = group.findSpawnLocations(spawnLoc, spawnDirection, SpawnableGroup.SpawnMode.DEFAULT);

        // Attempt spawning here
        return spawnAtLocations(group, locationList, options);
    }

    public static SpawnResult spawnLookingAt(SpawnableGroup group, Player player, Location eyeLocation, SpawnOptions options) {
        // Clicked in the air. Perform a raytrace hit-test to find
        // possible rails in range of where the player clicked.
        // No need to make this complicated, we only need to get close
        // to the actual rail. Once we find the block within the rail is found,
        // then we can do finetuning to find exactly where on the rail
        // was clicked.
        final double stepSize = 0.05;
        final int steps = (int) (SPAWN_LOOKING_AT_REACH / stepSize);
        final Vector step = eyeLocation.getDirection().multiply(SPAWN_LOOKING_AT_REACH / (double) steps);

        RailState bestState = null;
        {
            Location pos = eyeLocation.clone();
            RailState tmp = new RailState();
            tmp.setRailPiece(RailPiece.createWorldPlaceholder(eyeLocation.getWorld()));
            double bestDistanceSq = (2.0 * 2.0); // at most 2 blocks away from where the player is looking
            for (int n = 0; n < steps; n++) {
                pos.add(step);
                tmp.position().setLocation(pos);

                if (!RailType.loadRailInformation(tmp)) {
                    continue;
                }

                tmp.loadRailLogic().getPath().move(tmp, 0.0);
                double dist_sq = tmp.position().distanceSquared(pos);
                if (dist_sq < bestDistanceSq) {
                    bestDistanceSq = dist_sq;
                    bestState = tmp.clone();
                }
            }
        }

        // No rail found at all?
        if (bestState == null) {
            return SpawnResult.FAIL_NORAIL_LOOK;
        }

        // Reverse direction to align how the player is looking
        if (bestState.position().motDot(step) < 0.0) {
            bestState.position().invertMotion();
        }
        bestState.initEnterDirection();

        // Extend-behind logic
        {
            Optional<SpawnResult> behindResult = trySpawnExtendBehind(group, bestState, options);
            if (behindResult.isPresent()) {
                return behindResult.get();
            }
        }

        // Try spawning
        return spawnAtState(group, bestState, options);
    }

    /**
     * Looks in reverse from a spawn start state for trains already on the track. Returns the spawn result
     * if such a train was found, or empty otherwise.
     *
     * @param group SpawnableGroup
     * @param spawnStartState Initial state on the track pointing forwards of where to spawn
     * @param options SpawnOptions
     * @return SpawnResult if a train was found, or empty otherwise
     */
    private static Optional<SpawnResult> trySpawnExtendBehind(SpawnableGroup group, RailState spawnStartState, SpawnOptions options) {
        // If enabled, try to find another train behind and extend that one
        // We might also find one later during spawn location finding, but there
        // we would not find trains right behind.
        // We assume 2x cart coupler length because we don't know what to expect for a train we find
        // A safe assumption is that it'll use the same coupler length.
        if (options.tryExtendTrains) {
            SpawnableMember lastMember = group.getMembers().get(group.getMembers().size() - 1);
            double searchDistance = AUTOCONNECT_EXTRA_DISTANCE +
                    2.0 * lastMember.getCartCouplerLength() + 0.5 * lastMember.getLength();

            TrainChestExtendableTrain extendableTrain = TrainChestExtendableTrain.find(spawnStartState.cloneAndInvertMotion(), searchDistance, lastMember);
            if (extendableTrain != null) {
                // Handle further spawning with the extendable train state
                // After spawning, link the newly spawned train with this one
                options.tryExtendTrains = false; // Avoid infinite recursion
                options.connectWith = extendableTrain.member;
                options.spawnMode = SpawnableGroup.SpawnMode.DEFAULT_EDGE;
                return Optional.of(spawnAtState(group, extendableTrain.startState, options));
            }
        }

        return Optional.empty();
    }

    /**
     * Performs spawning logic after having calculated the spawn location list. Pre-checks will occur to see
     * if spawning is actually allowed or possible. If options include {@link SpawnOptions#tryExtendTrains} then
     * it will also try to connect with trains already on the track. (But not behind)
     *
     * @param group SpawnableGroup
     * @param locationList List of spawn locations that were previously calculated
     * @param options SpawnOptions
     * @return SpawnResult
     */
    private static SpawnResult spawnAtLocations(SpawnableGroup group, SpawnableGroup.SpawnLocationList locationList, SpawnOptions options) {
        // Not enough spawn positions on the rails?
        if (locationList == null) {
            return SpawnResult.FAIL_RAILTOOSHORT;
        }

        // Prepare chunks
        locationList.loadChunks();

        // Verify spawn area is clear of trains before spawning
        // Do try to connect with trains occupying the track if enabled
        if (options.tryExtendTrains) {
            // Try to extend trains. If this fails, fail with BLOCKED.
            List<SpawnableGroup.OccupiedLocation> occupiedLocations = locationList.getOccupiedLocations();
            if (!occupiedLocations.isEmpty()) {
                if (options.tryExtendTrains) {
                    TrainChestExtendableTrain extendableTrain = TrainChestExtendableTrain.findOccupied(occupiedLocations, group.getMembers().get(0));
                    if (extendableTrain != null) {
                        options.tryExtendTrains = false; // Avoid infinite recursion
                        options.connectWith = extendableTrain.member;
                        options.spawnMode = SpawnableGroup.SpawnMode.REVERSE_EDGE;
                        return spawnAtState(group, extendableTrain.startState.cloneAndInvertMotion(), options);
                    }
                }

                // Fail!
                return SpawnResult.FAIL_BLOCKED;
            }
        } else if (options.connectWith != null) {
            // If connecting with an existing train, ignore that train when checking
            MinecartGroup connectWithGroup = options.connectWith.getGroup();
            for (SpawnableGroup.OccupiedLocation occupied : locationList.getOccupiedLocations()) {
                if (occupied.member.getGroup() != connectWithGroup) {
                    // Occupied by another train (one we are not connecting with)
                    return SpawnResult.FAIL_BLOCKED;
                }
            }
        } else if (locationList.isOccupied()) {
            // Occupied by another train
            return SpawnResult.FAIL_BLOCKED;
        }

        // Not enough spawn positions on the rails?
        if (locationList.locations.size() < group.getMembers().size()) {
            return SpawnResult.FAIL_RAILTOOSHORT;
        }

        // Also check whether a short distance past the last cart being spawned, there's a train
        // Same logic as when handling occupied locations (forwards)
        // We assume 2x cart coupler length because we don't know what to expect for a train we find
        // A safe assumption is that it'll use the same coupler length.
        if (options.tryExtendTrains && locationList.endState != null) {
            SpawnableMember firstMember = group.getMembers().get(0);
            double searchDistance = AUTOCONNECT_EXTRA_DISTANCE +
                    2.0 * firstMember.getCartCouplerLength() + 0.5 * firstMember.getLength();

            TrainChestExtendableTrain extendableTrain = TrainChestExtendableTrain.find(locationList.endState, searchDistance, firstMember);
            if (extendableTrain != null) {
                // Handle further spawning with the extendable train state
                // After spawning, link the newly spawned train with this one
                options.tryExtendTrains = false; // Avoid infinite recursion
                options.connectWith = extendableTrain.member;
                options.spawnMode = SpawnableGroup.SpawnMode.REVERSE_EDGE;
                return spawnAtState(group, extendableTrain.startState.cloneAndInvertMotion(), options);
            }
        }

        // ======================= Pre-checks done ========================

        // Spawn.
        MinecartGroup spawnedGroup = group.spawn(locationList, options.initialSpeed);
        if (!spawnedGroup.isEmpty()) {
            spawnedGroup.getTrainCarts().getPlayer(options.player).editMember(spawnedGroup.tail());
        }

        // Link it up!
        // This could technically fail, but since we've already spawned the train, count it
        // as an absolute win.
        if (options.connectWith != null) {
            MinecartMember<?> with = options.spawnMode.isReverseOrder() ? spawnedGroup.head() : spawnedGroup.tail();
            // Order is important! This way, we keep the group of connectWith around. (g2)
            MinecartGroup.link(with, options.connectWith);
        }

        return SpawnResult.SUCCESS;
    }

    public static SpawnResult spawnAtState(SpawnableGroup group, RailState state, SpawnOptions options) {
        if (group == null) {
            return SpawnResult.FAIL_EMPTY;
        }

        // Check not too long
        {
            int totalLength = group.getMembers().size();
            if (options.connectWith != null) {
                totalLength += options.connectWith.getGroup().size();
            }
            if (TCConfig.maxCartsPerTrain >= 0 && totalLength > TCConfig.maxCartsPerTrain) {
                return SpawnResult.FAIL_TOO_LONG;
            }
        }

        // Check not reached limit
        if (group.isExceedingSpawnLimit()) {
            return SpawnResult.FAIL_LIMIT_REACHED;
        }

        // Check the per-world limits
        if (MinecartGroupStore.isPerWorldSpawnLimitReached(state.positionLocation(), group.getMembers().size())) {
            return SpawnResult.FAIL_MAX_PER_WORLD;
        }

        // Find locations to spawn at
        SpawnableGroup.SpawnLocationList locationList = group.findSpawnLocations(state, options.spawnMode);

        // Attempt spawning here
        return spawnAtLocations(group, locationList, options);
    }

    public static class SpawnOptions {
        /** The player that initiated the spawn with the chest */
        public final Player player;
        /** Initial launch speed after spawning */
        public double initialSpeed = 0.0;
        /** Whether or not to look for existing trains and to spawn additional connected carts */
        public boolean tryExtendTrains = false;
        /** Mode of spawning the train */
        public SpawnableGroup.SpawnMode spawnMode = SpawnableGroup.SpawnMode.DEFAULT;
        /** Connect with another, existing train after spawning */
        public MinecartMember<?> connectWith = null;

        public SpawnOptions(Player player) {
            this.player = player;
        }
    }

    public static enum SpawnResult {
        SUCCESS(Localization.CHEST_SPAWN_SUCCESS),
        FAIL_EMPTY(Localization.CHEST_SPAWN_EMPTY),
        FAIL_NORAIL(Localization.CHEST_SPAWN_NORAIL),
        FAIL_NORAIL_LOOK(Localization.CHEST_SPAWN_NORAIL_LOOK),
        FAIL_RAILTOOSHORT(Localization.CHEST_SPAWN_RAILTOOSHORT),
        FAIL_BLOCKED(Localization.CHEST_SPAWN_BLOCKED),
        FAIL_NO_PERM(Localization.SPAWN_FORBIDDEN_CONTENTS),
        FAIL_LIMIT_REACHED(Localization.CHEST_SPAWN_LIMIT_REACHED),
        FAIL_MAX_PER_WORLD(Localization.SPAWN_MAX_PER_WORLD),
        FAIL_TOO_LONG(Localization.SPAWN_TOO_LONG);

        private final Localization locale;

        private SpawnResult(Localization locale) {
            this.locale = locale;
        }

        public boolean hasMessage() {
            return this.locale != null;
        }

        public Localization getLocale() {
            return this.locale;
        }
    }
}
