package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.Location;

import com.bergerkiller.bukkit.tc.MinecartMember;

public class MemberActionWaitLocation extends MemberAction implements WaitAction {
	
	private final Location dest;
	private final double radiussquared;
	public MemberActionWaitLocation(final MinecartMember member, final Location dest) {
		this(member, dest, 1);
	}
	public MemberActionWaitLocation(final MinecartMember member, final Location dest, final double radius) {
		super(member);
		this.dest = dest;
		this.radiussquared = radius * radius;
	}

	@Override
	public boolean update() {
		if (this.getWorld() != dest.getWorld()) return false;
		return this.getMember().distanceSquared(dest) <= this.radiussquared;
	}
	
	@Override
	public boolean isVelocityChangesSuppressed() {
		return true;
	}

}
