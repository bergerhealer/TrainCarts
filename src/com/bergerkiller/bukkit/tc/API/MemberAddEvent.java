package com.bergerkiller.bukkit.tc.API;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class MemberAddEvent extends MemberEvent {
	private static final long serialVersionUID = 1L;
	
	private final MinecartGroup toGroup;
	public MemberAddEvent(final MinecartMember member, final MinecartGroup toGroup) {
		super("MemberAddEvent", member);
		this.toGroup = toGroup;
	}
	
	public MinecartGroup getTo() {
		return this.toGroup;
	}
	
	public static void call(final MinecartMember member, final MinecartGroup toGroup) {
		Util.call(new MemberAddEvent(member, toGroup));
	}

}
