package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Read-only copy of a node's information. Has proper equality checks
 * and such. Implements Comparable to sort "interesting" path nodes
 * to the top.
 */
public class PathNodeSnapshot implements Comparable<PathNodeSnapshot> {
    private final Set<String> names;
    private final BlockLocation location;
    private final boolean isRailSwitchable;

    public PathNodeSnapshot(Set<String> names, BlockLocation location, boolean isRailSwitchable) {
        this.names = names;
        this.location = location;
        this.isRailSwitchable = isRailSwitchable;
    }

    /**
     * Gets the Display name of this Path Node, which covers the names given or the location
     * if this is an unnamed node.
     *
     * @return Node display name
     */
    public String getDisplayName() {
        return PathNode.formatDisplayName(location, names);
    }

    public Set<String> getNames() {
        return names;
    }

    public BlockLocation getRailLocation() {
        return location;
    }

    public String getWorldName() {
        return location.world;
    }

    /**
     * Checks whether this node was covered by a switcher sign
     *
     * @return True if a switcher sign was contained, False if not
     */
    public boolean containsSwitcher() {
        return this.isRailSwitchable;
    }

    /**
     * Gets a suitable update message to show to players after this snapshot
     * node was found again. If details are identical, returns null.
     *
     * @param node PathNode found using this snapshot as the best-fitting.
     *             Null if missing.
     * @return Update message, or null if the node details are identical
     */
    public MessageBuilder getUpdateMessage(PathNode node) {
        // Keep invalid nodes silent
        if (!isRailSwitchable && names.isEmpty()) {
            return null;
        }

        // Complete node removal
        if (node == null) {
            MessageBuilder message = new MessageBuilder();
            appendInfo(message, ChatColor.RED);
            message.red(" was removed");
            return message;
        }

        // Track name changes
        List<String> removedNames = Collections.emptyList();
        List<String> addedNames = Collections.emptyList();
        for (String oldName : names) {
            if (!node.containsName(oldName)) {
                if (removedNames.isEmpty()) {
                    removedNames = new ArrayList<>();
                }
                removedNames.add(oldName);
            }
        }
        for (String newName : node.getNames()) {
            if (!names.contains(newName)) {
                if (addedNames.isEmpty()) {
                    addedNames = new ArrayList<>();
                }
                addedNames.add(newName);
            }
        }

        // Same rail, did names change?
        if (node.getRailLocation().equals(this.getRailLocation())) {
            if (removedNames.isEmpty() && addedNames.isEmpty()) {
                return null;
            }

            MessageBuilder message = new MessageBuilder();
            appendInfo(message, ChatColor.YELLOW);
            message.yellow (" was changed:");
            if (!removedNames.isEmpty()) {
                message.newLine().yellow(" - ").red("Destinations removed: ");
                appendNames(message, ChatColor.RED, removedNames);
            }
            if (!addedNames.isEmpty()) {
                message.newLine().yellow(" - ").green("Destinations added: ");
                appendNames(message, ChatColor.GREEN, addedNames);
            }
            return message;
        }

        // Different rail for existing destination names?
        if (!names.isEmpty()) {
            MessageBuilder message = new MessageBuilder();
            appendInfo(message, ChatColor.YELLOW);
            message.yellow(" was moved:");
            message.newLine().yellow(" - Now at: ");
            appendLocation(message, node.getRailLocation());

            if (!removedNames.isEmpty()) {
                message.newLine().yellow(" - ").red("Destinations removed: ");
                appendNames(message, ChatColor.RED, removedNames);
            }
            if (!addedNames.isEmpty()) {
                message.newLine().yellow(" - ").green("Destinations added: ");
                appendNames(message, ChatColor.GREEN, addedNames);
            }
            return message;
        }

        return null;
    }

    @Override
    public int compareTo(PathNodeSnapshot pathNodeSnapshot) {
        int comp;

        // First by name count (anonymous switchers and such last)
        if ((comp = Integer.compare(names.size(), pathNodeSnapshot.names.size())) != 0) {
            return comp;
        }

        // If both have same number of names, put ones with a switcher first
        // Puts station-sign-routing nodes first
        if ((comp = Boolean.compare(isRailSwitchable, pathNodeSnapshot.isRailSwitchable)) != 0) {
            return comp;
        }

        // If both have names, sort by name alphabetically
        if (names.size() == 1) {
            return names.iterator().next().compareTo(pathNodeSnapshot.names.iterator().next());
        }

        return 0;
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof PathNodeSnapshot) {
            PathNodeSnapshot other = (PathNodeSnapshot) o;
            return this.names.equals(other.names) &&
                    this.location.equals(other.location) &&
                    this.isRailSwitchable == other.isRailSwitchable;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "PathNodeSnapshot{rail=" + location + ", names=" + names + "}";
    }

    private void appendInfo(MessageBuilder message, ChatColor color) {
        if (isRailSwitchable) {
            message.append(color, "Switched ");
        }
        if (names.isEmpty()) {
            message.append(color, "Node");
        } else if (names.size() == 1) {
            message.append(color, "Destination ");
            appendNames(message, color, names);
        } else {
            message.append(color, "Destinations [");
            appendNames(message, color, names);
            message.append(color, "]");
        }
        message.append(color, " at ");
        appendLocation(message, location);
    }

    private static void appendLocation(MessageBuilder message, BlockLocation location) {
        message.white("[", location.x, "/", location.y, "/", location.z, "]");
    }

    private static void appendNames(MessageBuilder message, ChatColor color, Collection<String> names) {
        boolean first = true;
        for (String name : names) {
            if (first) {
                first = false;
            } else {
                message.append(color, ", ");
            }
            message.white(name);
        }
    }
}
