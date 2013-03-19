package com.bergerkiller.bukkit.tc.events;

import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberCoalUsedEvent extends MemberEvent {
	private static final HandlerList handlers = new HandlerList();
	private boolean useCoal;
	private boolean refill;
	
	public MemberCoalUsedEvent(final MinecartMember<?> source) {
		super(source);
		this.useCoal = TrainCarts.useCoalFromStorageCart;
		this.refill = false;
	}

	public boolean useCoal() {
		return this.useCoal;
	}

	public boolean refill() {
		return this.refill;
	}

	public void setUseCoal(boolean use) {
		this.useCoal = use;
	}

	public void setRefill(boolean refill) {
		this.refill = refill;
	}

	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

	public static MemberCoalUsedEvent call(final MinecartMember<?> member) {
		return CommonUtil.callEvent(new MemberCoalUsedEvent(member));
	}
}
