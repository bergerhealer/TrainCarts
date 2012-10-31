package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles how a minecart travels on top of a rail
 */
public abstract class RailLogic {
	private final BlockFace horizontalDir;

	public RailLogic(BlockFace horizontalDirection) {
		this.horizontalDir = horizontalDirection;
	}

	/**
	 * Gets the horizontal direction of the rails this logic is for
	 * 
	 * @return horizontal rail direction
	 */
	public BlockFace getDirection() {
		return this.horizontalDir;
	}

	/**
	 * Checks if this type of Rail Logic is for sloped tracks
	 * 
	 * @return True if sloped, False if not
	 */
	public boolean isSloped() {
		return false;
	}

	/**
	 * Updates the velocity and positioning of a minecart
	 * 
	 * @param member to update
	 */
	public abstract void update(MinecartMember member);

	/**
	 * Tries to find out the rail logic for the minecart specified
	 * 
	 * @param member to get the rail logic for
	 * @return Rail Logic
	 */
	public static RailLogic get(MinecartMember member) {
		Block rails = member.getBlock();
		int typeId = rails.getTypeId();
		if (MaterialUtil.ISRAILS.get(typeId)) {
			BlockFace direction = BlockUtil.getRails(rails).getDirection();
			if (Util.isSloped(rails.getData())) {
				boolean wasVertical = false;
				if (Util.ISVERTRAIL.get(rails.getRelative(BlockFace.UP))) {
					// Is this minecart moving towards the up-side of the slope?
					if (member.motY > 0 || member.isHeadingTo(direction)) {
						// Show information of the rail above, but keep old blockY
						return RailLogicVertical.get(direction, true);
					} else if (member.hasBlockChanged()) {
						wasVertical = true;
					}
				} else if (member.getPrevRailLogic() instanceof RailLogicVertical) {
					wasVertical = true;
				}
				// Sloped logic
				return RailLogicSloped.get(direction, wasVertical);
			} else {
				// Horizontal logic
				return RailLogicHorizontal.get(direction);
			}
		} else if (MaterialUtil.ISPRESSUREPLATE.get(typeId)) {
			// Get the direction of the rails to find out the logic to use
			BlockFace dir = Util.getPlateDirection(rails);
			if (dir == BlockFace.SELF) {
				//set track direction based on direction of this cart
				if (Math.abs(member.motX) > Math.abs(member.motZ)) {
					dir = BlockFace.SOUTH;
				} else {
					dir = BlockFace.WEST;
				}
			}
			return RailLogicHorizontal.get(dir);
		} else if (Util.ISVERTRAIL.get(typeId)) {
			return RailLogicVertical.get(Util.getVerticalRailDirection(rails.getData()), false);
		}
		// Final two no-rail logic types
		if (member.isFlying()) {
			return RailLogicAir.INSTANCE;
		} else {
			return RailLogicGround.INSTANCE;
		}
	}
}
