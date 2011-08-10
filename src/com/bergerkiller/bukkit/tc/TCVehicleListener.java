package com.bergerkiller.bukkit.tc;

import org.bukkit.entity.Minecart;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleListener;
import org.bukkit.event.vehicle.VehicleUpdateEvent;

public class TCVehicleListener extends VehicleListener {
	private final TrainCarts plugin;
	public TCVehicleListener(final TrainCarts instance) {
		this.plugin = instance;
	}	
	
	@Override
	public void onVehicleBlockCollision(VehicleBlockCollisionEvent event) {
		MinecartGroup g = MinecartGroup.get(event.getVehicle());
		if (g != null) {
			//direct hit or not?
			if (!g.getMember(event.getVehicle()).isTurned()) {
				g.stop();
			}
		}
	}
	

	@Override
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			MinecartGroup.remove((Minecart) event.getVehicle());
			MinecartFixer.removeReplacedCart((Minecart) event.getVehicle());
		}
	}
	
	@Override
	public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
		if (event.getEntity() instanceof Minecart) {
			if (event.getVehicle() instanceof Minecart) {			
				if (TrainCarts.contactLinking) {
					Minecart m1 = (Minecart) event.getEntity();
					Minecart m2 = (Minecart) event.getVehicle();
					if (!MinecartGroup.link(m1, m2)) {
						event.setCancelled(true);
					}
				} else {
					event.setCancelled(true);
				}
			}
		}
	}

	@Override
	public void onVehicleUpdate(VehicleUpdateEvent event) {
		MinecartGroup g = MinecartGroup.get(event.getVehicle());
		if (g != null) {
			g.update();
		}
	}
}
