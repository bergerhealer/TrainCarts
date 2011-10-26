package com.bergerkiller.bukkit.tc.Listeners;

import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.server.EntityPlayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
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
import com.bergerkiller.bukkit.tc.Task;
import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.VelocityTarget;
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
			
			VelocityTarget lastTarget = null;
			TrainProperties prop = group.getProperties();
			
			//What do we do?
			Location l = info.getRailLocation().add(0.5, 0, 0.5);
			if (instruction == BlockFace.SELF) {
				if (north || east || south || west) {
					//Redstone change and moving?
					if (!info.isAction(ActionType.REDSTONE_CHANGE) || !info.getMember().isMoving()) {
						//Brake
						if (prop.pushAtStation) {
							prop.pushAway = true;
						}
						group.ignoreForces = true;
						midd.setTarget(l, 0, 0);			
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
							lastTarget = midd.addTarget(l, midd.maxSpeed, delayMS);
						}
					}
				}
			} else {
				//Launch
				if (prop.pushAtStation) {
					prop.pushAway = true;
				}
				group.ignoreForces = true;
				midd.getGroup().clearTargets();
				Location next = l.clone().add(instruction.getModX() * length, 0, instruction.getModZ() * length);
				if (midd.isMoving() && !midd.isHeadingTo(next)) {
					//Reversing, need to center it in the middle first
					midd.setTarget(l, 0, 0);
				}
				lastTarget = midd.addTarget(next, midd.maxSpeed, delayMS);
			}
			if (prop.pushAtStation && lastTarget != null) {
				lastTarget.afterTask = new Task(TrainCarts.plugin, group) {
					public void run() {
						MinecartGroup group = (MinecartGroup) getArg(0);
						group.getProperties().pushAway = false;
						group.ignoreForces = false;
					}
				};
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
		
		//Create the group
		MinecartGroup g = MinecartGroup.create();
		
		//Default properties
		g.getProperties().setDefault();
		
		//Spawn the train
		for (int i = 0;i < types.size();i++) {
			g.addMember(MinecartMember.get(locs[i], types.get(i)));
		}
		g.tail().setForwardForce(force);
	}
	
	private static HashMap<MinecartGroup, Long> teleportTimes = new HashMap<MinecartGroup, Long>();
	private static void setTPT(MinecartGroup at) {
		teleportTimes.put(at, System.currentTimeMillis());
	}
	private static boolean getTPT(MinecartGroup at) {
		if (!teleportTimes.containsKey(at)) return true;
		long time = teleportTimes.get(at);
		return ((System.currentTimeMillis() - time) > MyWorlds.teleportInterval);
	}
	public static void teleportTrain(SignActionEvent info, Block destinationRail) {
		if (!getTPT(info.getGroup())) {
			setTPT(info.getGroup());
			return;
		}
		
		//Let's do this (...)
		BlockFace direction = info.getFacing().getOppositeFace();
		Location[] newLocations = TrackMap.walk(destinationRail, direction, info.getGroup().size(), TrainCarts.cartDistance);
		double force = info.getGroup().getAverageForce();
		
		MinecartGroup gnew = MinecartGroup.create();
		gnew.ignoreForces = info.getGroup().ignoreForces;
		gnew.setProperties(info.getGroup().getProperties());
		info.getGroup().setProperties(null);
		
		for (int i = 0; i < newLocations.length; i++) {
			MinecartMember mm = info.getGroup().getMember(i);
			Location to = newLocations[newLocations.length - i - 1].add(0.5, 0.5, 0.5);
			MinecartMember mnew = MinecartMember.get(to, mm.type);
			//Set important data over
			EntityUtil.transferItems(mm, mnew);
			mnew.e = mm.e;
			mnew.f = mm.f;
			mnew.g = mm.g;
			
			gnew.addMember(mnew);
						
			//Teleport passenger
			if (mm.passenger != null) {
				net.minecraft.server.Entity e = mm.passenger;
				//e.setPassengerOf(null);
				Task t = new Task(TrainCarts.plugin, e.getBukkitEntity(), to, mnew.getBukkitEntity()) {
					public void run() {
						Entity e = (Entity) getArg(0);
						Location to = (Location) getArg(1);
						Minecart mnew = (Minecart) getArg(2);
						if (e.getLocation().getWorld() != to.getWorld()) {
							e.teleport(to);
						}
						mnew.setPassenger(e);
					}
				};
				t.startDelayed(0);
			}
		}
		setTPT(gnew);
		
		//Remove the old group (with delay or we hear the sizzle)
		Task t = new Task(TrainCarts.plugin, info.getGroup()) {
			public void run() {
				((MinecartGroup) getArg(0)).destroy();
			}
		};
		t.startDelayed(2);
		
		//Force
		t = new Task(TrainCarts.plugin, gnew, gnew.head(), direction, force) {
			public void run() {
				MinecartGroup group = (MinecartGroup) getArg(0);
				MinecartMember head = (MinecartMember) getArg(1);
				BlockFace direction = (BlockFace) getArg(2);
				double force = getDoubleArg(3);
				if (group.size() == 0) return;
				if (group.size() == 1) {
					head.setForce(force, FaceUtil.faceToYaw(direction));
				} else {
					group.updateYaw();
					for (MinecartMember mm : group.getMembers()) {
						mm.setForwardForce(force);
					}
				}
			}
		};
		t.startDelayed(1);
	}
	private static void handleProperties(SignActionEvent info, TrainProperties prop, String mode) {
		if (mode.equals("addtag")) {
			prop.addTags(info.getLine(3));
		} else if (mode.equals("settag")) {
			prop.setTags(info.getLine(3));
		} else if (mode.equals("remtag")) {
			prop.tags.remove(info.getLine(3));
		} else if (mode.equals("collision") || mode.equals("collide")) {
			prop.trainCollision = Util.getBool(info.getLine(3));
		} else if (mode.equals("linking") || mode.equals("link")) {
			prop.allowLinking = Util.getBool(info.getLine(3));
		} else if (mode.equals("mobenter") || mode.equals("mobsenter")) {
			prop.allowMobsEnter = Util.getBool(info.getLine(3));
		} else if (mode.equals("slow") || mode.equals("slowdown")) {
			prop.slowDown = Util.getBool(info.getLine(3));
		} else if (mode.equals("setdefault") || mode.equals("default")) {
			prop.setDefault(info.getLine(3));
		} else if (mode.equals("push") || mode.equals("pushing")) {
			prop.pushAway = Util.getBool(info.getLine(3));
		} else if (mode.equals("pushmobs")) {
			prop.pushMobs = Util.getBool(info.getLine(3));
		} else if (mode.equals("pushplayers")) {
			prop.pushPlayers = Util.getBool(info.getLine(3));
		} else if (mode.equals("pushmisc")) {
			prop.pushMisc = Util.getBool(info.getLine(3));
		} else if (mode.equals("playerenter")) {
			prop.allowPlayerEnter = Util.getBool(info.getLine(3));
		} else if (mode.equals("playerexit")) {
			prop.allowPlayerExit = Util.getBool(info.getLine(3));
		} else if (mode.equals("speedlimit") || mode.equals("maxspeed")) {
			 try {
				 prop.speedLimit = Double.parseDouble(info.getLine(3));
			 } catch (NumberFormatException ex) {
				 prop.speedLimit = 0.4;
			 }
		}
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
						if (group != null && !info.isAction(ActionType.GROUP_LEAVE)) {
							handleStation(info);
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
							handleProperties(info, prop, mode);
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
				if (info.isAction(ActionType.REDSTONE_ON) || (info.isFacing() && info.isPowered())) {
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
								Block dblock = sign.getRelative(0, 2, 0);
								if (BlockUtil.isRails(dblock)) {
									//rail aligned at sign?
								    if (facing == BlockFace.NORTH) facing = BlockFace.SOUTH;
								    if (facing == BlockFace.EAST) facing = BlockFace.WEST;
									if (facing == BlockUtil.getRails(dblock).getDirection()) {
										//Can the passengers teleport? If not, get them out of the train!
										for (MinecartMember mm : info.getGroup().getMembers()) {
											if (mm.passenger != null) {
												if (mm.passenger instanceof EntityPlayer) {
													Player p = (Player) mm.passenger.getBukkitEntity();
													//has permission?
													if (Permission.canEnterWorld(p, dest.getWorld().getName())) {
														if (Permission.has(p, "portal.use") && 
																(!MyWorlds.usePortalEnterPermissions || 
																Permission.has(p, "portal.enter." + destname))) {
															//Has permission, show message
															p.sendMessage(Localization.getPortalEnter(destname));
														} else {
															Localization.message(p, "portal.noaccess");
															mm.passenger.setPassengerOf(null);
														}
													} else {
														Localization.message(p, "world.noaccess");
														mm.passenger.setPassengerOf(null);
													}
												}
											}
										}
									}
									teleportTrain(info, dblock);
								}
							}
						}
					}
					
				}
			}
		}
				
		if (info.isAction(ActionType.GROUP_ENTER) || info.isAction(ActionType.GROUP_LEAVE)) {
			if (info.getLine(0).equalsIgnoreCase("[train]")) {
				if (info.getLine(1).toLowerCase().startsWith("tag")) {
					TrainProperties prop = info.getGroup().getProperties(); 
					//=============================
					if (!prop.destination.isEmpty()){
						//Handle rails based on destination
						if (info.isAction(ActionType.GROUP_ENTER)){
							info.setRails(info.getDestDir(prop.destination));
						}
					}else{
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
						for (MinecartMember mm : info.getGroup().getMembers()) {
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
