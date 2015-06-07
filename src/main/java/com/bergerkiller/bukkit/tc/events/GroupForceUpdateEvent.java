package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.event.HandlerList;

public class GroupForceUpdateEvent extends GroupEvent {
    private static final HandlerList handlers = new HandlerList();
    private double force;

    public GroupForceUpdateEvent(final MinecartGroup group, double force) {
        super(group);
        this.force = force;
    }

    public static double call(MinecartGroup group, double force) {
        return CommonUtil.callEvent(new GroupForceUpdateEvent(group, force)).getForce();
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public double getForce() {
        return this.force;
    }

    public void setForce(double value) {
        this.force = value;
    }

    public HandlerList getHandlers() {
        return handlers;
    }
}
