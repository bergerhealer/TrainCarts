package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVertical;

public class RailTypeVertical extends RailType {

	@Override
	public boolean isRail(int typeId, int data) {
		return Util.ISVERTRAIL.get(typeId);
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
	public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
		return RailLogicVertical.get(Util.getVerticalRailDirection(railsBlock.getData()));
	}
}
