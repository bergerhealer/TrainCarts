package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberAddEvent extends MemberEvent {
	private static final HandlerList handlers = new HandlerList();	
	private final MinecartGroup toGroup;

	public MemberAddEvent(final MinecartMember<?> member, final MinecartGroup toGroup) {
		super(member);
		this.toGroup = toGroup;
	}

	public MinecartGroup getTo() {
		return this.toGroup;
	}

	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public static void call(final MinecartMember<?> member, final MinecartGroup toGroup) {
		CommonUtil.callEvent(new MemberAddEvent(member, toGroup));
	}
}
