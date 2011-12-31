package com.bergerkiller.bukkit.tc.API;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Util;

public class GroupRemoveEvent extends GroupEvent {
	private static final long serialVersionUID = 1L;

	public GroupRemoveEvent(final MinecartGroup group) {
		super("GroupRemoveEvent", group);
	}
	
	public static void call(final MinecartGroup group) {
		Util.call(new GroupRemoveEvent(group));
	}

}
