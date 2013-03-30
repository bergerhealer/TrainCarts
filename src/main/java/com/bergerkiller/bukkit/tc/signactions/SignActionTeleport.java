package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.mw.MyWorlds;
import com.bergerkiller.bukkit.mw.Portal;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.BlockTimeoutMap;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

public class SignActionTeleport extends SignAction {
	private BlockTimeoutMap teleportTimes = new BlockTimeoutMap();

	@Override
	public boolean match(SignActionEvent info) {
		return TrainCarts.MyWorldsEnabled && info.getLine(0).equalsIgnoreCase("[portal]") && info.hasRails();
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON) || !info.hasGroup() || !info.isPowered()) {
			return;
		}
		Portal portal = Portal.get(info.getLocation());
		if (portal == null) {
			return;
		}
		String destname = portal.getDestinationName();
		Location dest = Portal.getPortalLocation(destname, info.getGroup().getWorld().getName());
		if (dest != null) {
			//Teleport the ENTIRE train to the destination...
			Block sign = dest.getBlock();
			sign.getChunk(); //load the chunk
			if (MaterialUtil.ISSIGN.get(sign)) {
				BlockFace facing = BlockUtil.getFacing(sign);
				BlockFace direction = facing;
				Block destinationRail = Util.getRailsFromSign(sign);
				if (destinationRail == null) {
					return;
				}
				boolean isPlate = MaterialUtil.ISPRESSUREPLATE.get(destinationRail);
				if (isPlate || MaterialUtil.ISRAILS.get(destinationRail)) {
					//rail aligned at sign?
					facing = FaceUtil.toRailsDirection(facing);
					if (isPlate || facing == BlockUtil.getRails(destinationRail).getDirection()) {
						//Allowed?
						if (!this.teleportTimes.isMarked(info.getBlock(), MyWorlds.teleportInterval)) {
							this.teleportTimes.mark(sign);
							info.getGroup().teleportAndGo(destinationRail, direction);
						}
					}
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.hasRails()) {
			return handleBuild(event, Permission.BUILD_TELEPORTER, "train teleporter", "teleport trains large distances to another teleporter sign");
		}
		return false;
	}
}
