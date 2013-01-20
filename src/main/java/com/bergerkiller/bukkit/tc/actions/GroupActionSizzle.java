package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public class GroupActionSizzle extends GroupAction {
	
	public GroupActionSizzle(MinecartGroup group) {
		super(group);
	}

	public void start() {
		int j;
		for (int i = 0; i < this.getGroup().size(); i++) {
			j = i * 3;
			if (j < this.getGroup().size()) {
				this.getGroup().get(j).playLinkEffect(false);
			}
		}
	}
	
}
