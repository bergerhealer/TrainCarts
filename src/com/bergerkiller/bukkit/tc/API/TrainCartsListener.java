package com.bergerkiller.bukkit.tc.API;

import org.bukkit.Bukkit;
import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class TrainCartsListener extends CustomEventListener implements Listener {
	
	public void onCoalUsed(MemberCoalUsedEvent event) {};
	public void onMemberBlockChange(MemberBlockChangeEvent event) {};
	public void onSignAction(SignActionEvent event) {};
	public void onMemberRemove(MemberRemoveEvent event) {};
	public void onMinecartSwap(MinecartSwapEvent event) {};
	public void onGroupForceUpdate(GroupForceUpdateEvent event) {};
	public void onGroupRemove(GroupRemoveEvent event) {};
	public void onGroupCreate(GroupCreateEvent event) {};
	public void onGroupLink(GroupLinkEvent event) {};
	public void onGroupUnload(GroupUnloadEvent event) {};
	public void onMemberAdd(MemberAddEvent event) {};
	
	public final void register(Plugin plugin, Priority priority) {
		Bukkit.getServer().getPluginManager().registerEvent(Event.Type.CUSTOM_EVENT, this, priority, plugin);
	}
	
	@Override
	public final void onCustomEvent(Event event) {
		if (event instanceof MemberCoalUsedEvent) {
			this.onCoalUsed((MemberCoalUsedEvent) event);
		} else if (event instanceof MemberBlockChangeEvent) {
			this.onMemberBlockChange((MemberBlockChangeEvent) event);
		} else if (event instanceof MemberRemoveEvent) {
			this.onMemberRemove((MemberRemoveEvent) event);
		} else if (event instanceof MinecartSwapEvent) {
			this.onMinecartSwap((MinecartSwapEvent) event);
		} else if (event instanceof GroupForceUpdateEvent) {
			this.onGroupForceUpdate((GroupForceUpdateEvent) event);
		} else if (event instanceof GroupRemoveEvent) {
			this.onGroupRemove((GroupRemoveEvent) event);
		} else if (event instanceof GroupCreateEvent) {
			this.onGroupCreate((GroupCreateEvent) event);
		} else if (event instanceof GroupLinkEvent) {
			this.onGroupLink((GroupLinkEvent) event);
		} else if (event instanceof GroupUnloadEvent) {
			this.onGroupUnload((GroupUnloadEvent) event);
		} else if (event instanceof MemberAddEvent) {
			this.onMemberAdd((MemberAddEvent) event);
		} else if (event instanceof SignActionEvent) {
			this.onSignAction((SignActionEvent) event);
		}
	}

}
