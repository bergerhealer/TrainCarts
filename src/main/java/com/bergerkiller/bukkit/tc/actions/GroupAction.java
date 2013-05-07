package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.World;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public class GroupAction extends Action {
	private MinecartGroup group;

	@Override
	public boolean doTick() {
		if (this.group.isEmpty()) {
			return true;
		}
		return super.doTick();
	}

	public MinecartGroup getGroup() {
		return this.group;
	}

	public void setGroup(MinecartGroup group) {
		this.group = group;
	}

	public World getWorld() {
		return this.group.getWorld();
	}
}
