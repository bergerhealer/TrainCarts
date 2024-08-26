package com.bergerkiller.bukkit.tc.pathfinding;

import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailState;

/**
 * Base implementation of PathNavigateEvent. Can be further extended to create
 * custom PathNavigateEvent implementations.
 */
public class PathNavigateEventBaseImpl implements PathNavigateEvent {
    private RailState railState;
    private RailPath railPath;
    private RailPath.Position nextPosition;
    private double currentDistance;
    private boolean abortNavigation;

    /**
     * Constructs a new PathNavigateEventBaseImpl.
     * Must call {@link #resetToInitialState(RailState, RailPath, double)} to set it up
     * before using as an event.
     */
    public PathNavigateEventBaseImpl() {
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
}
