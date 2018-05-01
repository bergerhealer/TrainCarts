package com.bergerkiller.bukkit.tc.debug;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailLogicState;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.RailInfo;
import com.bergerkiller.bukkit.tc.utils.TrackMap;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

import net.md_5.bungee.api.ChatColor;

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
        // When clicking air, raytrace to find the first rails block in view and walk from the exact position on the path
        if (clickedBlock == null) {
            Location loc = player.getEyeLocation();
            Vector dir = loc.getDirection();
            RailState result = null;
            RailState state = new RailState();
            state.setRailBlock(loc.getBlock());
            state.position().setMotion(dir);

            double minDist = Double.MAX_VALUE;
            for (double d = 0.0; d <= 200.0; d += 0.01) {
                RailPath.Position p = state.position();
                p.posX = loc.getX() + dir.getX() * d;
                p.posY = loc.getY() + dir.getY() * d;
                p.posZ = loc.getZ() + dir.getZ() * d;
                if (RailType.loadRailInformation(state, null)) {
                    RailPath path = state.loadRailLogic(null).getPath();
                    double distSq = path.distanceSquared(state.railPosition());
                    if (distSq < minDist) {
                        minDist = distSq;
                        result = state.clone();
                        path.snap(result.position(), result.railBlock());
                    } else {
                        break;
                    }
                }
            }

            if (result == null) {
                player.sendMessage(ChatColor.RED + "No rails found here");
                return;
            }

            // Go!
            player.sendMessage(ChatColor.YELLOW + "Checking for rails from path [" +
                    MathUtil.round(result.position().posX, 3) + "/" +
                    MathUtil.round(result.position().posY, 3) + "/" +
                    MathUtil.round(result.position().posZ, 3) + "]");
            debugRails(player, result);
            return;
        }

        // From rails block clicked
        player.sendMessage(ChatColor.YELLOW + "Checking for rails from [" +
                clickedBlock.getX() + "/" +
                clickedBlock.getY() + "/" +
                clickedBlock.getZ() + "]");

        if (debugType.equalsIgnoreCase("Rails")) {
            debugRails(player, clickedBlock);
        }
    }

    public static void debugRails(Player player, RailState state) {
        TrackWalkingPoint walker = new TrackWalkingPoint(state);
        walker.setLoopFilter(true);
        int lim = 10000;
        if (player.isSneaking()) {
            while (walker.moveFull() && --lim > 0) {
                showParticle(walker.state.railBlock().getLocation().add(0.5, 0.5, 0.5));
            }
        } else {
            while (walker.move(0.3) && --lim > 0) {
                showParticle(walker.state.positionLocation());
            }
        }
    }

    public static void debugRails(Player player, Block railsBlock) {
        Vector direction = player.getEyeLocation().getDirection();
        TrackWalkingPoint walker = new TrackWalkingPoint(railsBlock, FaceUtil.getDirection(direction, false));
        walker.setLoopFilter(true);
        int lim = 10000;
        if (player.isSneaking()) {
            while (walker.moveFull() && --lim > 0) {
                showParticle(walker.state.railBlock().getLocation().add(0.5, 0.5, 0.5));
            }
        } else {
            while (walker.move(0.3) && --lim > 0) {
                showParticle(walker.state.positionLocation());
            }
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
            RailLogicState state = new RailLogicState(null, block, faceDirection);
            RailLogic logic = type.getLogic(state);
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
            RailLogicState state = new RailLogicState(null, block, direction);
            RailLogic logic = type.getLogic(state);
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
