package com.bergerkiller.bukkit.tc.API;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class MemberRemoveEvent extends MemberEvent {
	private static final long serialVersionUID = 1L;

	public MemberRemoveEvent(final MinecartMember member) {
		super("MemberRemoveEvent", member);
	}
	
	public static void call(final MinecartMember member) {
		Util.call(new MemberRemoveEvent(member));
	}

}
