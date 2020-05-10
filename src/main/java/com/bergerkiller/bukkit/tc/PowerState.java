package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Diode;
import org.bukkit.material.Lever;
import org.bukkit.material.MaterialData;
import org.bukkit.material.PressureSensor;
import org.bukkit.material.Redstone;
import org.bukkit.material.RedstoneTorch;
import org.bukkit.material.RedstoneWire;

/**
 * The way a block can be powered:<br>
 * - ON: Block is powered by something<br>
 * - OFF: Power source is nearby but is not powering<br>
 * - NONE: No power source nearby
 */
public enum PowerState {
    ON, OFF, NONE;

    private static boolean isDistractingColumn(Block main, BlockFace face) {
        Block side = main.getRelative(face);
        BlockData side_data = WorldUtil.getBlockData(side);
        if (MaterialUtil.ISPOWERSOURCE.get(side_data)) {
            return true;
        } else if (side_data.isType(Material.AIR)) {
            //check level below
            if (MaterialUtil.ISPOWERSOURCE.get(side.getRelative(BlockFace.DOWN))) {
                return true;
            }
        } else if (MaterialUtil.ISDIODE.get(side_data)) {
            //powered by repeater?
            BlockFace facing = BlockUtil.getFacing(side);
            return facing == face;
        }
        BlockData up_data = WorldUtil.getBlockData(main.getRelative(BlockFace.UP));
        if (up_data.isType(Material.AIR)) {
            //check level on top
            return MaterialUtil.ISPOWERSOURCE.get(side.getRelative(BlockFace.UP));
        } else {
            return false;
        }
    }

    private static boolean isDistracted(Block wire, BlockFace face) {
        return isDistractingColumn(wire, FaceUtil.rotate(face, -2)) || isDistractingColumn(wire, FaceUtil.rotate(face, 2));
    }

    public static PowerState get(Block block, BlockFace from) {
        return get(block, from, true);
    }

    /**
     * Computes the power state for a given block
     *
     * @param block        to get the power state of
     * @param from         what BlockFace side the block power should be computed
     * @param useSignLogic setting, sets whether redstone next to the block acts as power source
     * @return The Power State of the block
     */
    public static PowerState get(Block block, BlockFace from, boolean useSignLogic) {
        Block fromBlock = block.getRelative(from);
        BlockData fromBlockInfo = WorldUtil.getBlockData(fromBlock);
        MaterialData fromBlockData = fromBlockInfo.getMaterialData();
        if (fromBlockData instanceof RedstoneTorch) {
            if (useSignLogic || from == BlockFace.DOWN) {
                return ((RedstoneTorch) fromBlockData).isPowered() ? ON : OFF;
            } else {
                return NONE;
            }
        } else if (fromBlockData instanceof Diode && !FaceUtil.isVertical(from)) {
            Diode diode = (Diode) fromBlockData;
            if (diode.getFacing().getOppositeFace() == from) {
                // Note: not supported on 1.8.8
                //return diode.isPowered() ? ON : OFF;
                return fromBlockInfo.isType(getMaterial("LEGACY_DIODE_BLOCK_ON")) ? ON : OFF;
            } else {
                return NONE;
            }
        } else if (fromBlockData instanceof RedstoneWire) {
            if (useSignLogic || from == BlockFace.UP || (from != BlockFace.DOWN && !isDistracted(fromBlock, from))) {
                return ((RedstoneWire) fromBlockData).isPowered() ? ON : OFF;
            } else {
                return NONE;
            }
        }

        // Ignore indirect power from levers, because the sign is controlling that lever
        if (fromBlockData instanceof Lever && !useSignLogic) {
            return NONE;
        }

        // Power source read-out
        if (fromBlockInfo.isPowerSource()) {
            if (fromBlockData instanceof Redstone) {
                return ((Redstone) fromBlockData).isPowered() ? ON : OFF;
            } else if (fromBlockData instanceof PressureSensor) {
                return ((PressureSensor) fromBlockData).isPressed() ? ON : OFF;
            }
        }

        // For signs, the block they are attached to can be powered too
        // For those we need to do recursive checks if those are powered in some way
        if (useSignLogic && BlockUtil.getAttachedFace(block) == from) {
            PowerState state = PowerState.NONE;
            for (BlockFace attFace : FaceUtil.BLOCK_SIDES) {
                if (attFace == from.getOppositeFace()) continue;

                PowerState attState = PowerState.get(fromBlock, attFace, false);
                if (attState != PowerState.NONE) {
                    state = attState;
                    if (state == PowerState.ON) {
                        break;
                    }
                }
            }
            return state;
        }

        return NONE;
    }

    /**
     * Gets whether a certain sign block is powered by a Redstone power source.
     * 
     * @param signBlock the block where the sign is located
     * @return True if powered by Redstone
     */
    public static boolean isSignPowered(Block signBlock) {
        return isSignPowered(signBlock, false);
    }

    /**
     * Gets whether a certain sign block is powered by a Redstone power source.
     * 
     * @param signBlock the block where the sign is located
     * @param inverted power - True to invert the power as a result, False to get the normal result
     * @return True if powered when not inverted, or not powered and inverted
     */
    public static boolean isSignPowered(Block signBlock, boolean inverted) {
        if (inverted) {
            for (BlockFace face : FaceUtil.BLOCK_SIDES) {
                if (PowerState.get(signBlock, face, true) == PowerState.ON) {
                    return false;
                }
            }
            return true;
        } else {
            for (BlockFace face : FaceUtil.BLOCK_SIDES) {
                if (PowerState.get(signBlock, face, true).hasPower()) return true;
            }
            return false;
        }
    }

    /**
     * Gets whether this Power State is supplying redstone power
     *
     * @return True if power is supplied, False if not
     */
    public boolean hasPower() {
        switch (this) {
            case ON:
                return true;
            default:
                return false;
        }
    }
}