package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import org.bukkit.block.Block;

import java.util.*;

public class PathNode {
    private final PathWorld world;
    public final BlockLocation location;
    private final Set<String> names = new HashSet<>();
    private final List<PathConnection> neighbors = new ArrayList<>(3);
    public int index;
    private double lastDistance;
    private PathConnection lastTaken;
    private boolean isRailSwitchable;

    protected PathNode(final PathWorld world, final BlockLocation location) {
        this.world = world;
        this.location = location;
        this.isRailSwitchable = false;
    }

    public static void clearAll() {
        for (PathWorld world : TrainCarts.plugin.getPathProvider().getWorlds()) {
            world.clearAll();
        }
    }

    /**
     * Re-calculates all path nodes from scratch
     */
    public static void reroute() {
        TrainCarts.plugin.getPathProvider().reroute();
    }

    public static PathNode get(BlockLocation railLocation) {
        return TrainCarts.plugin.getPathProvider().getWorld(railLocation.world).getNodeAtRail(railLocation);
    }

    public static PathNode get(Block block) {
        if (block == null) {
            return null;
        } else {
            return TrainCarts.plugin.getPathProvider().getWorld(block.getWorld()).getNodeAtRail(block);
        }
    }

    public static PathNode remove(Block railsblock) {
        if (railsblock == null) return null;
        return TrainCarts.plugin.getPathProvider().getWorld(railsblock.getWorld()).removeAtRail(railsblock);
    }

    public static PathNode getOrCreate(SignActionEvent event) {
        if (event.isType("destination")) {
            //get this destination name
            PathNode node = getOrCreate(event.getRails());
            node.addName(event.getLine(2));
            return node;
        } else {
            //check if the current train or cart has a destination
            if (event.isCartSign()) {
                if (!event.hasMember() || !event.getMember().getProperties().hasDestination()) {
                    return null;
                }
            } else if (event.isTrainSign()) {
                if (!event.hasGroup() || !event.getGroup().getProperties().hasDestination()) {
                    return null;
                }
            }
            //create from location
            PathNode node = getOrCreate(event.getRails());
            node.addSwitcher();
            return node;
        }
    }

    public static PathNode getOrCreate(Block location) {
        return getOrCreate(new BlockLocation(location));
    }

    public static PathNode getOrCreate(final BlockLocation location) {
        return TrainCarts.plugin.getPathProvider().getWorld(location.world).getOrCreateAtRail(location);
    }

    private static double getDistanceTo(PathConnection from, PathConnection conn, double currentDistance, double maxDistance, PathNode destination) {
        final PathNode node = conn.destination;
        currentDistance += conn.distance;
        // Consider taking turns as one distance longer
        // This avoids the excessive use of turns in 2-way 'X' intersections
        if (destination == node) {
            return currentDistance;
        }
        // Initial distance check before continuing
        if (node.lastDistance < currentDistance || currentDistance > maxDistance) {
            return Integer.MAX_VALUE;
        }
        node.lastDistance = currentDistance;
        // Check all neighbors and obtain the lowest distance recursively
        double distance;
        for (PathConnection connection : node.neighbors) {
            distance = getDistanceTo(conn, connection, currentDistance, maxDistance, destination);
            if (maxDistance > distance) {
                maxDistance = distance;
                node.lastTaken = connection;
            }
        }
        return maxDistance;
    }

    public static void deinit() {
        clearAll();
    }

    /**
     * Tries to find a connection from this node to the node specified
     *
     * @param destination name of the node to find
     * @return A connection, or null if none could be found
     */
    public PathConnection findConnection(String destination) {
        PathNode node = world.getNodeByName(destination);
        return node == null ? null : findConnection(node);
    }

    /**
     * Tries to find a connection from this node to the node specified
     *
     * @param destination node to find
     * @return A connection, or null if none could be found
     */
    public PathConnection findConnection(PathNode destination) {
        for (PathNode node : world.getNodes()) {
            node.lastDistance = Integer.MAX_VALUE;
            node.lastTaken = null;
        }
        double maxDistance = Integer.MAX_VALUE;
        double distance;
        final PathConnection from = new PathConnection(this, 0, "");
        for (PathConnection connection : this.neighbors) {
            distance = getDistanceTo(from, connection, 0.0, maxDistance, destination);
            if (maxDistance > distance) {
                maxDistance = distance;
                this.lastTaken = connection;
            }
        }
        if (this.lastTaken == null) {
            return null;
        } else {
            return new PathConnection(destination, maxDistance, this.lastTaken.junctionName);
        }
    }

    /**
     * Tries to find the exact route (all nodes) to reach a destination from this node
     *
     * @param destination to reach
     * @return the route taken, or an empty array if none could be found
     */
    public PathNode[] findRoute(PathNode destination) {
        if (findConnection(destination) == null) {
            return new PathNode[0];
        }
        List<PathNode> route = new ArrayList<>();
        route.add(this);
        PathConnection conn = this.lastTaken;
        while (conn != null) {
            route.add(conn.destination);
            conn = conn.destination.lastTaken;
        }
        return route.toArray(new PathNode[0]);
    }

