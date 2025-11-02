package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import org.bukkit.block.Block;

import java.util.*;
import java.util.logging.Level;

public class PathNode {
    private final PathWorld world;
    public final BlockLocation location;
    private final Set<String> names = new HashSet<>();
    private final List<PathConnection> neighbors = new ArrayList<>(3);
    public int index;
    private boolean isRailSwitchable;

    // Used while calculating paths to avoid infinite recursion
    private PathSearchOperation lastSearch = null;
    private PathSearchResult lastSearchResult = null;
    private double lastSearchStartDistance = Double.MAX_VALUE;

    protected PathNode(PathWorld world, BlockLocation location) {
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
        if (!event.hasRails()) {
            throw new IllegalArgumentException("Sign has no rails - check hasRails()");
        }
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

    /**
     * Gets the world which this node is part of
     * 
     * @return path world
     */
    public PathWorld getWorld() {
        return this.world;
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
     * Tries to find the exact route (all nodes) to reach a destination from this node
     *
     * @param destination to reach
     * @return the route taken, or an empty array if none could be found or this == destination
     */
    public PathConnection[] findRoute(PathNode destination) {
        PathSearchResult result = findBestPath(destination);
        if (result.found) {
            List<PathConnection> route = new ArrayList<>();
            while (result.connection != null) {
                route.add(result.connection);
                result = result.next;
            }
            return route.toArray(new PathConnection[0]);
        } else {
            return new PathConnection[0];
        }
    }

    /**
     * Tries to find a connection from this node to the node specified
     *
     * @param destination node to find
     * @return A connection, or null if none could be found or this == destination
     */
    public PathConnection findConnection(PathNode destination) {
        PathSearchResult result = findBestPath(destination);
        if (result.found && result.connection != null) {
            return new PathConnection(destination, result.distance, result.connection.junctionName);
        } else {
            return null;
        }
    }

    private PathSearchResult findBestPath(PathNode destination) {
        PathSearchResult result = findBestPath(new PathSearchOperation(destination), 0.0);
        if (result == PathSearchResult.DUMMY_NOT_FOUND) {
            result = PathSearchResult.missing(this, destination);
        }
        result.cache();
        return result;
    }

    private PathSearchResult findBestPath(PathSearchOperation search, double startDistance) {
        // Shortcut to quit early
        if (startDistance > search.maxTotalDistance) {
            return PathSearchResult.DUMMY_NOT_FOUND;
        }

        // Either we recursively hit ourselves and the last search wasn't finished yet,
        // in which case it is set to DUMMY_NOT_FOUND. Or we had completed the search before and
        // already have the remainder of the trip. Either way, stop searching.
        if (this.lastSearch == search && startDistance > this.lastSearchStartDistance) {
            return PathSearchResult.DUMMY_NOT_FOUND;
        } else {
            this.lastSearch = search;
            this.lastSearchStartDistance = startDistance;
        }

        // If destination == this, return instantly with 0 distance
        if (this == search.destination) {
            return search.acceptResult(startDistance, this.lastSearchResult = PathSearchResult.self(this));
        }

        // First time hitting this node. Initiate a new search of its neighbours.
        // Seed the initial result before it is known as not-found to avoid infinite recursion.
        // Before we proceed, see if a path to this same destination was already cached.
        // If so, we can simply try to use that
        {
            PathSearchResult result = this.lastSearchResult = this.world.findCachedSearchResult(this, search.destination);
            if (result != PathSearchResult.DUMMY_NOT_FOUND) {
                return search.acceptResult(startDistance, result);
            }
        }

        // Ask all neighbouring nodes for the same destination, recursively
        for (PathConnection neighbour : this.neighbors) {
            PathSearchResult neigh_result = neighbour.destination.findBestPath(search, startDistance + neighbour.distance);
            if (neigh_result.found) {
                this.lastSearchResult = PathSearchResult.chain(this, search.destination, neighbour, neigh_result);
            }
        }

        return this.lastSearchResult;
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
        world.getProvider().scheduleNodeIfNotRecentlyRouted(to);
        world.markChanged();
        return conn;
    }

    protected void addNeighbourFast(PathConnection connection) {
        this.neighbors.add(connection);
    }

    /**
     * Clears the destinations known from this node to other nodes. The
     * connection from those other nodes to this node are forgotten too.
     */
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
        world.markChanged();
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
            world.getTrainCarts().log(Level.INFO, dbg);
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

    public BlockLocation getRailLocation() {
        return location;
    }

    public Collection<PathConnection> getNeighbours() {
        return this.neighbors;
    }

    /**
     * Gets all neighbouring nodes, and nodes that can be reached from those neighbours, recursively.
     * The lowest distance towards those nodes are returned, with the junction name of this node that is used
     * to reach it. Each list of connections is sorted by distance close to far.
     * 
     * @return connections
     */
    public Map<PathConnection, List<PathConnection>> getDeepNeighbours() {
        Map<PathNode, PathConnection> connections = new HashMap<>();
        for (PathConnection neighbour : this.neighbors) {
            connections.put(neighbour.destination, neighbour);
        }
        for (PathConnection neighbour : this.neighbors) {
            neighbour.destination.fillDeepNeighbours(connections, neighbour.junctionName, neighbour.distance);
        }
        connections.remove(this);

        Map<PathConnection, List<PathConnection>> result = new HashMap<>();
        for (PathConnection connection : connections.values()) {
            boolean found = false;
            for (Map.Entry<PathConnection, List<PathConnection>> entry : result.entrySet()) {
                if (entry.getKey().junctionName.equals(connection.junctionName)) {
                    found = true;
                    entry.getValue().add(connection);
                    break;
                }
            }
            if (!found) {
                for (PathConnection neighbour : this.neighbors) {
                    if (neighbour.junctionName.equals(connection.junctionName)) {
                        List<PathConnection> list = new ArrayList<>();
                        list.add(connection);
                        result.put(neighbour, list);
                        break;
                    }
                }
            }
        }
        for (List<PathConnection> collection : result.values()) {
            Collections.sort(collection, (c1, c2) -> {
                return Double.compare(c1.distance, c2.distance);
            });
        }
        return result;
    }

    private void fillDeepNeighbours(Map<PathNode, PathConnection> connections, String junctionName, double startDistance) {
        for (PathConnection neighbour : this.neighbors) {
            double distance = startDistance + neighbour.distance;
            PathConnection previous = connections.get(neighbour.destination);
            if (previous == null || previous.distance > distance) {
                connections.put(neighbour.destination, new PathConnection(neighbour.destination, distance, junctionName));
                neighbour.destination.fillDeepNeighbours(connections, junctionName, distance);
            }
        }
    }

    /**
     * Schedules this node and all nodes that can be reached from here for recalculation.
     * Nodes that have a one-way route to this node are rerouted too.<br>
     * <br>
     * Is deeply recursive, meaning all nodes are rerouted that can currently be reached
     * from this one node. This might cause performance problems for very large networks.
     */
    public void rerouteConnectedDeepRecursive() {
        HashSet<PathNode> reachable = new HashSet<PathNode>();
        this.addReachable(reachable);

        // Also add nodes that have a connection to one of the reachable nodes (recurse)
        boolean changed;
        do {
            changed = false;
            for (PathNode node : this.world.getNodes()) {
                if (!reachable.contains(node)) {
                    for (PathConnection neighbour : node.neighbors) {
                        if (reachable.contains(neighbour.destination)) {
                            changed = true;
                            node.addReachable(reachable);
                        }
                    }
                }
            }
        } while (changed);

        // Remove all the reachable nodes we have collected, deleting the entire network
        // Schedule all these nodes for path finding
        for (PathNode node : reachable) {
            node.neighbors.clear();
            world.removeFromMapping(node);
            world.getProvider().discoverFromRail(node.location);
        }
    }

    /**
     * Rediscovers the connections from this node to all connected nodes,
     * and from those connected nodes outwards so long more connections are
     * found.
     */
    public void rerouteConnected() {
        this.clear();
        world.removeFromMapping(this);
        world.getProvider().discoverFromRail(location);
    }

    private void addReachable(Set<PathNode> reachable) {
        if (reachable.add(this)) {
            for (PathConnection neighbour : this.neighbors) {
                neighbour.destination.addReachable(reachable);
            }
        }
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
            world.getTrainCarts().log(Level.INFO, "NODE AT " + this.location.toString() + " ADDED SWITCHER");
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
        return formatDisplayName(location, names);
    }

    @Override
    public String toString() {
        return this.getDisplayName();
    }

    public void addName(String name) {
        if (this.names.add(name)) {
            if (PathProvider.DEBUG_MODE) {
                world.getTrainCarts().log(Level.INFO, "NODE AT " + this.location.toString() + " ADDED DESTINATION " + name);
            }
            world.addNodeName(this, name);
        }
    }

    /**
     * Gets a snapshot copy of this node's information. The returned snapshot will not change
     * when properties of this node change.
     *
     * @return PathNodeSnapshot
     */
    public PathNodeSnapshot getSnapshot() {
        return new PathNodeSnapshot(new HashSet<>(names), location, isRailSwitchable);
    }

    protected static String formatDisplayName(BlockLocation location, Set<String> names) {
        // No name at all - use location as name
        if (names.isEmpty()) {
            return "[" + location.x + "/" + location.y + "/" + location.z + "]";
        }

        if (names.size() == 1) {
            // Show this one name
            return names.iterator().next();
        } else {
            // Show a list of names
            StringBuilder builder = new StringBuilder(names.size() * 15);
            builder.append('{');
            for (String name : names) {
                if (builder.length() > 1) {
                    builder.append("/");
                }
                builder.append(name);
            }
            builder.append('}');
            return builder.toString();
        }
    }

    /**
     * A single, unique, search operation and its state information
     */
    private static class PathSearchOperation {
        /** Destination node being reached */
        public final PathNode destination;
        /**
         * Maximum distance allowed for a valid search result. Avoids navigating
         * paths that are longer than other paths that are already found.
         */
        public double maxTotalDistance = Double.MAX_VALUE;

        public PathSearchOperation(PathNode destination) {
            this.destination = destination;
        }

        /**
         * Notifies that a search resulted in a solution, and wants to check whether
         * the total distance is lower than any previous result (or no result).
         *
         * @param startDistance Distance before this part of the result was found
         * @param result Part of the search path solution
         * @return input result if accepted (or not found, unchanged), DUMMY_NOT_FOUND if not accepted
         */
        public PathSearchResult acceptResult(double startDistance, PathSearchResult result) {
            if (!result.found) {
                return result;
            }
            double total = startDistance + result.distance;
            if (total < maxTotalDistance) {
                maxTotalDistance = total;
                return result;
            } else {
                return PathSearchResult.DUMMY_NOT_FOUND;
            }
        }
    }
}
