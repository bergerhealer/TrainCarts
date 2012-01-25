package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class MemberAddEvent extends MemberEvent {
	private static final long serialVersionUID = 1L;
    private static final HandlerList handlers = new HandlerList();
    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList() {
        return handlers;
    }
	
	private final MinecartGroup toGroup;
	public MemberAddEvent(final MinecartMember member, final MinecartGroup toGroup) {
		super("MemberAddEvent", member);
		this.toGroup = toGroup;
	}
	
	public MinecartGroup getTo() {
		return this.toGroup;
	}
	
	public static void call(final MinecartMember member, final MinecartGroup toGroup) {
		Util.call(new MemberAddEvent(member, toGroup));
	}

}
