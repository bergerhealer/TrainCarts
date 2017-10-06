package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.material.PoweredRail;
import org.bukkit.material.Rails;
import org.bukkit.material.Redstone;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

/**
 * Handles the logic with which powered rails can power themselves in chains.
 * Used for upside-down powered rails, which have normal physics disabled.
 */
public class PoweredTrackLogic {
    private final Material railType;

    public PoweredTrackLogic(Material railType) {
        this.railType = railType;
    }

    public void updateRedstone(Block railsBlock) {
        PoweredRail rails = BlockUtil.getData(railsBlock, PoweredRail.class);
        if (rails == null) {
            return;
        }

        boolean oldPowered = rails.isPowered();
        boolean newPowered = checkPowered(railsBlock);
        if (oldPowered != newPowered) {

            // CraftBukkit start
            BlockRedstoneEvent redstoneEvent = new BlockRedstoneEvent(railsBlock, oldPowered ? 15 : 0, newPowered ? 15 : 0);
            CommonUtil.callEvent(redstoneEvent);
            if ((redstoneEvent.getNewCurrent() > 0) == oldPowered) {
                return;
            }
            // CraftBukkit end

            rails.setPowered(newPowered);
            WorldUtil.setBlockDataFast(railsBlock, BlockData.fromMaterialData(rails));
            WorldUtil.queueBlockSend(railsBlock);
            //BlockUtil.applyPhysics(railsBlock, this.railType, false);

            // Also apply physics to the blocks directly down the two ends of this rails
            // This is needed, because the rails can be upside-down where this matters
            BlockUtil.applyPhysics(railsBlock.getRelative(rails.getDirection()), this.railType, true);
            BlockUtil.applyPhysics(railsBlock.getRelative(rails.getDirection().getOppositeFace()), this.railType, true);
        }
    }

    public boolean checkPowered(Block railsBlock) {
        World world = railsBlock.getWorld();
        IntVector3 blockposition = new IntVector3(railsBlock);
        BlockData iblockdata = WorldUtil.getBlockData(railsBlock);
        return railsBlock.isBlockIndirectlyPowered() ||
                this.checkEnd(world, blockposition, iblockdata, true, 0) ||
                this.checkEnd(world, blockposition, iblockdata, false, 0);
    }

    public boolean checkEnd(World world, IntVector3 blockposition, BlockData iblockdata, boolean directionMode, int iterCtr) {
        if (iterCtr >= 8) {
            return false;
        } else {
            int j = blockposition.x;
            int k = blockposition.y;
            int l = blockposition.z;
            boolean checkBelow = true;

            MaterialData data = iblockdata.getMaterialData();
            if (!(data instanceof Rails)) {
                return false;
            }

            Rails rails = (Rails) data;
            BlockFace railDirection = ((Rails) data).getDirection();
            BlockFace checkDirection = railDirection;

            if (rails.isOnSlope()) {
                switch (railDirection) {
                case EAST:
                    if (directionMode) {
                        --j;
                    } else {
                        ++j;
                        ++k;
                        checkBelow = false;
                    }
                    checkDirection = BlockFace.EAST;
                    break;
                    
                case WEST:
                    if (directionMode) {
                        --j;
                        ++k;
                        checkBelow = false;
                    } else {
                        ++j;
                    }
                    checkDirection = BlockFace.EAST;
                    break;
                    
                case NORTH:
                    if (directionMode) {
                        ++l;
                    } else {
                        --l;
                        ++k;
                        checkBelow = false;
                    }
                    checkDirection = BlockFace.SOUTH;
                    break;
                    
                case SOUTH:
                    if (directionMode) {
                        ++l;
                        ++k;
                        checkBelow = false;
                    } else {
                        --l;
                    }
                    checkDirection = BlockFace.SOUTH;
                    break;
                    
                default:
                    break;
                }
            } else {
                switch (railDirection) {
                case NORTH:
                case SOUTH:
                    if (directionMode) {
                        ++l;
                    } else {
                        --l;
                    }
                    break;

                case EAST:
                case WEST:
                    if (directionMode) {
                        --j;
                    } else {
                        ++j;
                    }
                    break;

                default:
                    break;
                }
            }

            if (this.checkStep(world, new IntVector3(j, k, l), directionMode, iterCtr, checkDirection)) {
                return true;
            }
            if (checkBelow && this.checkStep(world, new IntVector3(j, k - 1, l), directionMode, iterCtr, checkDirection)) {
                return true;
            }
            return false;
        }
    }

    public boolean checkStep(World world, IntVector3 blockposition, boolean directionMode, int iterCtr, BlockFace blockminecarttrackabstract_enumtrackposition) {
        BlockData iblockdata = WorldUtil.getBlockData(world, blockposition);

        if (iblockdata.getType() != this.railType) {
            return false;
        } else {
            MaterialData blockData = iblockdata.getMaterialData();
            if (!(blockData instanceof Rails)) {
                return false;
            }

            Rails rails = (Rails) blockData;
            BlockFace blockminecarttrackabstract_enumtrackposition1 = rails.getDirection();

            if (FaceUtil.isAlongX(blockminecarttrackabstract_enumtrackposition) != FaceUtil.isAlongX(blockminecarttrackabstract_enumtrackposition1)) {
                return false;
            }

            if (!(blockData instanceof Redstone) || !((Redstone) blockData).isPowered()) {
                return false;
            }

            if (blockposition.toBlock(world).isBlockIndirectlyPowered()) {
                return true;
            }

            return this.checkEnd(world, blockposition, iblockdata, directionMode, iterCtr + 1);
        }
    }

}
