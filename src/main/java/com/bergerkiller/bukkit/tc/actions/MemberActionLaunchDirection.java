package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.BlockFace;

public class MemberActionLaunchDirection extends MemberActionLaunch implements MovementAction {
	private BlockFace direction;

	public MemberActionLaunchDirection(double targetdistance, double targetvelocity, final BlockFace direction) {
		super(targetdistance, targetvelocity);
		this.direction = direction;
	}

	public void setDirection(BlockFace direction) {
		this.direction = direction;
	}

	@Override
	public boolean update() {
		if (super.update()) {
			return true;
		}
		if (super.getDistance() < 1 && this.getMember().isDirectionTo(this.direction.getOppositeFace())) {
			this.getGroup().reverse();
		}
		return false;
	}
}
