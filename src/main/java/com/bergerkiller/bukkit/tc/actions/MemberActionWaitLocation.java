package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.Location;

public class MemberActionWaitLocation extends MemberAction implements WaitAction {
	private final Location dest;
	private final double radiussquared;

	public MemberActionWaitLocation(final Location dest) {
		this(dest, 1);
	}

	public MemberActionWaitLocation(final Location dest, final double radius) {
		this.dest = dest;
		this.radiussquared = radius * radius;
	}

	@Override
	public boolean update() {
		if (this.getWorld() != dest.getWorld()) return false;
		return this.getEntity().loc.distanceSquared(dest) <= this.radiussquared;
	}

	@Override
	public boolean isMovementSuppressed() {
		return true;
	}
}
