package com.bergerkiller.bukkit.tc.Listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
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
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent.ActionType;
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
		if (event.getVehicle() instanceof Minecart && !MinecartGroup.isDisabled) {
			if (!(EntityUtil.getNative(event.getVehicle()) instanceof MinecartMember)) {
				MinecartGroup g = MinecartGroup.create(event.getVehicle());
				if (lastPlayer != null) {
					g.getProperties().setDefault(lastPlayer);
					lastPlayer = null;
				}
				if (g.size() != 0) {
					updateSign(g.head());
				}
			}
		}
	}
		
	@Override
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (!event.isCancelled() && event.getEntered() instanceof Player) {
			MinecartGroup g = MinecartGroup.get(event.getVehicle());
			if (g != null) {
				TrainProperties prop = g.getProperties();
				if (prop.isPassenger(event.getEntered())) {
					prop.showEnterMessage(event.getEntered());
				} else {
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
	
	private void updateSign(MinecartMember mm) {
		if (mm != null) {
			Block signblock = mm.getSignBlock();
			if (signblock != null) {
				SignActionEvent info = new SignActionEvent(signblock, mm);
				CustomEvents.onSign(info, ActionType.MEMBER_MOVE);
				if (!mm.isActiveSign(signblock)) {
					mm.setActiveSign(signblock);
					CustomEvents.onSign(info, ActionType.MEMBER_ENTER);
				}
				if (!mm.getGroup().getSignActive(signblock)) {
					mm.getGroup().setSignActive(signblock, true);
					CustomEvents.onSign(info, ActionType.GROUP_ENTER);
				}
			} else if (mm.hasActiveSign()) {
				if (mm == mm.getGroup().tail()) {
					signblock = mm.getActiveSign();
					mm.getGroup().setSignActive(signblock, false);
					CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_LEAVE, signblock, mm));
				}
				mm.setActiveSign(null);
			}
		}
	}
	
	@Override
	public void onVehicleMove(VehicleMoveEvent event) {
		updateSign(MinecartMember.get(event.getVehicle()));
	}
	
	@Override
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		if (!event.isCancelled()) {
			if (event.getVehicle() instanceof Minecart) {
				MinecartMember mm = MinecartMember.get(event.getVehicle());
				if (mm != null) mm.remove();
			}
		}
	}
	
	@Override
	public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			MinecartMember mm1 = MinecartMember.get(event.getVehicle());
			if (mm1 != null) {
				if (event.getEntity() instanceof Minecart) {
					MinecartMember mm2 = MinecartMember.get(event.getEntity());
					if (MinecartGroup.isInSameGroup(mm1, mm2) || MinecartGroup.link(mm1, mm2)) {
						event.setCancelled(true);
					}
				}
				if (!mm1.getGroup().getProperties().canCollide(event.getEntity())) {
					event.setCancelled(true);
				} else if (EntityUtil.pushAway(mm1, event.getEntity())) {
					event.setCancelled(true);
				}
			}
		}
	}

}
