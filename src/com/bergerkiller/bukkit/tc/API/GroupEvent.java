package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartGroup;

public abstract class GroupEvent extends Event {
	private final MinecartGroup group;
	public GroupEvent(final MinecartGroup group) {
		this.group = group;
	}
	
	public MinecartGroup getGroup() {
		return this.group;
	}

}
