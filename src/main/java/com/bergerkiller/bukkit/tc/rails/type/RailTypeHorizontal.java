package com.bergerkiller.bukkit.tc.rails.type;

import org.bukkit.World;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public abstract class RailTypeHorizontal extends RailType {

	@Override
	public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
		// Try to find the rail at the current position or one below
		if (isRail(world, pos.x, pos.y, pos.z)) {
			return pos;
		}
		if (isRail(world, pos.x, pos.y - 1, pos.z)) {
			return pos.add(BlockFace.DOWN);
		}
		return null;
	}
}
