package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public class GroupActionWaitForever extends GroupAction implements WaitAction {

	public GroupActionWaitForever(final MinecartGroup group) {
		super(group);
	}

	@Override
	public boolean update() {
		getGroup().stop();
		return false;
	}

	@Override
	public boolean isVelocityChangesSuppressed() {
		return true;
	}
}
