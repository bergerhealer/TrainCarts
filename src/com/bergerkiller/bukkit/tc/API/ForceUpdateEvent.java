package com.bergerkiller.bukkit.tc.API;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartGroup;

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
		ForceUpdateEvent f = new ForceUpdateEvent(group, force);
		Bukkit.getServer().getPluginManager().callEvent(f);
		return f.getForce();
	}

}
