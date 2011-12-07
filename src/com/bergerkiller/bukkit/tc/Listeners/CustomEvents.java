package com.bergerkiller.bukkit.tc.Listeners;

import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.server.EntityPlayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.mw.Localization;
import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.Permission;
import com.bergerkiller.bukkit.mw.Portal;
import com.bergerkiller.bukkit.tc.ArrivalSigns;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.Properties;
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent.ActionType;
import com.bergerkiller.bukkit.tc.Utils.BlockUtil;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;
import com.bergerkiller.bukkit.tc.Utils.FaceUtil;

public class CustomEvents {

	public static void handleStation(SignActionEvent info) {
		//Check if not already targeting
		MinecartGroup group = info.getGroup();
		if (group != null && info.hasRails()) {		
			//Not already targeting from this station?


			//Get station length
			if (!info.getLine(0).equalsIgnoreCase("[train]")) return;
			if (!info.getLine(1).toLowerCase().startsWith("station")) return;
			double length = 0;
			try {
				length = Double.parseDouble(info.getLine(1).substring(7).trim());
			} catch (Exception ex) {};
			long delayMS = 0;
			try {
				delayMS = (long) (Double.parseDouble(info.getLine(2)) * 1000);
			} catch (Exception ex) {};
			//Get the mode used
			int mode = 0;
			if (info.getLine(3).equalsIgnoreCase("continue")) {
				mode = 1;
			} else if (info.getLine(3).equalsIgnoreCase("reverse")) {
				mode = 2;
			} else if (info.getLine(3).equalsIgnoreCase("left")) {
				mode = 3;
			} else if (info.getLine(3).equalsIgnoreCase("right")) {
				mode = 4;
			}

			//Get the middle minecart
			MinecartMember midd = group.middle();
			//First, get the direction of the tracks above
			BlockFace dir = info.getRailDirection();
			//Get the length of the track to center in
			if (length == 0) {
				//manually calculate the length
				//use the amount of straight blocks
				for (BlockFace face : FaceUtil.getFaces(dir)) {
					int tlength = 0;
					//get the type of rail required
					BlockFace checkface = face;
					if (checkface == BlockFace.NORTH)
						checkface = BlockFace.SOUTH;
					if (checkface == BlockFace.EAST)
						checkface = BlockFace.WEST;

					Block b = info.getRails();
					int maxlength = 20;
					while (true) {
						//Next until invalid
						b = b.getRelative(face);
						Rails rr = BlockUtil.getRails(b);
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
			boolean west = info.isPowered(BlockFace.WEST);
			boolean east = info.isPowered(BlockFace.EAST);
			boolean north = info.isPowered(BlockFace.NORTH);
			boolean south = info.isPowered(BlockFace.SOUTH);

			//which directions to move, or brake?
			BlockFace instruction = BlockFace.UP; //SELF is brake
			if (dir == BlockFace.WEST) {
				if (west && !east) {
					instruction = BlockFace.WEST;
				} else if (east && !west) {
					instruction = BlockFace.EAST;
				} else {
					instruction = BlockFace.SELF;
				}
			} else if (dir == BlockFace.SOUTH) {
				if (north && !south) {
					instruction = BlockFace.NORTH;
				} else if (south && !north) {
					instruction = BlockFace.SOUTH;
				} else {
					instruction = BlockFace.SELF;
				}
			}
			if (instruction == BlockFace.UP) return; 

			TrainProperties prop = group.getProperties();

			//What do we do?
			Location l = info.getRailLocation();
			if (instruction == BlockFace.SELF) {
				if (north || east || south || west) {
					//Redstone change and moving?
					if (!info.isAction(ActionType.REDSTONE_CHANGE) || !info.getMember().isMoving()) {
						//Brake
						midd.setTarget(l, 0, 0);
						prop.setStation(true);
						BlockFace trainDirection = null;
						if (mode == 1) {
							//Continue
							trainDirection = midd.getDirection();
						} else if (mode == 2) {
							//Reverse
							trainDirection = midd.getDirection().getOppositeFace();
						} else if (mode == 3 || mode == 4) {
							//Relative left/right
							BlockFace signdir = info.getFacing();
							//Convert :)
							float yaw = FaceUtil.faceToYaw(signdir);
							if (mode == 3) {
								//Left
								yaw += 90;
							} else {
								//Right
								yaw -= 90;
							}
							//Apply
							trainDirection = FaceUtil.yawToFace(yaw);					
						} else {
							l = null; //Nothing
						}
						if (l != null) {
							//Actual launching here
							l = l.add(trainDirection.getModX() * length, 0, trainDirection.getModZ() * length);
							midd.addTarget(l, midd.maxSpeed, delayMS);
						}
					}
				}
			} else {
				//Launch
				prop.setStation(true);
				group.clearTargets();
				Location next = l.clone().add(instruction.getModX() * length, 0, instruction.getModZ() * length);
				MinecartMember head = group.head();
				if (head.isMoving() && head.getDirection() != instruction) {
					//Reversing, need to center it in the middle first
					midd.addTarget(l, 0, 0);
				}
				midd.addTarget(next, midd.maxSpeed, delayMS);
			}
		}
	}
	public static void spawnTrain(SignActionEvent info) {
		double force = 0;
		try {
			force = Double.parseDouble(info.getLine(1).substring(5).trim());
		} catch (Exception ex) {};

		//Get the cart types to spawn
		ArrayList<Integer> types = new ArrayList<Integer>();
		for (char cart : (info.getLine(2) + info.getLine(3)).toCharArray()) {
			if (cart == 'm') {
				types.add(0);
			} else if (cart == 's') {
				types.add(1);
			} else if (cart == 'p') {
				types.add(2);
			}
		}

		if (types.size() == 0) return;

		BlockFace dir = info.getFacing();
		Location[] locs = TrackMap.walk(info.getRails(), dir, types.size(), TrainCarts.cartDistance);

		//Check if spot is taken
		for (int i = 0;i < locs.length;i++) {
			if (MinecartMember.getAt(locs[i]) != null) return;
		}		
		
		//Spawn the group
		MinecartGroup.spawn(info.getRails(), info.getFacing(), types, force);
	}

	public static void deinit() {
		teleportTimes.clear();
		teleportTimes = null;
	}
	
	private static HashMap<Location, Long> teleportTimes = new HashMap<Location, Long>();
	private static void setTPT(Location at) {
		teleportTimes.put(at, System.currentTimeMillis());
	}
	private static boolean getTPT(SignActionEvent info) {
		if (!teleportTimes.containsKey(info.getLocation())) return true;
		long time = teleportTimes.get(info.getLocation());
		return ((System.currentTimeMillis() - time) > MyWorlds.teleportInterval);
	}
	public static void teleportTrain(SignActionEvent info) {
		Portal portal = Portal.get(info.getLocation());
		if (portal != null) {
			String destname = portal.getDestinationName();
			Location dest = Portal.getPortalLocation(destname, info.getGroup().getWorld().getName());
			if (dest != null) {
				//Teleport the ENTIRE train to the destination...
				Block sign = dest.getBlock();
				sign.getChunk(); //load the chunk
				if (BlockUtil.isSign(sign)) {
					BlockFace facing = BlockUtil.getFacing(sign);
					BlockFace direction = facing;
					Block destinationRail = sign.getRelative(0, 2, 0);
					if (BlockUtil.isRails(destinationRail)) {
						//rail aligned at sign?
						if (facing == BlockFace.NORTH) facing = BlockFace.SOUTH;
						if (facing == BlockFace.EAST) facing = BlockFace.WEST;
						if (facing == BlockUtil.getRails(destinationRail).getDirection()) {
							//Allowed?
							if (getTPT(info)) {
								//Teleportation is allowed: get info from source
								double force = info.getGroup().getAverageForce();

								//Spawn a new group and transfer data
								MinecartGroup gnew = MinecartGroup.spawn(destinationRail, direction, info.getGroup().getTypes(), force);
								gnew.setProperties(info.getGroup().getProperties());
								info.getGroup().setProperties(null);
								
								//Transfer individual data and teleport passengers
								for (int i = 0; i < gnew.size(); i++) {
									MinecartMember from = info.getGroup().get(i);
									MinecartMember to = gnew.get(i);
									//Set important data
									EntityUtil.transferItems(from, to);
									to.b = from.b;
									to.c = from.c;
									to.e = from.e;

									//Teleport passenger

									if (from.passenger != null) {
										net.minecraft.server.Entity e = from.passenger;
										from.getMinecart().eject();

										boolean transfer = true;
										if (e instanceof EntityPlayer) {
											Player p = (Player) e.getBukkitEntity();
											//has permission?
											if (Permission.canEnterWorld(p, dest.getWorld().getName())) {
												if (Permission.canEnterPortal(p, destname)) {
													//Has permission, show message
													p.sendMessage(Localization.getPortalEnter(portal));
												} else {
													Localization.message(p, "portal.noaccess");
													transfer = false;
												}
											} else {
												Localization.message(p, "world.noaccess");
												transfer = false;
											}
										}
										if (transfer) {
											Task t = new Task(TrainCarts.plugin, e.getBukkitEntity(), to.getLocation()) {
												public void run() {
													Entity e = (Entity) getArg(0);
													Location to = (Location) getArg(1);
													e.teleport(to);
												}
											};
											t.startDelayed(0);
											t = new Task(TrainCarts.plugin, e, to) {
												public void run() {
													net.minecraft.server.Entity e = (net.minecraft.server.Entity) getArg(0);
													MinecartMember mnew = (MinecartMember) getArg(1);
													e.setPassengerOf(mnew);
												}
											};
											t.startDelayed(1);
										}
									}
								}

								//Remove the old group
								for (MinecartMember mm : info.getGroup()) mm.dead = true;
								info.getGroup().remove();

								setTPT(destinationRail.getLocation().add(0, -2, 0));
							}
						}
					}
				}
			}
		}
	}

	private static void handleProperties(TrainProperties tprop, String mode, String arg) {
		Properties prop = tprop.getSaved();
		if (mode.equals("addtag")) {
			prop.addTags(arg);
		} else if (mode.equals("settag")) {
			prop.setTags(arg);
		} else if (mode.equals("destination")) {
			prop.destination = arg;
		} else if (mode.equals("remtag")) {
			prop.tags.remove(arg);
		} else if (mode.equals("collision") || mode.equals("collide")) {
			prop.trainCollision = Util.getBool(arg);
		} else if (mode.equals("linking") || mode.equals("link")) {
			prop.allowLinking = Util.getBool(arg);
		} else if (mode.equals("mobenter") || mode.equals("mobsenter")) {
			prop.allowMobsEnter = Util.getBool(arg);
		} else if (mode.equals("slow") || mode.equals("slowdown")) {
			prop.slowDown = Util.getBool(arg);
		} else if (mode.equals("setdefault") || mode.equals("default")) {
			tprop.setDefault(arg);
		} else if (mode.equals("pushmobs")) {
			prop.pushMobs = Util.getBool(arg);
		} else if (mode.equals("pushplayers")) {
			prop.pushPlayers = Util.getBool(arg);
		} else if (mode.equals("pushmisc")) {
			prop.pushMisc = Util.getBool(arg);
		} else if (mode.equals("push") || mode.equals("pushing")) {
			prop.pushMobs = Util.getBool(arg);
			prop.pushPlayers = prop.pushMobs;
			prop.pushMisc = prop.pushMobs;
		} else if (mode.equals("playerenter")) {
			prop.allowPlayerEnter = Util.getBool(arg);
		} else if (mode.equals("playerexit")) {
			prop.allowPlayerExit = Util.getBool(arg);
		} else if (mode.equals("speedlimit") || mode.equals("maxspeed")) {
			try {
				prop.speedLimit = Double.parseDouble(arg);
			} catch (NumberFormatException ex) {
				prop.speedLimit = 0.4;
			}
		}
		tprop.restore();
	}

	public static void onSign(SignActionEvent info, ActionType action) {
		info.setAction(action);
		onSign(info);
	}
	public static void onSign(SignActionEvent info) {
		if (info.getSign() == null) return;
		//Event
		info.setCancelled(false);
		Bukkit.getServer().getPluginManager().callEvent(info);
		if (info.isCancelled()) return;

		if (info.isAction(ActionType.REDSTONE_ON)) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				String secondline = info.getLine(1).toLowerCase();
				if (secondline.startsWith("spawn")) {
					spawnTrain(info);
				}
			}
		}

		if (info.isAction(ActionType.REDSTONE_CHANGE, ActionType.GROUP_ENTER, ActionType.GROUP_LEAVE)) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				if (info.getLine(1).toLowerCase().startsWith("station")) {
					if (info.hasRails()) {
						MinecartGroup group = info.getGroup();
						if (group != null) {
							if (info.isAction(ActionType.GROUP_LEAVE)) {
								group.getProperties().setStation(false);
							} else if (!info.isPowered()) {
								group.clearTargets();
								group.getProperties().setStation(false);
							} else {
								handleStation(info);
							}
						}
						if (!info.isAction(ActionType.REDSTONE_CHANGE)) {
							info.setLevers(info.isAction(ActionType.GROUP_ENTER));
						}
					}
				}
			}
		}

