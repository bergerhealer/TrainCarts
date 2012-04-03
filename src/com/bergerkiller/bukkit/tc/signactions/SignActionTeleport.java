package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.BlockMap;
import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.Portal;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.common.utils.BlockUtil;

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
		if (!info.getLine(0).equalsIgnoreCase("[portal]")) return;
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.getGroup() != null) {
			if (info.isPoweredFacing()) {
				if (!info.hasRails()) return;
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
							Block destinationRail = Util.getRailsFromSign(sign);
							boolean isPlate = Util.isRails(destinationRail);
							if (isPlate || BlockUtil.isRails(destinationRail)) {
								//rail aligned at sign?
								if (facing == BlockFace.NORTH) facing = BlockFace.SOUTH;
								if (facing == BlockFace.EAST) facing = BlockFace.WEST;
								if (isPlate || facing == BlockUtil.getRails(destinationRail).getDirection()) {
									//Allowed?
									if (getTPT(info)) {
										double force = info.getGroup().getAverageForce();
										info.getGroup().teleport(destinationRail, direction);
										info.getGroup().stop();
										info.getGroup().head().addActionLaunch(direction, 1, force);
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
			if (Util.getRailsFromSign(event.getBlock()) != null) {
				handleBuild(event, Permission.BUILD_TELEPORTER, "train teleporter", "teleport trains large distances to another teleporter sign");
			}
		}
	}

}
