package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartGroup;

public abstract class GroupEvent extends Event {
	private static final long serialVersionUID = 1L;
	
	private final MinecartGroup group;
	public GroupEvent(final String eventname, final MinecartGroup group) {
		super(eventname);
		this.group = group;
	}
	
	public MinecartGroup getGroup() {
		return this.group;
	}

}
