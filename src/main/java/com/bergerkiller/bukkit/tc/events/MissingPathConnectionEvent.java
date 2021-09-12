package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * This event fires if a destination couldn't be reached when a train is passing a switcher-sign
 */
public class MissingPathConnectionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    protected final PathNode node;
    private MinecartGroup group;
    private final String destination;

    public MissingPathConnectionEvent(PathNode node, MinecartGroup group, String destination) {
        this.node = node;
        this.group = group;
        this.destination = destination;
    }

    public PathNode getPathNode() {
        return this.node;
    }
    public MinecartGroup getGroup() { return group; }
    public String getDestination() { return destination; }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
