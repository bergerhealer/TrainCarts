package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
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
public class SignRoutingEvent extends SignActionEvent implements PathNavigateEvent {
    private RailState railState;
    private RailPath railPath;
    private RailPath.Position nextPosition;
    private double currentDistance;
    private boolean abortNavigation;
    private boolean switchable = false;
    private List<String> destinationNames = Collections.emptyList();

    public SignRoutingEvent(RailLookup.TrackedSign sign) {
        super(sign);
        this.setAction(SignActionType.GROUP_ENTER);
    }

    @Override
    public void resetToInitialState(RailState railState, RailPath railPath, double currentDistance) {
        this.railState = railState;
        this.railPath = railPath;
        this.nextPosition = null;
        this.currentDistance = currentDistance;
        this.abortNavigation = false;
    }

    @Override
    public double currentDistance() {
        return currentDistance;
    }

    @Override
    public boolean isNavigationAborted() {
        return abortNavigation;
    }

    @Override
    public void abortNavigation() {
        abortNavigation = true;
    }

    @Override
    public RailState railState() {
        return this.railState;
    }

    @Override
    public RailPath railPath() {
        return this.railPath;
    }

    @Override
    public RailPath.Position getSwitchedPosition() {
        return this.nextPosition;
    }

    @Override
    public boolean hasSwitchedPosition() {
        return this.nextPosition != null;
    }

    @Override
    public void setSwitchedPosition(RailPath.Position nextPosition) {
        this.nextPosition = nextPosition;
    }

    /**
     * Gets whether the track can be changed here to find routes, and all junctions
     * must be navigated to find additional paths. Used for powered switcher signs.
     *
     * @return True if switchable
     */
    public boolean isRouteSwitchable() {
        return switchable;
    }

    /**
     * Sets whether the track can be changed here to find routes, and all junctions
     * must be navigated to find additional paths. Used for powered switcher signs.
     *
     * @param switchable Whether it is switchable
     */
    public void setRouteSwitchable(boolean switchable) {
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
