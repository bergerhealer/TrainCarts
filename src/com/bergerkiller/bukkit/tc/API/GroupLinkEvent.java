package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Util;

public class GroupLinkEvent extends Event implements Cancellable {
	private static final long serialVersionUID = 1L;
	
	private final MinecartGroup group1;
	private final MinecartGroup group2;
	private boolean cancelled = false;
	public GroupLinkEvent(final MinecartGroup group1, final MinecartGroup group2) {
		super("GroupLinkEvent");
		this.group1 = group1;
		this.group2 = group2;
	}
	
	public MinecartGroup getGroup1() {
		return this.group1;
	}
	public MinecartGroup getGroup2() {
		return this.group2;
	}

	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}
	
	public static GroupLinkEvent call(final MinecartGroup group1, final MinecartGroup group2) {
		return Util.call(new GroupLinkEvent(group1, group2));
	}
	
}
