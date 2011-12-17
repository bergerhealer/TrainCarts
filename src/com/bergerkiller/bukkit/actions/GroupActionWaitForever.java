package com.bergerkiller.bukkit.actions;

import com.bergerkiller.bukkit.tc.MinecartGroup;

public class GroupActionWaitForever extends GroupAction {
	
	public GroupActionWaitForever(final MinecartGroup group) {
		super(group);
	}
	
	public boolean update() {
		return false;
	}

}
