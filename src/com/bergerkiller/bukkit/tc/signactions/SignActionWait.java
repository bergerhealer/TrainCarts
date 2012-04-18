package com.bergerkiller.bukkit.tc.signactions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.ChunkCoordinates;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.MemberActionWaitOccupied;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionWait extends SignAction {
	
	@Override
	public void execute(SignActionEvent info) {
		if (info.isType("wait")) {
			if (info.isAction(SignActionType.GROUP_ENTER) && info.isPoweredFacing()) {
				int dist = Math.min(Util.parse(info.getLine(1), 100), TrainCarts.maxDetectorLength);
				
//				//find out all possible track blocks to check for trains
//				Set<ChunkCoordinates> blocks = new HashSet<ChunkCoordinates>(dist);
//				
//				List<Block> current = new ArrayList<Block>();
//				List<BlockFace> currentFrom = new ArrayList<BlockFace>();
//				List<Block> newCurrent = new ArrayList<Block>();
//				List<BlockFace> newCurrentFrom = new ArrayList<BlockFace>();
//				BlockFace[] tmpdirs;
//				current.add(info.getRails());
//				while (!current.isEmpty() && dist-- >= 0) {
//					newCurrent.clear();
//					newCurrentFrom.clear();
//					//===========================
//					for (int i = 0; i < current.size(); i++) {
//						Block block = current.get(i);
//						//check all surrounding sides for connected rails
//						for (BlockFace face : FaceUtil.axis) {
//							//process
//							Block newblock = Util.getRailsBlock(block.getRelative(face));
//							if (newblock == null) {
//								continue;
//							} else if (BlockUtil.isRails(newblock)) {
//								tmpdirs = FaceUtil.getFaces(BlockUtil.getRails(newblock).getDirection());
//							} else if (Util.isPressurePlate(newblock.getTypeId())) {
//								tmpdirs = FaceUtil.getFaces(Util.getPlateDirection(newblock));
//							} else {
//								continue;
//							}
//							if (tmpdirs[0] == face || tmpdirs[1] == face) {
//								//not already added?
//								ChunkCoordinates newcoord = BlockUtil.getCoordinates(newblock);
//								if (blocks.add(newcoord)) {
//									newCurrentFrom.add(face);
//									newCurrent.add(newblock);
//								}
//							}
//						}
//					}
//					//===========================
//					current.clear();
//					currentFrom.clear();
//					current.addAll(newCurrent);
//					currentFrom.addAll(newCurrentFrom);
//				}
//				
//				Material[] rand = new Material[] {Material.WOOD, Material.GOLD_BLOCK, Material.IRON_ORE, Material.DIAMOND_ORE, Material.BOOKSHELF};
//				Material mat = rand[(int) (Math.random() * rand.length)];
//				World world = info.getWorld();
//				for (ChunkCoordinates coord : blocks) {
//					world.getBlockAt(coord.x, coord.y - 1, coord.z).setType(mat);
//				}
				
				
				//allowed?
				BlockFace dir = info.getMember().getDirectionTo();
				
				//distance
				if (MemberActionWaitOccupied.handleOccupied(info.getRails(), dir, info.getMember(), dist)) {
					info.getGroup().clearActions();
					//info.getGroup().stop(true);
					info.getMember().addActionWaitOccupied(dist);
				}
			} else if (info.isAction(SignActionType.REDSTONE_OFF)) {
				info.getGroup().clearActions();
			}
		}
	}

	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("wait")) {
				return handleBuild(event, Permission.BUILD_WAIT, "train waiter sign", "waits the train until the tracks ahead are clear");
			}
		}
		return false;
	}

}
