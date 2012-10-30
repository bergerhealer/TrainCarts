package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class RailLogicVertical extends RailLogic {
	private static final RailLogicVertical[] values = new RailLogicVertical[4];
	static {
		for (int i = 0; i < 4; i++) {
			values[i] = new RailLogicVertical(FaceUtil.notchToFace(2 * i));
		}
	}

	private final BlockFace direction;

	private RailLogicVertical(BlockFace direction) {
		this.direction = direction;
	}

	@Override
	public void update(MinecartMember member) {
		//TODO: Implement this!
	}

	/**
	 * Gets the vertical rail logic for the direction specified
	 * 
	 * @param direction of the ral
	 * @return Rail Logic
	 */
	public static RailLogicVertical get(BlockFace direction) {
		return values[FaceUtil.faceToNotch(direction)];
	}
}
