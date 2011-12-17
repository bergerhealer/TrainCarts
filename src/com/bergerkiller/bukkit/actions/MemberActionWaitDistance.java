package com.bergerkiller.bukkit.actions;

import com.bergerkiller.bukkit.tc.MinecartMember;

public class MemberActionWaitDistance extends MemberAction {

	private double distance;
	public MemberActionWaitDistance(final MinecartMember member, double distance) {
		super(member);
		this.distance = distance;
	}
	
	@Override
	public boolean update() {
		this.distance -= this.getMember().getMovedDistanceXZ();
		return this.distance <= 0;
	}
	
}
