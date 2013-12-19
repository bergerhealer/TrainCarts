package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVertical;

public class RailTypeVertical extends RailType {

	@Override
	public boolean isRail(Material type, int data) {
		return Util.ISVERTRAIL.get(type);
	}

	@Override
	public Block findRail(Block pos) {
		if (isRail(pos)) {
			return pos;
		} else if (isRail(pos, BlockFace.DOWN)) {
			return pos.getRelative(BlockFace.DOWN);
		}
		return null;
	}

	@Override
	public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
		IntVector3 next;
		if (isRail(world, pos.x, pos.y, pos.z)) {
			next = pos;
		} else if (member.getRailTracker().getLastRailType() != RailType.VERTICAL && isRail(world, pos.x, pos.y - 1, pos.z)) {
			next = pos.add(BlockFace.DOWN);
		} else {
			return null;
		}
		// Check for movement from sloped to vertical, and adjust the Y-position based on that
		RailLogic lastLogic = member.getRailTracker().getLastLogic();
		if (lastLogic.isSloped()) {
			if (lastLogic.getDirection() == member.getDirection().getOppositeFace()) {
				member.getEntity().loc.setY((double) next.y + 0.95);
			}
		}
		return next;
	}

	@Override
	public Block findMinecartPos(Block trackBlock) {
		return trackBlock;
	}

	@Override
	public boolean onBlockCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock, BlockFace hitFace) {
		if (!super.onBlockCollision(member, railsBlock, hitBlock, hitFace)) {
			return false;
		}
		// Check if the collided block has vertical rails below when hitting it
		if (FaceUtil.isVertical(hitFace) && Util.ISVERTRAIL.get(hitBlock.getRelative(hitFace))) {
			return false;
		}
		return true;
	}

	@Override
	public BlockFace[] getPossibleDirections(Block trackBlock) {
		return new BlockFace[] {BlockFace.UP, BlockFace.DOWN};
	}

	@Override
	public boolean onCollide(MinecartMember<?> with, Block block, BlockFace hitFace) {
		return false;
	}

	@Override
	public BlockFace getDirection(Block railsBlock) {
		return BlockFace.UP;
	}

	@Override
	public BlockFace getSignColumnDirection(Block railsBlock) {
		return Util.getVerticalRailDirection(railsBlock);
	}

	@Override
	public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
		if (currentDirection == BlockFace.UP) {
			Block next = currentTrack.getRelative(BlockFace.UP);
			if (!Util.ISTCRAIL.get(next)) {
				// Check for a possible sloped rail leading up from next
				BlockFace dir = Util.getVerticalRailDirection(currentTrack);
				Block possible = next.getRelative(dir);
				Rails rails = BlockUtil.getRails(possible);
				if (rails != null && rails.isOnSlope() && rails.getDirection() == dir) {
					return possible;
				}
			}
			return next;
		} else {
			return currentTrack.getRelative(BlockFace.DOWN);
		}
	}

	@Override
	public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
		return RailLogicVertical.get(Util.getVerticalRailDirection(railsBlock));
	}
}
