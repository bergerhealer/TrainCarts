package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.InteractType;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

/**
 * Utilities for dealing with item transfers between different containers
 */
public class TransferSignUtil {
	public static Collection<BlockState> getBlockStates(SignActionEvent info, int radius) {
		int radX = radius;
		int radZ = radius;
		BlockFace dir = info.getRailDirection();
		if (FaceUtil.isAlongX(dir)) {
			radX = 0;
		} else if (FaceUtil.isAlongZ(dir)) {
			radZ = 0;
		}
		return BlockUtil.getBlockStates(info.getRails(), radX, radius, radZ);
	}

	/**
	 * Finds all nearby block states with which can be interacted according to the sign
	 * 
	 * @param info to use
	 * @param mode to use, collect or dispense
	 * @return A collection of BlockStates to operate on
	 */
	public static Collection<BlockState> findBlocks(SignActionEvent info, String mode) {
		Collection<InteractType> typesToCheck = InteractType.parse(mode, info.getLine(1));
		if (typesToCheck.isEmpty()) {
			return Collections.emptyList();
		}

		//get the block types to collect and the radius (2nd line)
		int radius = ParseUtil.parseInt(info.getLine(1), TrainCarts.defaultTransferRadius);

		//get the tile entities to collect
		Collection<BlockState> found = TransferSignUtil.getBlockStates(info, radius);
		if (found.isEmpty()) {
			return Collections.emptyList();
		}
		List<BlockState> rval = new ArrayList<BlockState>(found.size());
		// This weird for loop is needed because typesToCheck is not a set!
		// The order in which inventories are added is of importance
		for (InteractType type : typesToCheck) {
			switch (type) {
				case CHEST : {
					for (BlockState state : found) {
						if (state instanceof Chest) {
							rval.add(state);
						}
					}
					break;
				}
				case FURNACE : {
					for (BlockState state : found) {
						if (state instanceof Furnace) {
							rval.add(state);
						}
					}
					break;
				}
				case DISPENSER : {
					for (BlockState state : found) {
						if (state instanceof Dispenser) {
							rval.add(state);
						}
					}
					break;
				}
				case GROUNDITEM : {
					rval.add(new GroundItemsState(info.getRails(), radius));
					break;
				}
			}
		}
		return rval;
	}
}
