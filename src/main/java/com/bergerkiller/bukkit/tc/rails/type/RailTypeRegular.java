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
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
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
	public boolean isRail(int typeId, int data) {
		return typeId == Material.RAILS.getId();
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
				int typeId = WorldUtil.getBlockTypeId(world, nextPos);
				int data = WorldUtil.getBlockData(world, nextPos);
				if (this.isRail(typeId, data)) {
					// Check that the direction of the rail is correct
					Rails rails = CommonUtil.tryCast(BlockUtil.getData(typeId, (byte) data), Rails.class);
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
	public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
		Rails rails = BlockUtil.getRails(railsBlock);
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
}
