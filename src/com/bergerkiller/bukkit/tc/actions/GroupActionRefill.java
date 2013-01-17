package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class GroupActionRefill extends GroupAction {

	public GroupActionRefill(MinecartGroup group) {
		super(group);
	}

	public void start() {
		for (MinecartMember member : this.getGroup()) {
			if (member.isPoweredCart()) {
				member.fuel = MinecartMember.FUEL_PER_COAL;
			}
		}
	}
}
