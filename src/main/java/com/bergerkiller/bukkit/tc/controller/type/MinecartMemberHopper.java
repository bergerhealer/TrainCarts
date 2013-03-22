package com.bergerkiller.bukkit.tc.controller.type;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartHopper;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberInventory;

public class MinecartMemberHopper extends MinecartMember<CommonMinecartHopper> {

	@Override
	public void onAttached() {
		super.onAttached();
		entity.setInventoryController(new MinecartMemberInventory());
	}

	@Override
	public void onActivatorUpdate(boolean activated) {
		if (entity.isSuckingItems() != activated) {
			entity.setSuckingItems(activated);
		}
	}

	@Override
	public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
		super.onPhysicsPostMove(speedFactor);
		if (entity.isDead() || !entity.isSuckingItems()) {
			return;
		}
		entity.setSuckingCooldown(entity.getSuckingCooldown() - 1);
		if (entity.getSuckingCooldown() <= 0) {
			entity.setSuckingCooldown(0);
			if (entity.suckItems()) {
				entity.setSuckingCooldown(4);
				entity.update();
			}
		}
	}
}
