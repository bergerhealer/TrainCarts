package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicGround;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicSloped;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeDown;

/**
 * All regular types of rails - the ones provided by Minecraft.
 * All these rail types can slope and some of them can make curves.
 * None of them can be used vertically, hence, it is a horizontal rail.<br><br>
 * 
 * Warning: all 'regular' type of rails REQUIRE a valid Rails Material type.
 * Do not write an isType check that results in non-Rails material types.
 */
public class RailTypeRegular extends RailTypeHorizontal {

	@Override
	public boolean isRail(Material type, int data) {
		return type == Material.RAILS;
	}

	@Override
	public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
		if (member.getEntity().vel.getY() > 0.0) {
			// Only if we came from a vertical rail do we allow this to be used
			// In all other cases, we will no longer be using this (horizontal) rail.
			// If we came from a vertical rail and need to move onto a slope
			// Vertical -> Slope UP
			RailTracker railTracker = member.getRailTracker();
			if (railTracker.getLastRailType() == RailType.VERTICAL) {
				IntVector3 nextPos = pos.add(railTracker.getLastLogic().getDirection());
				Material type = WorldUtil.getBlockType(world, nextPos.x, nextPos.y, nextPos.z);
				int data = WorldUtil.getBlockData(world, nextPos);
				if (this.isRail(type, data)) {
					// Check that the direction of the rail is correct
					Rails rails = CommonUtil.tryCast(BlockUtil.getData(type, data), Rails.class);
					BlockFace lastDirection = railTracker.getLastLogic().getDirection();
					if (rails != null && rails.isOnSlope() && rails.getDirection() == lastDirection) {
						// We got a winner!
						// Some position and velocity adjustment prior to moving between the types
						CommonMinecart<?> entity = member.getEntity();
						entity.loc.xz.set(nextPos.x + 0.5, nextPos.z + 0.5);
						entity.loc.xz.subtract(lastDirection, 0.49);
						// Y offset
						final double transOffset = 0.01; // How high above the slope to teleport to
						entity.loc.setY(nextPos.y + transOffset);
						// Convert Y-velocity into XZ-velocity
						entity.vel.xz.add(rails.getDirection(), entity.vel.getY());
						entity.vel.y.setZero();
						return nextPos;
					}
				}
			}
			return null;
		} else {
			// Find the rail, that'll be the end of it
			return super.findRail(member, world, pos);
		}
	}

	@Override
	public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
		Rails rail = BlockUtil.getRails(currentTrack);
		if (rail == null) {
			return null;
		}
		return getNextPos(currentTrack, currentDirection, rail.getDirection(), rail.isOnSlope());
	}

	@Override
	public BlockFace[] getPossibleDirections(Block trackBlock) {
		Rails rails = BlockUtil.getRails(trackBlock);
		return rails == null ? new BlockFace[0] : getPossibleDirections(rails.getDirection());
	}

	@Override
	public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
		Rails rails = BlockUtil.getRails(railsBlock);
		if (rails == null) {
			return RailLogicGround.INSTANCE;
		}
		BlockFace direction = rails.getDirection();

		// Sloped logic
		if (rails.isOnSlope()) {
			// To vertical
			if (Util.isVerticalAbove(railsBlock, direction)) {
				return RailLogicVerticalSlopeDown.get(direction);
			}
			// Default
			return RailLogicSloped.get(direction);
		}

		// Default Horizontal logic
		return RailLogicHorizontal.get(direction);
	}

	@Override
	public BlockFace getDirection(Block railsBlock) {
		Rails rails = BlockUtil.getRails(railsBlock);
		return rails == null ? BlockFace.SELF : rails.getDirection();
	}

	/**
	 * Gets all possible directions a Minecart can go when on regular rails.
	 * There is no 'UP' direction for vertical rails - it assumes the horizontal direction
	 * towards the wall this vertical rail is attached to instead.
	 * 
	 * @param railDirection of the track
	 * @return all possible directions
	 */
	public static BlockFace[] getPossibleDirections(BlockFace railDirection) {
		return FaceUtil.getFaces(railDirection.getOppositeFace());
	}

	/**
	 * Gets the next position to go to, without requesting information from the rail itself.
	 * This allows it to be used by other rail types.
	 * 
	 * @param currentTrack the 'Minecart' is on
	 * @param currentDirection the 'Minecart' is moving
	 * @param railDirection of the currentTrack
	 * @param sloped state of the rail - whether it is sloped
	 * @return the next position the 'Minecart' goes to
	 */
	public static Block getNextPos(Block currentTrack, BlockFace currentDirection, BlockFace railDirection, boolean sloped) {
		if (FaceUtil.isSubCardinal(railDirection)) {
			// Get a set of possible directions to go to
			BlockFace[] possible = FaceUtil.getFaces(railDirection.getOppositeFace());

			// Simple forward - always true
			for (BlockFace newdir : possible) {
				if (newdir == currentDirection) {
					return currentTrack.getRelative(currentDirection);
				}
			}

			// Get connected faces
			BlockFace dir = currentDirection.getOppositeFace();
			BlockFace nextDir;
			if (possible[0].equals(dir)) {
				nextDir = possible[1];
			} else if (possible[1].equals(dir)) {
				nextDir = possible[0];
				// south-east rule
			} else if (possible[0] == BlockFace.SOUTH || possible[0] == BlockFace.EAST) {
				nextDir = possible[0];
			} else {
				nextDir = possible[1];
			}
			return currentTrack.getRelative(nextDir);
		} else if (sloped) {
			if (railDirection == currentDirection) {
				// Moving up the slope
				Block above = currentTrack.getRelative(BlockFace.UP);
				if (Util.ISVERTRAIL.get(above) && Util.getVerticalRailDirection(above) == currentDirection) {
					// Go to vertical rails above
					return above;
				} else {
					// Go up one and then forward
					return above.getRelative(railDirection);
				}
			} else {
				// Moving down the slope, follow slope end-direction
				return currentTrack.getRelative(railDirection.getOppositeFace());
			}
		} else if (railDirection == currentDirection || railDirection.getOppositeFace() == currentDirection) {
			// Move along horizontal tracks
			return currentTrack.getRelative(currentDirection);
		} else {
			// South-West rule
			return currentTrack.getRelative(railDirection);
		}
	}
}
