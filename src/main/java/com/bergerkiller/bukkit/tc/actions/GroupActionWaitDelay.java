package com.bergerkiller.bukkit.tc.actions;

public class GroupActionWaitDelay extends GroupActionWaitTill implements WaitAction {
	private long delay;

	public GroupActionWaitDelay(long delayMS) {
		super(System.currentTimeMillis() + delayMS);
		this.delay = delayMS;
	}

	@Override
	public void start() {
		this.setTime(System.currentTimeMillis() + delay);
	}
}
