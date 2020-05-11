package com.bergerkiller.bukkit.tc.debug;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import com.bergerkiller.bukkit.tc.pathfinding.PathRailInfo;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZone;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Manages the different functionalities provided by /train debug [type]
 */
public class DebugTool {
    private static final double PARTICLE_SPACING = 0.2;

    // cyclical array of chat colors used to turn an index into a color
    // there is a clear red/green/blue/cyan/magenta/yellow repeating pattern
    private static final ChatColor[] chatcolor_wheel_values = {
            ChatColor.RED, ChatColor.GREEN, ChatColor.BLUE,
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GOLD,
            ChatColor.DARK_RED, ChatColor.DARK_GREEN, ChatColor.DARK_BLUE,
            ChatColor.DARK_AQUA, ChatColor.DARK_PURPLE, ChatColor.YELLOW,
            ChatColor.BLACK, ChatColor.DARK_GRAY, ChatColor.GRAY, ChatColor.WHITE
    };
    private static final Color[] color_wheel_values = {
            Color.RED, Color.GREEN, Color.BLUE,
            Color.AQUA, Color.FUCHSIA, Color.YELLOW,
            Color.MAROON, Color.OLIVE, Color.NAVY,
            Color.TEAL, Color.PURPLE,Color.YELLOW,
            Color.BLACK, Color.ORANGE, Color.GRAY, Color.WHITE
    };

