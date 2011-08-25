package com.bergerkiller.bukkit.tc.API;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import com.bergerkiller.bukkit.tc.MinecartGroup;

public class GroupUpdateEvent extends Event
implements Cancellable {
	private static final long serialVersionUID = 1L;
	private MinecartGroup group;
	private UpdateStage stage;
	private boolean cancelled = false;
	
	public GroupUpdateEvent(MinecartGroup group, UpdateStage stage) {
		super("GroupUpdateEvent");
		this.group = group;
		this.stage = stage;
	}
		
	public MinecartGroup getGroup() {
		return this.group;
	}
	public UpdateStage getStage() {
		return this.stage;
	}
	
	public static boolean call(MinecartGroup group, UpdateStage stage) {
		GroupUpdateEvent g = new GroupUpdateEvent(group, stage);
		Bukkit.getServer().getPluginManager().callEvent(g);
		return !g.isCancelled();
	}
	
	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}
	@Override
	public void setCancelled(boolean arg0) {
		this.cancelled = arg0;
	}
	
}
