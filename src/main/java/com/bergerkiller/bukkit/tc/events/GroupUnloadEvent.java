package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public class GroupUnloadEvent extends GroupEvent {
	private static final HandlerList handlers = new HandlerList();

	public GroupUnloadEvent(final MinecartGroup group) {
		super(group);
	}
	
	public static void call(final MinecartGroup group) {
		CommonUtil.callEvent(new GroupUnloadEvent(group));
	}

	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
