package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;

public class MemberBlockChangeEvent extends MemberEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Block from;
    private final Block to;

    private MemberBlockChangeEvent(final MinecartMember<?> member, final Block from, final Block to) {
        super(member);
        this.from = from;
        this.to = to;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public static void call(final MinecartMember<?> member, final Block from, final Block to) {
        CommonUtil.callEvent(new MemberBlockChangeEvent(member, from, to));
    }

    public Block getFrom() {
        return this.from;
    }

    public Block getTo() {
        return this.to;
    }

    public HandlerList getHandlers() {
        return handlers;
    }
}
