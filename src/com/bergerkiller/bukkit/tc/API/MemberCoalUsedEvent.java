package com.bergerkiller.bukkit.tc.API;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;

public class MemberCoalUsedEvent extends MemberEvent {
	private static final long serialVersionUID = 1L;

	private boolean useCoal;
	private boolean refill;
	
	public MemberCoalUsedEvent(final MinecartMember source) {
		super("CoalUsedEvent", source);
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
	
	public static MemberCoalUsedEvent call(final MinecartMember member) {
		return Util.call(new MemberCoalUsedEvent(member));
	}

}
