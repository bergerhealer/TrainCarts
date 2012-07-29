package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public class GroupActionWaitState extends GroupActionWaitForever {

	private boolean stop = false;
	
	public GroupActionWaitState(MinecartGroup group) {
		super(group);
	}
	
	public boolean update() {
		return this.stop;
	}
	
	public void stop() {
		this.stop = true;
	}
	
	@Override
	public boolean isVelocityChangesSuppressed() {
		return false;
	}

}
