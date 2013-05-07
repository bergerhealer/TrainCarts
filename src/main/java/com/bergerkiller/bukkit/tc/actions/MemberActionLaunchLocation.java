package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;

public class MemberActionLaunchLocation extends MemberActionLaunchDirection implements MovementAction {
	private final Location target;

	public MemberActionLaunchLocation(double targetvelocity, Location target) {
		super(0.0, targetvelocity, BlockFace.SELF);
		this.target = target.clone();
	}

	@Override
	public void bind() {
		super.bind();
		this.setTargetDistance(getMember().getEntity().loc.distance(target));
		this.setDirection(getMember().getDirection());
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
