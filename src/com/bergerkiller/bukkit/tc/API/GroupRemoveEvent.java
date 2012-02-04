package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;

public class GroupRemoveEvent extends GroupEvent {
	private static final long serialVersionUID = 1L;
    private static final HandlerList handlers = new HandlerList();
    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList() {
        return handlers;
    }

	public GroupRemoveEvent(final MinecartGroup group) {
		super("GroupRemoveEvent", group);
	}
	
	public static void call(final MinecartGroup group) {
		CommonUtil.callEvent(new GroupRemoveEvent(group));
	}

}
