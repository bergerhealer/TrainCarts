package com.bergerkiller.bukkit.tc.actions;

/**
 * TODO: Used by the blocker sign only, is slightly misnamed, needs proper Blocker class instead
 */
public class GroupActionWaitState extends GroupActionWaitForever {
	private boolean stop = false;

	@Override
	public boolean update() {
		return this.stop || super.update();
	}

	public void stop() {
		this.stop = true;
	}

	@Override
	public boolean isMovementSuppressed() {
		return false;
	}
}
