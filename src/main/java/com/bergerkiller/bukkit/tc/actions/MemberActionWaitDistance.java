package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberActionWaitDistance extends MemberAction implements WaitAction {
	private double distance;

	public MemberActionWaitDistance(final MinecartMember<?> member, double distance) {
		super(member);
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
