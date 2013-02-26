package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberActionLaunchDirection extends MemberActionLaunch implements MovementAction {
	private BlockFace direction;

	public MemberActionLaunchDirection(final MinecartMember member, double targetdistance, double targetvelocity, final BlockFace direction) {
		super(member, targetdistance, targetvelocity);
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
		if (super.getDistance() < 1 && this.getMember().getDirection() == this.direction.getOppositeFace()) {
			this.getGroup().reverse();
		}
		return false;
	}
}
