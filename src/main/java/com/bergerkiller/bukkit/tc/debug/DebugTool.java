package com.bergerkiller.bukkit.tc.debug;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackMap;

/**
 * Manages the different functionalities provided by /train debug [type]
 */
public class DebugTool {

    /**
     * Called when a player interacts with a block using a (stick) debug item
     * 
     * @param player
     * @param clickedBlock
     * @param item
     * @param debugType
     */
    public static void onDebugInteract(Player player, Block clickedBlock, ItemStack item, String debugType) {
        if (debugType.equalsIgnoreCase("Rails")) {
            debugRails(player, clickedBlock);
        }
    }

    /**
     * Shows particle effects where the minecart positions would be, to show the calculated path
     * 
     * @param player
     * @param railsBlock
     */
    public static void debugRails(Player player, Block railsBlock) {
        TrackMap map = new TrackMap(railsBlock, FaceUtil.yawToFace(player.getLocation().getYaw() - 90, false));
        while (map.hasNext()) {
            Block block = map.next();
            BlockFace direction = map.getDirection();
            RailType type = map.getRailType();
            RailLogic logic = type.getLogic(null, block, direction);
            RailPath path = logic.getPath();
            if (!path.isEmpty()) {
                RailPath.Point prev = null;
                for (RailPath.Point p : path.getPoints()) {
                    Location loc = p.getLocation(block);
                    showParticle(loc);

                    if (prev != null) {
                        loc = prev.getLocation(block);
                        loc.add(p.toVector().subtract(prev.toVector()).multiply(0.5));
                        showParticle(loc);
                    }

                    prev = p;
                }
            }
        }
    }

    private static void showParticle(Location loc) {
        loc.getWorld().spawnParticle(Particle.FOOTSTEP, loc, 5);
    }
}
