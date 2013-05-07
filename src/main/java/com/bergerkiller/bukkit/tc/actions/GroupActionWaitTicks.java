package com.bergerkiller.bukkit.tc.actions;

public class GroupActionWaitTicks extends GroupActionWaitForever {
	private int ticks;

	public GroupActionWaitTicks(int ticks) {
		this.ticks = ticks;
	}

	@Override
	public boolean update() {
		if (this.ticks <= 0) {
			return true;
		} else {
			this.ticks--;
			return super.update();
		}
	}
}
