package com.bergerkiller.bukkit.tc.API;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Util;

public class GroupForceUpdateEvent extends GroupEvent {
	private static final long serialVersionUID = 1L;
	private double force;
	
	public GroupForceUpdateEvent(final MinecartGroup group, double force) {
		super("ForceUpdateEvent", group);
		this.force = force;
	}
	
	public double getForce() {
		return this.force;
	}
	public void setForce(double value) {
		this.force = value;
	}
	
	public static double call(MinecartGroup group, double force) {
		return Util.call(new GroupForceUpdateEvent(group, force)).getForce();
	}

}
