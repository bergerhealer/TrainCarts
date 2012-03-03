package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;

public abstract class MemberEvent extends Event {
	
	private final MinecartMember member;
	public MemberEvent(final MinecartMember member) {
		this.member = member;
	}
	
	public MinecartMember getMember() {
		return this.member;
	}
	public MinecartGroup getGroup() {
		return this.getMember().getGroup();
	}
	
}
