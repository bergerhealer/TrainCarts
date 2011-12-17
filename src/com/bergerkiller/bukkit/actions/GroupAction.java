package com.bergerkiller.bukkit.actions;

import org.bukkit.World;

import com.bergerkiller.bukkit.tc.MinecartGroup;

public class GroupAction extends Action {
	
	public boolean doTick() {
		if (this.group.isEmpty()) return true;
		return super.doTick();
	}
	
	private final MinecartGroup group;
	public GroupAction(final MinecartGroup group) {
		this.group = group;
	}
	public MinecartGroup getGroup() {
		return this.group;
	}
	public World getWorld() {
		return this.group.getWorld();
	}
	
}
