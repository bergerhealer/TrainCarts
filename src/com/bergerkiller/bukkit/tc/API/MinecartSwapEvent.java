package com.bergerkiller.bukkit.tc.API;

import net.minecraft.server.EntityMinecart;

import org.bukkit.entity.Minecart;
import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Util;

public class MinecartSwapEvent extends Event {

	private static final long serialVersionUID = 1L;
	
	private final EntityMinecart from;
	private final EntityMinecart to;
	public MinecartSwapEvent(final EntityMinecart from, final EntityMinecart to) {
		super("MinecartSwapEvent");
		this.from = from;
		this.to = to;
	}
	
	public MinecartMember getMember() {
		if (this.from instanceof MinecartMember) {
			return (MinecartMember) this.from;
		} else if (this.to instanceof MinecartMember) {
			return (MinecartMember) this.to;
		} else {
			return null;
		}
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
		Util.call(new MinecartSwapEvent(from, to));
	}
	
}
