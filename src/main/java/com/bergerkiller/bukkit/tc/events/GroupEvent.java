package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;

public abstract class GroupEvent extends Event {
	private final MinecartGroup group;

	public GroupEvent(final MinecartGroup group) {
		this.group = group;
	}

	public MinecartGroup getGroup() {
		return this.group;
	}
}
