package com.bergerkiller.bukkit.tc.debug.types;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.debug.DebugToolUtil;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathProvider;
import com.bergerkiller.bukkit.tc.pathfinding.PathRailInfo;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

public class DebugToolTypeListDestinations extends DebugToolTrackWalkerType {
    private final String destination;

    public DebugToolTypeListDestinations() {
        this.destination = null;
    }

    public DebugToolTypeListDestinations(String destination) {
        this.destination = destination;
    }

    @Override
    public String getIdentifier() {
        if (this.destination != null) {
            return "Destination " + this.destination;
        } else {
            return "Destinations";
        }
    }

    @Override
    public String getTitle() {
        if (this.destination != null) {
            return "Pathfinding destination searcher (routes to " + this.destination + ")";
        } else {
            return "Pathfinding destination searcher";
        }
    }

    @Override
    public String getDescription() {
        if (this.destination != null) {
            return "Identifies the route to reach destination '" + this.destination + "'";
        } else {
            return "Identifies all the destination routes reachable from the rails clicked";
        }
    }

    @Override
    public String getInstructions() {
        if (this.destination != null) {
            return "Right-click rails to see whether and how a train would travel to " + this.destination + ".";
        } else {
            return "Right-click rails to see what destinations can be reached from there.";
        }
    }

    /**
     * Starts walking along the rails of a track walking point. Once it hits the first path finding node,
     * which can be a switcher or destination sign, it will resume further searching using path finding.
     * If a destination name is specified, it will attempt to route towards this destination. If null,
     * it will list all destinations encountered.
     * 
     * @param player Player that is performing the search
     * @param walker Walking point from which to start searching for nodes
     */
    @Override
    public void onBlockInteract(Player player, TrackWalkingPoint walker, ItemStack item, boolean isRightClick) {
        // TODO Auto-generated method stub
        PathProvider provider = TrainCarts.plugin.getPathProvider();
        Block old_railBlock = null;
        double stopDistance = walker.movedTotal + 2000.0; // 2000 blocks at a time
        int lim = 10000;
        while (true) {
            if (--lim == 0 || walker.movedTotal >= stopDistance) {
                // Reached the limit for the current tick. Resume next tick.
                CommonUtil.getPluginExecutor(TrainCarts.plugin).execute(() -> {
                    onBlockInteract(player, walker, item, isRightClick);
                });
                break;
            } else if (this.destination != null) {
                if (!walker.move(0.3)) {
                    DebugToolUtil.showEndOfTheRail(player, walker, 0.0);
                    break;
                }
                Util.spawnDustParticle(walker.state.positionLocation(), Color.RED);
            } else {
                if (!walker.moveFull()) {
                    DebugToolUtil.showEndOfTheRail(player, walker, 0.0);
                    break;
                }
                Util.spawnDustParticle(walker.state.positionLocation(), Color.GRAY);
            }

            // Every new rail block
            if (BlockUtil.equals(walker.state.railBlock(), old_railBlock)) {
                continue;
            } else {
                old_railBlock = walker.state.railBlock();
            }

            PathRailInfo info = provider.getRailInfo(walker.state);
            if (info == PathRailInfo.BLOCKED) {
                if (this.destination != null) {
                    player.sendMessage(ChatColor.RED + "Destination " + this.destination + " can not be reached!");
                }
                player.sendMessage(ChatColor.RED + "A blocker sign at " +
                        ChatColor.YELLOW + DebugToolUtil.coordinates(walker.state.position()) +
                        ChatColor.RED + " is blocking trains!");
                break;
            } else if (info == PathRailInfo.NODE) {
                debugListRoutesFrom(player, walker.state, this.destination, player.isSneaking(), walker.movedTotal);
                break;
            }
        }
    }

    private static void debugListRoutesFrom(Player player, RailState state, String destinationName, boolean reroute, double initialDistance) {
        PathProvider provider = TrainCarts.plugin.getPathProvider();

        // Check early
        if (!state.railLookup().isValid()) {
            player.sendMessage(ChatColor.RED + "Failed to list destinations - World is no longer loaded!");
            return;
        }

        // Find the node at this rails block
        PathNode node = provider.getWorld(state.railWorld()).getNodeAtRail(state.railBlock());
        if (node == null) {
            provider.discoverFromRail(new BlockLocation(state.railBlock()));
            player.sendMessage(ChatColor.YELLOW + "Discovering paths from " + DebugToolUtil.coordinates(state.position()));
        } else if (reroute) {
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
                        debugListRoutesFrom(player, state, destinationName, f_reroute, initialDistance);
                    }
                }
            }.start(1, 1);
            return;
        }

        // Might be null even after path finding takes care of this
        if (node == null) {
            player.sendMessage(ChatColor.RED + "[Error] Path finding node is missing at " + DebugToolUtil.coordinates(state.position()) +
                    " after " + ((int) initialDistance) + " blocks");
            return;
        }

        if (destinationName != null) {
            PathNode destination = node.getWorld().getNodeByName(destinationName);
            if (destination == null) {
                player.sendMessage(ChatColor.RED + "Destination " + destinationName + " does not exist. Try rerouting (sneak-click)");
                return;
            }
            debugShowRouteFromTo(player, node, state.railBlock(), destination, initialDistance);
        } else {
            debugListAllRoutes(player, node, state.railBlock(), initialDistance);
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
            player.sendMessage(ChatColor.RED + "Destination '" + destination.getDisplayName() + "' could not be reached from " +
                    DebugToolUtil.coordinates(railBlock.getX(), railBlock.getY(), railBlock.getZ()));
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
        int lim = 10000;
        for (PathConnection connection : route) {
            TrackWalkingPoint walker = takeJunction(railBlock, connection);
            if (walker == null) {
                player.sendMessage(ChatColor.RED + "Path broke at rail " +
                        DebugToolUtil.coordinates(railBlock.getX(), railBlock.getY(), railBlock.getZ()));
                return;
            }

            // Next rail block
            railBlock = connection.destination.location.getBlock();
            Color color = colors[color_idx++ % colors.length];
            do {
                if (--lim <= 0 || walker.movedTotal > maxDistance) {
                    break;
                }
                if (!walker.move(0.3)) {
                    DebugToolUtil.showEndOfTheRail(player, walker, initialDistance);
                    return;
                }
                Util.spawnDustParticle(walker.state.positionLocation(), color);
            } while (!BlockUtil.equals(railBlock, walker.state.railBlock()));

            maxDistance -= walker.movedTotal;
            if (lim <= 0 || maxDistance <= 0.0) {
                break;
            }
        }
        player.sendMessage(ChatColor.GREEN + "Route to " + ChatColor.YELLOW + destination.getDisplayName() +
                ChatColor.GREEN + " was found with a distance of " + ChatColor.YELLOW +
                MathUtil.round(totalDistance, 1) + ChatColor.GREEN + " blocks");
    }

    private static void debugListAllRoutes(Player player, PathNode node, Block railBlock, double initialDistance) {
        MessageBuilder message = new MessageBuilder();
        message.gray("Node ").white(DebugToolUtil.coordinates(node.location.x, node.location.y, node.location.z));
        message.gray(" reached after ").white(MathUtil.round(initialDistance, 1)).gray(" blocks").newLine();
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
            ChatColor chatcolor = DebugToolUtil.getWheelChatColor(color_idx);
            Color color = DebugToolUtil.getWheelColor(color_idx);
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
}
