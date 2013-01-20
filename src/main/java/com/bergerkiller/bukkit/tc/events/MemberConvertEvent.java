package com.bergerkiller.bukkit.tc.events;

import net.minecraft.server.v1_4_R1.EntityMinecart;

import org.bukkit.entity.Minecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.NativeUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberConvertEvent extends MemberEvent implements Cancellable {
	private static final HandlerList handlers = new HandlerList();
	private final EntityMinecart from;
	private boolean cancelled = false;

	public MemberConvertEvent(final EntityMinecart from, MinecartMember to) {
		super(to);
		this.from = from;
	}

	public void setTo(MinecartMember to) {
		this.member = to;
	}

	public EntityMinecart getNativeSource() {
		return this.from;
	}

	public Minecart getSource() {
		return (Minecart) NativeUtil.getEntity(this.from);
	}

	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public static MemberConvertEvent call(final EntityMinecart from, MinecartMember to) {
		return CommonUtil.callEvent(new MemberConvertEvent(from, to));
	}
}
