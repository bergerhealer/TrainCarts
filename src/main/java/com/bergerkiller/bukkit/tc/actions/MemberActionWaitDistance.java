package com.bergerkiller.bukkit.tc.actions;

public class MemberActionWaitDistance extends MemberAction implements WaitAction {
	private double distance;

	public MemberActionWaitDistance(double distance) {
		this.distance = distance;
	}

	@Override
	public boolean update() {
		this.distance -= this.getEntity().getMovedXZDistance();
		return this.distance <= 0;
	}

	@Override
	public boolean isMovementSuppressed() {
		return true;
	}
}
