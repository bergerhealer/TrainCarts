package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

public class TrainCartsListener extends CustomEventListener implements Listener {
	
	public void onCoalUsed(MemberCoalUsedEvent event) {};
	public void onMemberBlockChange(MemberBlockChangeEvent event) {};
	public void onSignActionEvent(SignActionEvent event) {};
	public void onMemberRemove(MemberRemoveEvent event) {};
	public void onMinecartSwap(MinecartSwapEvent event) {};
	public void onGroupForceUpdate(GroupForceUpdateEvent event) {};
	public void onGroupRemove(GroupRemoveEvent event) {};
	public void onGroupSpawn(GroupSpawnEvent event) {};
	
	@Override
	public void onCustomEvent(Event event) {
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
		} else if (event instanceof GroupSpawnEvent) {
			this.onGroupSpawn((GroupSpawnEvent) event);
		} else if (event instanceof SignActionEvent) {
			this.onSignActionEvent((SignActionEvent) event);
		}
	}

}
