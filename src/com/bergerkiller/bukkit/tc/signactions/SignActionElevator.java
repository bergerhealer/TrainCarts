package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.material.Directional;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.utils.BlockTimeoutMap;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

public class SignActionElevator extends SignAction {

	public Block findRails(Block from, BlockFace mode) {
		int sy = from.getY();
		int x = from.getX();
		int z = from.getZ();
		World world = from.getWorld();
		if (mode == BlockFace.DOWN) {
			for (int y = sy - 1; y > 0; --y) {
				if (Util.isRails(world.getBlockTypeIdAt(x, y, z))) {
					return world.getBlockAt(x, y, z);
				}
			}
		} else if (mode == BlockFace.UP) {
			int height = world.getMaxHeight();
			for (int y = sy + 1; y < height; y++) {
				if (Util.isRails(world.getBlockTypeIdAt(x, y, z))) {
					return world.getBlockAt(x, y, z);
				}
			}
		}
		return null;
	}
		
	public boolean isElevator(Sign sign) {
		if (SignActionMode.fromSign(sign) != SignActionMode.NONE) {
			if (sign.getLine(1).toLowerCase().startsWith("elevator")) {
				return true;
			}
		}
		return false;
	}
	
	public Block findElevator(Block from, BlockFace mode) {
		while ((from = findRails(from, mode)) != null) {
			for (Block signblock : Util.getSignsFromRails(from)) {
				if (isElevator(BlockUtil.getSign(signblock))) {
					return from;
				}
			}
		}
		return null;
	}
	
	public Block findElevator(Block from, BlockFace mode, int elevatorCount) {
		while ((from = findElevator(from, mode)) != null) {
			if (--elevatorCount <= 0) {
				return from;
			}
		}
		return null;
	}
	
	private BlockTimeoutMap ignoreTimes = new BlockTimeoutMap();
	
	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("elevator")) return;
		if (info.getMode() != SignActionMode.NONE && info.hasRails() && info.hasMember() && info.isPoweredFacing()) {
			if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_CHANGE)) {
				//is it allowed?
				if (this.ignoreTimes.isMarked(info.getRails(), 1000)) {
					return;
				}
				
				//where to go?
				BlockFace mode = BlockFace.UP;
				if (info.isLine(2, "down")) {
					mode = BlockFace.DOWN;
				}
				//possible amounts to skip?
				int elevatorCount = Util.parse(info.getLine(2), 1);
				Block dest = findElevator(info.getRails(), mode, elevatorCount);
				if (dest != null) {
					this.ignoreTimes.mark(dest);
					
					//get the direction to spawn and launch to
					
					//first, use the sign direction
					Sign destsign = null;
					for (Block signblock : Util.getSignsFromRails(dest)) {
						if (isElevator(destsign = BlockUtil.getSign(signblock))) {
							break;
						}
					}
					
					//facing towards a rail direction?
					BlockFace[] startDirs = FaceUtil.getFaces(BlockUtil.getRails(dest).getDirection().getOppositeFace());

					BlockFace launchDir = null;
					if (destsign != null) {
						BlockFace signdir = ((Directional) destsign.getData()).getFacing();
						if (startDirs[0] == signdir || startDirs[1] == signdir) {
							launchDir = signdir;
						}
					}
					if (launchDir == null) {
						//find out which direction is best for this occasion
						
						TrackIterator iter1 = new TrackIterator(dest, startDirs[0]);
						TrackIterator iter2 = new TrackIterator(dest, startDirs[1]);
						
						final int lim = 4;
						
						int iter1Dist = 0;
						for (iter1Dist = 0; iter1Dist < lim && iter1.hasNext(); iter1Dist++) iter1.next();
						
						int iter2Dist = 0;
						for (iter2Dist = 0; iter2Dist < lim && iter2.hasNext(); iter2Dist++) iter2.next();
						
						if (iter1Dist < iter2Dist) {
							launchDir = startDirs[1];
						} else {
							launchDir = startDirs[0];
						}
					}
					
					//teleport train
					double force = info.getGroup().getAverageForce();
					info.getGroup().teleport(dest, launchDir);
					info.getGroup().stop();
					if (force > 0.01) {
						info.getGroup().tail().addActionLaunch(launchDir, 1, force);
					}
				}
			}
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("elevator")) {
				handleBuild(event, Permission.BUILD_ELEVATOR, "train elevator", "teleport trains vertically");
			}
		}
	}

}
