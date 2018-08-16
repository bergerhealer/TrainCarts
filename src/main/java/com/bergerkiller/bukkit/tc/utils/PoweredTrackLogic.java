package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.material.PoweredRail;
import org.bukkit.material.Rails;

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
        MaterialData data = iblockdata.getMaterialData();
        if (!(data instanceof Rails)) {
            return false;
        }

        Rails rails = (Rails) data;
        BlockFace railDirection = rails.getDirection();

        // Compute the rail mode (along X or Z) and from that, calculate our walk direction based on direction mode
        BlockFace checkDirection = FaceUtil.isAlongX(railDirection) ? BlockFace.EAST : BlockFace.SOUTH;
        BlockFace walkDirection = directionMode ? checkDirection.getOppositeFace() : checkDirection;

        // Get the next block to check up on
        IntVector3 nextPos = blockposition.add(walkDirection);
        boolean isSlopeUp = (rails.isOnSlope() && (railDirection == walkDirection));
        if (isSlopeUp) {
            nextPos = nextPos.add(BlockFace.UP);
        }

        // Check
        if (this.checkStep(world, nextPos, directionMode, iterCtr, checkDirection)) {
            return true;
        }
        if (!isSlopeUp && this.checkStep(world, nextPos.add(BlockFace.DOWN), directionMode, iterCtr, checkDirection)) {
            return true;
        }
        return false;
    }

    public boolean checkStep(World world, IntVector3 blockposition, boolean directionMode, int iterCtr, BlockFace walkDirection) {
        BlockData iblockdata = WorldUtil.getBlockData(world, blockposition);

        if (!iblockdata.isType(this.railType)) {
            return false;
        } else {
            MaterialData blockData = iblockdata.getMaterialData();
            if (!(blockData instanceof Rails)) {
                return false;
            }

            Rails rails = (Rails) blockData;
            BlockFace railDirection = rails.getDirection();

            // Make sure rail is oriented the same way for it to 'connect'
            if (FaceUtil.isAlongX(walkDirection) != FaceUtil.isAlongX(railDirection)) {
                return false;
            }

            // Check that the rail itself is powered on
            if (!(blockData instanceof PoweredRail) || !((PoweredRail) blockData).isPowered()) {
                return false;
            }

            // If the block is receiving redstone power indirectly, it is actually powered and so are we!
            if (blockposition.toBlock(world).isBlockIndirectlyPowered()) {
                return true;
            }

            // Check that we have not exhausted the maximum power length by incrementing the counter here
            if (++iterCtr >= 8) {
                return false;
            }

            // Recursive 'next' checking
            return this.checkEnd(world, blockposition, iblockdata, directionMode, iterCtr);
        }
    }

}
