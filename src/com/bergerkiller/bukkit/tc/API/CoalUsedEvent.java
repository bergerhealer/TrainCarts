package com.bergerkiller.bukkit.tc.API;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrainCarts;

public class CoalUsedEvent extends Event {
	private static final long serialVersionUID = -4536478305019224422L;
	private MinecartMember member;
	private boolean useCoal;
	private boolean refill;
	
	public CoalUsedEvent(MinecartMember source) {
		super("CoalUsedEvent");
		this.member = source;
		this.useCoal = TrainCarts.useCoalFromStorageCart.get();
		this.refill = false;
	}
	
	public MinecartMember getMember() {
		return this.member;
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
	
	public static CoalUsedEvent call(MinecartMember member) {
		CoalUsedEvent c = new CoalUsedEvent(member);
		Bukkit.getServer().getPluginManager().callEvent(c);
		return c;
	}

}
