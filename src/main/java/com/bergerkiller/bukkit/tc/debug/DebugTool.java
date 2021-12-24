package com.bergerkiller.bukkit.tc.debug;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;

/**
 * Manages the different functionalities provided by /train debug [type]
 */
public class DebugTool {

    /**
     * Shows a box-shaped particle display for all mutex zones for a few seconds
     * 
     * @param player
     */
    public static void showMutexZones(final Player player) {
        Location loc = player.getEyeLocation();
        final List<MutexZone> zones = MutexZoneCache.findNearbyZones(
                OfflineWorld.of(loc.getWorld()),
                new IntVector3(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()),
                32);

        if (zones.isEmpty()) {
            return;
        }

        final int PARTICLE_DURATION = 100;
        final int PARTICLE_INTERVAL = 4;
        new Task(TrainCarts.plugin) {
            int life = PARTICLE_DURATION / PARTICLE_INTERVAL;

            @Override
            public void run() {
                Random r = new Random();
                for (MutexZone zone : zones) {
                    if (zone.slot.isAnonymous()) {
                        r.setSeed(MathUtil.longHashToLong(zone.start.hashCode(), zone.end.hashCode()));
                    } else {
                        r.setSeed(zone.slot.getName().hashCode());
                    }
                    java.awt.Color awt_color = java.awt.Color.getHSBColor(r.nextFloat(), 1.0f, 1.0f);
                    Color color = Color.fromRGB(awt_color.getRed(), awt_color.getGreen(), awt_color.getBlue());
                    double x1 = zone.start.x;
                    double y1 = zone.start.y;
                    double z1 = zone.start.z;
                    double x2 = zone.end.x + 1.0;
                    double y2 = zone.end.y + 1.0;
                    double z2 = zone.end.z + 1.0;
                    cube(color, x1, y1, z1, x2, y2, z2);
                }

                if (--life == 0) {
                    this.stop();
                }
            }

            void cube(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
                face(color, x1, y1, z1, x2, y1, z2);
                face(color, x1, y2, z1, x2, y2, z2);
                line(color, x1, y1, z1, x1, y2, z1);
                line(color, x2, y1, z1, x2, y2, z1);
                line(color, x1, y1, z2, x1, y2, z2);
                line(color, x2, y1, z2, x2, y2, z2);
            }

            void face(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
                DebugToolUtil.showFaceParticles(player, color, x1, y1, z1, x2, y2, z2);
            }

            void line(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
                DebugToolUtil.showLineParticles(player, color, x1, y1, z1, x2, y2, z2);
            }
        }.start(1, PARTICLE_INTERVAL);
    }

    public static boolean updateToolItem(Player player, ItemStack item) {
        ItemStack inMainHand = HumanHand.getItemInMainHand(player);
        if (inMainHand != null) {
            CommonTagCompound tag = ItemUtil.getMetaTag(inMainHand, false);
            if (tag != null && tag.containsKey("TrainCartsDebug")) {
                HumanHand.setItemInMainHand(player, item);
                return true;
            }
        }
        return false;
    }

    /**
     * Called when a player interacts with a block using a (stick) debug item
     * 
     * @param player
     * @param clickedBlock
     * @param item
     * @param isRightClick Whether this is a right click interaction (true) or left-click break (false)
     * @return true if handled, false if not and should interact like normal
     */
    public static boolean onDebugInteract(Player player, Block clickedBlock, ItemStack item, boolean isRightClick) {
        if (item == null) {
            return false;
        }

        CommonTagCompound tag = ItemUtil.getMetaTag(item);
        if (tag == null) {
            return false;
        }

        String debugType = tag.getValue("TrainCartsDebug", String.class);
        if (debugType == null) {
            return false;
        }

        if (!Permission.DEBUG_COMMAND_DEBUG.has(player)) {
            player.sendMessage(ChatColor.RED + "No permission to use this item!");
            return true;
        }

        Optional<DebugToolType> match = DebugToolTypeRegistry.match(debugType);
        if (!match.isPresent()) {
            player.sendMessage(ChatColor.RED + "Item has an unknown debug mode: " + debugType);
            return true;
        }

        // Left click check
        if (!isRightClick && !match.get().handlesLeftClick()) {
            return false;
        }

        match.get().onBlockInteract(player, clickedBlock, item, isRightClick);
        return true;
    }
}
