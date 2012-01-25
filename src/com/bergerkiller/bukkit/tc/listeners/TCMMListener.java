package com.bergerkiller.bukkit.tc.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.afforess.minecartmaniacore.event.MinecartCaughtEvent;
import com.afforess.minecartmaniacore.event.MinecartClickedEvent;
import com.bergerkiller.bukkit.tc.MinecartMember;

/*
 * Unused for now, we need Minecart Mania to add support manually
 * Class is still here may Minecart Mania add support for 1.1
 */
public class TCMMListener implements Listener {
		
	@EventHandler(priority = EventPriority.LOWEST)
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
	@EventHandler(priority = EventPriority.LOWEST)
	public void onClicked(MinecartClickedEvent event) {
		MinecartMember mm = MinecartMember.get(event.getMinecart().minecart);
		if (mm != null && mm.getGroup() != null) {
			if (mm.isMoving()) mm.getGroup().stop();
		}
	}

}
