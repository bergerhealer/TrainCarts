package com.bergerkiller.bukkit.tc.Listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleListener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

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
		if (mm != null && mm.grouped()) {
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
	
	@Override
	public void onVehicleCreate(VehicleCreateEvent event) {
		if (event.getVehicle() instanceof Minecart && !MinecartGroup.isDisabled) {
			Minecart m = (Minecart) event.getVehicle();
			if (!MinecartMember.isMember(m)) {
				MinecartGroup.load(new MinecartGroup(m));
			}
		}
	}
		
	@Override
	public void onVehicleMove(VehicleMoveEvent event) {
		MinecartMember mm = MinecartMember.get(event.getVehicle());
		if (mm != null) {
			Block signblock = mm.getSignBlock();
			if (signblock != null) {
				SignActionEvent info = new SignActionEvent(signblock, mm);
				CustomEvents.onSign(info, ActionType.MEMBER_MOVE);
				if (!mm.isActiveSign(signblock)) {
					mm.setActiveSign(signblock);
					CustomEvents.onSign(info, ActionType.MEMBER_ENTER);
				}
				if (mm.grouped()) {
					if (!mm.getGroup().getSignActive(signblock)) {
						mm.getGroup().setSignActive(signblock, true);
						CustomEvents.onSign(info, ActionType.GROUP_ENTER);
					}
				}
			} else if (mm.hasActiveSign()) {
				if (mm.grouped()) {
					if (mm == mm.getGroup().tail()) {
						signblock = mm.getActiveSign();
						mm.getGroup().setSignActive(signblock, false);
						CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_LEAVE, signblock, mm));
					}
				}
				mm.setActiveSign(null);
			}
		}
	}
	
	@Override
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		if (!event.isCancelled()) {
			if (event.getVehicle() instanceof Minecart) {
				event.getVehicle().remove();
				MinecartMember.remove((Minecart) event.getVehicle());
			}
		}
	}
	
	@Override
	public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
		if (event.getVehicle() instanceof Minecart) {
			if (event.getEntity() instanceof Minecart) {			
				Minecart m1 = (Minecart) event.getEntity();
				Minecart m2 = (Minecart) event.getVehicle();
				if (MinecartGroup.isInSameGroup(m1, m2)) {
					event.setCancelled(true);
				} else {
					MinecartGroup.link(m1, m2);
				}
			} else if (EntityUtil.pushAway((Minecart) event.getVehicle(), event.getEntity())) {
				event.setCancelled(true);
			}
		}
	}

}
