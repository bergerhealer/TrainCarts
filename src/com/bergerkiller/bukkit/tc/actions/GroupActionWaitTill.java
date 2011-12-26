package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.MinecartGroup;

public class GroupActionWaitTill extends GroupAction implements ActionWait {

	private long finishtime;
	public GroupActionWaitTill(final MinecartGroup group, final long finishtime) {
		super(group);
		this.setTime(finishtime);
	}
	
	protected void setTime(long finishtime) {
		this.finishtime = finishtime;
	}

	@Override
	public boolean update() {
		return this.finishtime <= System.currentTimeMillis();
	}

}
