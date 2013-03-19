package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public class GroupLinkEvent extends Event implements Cancellable {
	private static final HandlerList handlers = new HandlerList();	
	private final MinecartGroup group1;
	private final MinecartGroup group2;
	private boolean cancelled = false;

	public GroupLinkEvent(final MinecartGroup group1, final MinecartGroup group2) {
		this.group1 = group1;
		this.group2 = group2;
	}

	public MinecartGroup getGroup1() {
		return this.group1;
	}

	public MinecartGroup getGroup2() {
		return this.group2;
	}

	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public static GroupLinkEvent call(final MinecartGroup group1, final MinecartGroup group2) {
		return CommonUtil.callEvent(new GroupLinkEvent(group1, group2));
	}
}
