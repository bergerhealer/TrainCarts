package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;

public class GroupForceUpdateEvent extends GroupEvent {
    private static final HandlerList handlers = new HandlerList();
    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList() {
        return handlers;
    }
    
	private double force;
	
	public GroupForceUpdateEvent(final MinecartGroup group, double force) {
		super(group);
		this.force = force;
	}
	
	public double getForce() {
		return this.force;
	}
	public void setForce(double value) {
		this.force = value;
	}
	
	public static double call(MinecartGroup group, double force) {
		return CommonUtil.callEvent(new GroupForceUpdateEvent(group, force)).getForce();
	}

}
