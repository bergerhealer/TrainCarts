package com.bergerkiller.bukkit.tc.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.components.RailPath.Position;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.google.common.io.ByteStreams;

public class StoredTrainItemUtil {
    private static final String IDENTIFIER = "Traincarts.chest";
    private static final String TITLE = "Traincarts Chest";

    public static ItemStack createItem() {
        ItemStack item = ItemUtil.createItem(Material.ENDER_CHEST, 1);
        item.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
        CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
        tag.putValue("plugin", TrainCarts.plugin.getName());
        tag.putValue("identifier", IDENTIFIER);
        tag.putValue("name", "");
        tag.putValue("parsed", false);
        tag.putValue("locked", false);
        tag.putValue("HideFlags", 1);
        updateTitle(item);
        return item;
    }

    private static void updateTitle(ItemStack item) {
        String displayTitle = TITLE;
        String name = getName(item);
        if (name.isEmpty() && !isEmpty(item) && ItemUtil.getMetaTag(item).getValue("parsed", false)) {
            name = ItemUtil.getMetaTag(item).getValue("config", "");
        }
        if (!name.isEmpty()) {
            displayTitle += " (" + name + ")";
        }
        if (isEmpty(item)) {
            displayTitle += " (Empty)";
        }
        if (isLocked(item)) {
            displayTitle += " (Locked)";
        }
        ItemUtil.setDisplayName(item, displayTitle);
    }

    public static boolean isItem(ItemStack item) {
        if (!ItemUtil.isEmpty(item)) {
            CommonTagCompound tag = ItemUtil.getMetaTag(item, false);
            if (tag != null) {
                return IDENTIFIER.equals(tag.getValue("identifier", ""));
            }
        }
        return false;
    }

    public static void setLocked(ItemStack item, boolean locked) {
        if (isItem(item)) {
            ItemUtil.getMetaTag(item, true).putValue("locked", locked);
            updateTitle(item);
        }
    }

    public static boolean isLocked(ItemStack item) {
        return isItem(item) && ItemUtil.getMetaTag(item).getValue("locked", false);
    }

    public static void setName(ItemStack item, String name) {
        if (isItem(item)) {
            ItemUtil.getMetaTag(item, true).putValue("name", name);
            updateTitle(item);
        }
    }

    public static String getName(ItemStack item) {
        return isItem(item) ? ItemUtil.getMetaTag(item).getValue("name", "") : "";
    }

    public static void clear(ItemStack item) {
        if (isItem(item)) {
            ItemUtil.getMetaTag(item, true).remove("config");
            updateTitle(item);
        }
    }

    public static boolean isEmpty(ItemStack item) {
        return isItem(item) && !ItemUtil.getMetaTag(item).containsKey("config");
    }

    public static void playSoundStore(Player player) {
        PlayerUtil.playSound(player, SoundEffect.PISTON_CONTRACT, 0.4f, 1.5f);
    }

    public static void playSoundSpawn(Player player) {
        PlayerUtil.playSound(player, SoundEffect.PISTON_EXTEND, 0.4f, 1.5f);
    }

    public static void store(ItemStack item, String spawnPattern) {
        if (isItem(item)) {
            CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
            tag.putValue("config", spawnPattern);
            tag.putValue("parsed", true);
            updateTitle(item);
        }
    }

    public static void store(ItemStack item, MinecartGroup group) {
        if (group != null) {
            store(item, group.saveConfig());            
        }
    }

