package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public class GroupActionWaitDelay extends GroupActionWaitTill implements WaitAction {
	private long delay;

	public GroupActionWaitDelay(MinecartGroup group, long delayMS) {
		super(group, System.currentTimeMillis() + delayMS);
		this.delay = delayMS;
	}

	@Override
	public void start() {
		this.setTime(System.currentTimeMillis() + delay);
	}
}
