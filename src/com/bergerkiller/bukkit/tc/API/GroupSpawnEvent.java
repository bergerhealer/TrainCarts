package com.bergerkiller.bukkit.tc.API;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Util;

public class GroupSpawnEvent extends GroupEvent {
	private static final long serialVersionUID = 1L;

	public GroupSpawnEvent(final MinecartGroup group) {
		super("GroupSpawnEvent", group);
	}
	
	public static void call(final MinecartGroup group) {
		Util.call(new GroupSpawnEvent(group));
	}

}
