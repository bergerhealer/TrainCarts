package com.bergerkiller.bukkit.tc.API;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class MemberBlockChangeEvent extends MemberEvent {
	private static final long serialVersionUID = 1L;
	
	private final Block from;
	private final Block to;
	private MemberBlockChangeEvent(final MinecartMember member, final Block from, final Block to) {
		super("MemberBlockChangeEvent", member);
		this.from = from;
		this.to = to;
	}
	
	public Block getFrom() {
		return this.from;
	}
	public Block getTo() {
		return this.to;
	}
		
	public static void call(final MinecartMember member, final Block from, final Block to) {
		Util.call(new MemberBlockChangeEvent(member, from, to));
	}

}