    /**
     * Adds a neighbour connection to this node
     *
     * @param to        the node to make a connection with
     * @param distance  of the connection
     * @param junctionName of the connection
     * @return The connection that was made
     */
    public PathConnection addNeighbour(final PathNode to, final double distance, final String junctionName) {
        PathConnection conn;
        Iterator<PathConnection> iter = this.neighbors.iterator();
        while (iter.hasNext()) {
            conn = iter.next();
            if (conn.destination == to) {
                if (conn.distance <= distance) {
                    // Lower distance is contained - all done
                    return conn;
                } else {
                    // Higher distance is contained - remove old element
                    iter.remove();
                    break;
                }
            }
        }
        // Add a new one
        conn = new PathConnection(to, distance, junctionName);
        addNeighbourFast(conn);
        world.getProvider().markChanged();
        return conn;
    }

    protected void addNeighbourFast(PathConnection connection) {
        this.neighbors.add(connection);
    }

    public void clear() {
        this.neighbors.clear();
        for (PathNode node : world.getNodes()) {
            Iterator<PathConnection> iter = node.neighbors.iterator();
            while (iter.hasNext()) {
                if (iter.next().destination == this) {
                    iter.remove();
                }
            }
        }
        world.getProvider().markChanged();
    }

    /**
     * Removes a single available name that was usable by this Path Node.
     * If no names are left and this is not a switcher, the node is removed entirely.
     *
     * @param name to remove
     */
    public void removeName(String name) {
        if (!this.names.remove(name)) {
            return;
        }
        world.removeNodeName(this, name);
        if (PathProvider.DEBUG_MODE) {
            String dbg = "NODE " + location + " NO LONGER HAS NAME " + name;
            if (this.names.isEmpty()) {
                dbg += " AND IS NOW BEING REMOVED (NO NAMES)";
            }
            System.out.println(dbg);
        }
        if (this.names.isEmpty() && !this.containsSwitcher()) {
            this.remove();
        }
    }

    /**
     * Removes this node and all names associated with it.
     */
    public void remove() {
        this.clear();
        world.removeFromMapping(this);
    }

    /**
     * Checks whether this node contains a name
     *
     * @param name to check
     * @return True if the name is contained, False if not
     */
    public boolean containsName(String name) {
        return this.names.contains(name);
    }

    /**
     * Checks whether all this node contains is a switcher sign,
     * and no other signs (destinations) are set.
     *
     * @return True if only a switcher sign is contained, False if not
     */
    public boolean containsOnlySwitcher() {
        return this.names.isEmpty() && this.containsSwitcher();
    }

    public Collection<String> getNames() {
        return this.names;
    }

    public Collection<PathConnection> getNeighbours() {
        return this.neighbors;
    }

    /**
     * Checks whether this node is covered by a switcher sign
     *
     * @return True if a switcher sign is contained, False if not
     */
    public boolean containsSwitcher() {
        return this.isRailSwitchable;
    }

    /**
     * Sets that this node is covered by a switcher sign
     */
    public void addSwitcher() {
        if (PathProvider.DEBUG_MODE && !this.isRailSwitchable) {
            System.out.println("NODE AT " + this.location.toString() + " ADDED SWITCHER");
        }
        this.isRailSwitchable = true;
    }

    /**
     * Gets a name of this Node, using get on this name will result in this node being returned.
     * Returns null if this node contains no name (and is invalid)
     *
     * @return Reverse-lookup-able Node name
     */
    public String getName() {
        if (!this.names.isEmpty()) {
            return this.names.iterator().next();
        } else if (this.containsSwitcher()) {
            return this.location.toString();
        } else {
            return null; // invalid, should be removed
        }
    }

    /**
     * Gets the Display name of this Path Node, which covers the names given or the location
     * if this is an unnamed node.
     *
     * @return Node display name
     */
    public String getDisplayName() {
        // No name at all - use location as name
        if (this.names.isEmpty()) {
            return "[" + this.location.x + "/" + this.location.y + "/" + this.location.z + "]";
        }

        if (this.names.size() == 1) {
            // Show this one name
            return this.names.iterator().next();
        } else {
            // Show a list of names
            StringBuilder builder = new StringBuilder(this.names.size() * 15);
            builder.append('{');
            for (String name : this.names) {
                if (builder.length() > 1) {
                    builder.append("/");
                }
                builder.append(name);
            }
            builder.append('}');
            return builder.toString();
        }
    }

    @Override
    public String toString() {
        return this.getDisplayName();
    }

    public void addName(String name) {
        if (this.names.add(name)) {
            if (PathProvider.DEBUG_MODE) {
                System.out.println("NODE AT " + this.location.toString() + " ADDED DESTINATION " + name);
            }
            world.addNodeName(this, name);
        }
    }
}
