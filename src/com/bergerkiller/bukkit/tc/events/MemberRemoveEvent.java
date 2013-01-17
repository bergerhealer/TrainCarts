package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberRemoveEvent extends MemberEvent {
	private static final HandlerList handlers = new HandlerList();

	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public MemberRemoveEvent(final MinecartMember member) {
		super(member);
	}
	
	public static void call(final MinecartMember member) {
		CommonUtil.callEvent(new MemberRemoveEvent(member));
	}
}