		if (info.isAction(ActionType.REDSTONE_ON, ActionType.GROUP_ENTER)) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				if (info.getLine(1).equalsIgnoreCase("property")) {
					if (info.isAction(ActionType.REDSTONE_ON) || (info.isPowered() && info.isFacing())) {
						if (info.getGroup() != null) {
							//Handle property changes
							String mode = info.getLine(2).toLowerCase().trim();
							TrainProperties prop = info.getGroup().getProperties();
							handleProperties(prop, mode, info.getLine(3));
						}
					}
				}
			}
		}


		if (info.isAction(ActionType.REDSTONE_ON, ActionType.GROUP_ENTER, ActionType.REDSTONE_OFF)) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				if (info.getLine(1).toLowerCase().startsWith("trigger")) {
					if (info.isAction(ActionType.REDSTONE_ON) || info.isFacing()) {
						ArrivalSigns.trigger(info.getSign(), info.getMember());
					} else if (info.isAction(ActionType.REDSTONE_OFF)) {
						ArrivalSigns.timeCalcStop(info.getLocation());
					}
				}
			}
		}

		if (TrainCarts.MyWorldsEnabled && info.isAction(ActionType.GROUP_ENTER, ActionType.REDSTONE_ON)) {
			if (info.getGroup() != null) {
				if (info.isAction(ActionType.REDSTONE_ON) || (info.isFacing() && info.getGroup().isMoving() && info.isPowered())) {
					teleportTrain(info);
				}
			}
		}

		if (info.isAction(ActionType.GROUP_ENTER) || info.isAction(ActionType.GROUP_LEAVE)) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				if (info.getLine(1).toLowerCase().startsWith("tag")) {
					TrainProperties prop = info.getGroup().getProperties();
					if (!prop.destination.isEmpty()){
						//Handle rails based on destination
						if (info.isAction(ActionType.GROUP_ENTER)){
							BlockFace check = info.getDestDir(prop.destination);
							if (check != BlockFace.UP){
								info.setRailsFromCart(check);
								return; //do not parse further if destination is found
							}
						}
					}
					//Toggle levers and rails based on tags
					boolean down = false;
					if (info.isAction(ActionType.GROUP_ENTER) && info.isFacing()) {
						//get the tags    
						boolean left = prop.hasTag(info.getLine(2));         
						boolean right = prop.hasTag(info.getLine(3));          
						down = left || right;         
						if (info.isPowered()) {   
							BlockFace dir = BlockFace.NORTH;  
							if (left) dir = BlockFace.WEST;  
							if (right) dir = BlockFace.EAST; 
							info.setRailsRelative(dir);
						}
					}
					info.setLevers(down);
				}
			}
		}

		if (info.isAction(ActionType.REDSTONE_ON, ActionType.MEMBER_ENTER)) {
			if (info.isFacing() && info.getLine(0).equalsIgnoreCase("[train]")) {
				if (info.getLine(1).equalsIgnoreCase("destroy") && info.isPowered()) {
					if (info.getMember() != null) {
						info.getMember().destroy();
					}
				} else if (info.getLine(1).equalsIgnoreCase("destroy all") && info.isPowered() ) {
					if (info.getGroup() != null) {
						MinecartGroup group = info.getGroup();
						group.destroy();
					}
				} else if (info.getLine(1).toLowerCase().startsWith("eject") && info.isPowered()) {
					String[] offsettext = info.getLine(2).split("/");
					Vector offset = new Vector();
					if (offsettext.length == 3) {
						offset.setX(Util.tryParse(offsettext[0], 0));
						offset.setY(Util.tryParse(offsettext[1], 0));
						offset.setZ(Util.tryParse(offsettext[2], 0));
					} else if (offsettext.length == 1) {
						offset.setY(Util.tryParse(offsettext[0], 0));
					}
					if (info.getLine(1).equalsIgnoreCase("eject all") && info.getGroup() != null) {
						for (MinecartMember mm : info.getGroup()) {
							if (offset.equals(new Vector())) {
								mm.eject();
							} else {
								mm.eject(offset);
							}
						}
					} else if (info.getMember() != null) {
						if (offset.equals(new Vector())) {
							info.getMember().eject();
						} else {
							info.getMember().eject(offset);
						}
					}
				}
			}
		}
	}
	
}
