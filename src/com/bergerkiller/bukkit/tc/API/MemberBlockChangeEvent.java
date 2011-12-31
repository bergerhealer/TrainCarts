package com.bergerkiller.bukkit.tc.API;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class MemberBlockChangeEvent extends MemberEvent {
	private static final long serialVersionUID = 1L;
	
	private MemberBlockChangeEvent(MinecartMember member) {
		super("MemberBlockChangeEvent", member);
	}
		
	public static void call(MinecartMember member) {
		Util.call(new MemberBlockChangeEvent(member));
	}

}
