package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.Portal;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;
import com.bergerkiller.bukkit.tc.utils.BlockMap;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;

public class SignActionTeleport extends SignAction {

	private BlockMap<Long> teleportTimes = new BlockMap<Long>();
	private void setTPT(Block signblock) {
		teleportTimes.put(signblock, System.currentTimeMillis());
	}
	private boolean getTPT(SignActionEvent info) {
		Long time = teleportTimes.get(info.getBlock());
		if (time == null) return true;
		return (System.currentTimeMillis() - time) > MyWorlds.teleportInterval;
	}
		
	@Override
	public void execute(SignActionEvent info) {
		if (!TrainCarts.MyWorldsEnabled) return;
		if (!info.hasRails()) return;
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.getGroup() != null) {
			if (info.isPoweredFacing()) {
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
							Block destinationRail = BlockUtil.getRailsBlockFromSign(sign);
							if (BlockUtil.isRails(destinationRail)) {
								//rail aligned at sign?
								if (facing == BlockFace.NORTH) facing = BlockFace.SOUTH;
								if (facing == BlockFace.EAST) facing = BlockFace.WEST;
								if (facing == BlockUtil.getRails(destinationRail).getDirection()) {
									//Allowed?
									if (getTPT(info)) {
										double force = info.getGroup().getAverageForce();
										info.getGroup().teleport(destinationRail, direction);
										info.getGroup().stop();
										info.getGroup().head().addActionLaunch(direction, 1, force);
										
										
										
										
										
										
										
										
										
										
//										//Teleportation is allowed: get info from source
//										double force = info.getGroup().getAverageForce();
//
//										//Spawn a new group and transfer data
//										MinecartGroup gnew = MinecartGroup.spawn(destinationRail, direction, info.getGroup().getTypes());
//										gnew.setProperties(info.getGroup().getProperties());
//										info.getGroup().setProperties(null);
//										info.setCancelled(true);
//										
//										//Force
//										gnew.addActionWaitTicks(3);
//										gnew.head().addActionLaunch(direction, 1, force);
//										
//										//Load chunks
//										if (gnew.getProperties().keepChunksLoaded) {
//											gnew.loadChunks();
//										}
//										
//										//Prevent collisions for a while
//										for (int i = 0; i < gnew.size() - 1; i++) {
//											gnew.get(i).ignoreCollision(gnew.get(i + 1), 10);
//										}
//										
//										//Transfer individual data and teleport passengers
//										for (int i = 0; i < gnew.size(); i++) {
//											MinecartMember from = info.getGroup().get(i);
//											MinecartMember to = gnew.get(i);
//											//Set important data
//											ItemUtil.transfer(from, to);
//											to.b = from.b;
//											to.c = from.c;
//											to.fuel = from.fuel;
//
//											//Teleport passenger
//											if (from.passenger != null) {
//												net.minecraft.server.Entity e = from.passenger;
//												from.getMinecart().eject();
//
//												boolean transfer = true;
//												if (e instanceof EntityPlayer) {
//													Player p = (Player) e.getBukkitEntity();
//													//has permission?
//													if (com.bergerkiller.bukkit.mw.Permission.canEnterWorld(p, dest.getWorld().getName())) {
//														if (com.bergerkiller.bukkit.mw.Permission.canEnterPortal(p, destname)) {
//															//Has permission, show message
//															p.sendMessage(Localization.getPortalEnter(portal));
//														} else {
//															Localization.message(p, "portal.noaccess");
//															transfer = false;
//														}
//													} else {
//														Localization.message(p, "world.noaccess");
//														transfer = false;
//													}
//												}
//												if (transfer) {
//													Task t = new Task(TrainCarts.plugin, e.getBukkitEntity(), to.getLocation()) {
//														public void run() {
//															Entity e = (Entity) getArg(0);
//															Location to = (Location) getArg(1);
//															e.teleport(to);
//														}
//													};
//													t.startDelayed(0);
//													t = new Task(TrainCarts.plugin, e, to) {
//														public void run() {
//															net.minecraft.server.Entity e = (net.minecraft.server.Entity) getArg(0);
//															MinecartMember mnew = (MinecartMember) getArg(1);
//															e.setPassengerOf(mnew);
//														}
//													};
//													t.startDelayed(1);
//												}
//											}
//										}
//
//										//Remove the old group
//										info.getGroup().destroy();

										setTPT(sign);
									}
								}
							}
						}
					}
				}
			}
		}	
	}
	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (event.getLine(0).equalsIgnoreCase("[portal]")) {
			
			if (BlockUtil.getRailsAttached(event.getBlock()) != null) {
				handleBuild(event, Permission.BUILD_TELEPORTER, "train teleporter", "teleport trains large distances to another teleporter sign");
			}
		}
	}

}
