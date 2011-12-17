package com.bergerkiller.bukkit.actions;

import org.bukkit.Location;

import com.bergerkiller.bukkit.tc.MinecartMember;

public class MemberActionLaunchLocation extends MemberActionLaunch {

	private final Location target;
	public MemberActionLaunchLocation(final MinecartMember member, Location target, double targetvelocity) {
		super(member, 0, targetvelocity);
		this.target = target.clone();
	}
	
	public void start() {
		super.setTargetDistance(this.getMember().distanceXZ(this.target));
		super.start();
	}
	
	public boolean update() {
		if (super.update()) {
			return true;
		} else {
			if (!this.getMember().isHeadingTo(target)) {
				//correct this behavior!
				this.getGroup().reverse();
			}
			return false;
		}
	}
	
}
