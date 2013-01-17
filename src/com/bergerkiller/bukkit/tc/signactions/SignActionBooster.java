package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.util.Vector;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionBooster extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if(!info.isType("booster")) return;
		if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.hasGroup()) {
			for(MinecartMember member : info.getGroup()) {
				this.attachVelocity(member, info);
			}
		} else if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
			this.attachVelocity(info.getMember(), info);
		} else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
			for (MinecartGroup group : info.getRCTrainGroups()) {
				for(MinecartMember member : group) {
					this.attachVelocity(member, info);
				}
			}
		}
	}
	
	private void attachVelocity(MinecartMember member, SignActionEvent info) {
		try {
			double speed = Double.valueOf(info.getLine(2));
			Vector velocity = member.getVelocity();
			velocity.multiply(speed);
			member.setVelocity(velocity);
		} catch(Exception e) {}
	}
	
	@Override
	public boolean canSupportRC() {
		return true;
	}

	@Override
	public boolean build(SignChangeActionEvent info) {
		if(info.isType("booster")) {
			if (info.isCartSign()) {
				return handleBuild(info, Permission.BUILD_BOOSTER, "effect cart", "boost cart");
			} else if (info.isTrainSign()) {
				return handleBuild(info, Permission.BUILD_BOOSTER, "effect train", "boost train");
			} else if(info.isRCSign()) {
				return handleBuild(info, Permission.BUILD_BOOSTER, "effect train", "boost train");
			}
		}
		return false;
	}

}
