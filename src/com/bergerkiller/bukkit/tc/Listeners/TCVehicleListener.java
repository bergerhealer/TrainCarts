package com.bergerkiller.bukkit.tc.Listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleListener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;

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
	
	public static void handleStation(MinecartGroup group, Block railsblock, Block signblock, double length) {
		//Get the middle minecart
		MinecartMember midd = group.getMember((int) Math.floor((double) group.size() / 2));
		
		//Check if not already targeting
		if (!midd.hasTarget()) {
			//First, get the direction of the tracks above
			BlockFace dir = Util.getRails(railsblock).getDirection();
			//Get the length of the track to center in
			if (length == 0) {
				//manually calculate the length
				//use the amount of straight blocks
				for (BlockFace face : Util.getFaces(dir)) {
					int tlength = 0;
					//get the type of rail required
					BlockFace checkface = face;
					if (checkface == BlockFace.NORTH)
						checkface = BlockFace.SOUTH;
					if (checkface == BlockFace.EAST)
						checkface = BlockFace.WEST;
					
					Block b = railsblock;
					int maxlength = 20;
					while (true) {
						//Next until invalid
						b = b.getRelative(face);
						Rails rr = Util.getRails(b);
						if (rr == null || rr.getDirection() != checkface)
							break;
						tlength++;
						
						//prevent inf. loop or long processing
						maxlength--;
						if (maxlength <= 0) break;
					}
					//Update the length
					if (length == 0 || tlength < length) length = tlength;
				}
			}
			

			//What do we do?
			//which directions to move, or brake?
			Location l = signblock.getLocation().add(0, 2, 0);
			if (dir == BlockFace.WEST) {
				boolean west = signblock.getRelative(BlockFace.WEST).isBlockIndirectlyPowered();
				boolean east = signblock.getRelative(BlockFace.EAST).isBlockIndirectlyPowered();
				if (west && !east) {
					midd.setTarget(l.add(0, 0, length), TrainCarts.maxCartSpeed);
				} else if (!west && east) {
					midd.setTarget(l.add(0, 0, -length), TrainCarts.maxCartSpeed);
				} else if (west && east) {
					if (midd.getMinecart().getVelocity().length() > 0.1 && l.distance(midd.getLocation()) > 1) {
						midd.setTarget(l, 0);
					} else {
						group.stop();
					}
				}
			} else if (dir == BlockFace.SOUTH) {
				boolean north = signblock.getRelative(BlockFace.NORTH).isBlockIndirectlyPowered();
				boolean south = signblock.getRelative(BlockFace.SOUTH).isBlockIndirectlyPowered();
				if (north && !south) {
					midd.setTarget(l.add(-length, 0, 0), TrainCarts.maxCartSpeed);
				} else if (!north && south) {
					midd.setTarget(l.add(length, 0,  0), TrainCarts.maxCartSpeed);
				} else if (north && south) {
					if (midd.getMinecart().getVelocity().length() > 0.1 && l.distance(midd.getLocation()) > 1) {
						midd.setTarget(l, 0);
					} else {
						group.stop();
					}
				}
			}
		}
	}
	
	@Override
	public void onVehicleMove(VehicleMoveEvent event) {
		MinecartMember mm = MinecartMember.get(event.getVehicle());
		if (mm != null) {
			if (mm.getGroup() != null) {
			    Block sb = mm.getSignBlock();
				Sign s = Util.getSign(sb);
				if (s != null) {
					if (s.getLine(0).equals("[train]")) {
						if (s.getLine(1).equals("station")) {
							double length = 0;
							try {
								length = Double.parseDouble(s.getLine(2));
							} catch (Exception ex) {};
							handleStation(mm.getGroup(), mm.getRailsBlock(), sb, length);
						}
					}
				}
			}
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
