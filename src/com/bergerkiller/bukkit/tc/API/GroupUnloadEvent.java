package com.bergerkiller.bukkit.tc.API;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Util;

public class GroupUnloadEvent extends GroupEvent {
	private static final long serialVersionUID = 1L;

	public GroupUnloadEvent(final MinecartGroup group) {
		super("GroupUnloadEvent", group);
	}
	
	public static void call(final MinecartGroup group) {
		Util.call(new GroupUnloadEvent(group));
	}

}
