package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.Location;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.FaceUtil;

public class MemberActionLaunchLocation extends MemberActionLaunchDirection {

	private final Location target;
	public MemberActionLaunchLocation(final MinecartMember member, Location target, double targetvelocity) {
		super(member, 0, targetvelocity, member.getDirection());
		this.target = target.clone();
	}
		
	public void start() {
		double dx = this.getMember().getX() - target.getX();
		double dz = this.getMember().getZ() - target.getZ();
		super.setDirection(FaceUtil.getDirection(dx, dz, false));
		super.start();
	}
	
}
