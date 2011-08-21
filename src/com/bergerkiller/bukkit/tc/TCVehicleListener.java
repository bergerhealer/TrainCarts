package com.bergerkiller.bukkit.tc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleListener;

public class TCVehicleListener extends VehicleListener {
	
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
	public void onVehicleExit(VehicleExitEvent event) {
		if (!event.isCancelled() && event.getVehicle() instanceof Minecart) {
			Minecart m = (Minecart) event.getVehicle();
			Location loc = m.getLocation();
			loc.setYaw(Util.getMinecartYaw(m) + 180);
			loc = Util.move(loc, TrainCarts.exitOffset);
			//teleport
			Task t = new Task(TrainCarts.plugin, event.getExited(), loc) {
				public void run() {
					Entity e = (Entity) getArg(0);
					Location loc = (Location) getArg(1);
					loc.setYaw(e.getLocation().getYaw());
					loc.setPitch(e.getLocation().getPitch());
					e.teleport(loc);
				}
			};
			t.startDelayed(1);
		}
	}
	
	@Override
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			MinecartGroup.remove((Minecart) event.getVehicle());
		}
	}
	
	@Override
	public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			if (event.getEntity() instanceof Minecart) {			
				Minecart m1 = (Minecart) event.getEntity();
				Minecart m2 = (Minecart) event.getVehicle();
				if (!MinecartGroup.link(m1, m2)) {
					event.setCancelled(true);
				}
			} else if (Util.pushAway((Minecart) event.getVehicle(), event.getEntity())) {
				event.setCancelled(true);
			}
		}
	}

}
