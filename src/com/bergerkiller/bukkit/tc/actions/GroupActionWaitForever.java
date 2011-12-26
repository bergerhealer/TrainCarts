package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.MinecartGroup;

public class GroupActionWaitForever extends GroupAction implements ActionWait {
	
	public GroupActionWaitForever(final MinecartGroup group) {
		super(group);
	}
	
	public boolean update() {
		return false;
	}

}
