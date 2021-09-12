package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * This event fires if a destination couldn't be reached when a train is passing a switcher-sign
 */
public class MissingPathConnectionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final RailPiece rail;
    private final PathNode node;
    private final MinecartGroup group;
    private final String destination;

    public MissingPathConnectionEvent(RailPiece rail, PathNode node, MinecartGroup group, String destination) {
        this.rail = rail;
        this.node = node;
        this.group = group;
        this.destination = destination;
    }

    public RailPiece getRail() { return rail; }
    public PathNode getPathNode() { return this.node; }
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
