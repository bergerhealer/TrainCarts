package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.MinecartGroup;

public class GroupActionWait extends GroupActionWaitTill implements WaitAction {

	private long delay;
	public GroupActionWait(MinecartGroup group, long delayMS) {
		super(group, System.currentTimeMillis() + delayMS);
		this.delay = delayMS;
	}
	
	public void start() {
		this.setTime(System.currentTimeMillis() + delay);
	}

}
