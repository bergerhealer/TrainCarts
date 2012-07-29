package com.bergerkiller.bukkit.tc.events;

import net.minecraft.server.EntityMinecart;

import org.bukkit.entity.Minecart;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MinecartSwapEvent extends MemberEvent {
    private static final HandlerList handlers = new HandlerList();
    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList() {
        return handlers;
    }
	
	private final EntityMinecart from;
	private final EntityMinecart to;
	public MinecartSwapEvent(final EntityMinecart from, final EntityMinecart to) {
		super(from instanceof MinecartMember ? (MinecartMember) from : (MinecartMember) to);
		this.from = from;
		this.to = to;
	}
	
	public boolean isToMinecartMember() {
		return this.to instanceof MinecartMember;
	}
	public boolean isToEntityMinecart() {
		return this.from instanceof MinecartMember;
	}
	public EntityMinecart getNativeFrom() {
		return this.from;
	}
	public EntityMinecart getNativeTo() {
		return this.to;
	}
	public Minecart getFrom() {
		return (Minecart) this.from.getBukkitEntity();
	}
	public Minecart getTo() {
		return (Minecart) this.to.getBukkitEntity();
	}

	public static void call(final EntityMinecart from, final EntityMinecart to) {
		CommonUtil.callEvent(new MinecartSwapEvent(from, to));
	}
	
}
