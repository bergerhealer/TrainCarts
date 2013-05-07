package com.bergerkiller.bukkit.tc.actions;

public class GroupActionWaitForever extends GroupAction implements WaitAction {

	@Override
	public boolean update() {
		getGroup().stop();
		return false;
	}

	@Override
	public boolean isMovementSuppressed() {
		return true;
	}
}
