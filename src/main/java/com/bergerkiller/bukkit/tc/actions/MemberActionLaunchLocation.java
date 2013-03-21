package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.Location;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

public class MemberActionLaunchLocation extends MemberActionLaunchDirection implements MovementAction {
	private final Location target;

	public MemberActionLaunchLocation(final MinecartMember<?> member, double targetvelocity, Location target) {
		super(member, member.getEntity().loc.distance(target), targetvelocity, member.getDirection());
		this.target = target.clone();
	}

	@Override
	public void start() {
		//update direction to launch at
		super.setDirection(FaceUtil.getDirection(this.getEntity().getLocation(), this.target, false));
		double d = this.getEntity().loc.xz.distance(this.target);
		d += Math.abs(this.target.getBlockY() - this.getEntity().loc.y.block());
		super.setTargetDistance(d);
		super.start();
	}
}
