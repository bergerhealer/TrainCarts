package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Util;

public class ForceUpdateEvent extends Event {
	private static final long serialVersionUID = 1L;
	private MinecartGroup group;
	private double force;
	
	public ForceUpdateEvent(MinecartGroup group, double force) {
		super("ForceUpdateEvent");
		this.group = group;
		this.force = force;
	}
	
	public MinecartGroup getGroup() {
		return this.group;
	}
	public double getForce() {
		return this.force;
	}
	public void setForce(double value) {
		this.force = value;
	}
	
	public static double call(MinecartGroup group, double force) {
		return Util.call(new ForceUpdateEvent(group, force)).getForce();
	}

}
