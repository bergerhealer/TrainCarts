package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extends {@link SignActionEvent} with additional methods to control the
 * path finding routing algorithm. Destination names and switched nodes
 * can be designated, and a level of path prediction can be performed.
 */
public class SignRoutingEvent extends SignActionEvent {
    private boolean blocked = false;
    private boolean switchable = false;
    private List<String> destinationNames = Collections.emptyList();

    public SignRoutingEvent(RailLookup.TrackedSign sign) {
        super(sign);
        this.setAction(SignActionType.GROUP_ENTER);
    }

    /**
     * Gets whether the path is blocked and navigation should be aborted
     *
     * @return True if {@link #setBlocked()} was called
     */
    public boolean isBlocked() {
        return blocked;
    }

    /**
     * Aborts further navigation because the current path/route is blocked
     */
    public void setBlocked() {
        this.blocked = true;
    }

    /**
     * Gets whether the track can be changed here, and all junctions must be navigated
     * to find additional paths.
     *
     * @return True if switchable
     */
    public boolean isSwitchable() {
        return switchable;
    }

    /**
     * Sets whether the track can be changed here, and all junctions must be navigated
     * to find additional paths.
     *
     * @param switchable Whether it is switchable
     */
    public void setSwitchable(boolean switchable) {
        this.switchable = switchable;
    }

    /**
     * Gets an unmodifiable view of all destination names at the current rail position
     *
     * @return Destination names, empty if none
     */
    public List<String> getDestinationNames() {
        return destinationNames;
    }

    /**
     * Adds a new destination name that is set for the current rail position
     *
     * @param name Name to add
     */
    public void addDestinationName(String name) {
        if (destinationNames.isEmpty()) {
            destinationNames = new ArrayList<>(2);
        }
        destinationNames.add(name);
    }
}
