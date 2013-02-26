package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.Location;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

public class MemberActionLaunchLocation extends MemberActionLaunchDirection implements MovementAction {

	private final Location target;
	public MemberActionLaunchLocation(final MinecartMember member, double targetvelocity, Location target) {
		super(member, member.distance(target), targetvelocity, member.getDirection());
		this.target = target.clone();
	}

	public void start() {
		//update direction to launch at
		super.setDirection(FaceUtil.getDirection(this.getMember().getLocation(), this.target, false));
		double d = this.getMember().distanceXZ(this.target);
		d += Math.abs(this.target.getBlockY() - this.getMember().getLiveBlockY());
		super.setTargetDistance(d);
		super.start();
	}
}
