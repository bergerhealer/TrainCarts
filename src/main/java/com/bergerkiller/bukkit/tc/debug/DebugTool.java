package com.bergerkiller.bukkit.tc.debug;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

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

    public static void debugRails(Player player, Block railsBlock) {
        Vector direction = player.getEyeLocation().getDirection();
        TrackWalkingPoint walker = new TrackWalkingPoint(railsBlock, FaceUtil.getDirection(direction, false));
        walker.setLoopFilter(true);
        while (walker.move(0.3)) {
            showParticle(walker.position);
        }
    }

    public static void debugRails_old2(Player player, Block railsBlock) {
        Vector direction = player.getEyeLocation().getDirection();
        TrackMap map = new TrackMap(railsBlock, FaceUtil.getDirection(direction, false));
        final double STEP_DISTANCE = 0.1;
        double distance = STEP_DISTANCE;
        Location loc = null;
        while (map.hasNext()) {
            Block block = map.next();
            BlockFace faceDirection = map.getDirection();
            RailType type = map.getRailType();
            RailLogic logic = type.getLogic(null, block, faceDirection);
            RailPath path = logic.getPath();

            // Assign location for the first time (center of rail)
            if (loc == null) {
                loc = type.getSpawnLocation(block, faceDirection);
                showParticle(loc, Particle.BARRIER);
            }

            // Move along the path
            Vector position = new Vector(loc.getX() - block.getX(), loc.getY() - block.getY(), loc.getZ() - block.getZ());
            double moved;
            while ((moved = path.move(position, direction, distance)) != 0.0) {
                //System.out.println("MOVE: " + moved + " / " + distance + " : " + position);
                distance -= moved;
                loc.setX(position.getX() + block.getX());
                loc.setY(position.getY() + block.getY());
                loc.setZ(position.getZ() + block.getZ());
                if (distance <= 0.0001) {
                    distance = STEP_DISTANCE;
                    showParticle(loc);
                }
            }            
        }
    }

    /**
     * Shows particle effects where the minecart positions would be, to show the calculated path.
     * Old version, that does not perform actual movement but instead shows the path segments.
     * 
     * @param player
     * @param railsBlock
     */
    public static void debugRails_old(Player player, Block railsBlock) {
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
        showParticle(loc, Particle.FOOTSTEP);
    }

    private static void showParticle(Location loc, Particle particle) {
        loc.getWorld().spawnParticle(particle, loc, 5);
    }
}
