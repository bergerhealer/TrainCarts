package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberSpawnEvent extends MemberEvent {
	private static final HandlerList handlers = new HandlerList();

	public MemberSpawnEvent(MinecartMember<?> member) {
		super(member);
	}

	public void setMember(MinecartMember<?> member) {
		this.member = member;
	}

	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public static MemberSpawnEvent call(MinecartMember<?> member) {
		return CommonUtil.callEvent(new MemberSpawnEvent(member));
	}
}