    /**
     * Shows a box-shaped particle display for all mutex zones for a few seconds
     * 
     * @param player
     */
    public static void showMutexZones(final Player player) {
        Location loc = player.getEyeLocation();
        final List<MutexZone> zones = MutexZoneCache.findNearbyZones(
                loc.getWorld().getUID(),
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
                showFaceParticles(player, color, x1, y1, z1, x2, y2, z2);
            }

            void line(Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
                showLineParticles(player, color, x1, y1, z1, x2, y2, z2);
            }
        }.start(1, PARTICLE_INTERVAL);
    }

    /**
     * Called when a player interacts with a block using a (stick) debug item
     * 
     * @param player
     * @param clickedBlock
     * @param item
     * @param debugType
     */
    public static void onDebugInteract(Player player, Block clickedBlock, ItemStack item, String debugType) {
        TrackWalkingPoint walker = null;
        if (clickedBlock != null) {
            // From rails block clicked
            Vector direction = player.getEyeLocation().getDirection();
            walker = new TrackWalkingPoint(clickedBlock, FaceUtil.getDirection(direction, false));
            walker.setLoopFilter(true);
            if (walker.state.railType() == RailType.NONE) {
                walker = null;
            }
        }
        if (walker == null) {
            // When clicking air, raytrace to find the first rails block in view and walk from the exact position on the path
            Location loc = player.getEyeLocation();
            Vector dir = loc.getDirection();
            RailState result = null;
            RailState state = new RailState();
            state.setRailPiece(RailPiece.createWorldPlaceholder(loc.getWorld()));
            state.position().setMotion(dir);
            state.initEnterDirection();

            double minDist = Double.MAX_VALUE;
            for (double d = 0.0; d <= 200.0; d += 0.01) {
                RailPath.Position p = state.position();
                p.posX = loc.getX() + dir.getX() * d;
                p.posY = loc.getY() + dir.getY() * d;
                p.posZ = loc.getZ() + dir.getZ() * d;
                if (RailType.loadRailInformation(state)) {
                    RailPath path = state.loadRailLogic().getPath();
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
            walker = new TrackWalkingPoint(result);
            walker.setLoopFilter(true);
        }

        // Handle the different debug modes
        if (debugType.equalsIgnoreCase("Rails")) {
            debugRails(player, walker);
        } else if (debugType.equalsIgnoreCase("Destinations")) {
            debugListDestinations(player, walker, null);
        } else if (debugType.startsWith("Destination ")) {
            debugListDestinations(player, walker, debugType.substring(12));
        } else {
            player.sendMessage(ChatColor.RED + "Item has an unknown debug mode: " + debugType);
        }
    }

    public static void debugListDestinations(Player player, TrackWalkingPoint walker, String destinationName) {
        PathProvider provider = TrainCarts.plugin.getPathProvider();
        int lim = 10000;
        Block old_railBlock = null;
        while (--lim > 0) {
            if (destinationName != null) {
                if (!walker.move(0.3)) {
                    break;
                }
                Util.spawnDustParticle(walker.state.positionLocation(), Color.RED);
            } else {
                if (!walker.moveStep(1600.0 - walker.movedTotal)) {
                    break;
                }
                Util.spawnDustParticle(walker.state.positionLocation(), Color.GRAY);
            }

            // Every new rail block
            final Block railBlock;
            if (BlockUtil.equals(walker.state.railBlock(), old_railBlock)) {
                continue;
            } else {
                railBlock = old_railBlock = walker.state.railBlock();
            }

            PathRailInfo info = provider.getRailInfo(walker.state);
            if (info == PathRailInfo.BLOCKED) {
                if (destinationName != null) {
                    player.sendMessage(ChatColor.RED + "Destination " + destinationName + " can not be reached!");
                }
                player.sendMessage(ChatColor.RED + "A blocker sign at " +
                        ChatColor.YELLOW + "x=" + railBlock.getX() + " y=" + railBlock.getY() + " z=" + railBlock.getZ() +
                        ChatColor.RED + " is blocking trains!");
                return;
            } else if (info == PathRailInfo.NODE) {
                debugListRoutesFrom(player, railBlock, destinationName, player.isSneaking(), walker.movedTotal);
                return;
            }
        }

        player.sendMessage(ChatColor.RED + "No path finding node was discovered");
    }

    private static void debugListRoutesFrom(Player player, Block railBlock, String destinationName, boolean reroute, double initialDistance) {
        PathProvider provider = TrainCarts.plugin.getPathProvider();

        // Find the node at this rails block
        PathNode node = provider.getWorld(railBlock.getWorld()).getNodeAtRail(railBlock);
        if (reroute && node != null) {
            reroute = false;
            player.sendMessage(ChatColor.YELLOW + "Rerouting the node network from " + node.getDisplayName());
            node.rerouteConnected();
        }

        // Schedule a task that waits until processing finishes, if busy
        if (provider.isProcessing()) {
            final boolean f_reroute = reroute;
            Localization.PATHING_BUSY.message(player);
            new Task(TrainCarts.plugin) {
                @Override
                public void run() {
                    if (!provider.isProcessing()) {
                        stop();
                        debugListRoutesFrom(player, railBlock, destinationName, f_reroute, initialDistance);
                    }
                }
            }.start(1, 1);
            return;
        }

        if (node == null) {
            player.sendMessage(ChatColor.RED + "No node was detected");
            return;
        }

        if (destinationName != null) {
            PathNode destination = node.getWorld().getNodeByName(destinationName);
            if (destination == null) {
                player.sendMessage(ChatColor.RED + "Destination " + destinationName + " does not exist. Try rerouting");
                return;
            }
            debugShowRouteFromTo(player, node, railBlock, destination, initialDistance);
        } else {
            debugListAllRoutes(player, node, railBlock);
        }
    }

    private static void debugShowRouteFromTo(Player player, PathNode node, Block railBlock, PathNode destination, double initialDistance) {
        if (node == destination) {
            player.sendMessage(ChatColor.GREEN + "Route to " + ChatColor.YELLOW + destination.getDisplayName() +
                    ChatColor.GREEN + " was found with a distance of " + ChatColor.YELLOW +
                    MathUtil.round(initialDistance, 1) + ChatColor.GREEN + " blocks");
            return;
        }
        PathConnection[] route = node.findRoute(destination);
        if (route.length == 0) {
            player.sendMessage(ChatColor.RED + "Destination " + destination.getDisplayName() + " could not be reached!");
            return;
        }

        // Compute total distance
        double totalDistance = initialDistance;
        for (PathConnection connection : route) {
            totalDistance += connection.distance;
        }

        // Take this junction and show the path taken up until the next node is reached
        double maxDistance = 1600.0;
        Color[] colors = new Color[] {Color.BLUE, Color.GREEN, Color.RED};
        int color_idx = 0;
        for (PathConnection connection : route) {
            TrackWalkingPoint walker = takeJunction(railBlock, connection);
            if (walker == null) {
                player.sendMessage(ChatColor.RED + "Path broke at rail " +
                        "x=" + railBlock.getX() + " y=" + railBlock.getY() + " z=" + railBlock.getZ());
                return;
            }

            // Next rail block
            railBlock = connection.destination.location.getBlock();
            Color color = colors[color_idx++ % colors.length];
            int lim = 10000;
            do {
                if (--lim <= 0 || walker.movedTotal > maxDistance) {
                    Location at = walker.state.positionLocation();
                    player.sendMessage(ChatColor.RED + "Reached path maximum distance at " +
                            "x=" + at.getBlockX() + " y=" + at.getBlockY() + " z=" + at.getBlockZ());
                    return;
                }
                if (!walker.move(0.3)) {
                    Location at = walker.state.positionLocation();
                    player.sendMessage(ChatColor.RED + "Path broke at position " +
                            "x=" + at.getBlockX() + " y=" + at.getBlockY() + " z=" + at.getBlockZ());
                    return;
                }
                Util.spawnDustParticle(walker.state.positionLocation(), color);
            } while (!BlockUtil.equals(railBlock, walker.state.railBlock()));
            maxDistance -= walker.movedTotal;
            if (maxDistance <= 0.0) {
                break;
            }
        }
        player.sendMessage(ChatColor.GREEN + "Route to " + ChatColor.YELLOW + destination.getDisplayName() +
                ChatColor.GREEN + " was found with a distance of " + ChatColor.YELLOW +
                MathUtil.round(totalDistance, 1) + ChatColor.GREEN + " blocks");
    }

    private static void debugListAllRoutes(Player player, PathNode node, Block railBlock) {
        MessageBuilder message = new MessageBuilder();
        message.gray("Destinations from ").white(node.getDisplayName()).gray(":").newLine();

        int color_idx = 0;
        for (Map.Entry<PathConnection, List<PathConnection>> entry : node.getDeepNeighbours().entrySet()) {
            PathConnection connection = entry.getKey();
            Collection<PathConnection> destinations = entry.getValue();

            // Remove connections that are not destination nodes (switchers only)
            Iterator<PathConnection> iter = destinations.iterator();
            while (iter.hasNext()) {
                if (iter.next().destination.containsOnlySwitcher()) {
                    iter.remove();
                }
            }

            // Skip if empty
            if (destinations.isEmpty()) {
                continue;
            }

            // Color used to display it
            ChatColor chatcolor = chatcolor_wheel_values[(color_idx % color_wheel_values.length)];
            Color color = color_wheel_values[(color_idx % color_wheel_values.length)];
            color_idx++;

            // Walk a short distance and spawn colored particles to denote this junction
            TrackWalkingPoint walker = takeJunction(railBlock, connection);
            if (walker != null) {
                int lim = 100;
                while (walker.move(0.3) && --lim > 0 && walker.movedTotal < 3.0) {
                    Util.spawnDustParticle(walker.state.positionLocation(), color);
                }
            }

            // List closest 5 (or less) destinations that can be reached from here
            message.append(chatcolor, "- ");
            message.setIndent(2);
            message.setSeparator(ChatColor.GRAY, " / ");
            int limit = 5;
            for (PathConnection destination : destinations) {
                message.append(chatcolor,
                        "[", MathUtil.round(destination.distance, 1), "] ",
                        destination.destination.getDisplayName());
                if (--limit == 0) {
                    message.append(chatcolor, "...");
                    break;
                }
            }
            message.clearSeparator();
            message.setIndent(0);
            message.newLine();
        }
        message.send(player);
    }

    private static TrackWalkingPoint takeJunction(Block railBlock, PathConnection connection) {
        // Find the RailJunction by name and load it into a RailState
        RailState state = null;
        for (RailType type : RailType.values()) {
            if (type.isRail(railBlock)) {
                List<RailJunction> junctions = type.getJunctions(railBlock);
                RailJunction picked = null;
                for (RailJunction junction : junctions) {
                    if (connection.junctionName.equals(junction.name())) {
                        picked = junction;
                        break;
                    }
                }
                if (picked != null) {
                    state = type.takeJunction(railBlock, picked);
                }
                break;
            }
        }
        if (state == null) {
            return null; // not found
        }

        // Walk a short distance and spawn colored particles to denote this junction
        TrackWalkingPoint walker = new TrackWalkingPoint(state);
        walker.setLoopFilter(true);
        return walker;
    }

    public static void debugRails(Player player, TrackWalkingPoint walker) {
        player.sendMessage(ChatColor.YELLOW + "Checking for rails from path [" +
                MathUtil.round(walker.state.position().posX, 3) + "/" +
                MathUtil.round(walker.state.position().posY, 3) + "/" +
                MathUtil.round(walker.state.position().posZ, 3) + "]");

        int lim = 10000;
        if (player.isSneaking()) {
            if (walker.moveFull()) {
                // Show the exact path of the first section
                for (RailPath.Point point : walker.currentRailPath.getPoints()) {
                    Util.spawnDustParticle(point.getLocation(walker.state.railBlock()), 0.1, 0.1, 1.0);
                }

                // Show the rail blocks
                do {
                    showParticle(walker.state.railBlock().getLocation().add(0.5, 0.5, 0.5));
                } while (walker.moveFull() && --lim > 0);
            }
        } else {
            RailPath oldPath = null;
            int segmentCounter = 0;
            double[][] colors = new double[][] {{1.0, 0.1, 0.1}, {0.9, 0.1, 0.1}, {0.8, 0.1, 0.1}};
            while (walker.move(0.3) && --lim > 0) {
                if (oldPath != walker.currentRailPath) {
                    oldPath = walker.currentRailPath;
                    segmentCounter++;
                }
                Location loc = walker.state.positionLocation();
                double[] color = colors[segmentCounter % colors.length];
                Util.spawnDustParticle(loc, color[0], color[1], color[2]);
            }
        }
    }

    private static void showParticle(Location loc) {
        Util.spawnDustParticle(loc, 1.0, 0.1, 0.1);
    }

    public static void showFaceParticles(Player viewer, Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        showLineParticles(viewer, color, x1, y1, z1, x2, y1, z1);
        showLineParticles(viewer, color, x1, y1, z1, x1, y2, z1);
        showLineParticles(viewer, color, x1, y1, z1, x1, y1, z2);
        showLineParticles(viewer, color, x1, y2, z2, x2, y2, z2);
        showLineParticles(viewer, color, x2, y1, z2, x2, y2, z2);
        showLineParticles(viewer, color, x2, y2, z1, x2, y2, z2);
    }

    public static void showLineParticles(Player viewer, Color color, Vector p1, Vector p2) {
        showLineParticles(viewer, color, p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ());
    }

    public static void showLineParticles(Player viewer, Color color, double x1, double y1, double z1, double x2, double y2, double z2) {
        double dist = MathUtil.distance(x1, y1, z1, x2, y2, z2);
        if (dist < 1e-8) {
            return;
        }
        int n = MathUtil.ceil(dist / PARTICLE_SPACING);
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        Vector position = new Vector();
        for (int i = 0; i < n; i++) {
            double t = (double) i / (double) (n-1);
            position.setX(x1 + dx * t);
            position.setY(y1 + dy * t);
            position.setZ(z1 + dz * t);
            PlayerUtil.spawnDustParticles(viewer, position, color);
        }
    }
}
