package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.MinecartMember;

public class MemberRemoveEvent extends MemberEvent {
	private static final long serialVersionUID = 1L;
    private static final HandlerList handlers = new HandlerList();
    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList() {
        return handlers;
    }

	public MemberRemoveEvent(final MinecartMember member) {
		super("MemberRemoveEvent", member);
	}
	
	public static void call(final MinecartMember member) {
		CommonUtil.callEvent(new MemberRemoveEvent(member));
	}

}
