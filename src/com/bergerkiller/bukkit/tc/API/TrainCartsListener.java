package com.bergerkiller.bukkit.tc.API;

import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

public class TrainCartsListener extends CustomEventListener 
implements Listener {
	
	public void onCoalUsed(CoalUsedEvent event) {};
	public void onMemberBlockChange(MemberBlockChangeEvent event) {};
	public void onSignActionEvent(SignActionEvent event) {};
	
	@Override
	public void onCustomEvent(Event event) {
		if (event instanceof CoalUsedEvent) {
			this.onCoalUsed((CoalUsedEvent) event);
		} else if (event instanceof MemberBlockChangeEvent) {
			this.onMemberBlockChange((MemberBlockChangeEvent) event);
		} else if (event instanceof SignActionEvent) {
			this.onSignActionEvent((SignActionEvent) event);
		}
	}

}
