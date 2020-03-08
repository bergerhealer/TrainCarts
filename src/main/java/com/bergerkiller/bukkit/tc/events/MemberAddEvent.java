package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.event.HandlerList;

public class MemberAddEvent extends MemberEvent {
    private static final HandlerList handlers = new HandlerList();
    private final MinecartGroup toGroup;

    public MemberAddEvent(final MinecartMember<?> member, final MinecartGroup toGroup) {
        super(member);
        this.toGroup = toGroup;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public MinecartGroup getTo() {
        return this.toGroup;
    }

    public HandlerList getHandlers() {
        return handlers;
    }
}
