package com.bergerkiller.bukkit.tc.Listeners;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleListener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;

public class TCVehicleListener extends VehicleListener {
	
	@Override
	public void onVehicleBlockCollision(VehicleBlockCollisionEvent event) {
		MinecartMember mm = MinecartMember.get(event.getVehicle());
		if (mm != null) {
			//direct hit or not?
			if (!mm.isTurned()) {
				mm.getGroup().stop();
			}
		}
	}
	
	@Override
	public void onVehicleExit(VehicleExitEvent event) {
		if (!event.isCancelled() && event.getVehicle() instanceof Minecart) {			
			Minecart m = (Minecart) event.getVehicle();
			Location loc = m.getLocation();
			loc.setYaw(EntityUtil.getMinecartYaw(m) + 180);
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
			t.startDelayed(0);
		}
	}
	
	public static Player lastPlayer = null;
	@Override
	public void onVehicleCreate(VehicleCreateEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			if (!(EntityUtil.getNative(event.getVehicle()) instanceof MinecartMember)) {
				MinecartGroup g = MinecartGroup.create(event.getVehicle());
				if (g.size() != 0) {
					if (lastPlayer != null) {
						g.getProperties().setDefault(lastPlayer);
						g.getProperties().setEditing(lastPlayer);
						lastPlayer = null;
					}
				}
			}
		}
	}
		
	@Override
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (!event.isCancelled()) {
			MinecartGroup g = MinecartGroup.get(event.getVehicle());
			if (g != null) {
				TrainProperties prop = g.getProperties();
				if (event.getEntered() instanceof Player) {
					if (prop.isPassenger(event.getEntered()) && prop.allowPlayerEnter) {
						prop.showEnterMessage(event.getEntered());
					} else {
						event.setCancelled(true);
					}
				} else if (!prop.allowMobsEnter) {
					event.setCancelled(true);
				}
			}
		}
	}
	
	@Override
	public void onVehicleDamage(VehicleDamageEvent event) {
		if (event.getAttacker() != null && event.getAttacker() instanceof Player) {
			if (!event.isCancelled()) {
				MinecartGroup g = MinecartGroup.get(event.getVehicle());
				if (g != null) {
					Player p = (Player) event.getAttacker();
					TrainProperties prop = g.getProperties();
					if (prop.isOwner(p)) {
						prop.setEditing(p);
					} else {
						event.setCancelled(true);
					}
				}
			}
		}
	}
		
	@Override
	public void onVehicleMove(VehicleMoveEvent event) {
		MinecartMember mm = MinecartMember.get(event.getVehicle());
		if (mm != null) mm.updateActiveSign();
	}
	
	@Override
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		if (!event.isCancelled()) {
			MinecartMember mm = MinecartMember.get(event.getVehicle());
			if (mm != null) mm.remove();
		}
	}
	
	@Override
	public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			MinecartMember mm1 = MinecartMember.convert(event.getVehicle());
			if (mm1 != null) {
				TrainProperties prop = mm1.getGroup().getProperties();
				if (event.getEntity() instanceof Minecart) {
					MinecartMember mm2 = MinecartMember.convert(event.getEntity());
					if (MinecartGroup.isInSameGroup(mm1, mm2) || MinecartGroup.link(mm1, mm2)) {
						event.setCancelled(true);
					}
				} else if (prop.canPushAway(event.getEntity())) {
					mm1.push(event.getEntity());
					event.setCancelled(true);
				} else if (!prop.canCollide(event.getEntity())) {
					event.setCancelled(true);
				}
			}
		}
	}

}
