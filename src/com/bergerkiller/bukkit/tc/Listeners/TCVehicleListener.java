package com.bergerkiller.bukkit.tc.listeners;

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

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.utils.EntityUtil;

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
			if (MinecartMember.canConvert(event.getVehicle())) {
				new Task(TrainCarts.plugin, event.getVehicle(), lastPlayer) {
					public void run() {
						MinecartMember mm = MinecartMember.convert(getArg(0));
						Player lp = (Player) getArg(1);
						if (mm != null) {
							if (lp != null) {
								mm.getGroup().getProperties().setDefault(lp);
								if (TrainCarts.setOwnerOnPlacement) {
									mm.getProperties().setOwner(lp);
								}
								mm.setEditing(lp);
							}
						}
					}
				}.startDelayed(0);
				lastPlayer = null;
			}
		}
	}
		
	@Override
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (!event.isCancelled()) {
			MinecartMember member = MinecartMember.get(event.getVehicle());
			if (member != null) {
				CartProperties prop = member.getProperties();
				if (event.getEntered() instanceof Player) {
//					if (prop.isPassenger(event.getEntered()) && prop.allowPlayerEnter) {
//						prop.showEnterMessage(event.getEntered());
//					} else {
//						event.setCancelled(true);
//					}
					prop.showEnterMessage(event.getEntered());
					//TODO: PASSENGERS!
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
				MinecartMember mm = MinecartMember.get(event.getVehicle());
				if (mm != null) {
					Player p = (Player) event.getAttacker();
					if (mm.getProperties().hasOwnership(p)) {
						mm.setEditing(p);
					} else {
						event.setCancelled(true);
					}
				}
			}
		}
	}
			
	@Override
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		if (!event.isCancelled()) {
			MinecartMember mm = MinecartMember.get(event.getVehicle());
			if (mm != null) {
				mm.setActiveSign(null);
				mm.remove();
			}
		}
	}
	
	@Override
	public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
		if (event.getVehicle() instanceof Minecart && !event.getVehicle().isDead()) {
			MinecartMember mm1 = MinecartMember.convert(event.getVehicle());
			if (mm1 != null) {
				if (mm1.getGroup().isActionWait()) {
					event.setCancelled(true);
				} else if (mm1.isCollisionIgnored(event.getEntity())) {
					event.setCancelled(true);
				} else {
					TrainProperties prop = mm1.getGroup().getProperties();
					if (event.getEntity() instanceof Minecart) {
						MinecartMember mm2 = MinecartMember.convert(event.getEntity());
						if (mm2 == null || mm1.getGroup() == mm2.getGroup() || MinecartGroup.link(mm1, mm2)) {
							event.setCancelled(true);
						} else if (mm2.getGroup().isActionWait()) {
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

}
