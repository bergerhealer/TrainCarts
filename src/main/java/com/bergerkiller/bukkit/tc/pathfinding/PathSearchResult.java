package com.bergerkiller.bukkit.tc.pathfinding;

/**
 * The results of searching the path from one path node to another.
 * Stores the path nodes, connections and total distance traversed.
 */
final class PathSearchResult {
    /** Dummy result used to signal skipping a search */
    public static final PathSearchResult DUMMY_NOT_FOUND = new PathSearchResult(null, null, null, null, Double.MAX_VALUE, false);
    static {
        DUMMY_NOT_FOUND.needsToBeCached = false; // Just in case
    }

    /** Node this path search result is for */
    public final PathNode node;
    /** Destination to reach that this result is for */
    public final PathNode destination;
    /** Connection of this node that is taken to reach the destination. Null if end. */
    public final PathConnection connection;
    /** The next search search result when following the {@link #connection} Null if end. */
    public final PathSearchResult next;
    /** Total distance from this node to the destination */
    public final double distance;
    /** True if a path was found */
    public final boolean found;
    /** Whether this result has been cached in the node */
    private boolean needsToBeCached;

    /**
     * Creates a search result for the node itself with a distance of 0.
     * Cannot be cached.
     *
     * @param node Node which is both start and destination of the search
     * @return result
     */
    public static PathSearchResult self(PathNode node) {
        PathSearchResult r = new PathSearchResult(node, node, null, null, 0.0, true);
        r.needsToBeCached = false; // Prevent it being put into a cache, ever.
        return r;
    }

    /**
     * Creates a search result for when a destination can not be reached from a node.
     * Still stores the node and destination information, and a mod counter to invalidate it,
     * but nothing more. Can be cached.
     *
     * @param node Start node
     * @param destination Destination that could not be reached
     * @return result
     */
    public static PathSearchResult missing(PathNode node, PathNode destination) {
        return new PathSearchResult(node, destination, null, null, Double.MAX_VALUE, false);
    }

    /**
     * Creates a new search result from this node to another series of nodes, each
     * having their own search result in a linked chain. Can be cached.
     *
     * @param node Node searched from (owning node)
     * @param destination Final destination to reach, end of the chain
     * @param connection Connection taken from the start node
     * @param next Next search result of the node when following the connection
     * @return result
     */
    public static PathSearchResult chain(PathNode node, PathNode destination, PathConnection connection, PathSearchResult next) {
        return new PathSearchResult(node, destination, connection, next,
                connection.distance + next.distance, true);
    }

    private PathSearchResult(PathNode node, PathNode destination, PathConnection connection, PathSearchResult next, double distance, boolean found) {
        this.node = node;
        this.destination = destination;
        this.connection = connection;
        this.next = next;
        this.distance = distance;
        this.found = found;
        this.needsToBeCached = true;
    }

    /**
     * If not already cached, caches this search result in the node. If a chain
     * of nodes is the result, caches all other nodes part of this chain, too.
     */
    public void cache() {
        PathSearchResult r = this;
        while (r != null && r.needsToBeCached) {
            r.needsToBeCached = false;
            r.node.getWorld().cacheSearchResult(r);
            r = r.next;
        }
    }
}
