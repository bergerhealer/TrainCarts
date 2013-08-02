package com.bergerkiller.bukkit.tc.rails.type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;

public abstract class RailType {
	private static final List<RailType> values = new ArrayList<RailType>();
	public static final RailTypeActivator ACTIVATOR_ON = new RailTypeActivator(true);
	public static final RailTypeActivator ACTIVATOR_OFF = new RailTypeActivator(false);
	public static final RailTypeCrossing CROSSING = new RailTypeCrossing();
	public static final RailTypeRegular REGULAR = new RailTypeRegular();
	public static final RailTypeDetector DETECTOR = new RailTypeDetector();
	public static final RailTypePowered BRAKE = new RailTypePowered(false);
	public static final RailTypePowered BOOST = new RailTypePowered(true);
	public static final RailTypeVertical VERTICAL = new RailTypeVertical();
	public static final RailTypeNone NONE = new RailTypeNone();

	static {
		for (RailType type : CommonUtil.getClassConstants(RailType.class)) {
			if (type != NONE) {
				values.add(type);
			}
		}
	}

	/**
	 * Checks whether the block type Id and data given denote this type of Rail.
	 * 
	 * @param typeId of the Block
	 * @param data of the Block
	 * @return True if it is this type of Rail, False if not
	 */
	public abstract boolean isRail(int typeId, int data);

	/**
	 * Checks whether the Block specified denote this type of Rail
	 * 
	 * @param world the Block is in
	 * @param x - coordinate of the Block
	 * @param y - coordinate of the Block
	 * @param z - coordinate of the Block
	 * @return True if it is this Rail, False if not
	 */
	public boolean isRail(World world, int x, int y, int z) {
		return isRail(WorldUtil.getBlockTypeId(world, x, y, z), WorldUtil.getBlockData(world, x, y, z));
	}

	/**
	 * Checks whether the Block face specified denote this type of Rail
	 * 
	 * @param block to check
	 * @param offset face from the block to check
	 * @return True if it is this Rail, False if not
	 */
	public boolean isRail(Block block, BlockFace offset) {
		return isRail(block.getWorld(), block.getX() + offset.getModX(), block.getY() + offset.getModY(), 
				block.getZ() + offset.getModZ());
	}

	/**
	 * Tries to find this Rail Type near the Minecart at the Block position specified.
	 * 
	 * @param member to find the rail for
	 * @param world the Minecart is in
	 * @param pos of the Minecart
	 * @return the Rail position (of this type) the Minecart is on
	 */
	public abstract IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos);

	/**
	 * Obtains the Rail Logic to use for the Minecart at the (previously calculated) rail position in a World.
	 * 
	 * @param member to get the logic for
	 * @param railsBlock the Minecart is driving on
	 * @return Rail Logic
	 */
	public abstract RailLogic getLogic(MinecartMember<?> member, Block railsBlock);

	/**
	 * Unregisters a Rail Type so it can no longer be used
	 * 
	 * @param type to unregister
	 */
	public static void unregister(RailType type) {
		values.remove(type);
	}

	/**
	 * Registers a Rail Type for use
	 * 
	 * @param type to register
	 * @param withPriority - True to make it activate prior to other types, False after
	 */
	public static void register(RailType type, boolean withPriority) {
		if (withPriority) {
			values.add(0, type);
		} else {
			values.add(type);
		}
	}

	/**
	 * Gets a Collection of all available Rail Types.
	 * The NONE constant is not included.
	 * 
	 * @return Rail Types
	 */
	public static Collection<RailType> getAll() {
		return values;
	}
}
