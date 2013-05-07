package com.bergerkiller.bukkit.tc.actions;

public class GroupActionWaitTill extends GroupActionWaitForever {
	private long finishtime;

	public GroupActionWaitTill(final long finishtime) {
		this.setTime(finishtime);
	}

	protected void setTime(long finishtime) {
		this.finishtime = finishtime;
	}

	@Override
	public boolean update() {
		return this.finishtime <= System.currentTimeMillis() || super.update();
	}
}
