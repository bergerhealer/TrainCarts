package com.bergerkiller.bukkit.tc.Listeners;

import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

import com.afforess.minecartmaniacore.event.MinecartCaughtEvent;
import com.afforess.minecartmaniacore.event.MinecartClickedEvent;
import com.bergerkiller.bukkit.tc.MinecartMember;

public class TCCustomListener extends CustomEventListener implements Listener {
		
	public void onCaught(MinecartCaughtEvent event) {
		MinecartMember mm = MinecartMember.get(event.getMinecart().minecart);
		if (mm != null && mm.getGroup() != null) {
			if (mm == mm.getGroup().head()) {
				mm.getGroup().stop();
			} else {
				event.setActionTaken(false);
			}
		}
	}
	
	public void onClicked(MinecartClickedEvent event) {
		MinecartMember mm = MinecartMember.get(event.getMinecart().minecart);
		if (mm != null && mm.getGroup() != null) {
			if (mm.isMoving()) mm.getGroup().stop();
		}
	}

	public void onCustomEvent(Event event){
		if (event instanceof MinecartCaughtEvent) {
			this.onCaught((MinecartCaughtEvent) event);
		} else if (event instanceof MinecartClickedEvent) {
			this.onClicked((MinecartClickedEvent) event);
		}
	}

}
