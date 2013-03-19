package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public abstract class MemberEvent extends Event {
	protected MinecartMember<?> member;

	public MemberEvent(final MinecartMember<?> member) {
		this.member = member;
	}

	public MinecartMember<?> getMember() {
		return this.member;
	}

	public MinecartGroup getGroup() {
		return this.getMember().getGroup();
	}
}
