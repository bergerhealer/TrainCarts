package com.bergerkiller.bukkit.tc.railphysics;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Handles minecart movement on sloped rails
 */
public class RailLogicSloped extends RailLogic {
	private static final RailLogicSloped [] values = new RailLogicSloped [4];
	static {
		for (int i = 0; i < 4; i++) {
			values[i] = new RailLogicSloped (FaceUtil.notchToFace(2 * i));
		}
	}

	private RailLogicSloped(BlockFace direction) {
		super(direction);
	}

	@Override
	public void update(MinecartMember member) {
		//TODO: Implement this
	}

	/**
	 * Gets the sloped rail logic for the the sloped track leading up on the direction specified
	 * 
	 * @param direction of the sloped rail
	 * @return Rail Logic
	 */
	public static RailLogicSloped get(BlockFace direction) {
		return values[FaceUtil.faceToNotch(direction)];
	}
}