    public static void store(ItemStack item, ConfigurationNode config) {
        if (isItem(item)) {
            CommonTagCompound tag = ItemUtil.getMetaTag(item, true);

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
                t.printStackTrace();
            }
            tag.putValue("config", compressed);
            tag.putValue("parsed", false);
            updateTitle(item);
        }
    }

    public static SpawnResult spawn(ItemStack item, Player player, Block clickedBlock) {
        if (!isItem(item)) {
            return SpawnResult.FAIL_EMPTY;
        }
        if (isEmpty(item)) {
            return SpawnResult.FAIL_EMPTY;
        }

        // Check clicked rails Block is actually a rail
        BlockFace orientation = FaceUtil.getDirection(player.getEyeLocation().getDirection());
        RailType clickedRailType = RailType.getType(clickedBlock);
        if (clickedRailType == RailType.NONE) {
            return SpawnResult.FAIL_NORAIL;
        }
        Location spawnLoc = clickedRailType.getSpawnLocation(clickedBlock, orientation);
        if (spawnLoc == null) {
            return SpawnResult.FAIL_NORAIL;
        }

        // Compute movement direction on the clicked rails using rail state
        Vector spawnDirection;
        {
            RailState state = new RailState();
            state.setRailPiece(RailPiece.create(clickedRailType, clickedBlock));
            state.setPosition(Position.fromTo(spawnLoc, spawnLoc));
            state.setMotionVector(spawnLoc.getDirection());
            state.initEnterDirection();
            state.loadRailLogic().getPath().move(state, 0.0);
            spawnDirection = state.position().getMotion();
            if (state.position().motDot(player.getEyeLocation().getDirection()) < 0.0) {
                spawnDirection.multiply(-1.0);
            }
        }

        // Attempt parsing the Item's configuration into a SpawnableGroup
        SpawnableGroup group;
        if (ItemUtil.getMetaTag(item).getValue("parsed", false)) {
            group = SpawnableGroup.parse(ItemUtil.getMetaTag(item).getValue("config", ""));
        } else {
            BasicConfiguration basicConfig = new BasicConfiguration();
            try {
                byte[] uncompressed = new byte[0];
                byte[] compressed = ItemUtil.getMetaTag(item).getValue("config", new byte[0]);
                if (compressed != null && compressed.length > 0) {
                    try (ByteArrayInputStream inByteStream = new ByteArrayInputStream(compressed)) {
                        try (GZIPInputStream zipStream = new GZIPInputStream(inByteStream)) {
                            uncompressed = ByteStreams.toByteArray(zipStream);
                        }
                    }
                }
                basicConfig.loadFromStream(new ByteArrayInputStream(uncompressed));
            } catch (IOException ex) {
                ex.printStackTrace();
                return SpawnResult.FAIL_EMPTY;
            }
            group = SpawnableGroup.fromConfig(basicConfig);
        }
        if (group.getMembers().isEmpty()) {
            return SpawnResult.FAIL_EMPTY;
        }

        // Find locations to spawn at
        List<Location> locations = SignActionSpawn.getSpawnPositions(spawnLoc, true, spawnDirection, group.getMembers());
        if (locations.isEmpty() && group.getMembers().size() == 1) {
            // Spawn at the exact location
            locations.add(spawnLoc);
        }
        if (locations.size() < group.getMembers().size()) {
            return SpawnResult.FAIL_BLOCKED;
        }

        // Prepare chunks
        for (Location loc : locations) {
            WorldUtil.loadChunks(loc, 2);
        }

        // Verify spawn area is clear of trains before spawning
        for (Location loc : locations) {
            if (MinecartMemberStore.getAt(loc) != null) {
                return SpawnResult.FAIL_BLOCKED; // Occupied
            }
        }

        // Spawn.
        MinecartGroup spawnedGroup = MinecartGroupStore.spawn(group, locations);
        if (spawnedGroup != null && !spawnedGroup.isEmpty()) {
            CartProperties.setEditing(player, spawnedGroup.tail().getProperties());
        }
        return SpawnResult.SUCCESS;
    }

    public static enum SpawnResult {
        SUCCESS(Localization.CHEST_SPAWN_SUCCESS),
        FAIL_EMPTY(Localization.CHEST_SPAWN_EMPTY),
        FAIL_NORAIL(Localization.CHEST_SPAWN_NORAIL),
        FAIL_BLOCKED(Localization.CHEST_SPAWN_BLOCKED);

        private final Localization locale;

        private SpawnResult(Localization locale) {
            this.locale = locale;
        }

        public Localization getLocale() {
            return this.locale;
        }
    }
}
