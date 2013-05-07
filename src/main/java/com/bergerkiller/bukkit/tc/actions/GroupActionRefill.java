package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartFurnace;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.type.MinecartMemberFurnace;

public class GroupActionRefill extends GroupAction {

	@Override
	public void start() {
		for (MinecartMember<?> member : this.getGroup()) {
			if (member instanceof MinecartMemberFurnace) {
				((MinecartMemberFurnace) member).getEntity().setFuelTicks(CommonMinecartFurnace.COAL_FUEL);
			}
		}
	}
}
