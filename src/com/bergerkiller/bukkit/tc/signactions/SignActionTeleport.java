package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.Portal;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.utils.BlockTimeoutMap;
import com.bergerkiller.bukkit.common.utils.BlockUtil;

public class SignActionTeleport extends SignAction {

	private BlockTimeoutMap teleportTimes = new BlockTimeoutMap();

	@Override
	public void execute(SignActionEvent info) {
		if (!TrainCarts.MyWorldsEnabled) return;
		if (!info.getLine(0).equalsIgnoreCase("[portal]")) return;
		if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) && info.getGroup() != null) {
			if (info.isPowered()) {
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
									if (this.teleportTimes.isMarked(info.getBlock(), MyWorlds.teleportInterval)) {
										info.getGroup().teleportAndGo(destinationRail, direction);
										this.teleportTimes.mark(sign);
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
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (event.getLine(0).equalsIgnoreCase("[portal]")) {
			if (Util.getRailsFromSign(event.getBlock()) != null) {
				return handleBuild(event, Permission.BUILD_TELEPORTER, "train teleporter", "teleport trains large distances to another teleporter sign");
			}
		}
		return false;
	}

}
