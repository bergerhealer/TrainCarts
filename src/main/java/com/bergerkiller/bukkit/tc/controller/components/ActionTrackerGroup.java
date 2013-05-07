package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.actions.GroupAction;
import com.bergerkiller.bukkit.tc.actions.GroupActionRefill;
import com.bergerkiller.bukkit.tc.actions.GroupActionSizzle;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitDelay;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitForever;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitState;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitTicks;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitTill;
import com.bergerkiller.bukkit.tc.actions.MemberAction;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class ActionTrackerGroup extends ActionTracker {
	private final MinecartGroup owner;

	public ActionTrackerGroup(MinecartGroup owner) {
		this.owner = owner;
	}

	/**
	 * Gets the owner of this Block Tracker
	 * 
	 * @return the Owner
	 */
	public MinecartGroup getOwner() {
		return owner;
	}

	@Override
	public void clear() {
		super.clear();
		for (MinecartMember<?> member : owner) {
			member.getActions().clear();
		}
	}

	@Override
	public <T extends Action> T addAction(T action) {
		if (action instanceof GroupAction) {
			((GroupAction) action).setGroup(owner);
		} else if (action instanceof MemberAction && ((MemberAction) action).getMember() == null) {
			throw new RuntimeException("Can not add member action without a member set beforehand!");
		}
		return super.addAction(action);
	}

	public GroupActionWaitDelay addActionWait(long delay) {
		return this.addAction(new GroupActionWaitDelay(delay));
	}

	public GroupActionWaitTill addActionWaitTill(long time) {
		return this.addAction(new GroupActionWaitTill(time));
	}

	public GroupActionWaitTicks addActionWaitTicks(int ticks) {
		return this.addAction(new GroupActionWaitTicks(ticks));
	}

	public GroupActionWaitForever addActionWaitForever() {
		return this.addAction(new GroupActionWaitForever());
	}

	public GroupActionWaitState addActionWaitState() {
		return this.addAction(new GroupActionWaitState());
	}

	public GroupActionSizzle addActionSizzle() {
		return this.addAction(new GroupActionSizzle());
	}

	public GroupActionRefill addActionRefill() {
		return this.addAction(new GroupActionRefill());
	}
}
