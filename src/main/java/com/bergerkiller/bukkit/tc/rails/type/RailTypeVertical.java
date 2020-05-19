package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVertical;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeNormalB;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeUpsideDownB;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeUpsideDownC;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeUpsideDownD;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class RailTypeVertical extends RailType {
    private static final BlockFace[] SIGN_TRIGGER_DIRS = {BlockFace.UP, BlockFace.DOWN};

    @Override
    public boolean isRail(BlockData blockData) {
        return Util.ISVERTRAIL.get(blockData);
    }

    @Override
    public Block findRail(Block pos) {
        // At self position
        if (isRail(pos)) {
            return pos;
        }

        // When there is a slope connecting it, allow the vertical rail below
        Block below = pos.getRelative(BlockFace.DOWN);
        if (isRail(below) && getAfterSlope(below) != null) {
            return below;
        }

        // When there is an upside-down slope connecting it, allow the vertical rail above
        Block above = pos.getRelative(BlockFace.UP);
        if (isRail(above) && isVerticalSlopeUpsideDownB(above)) {
            return above;
        }

        // When there is an upside-down slope connecting it, allow the vertical rail TWO above
        Block twoAbove = pos.getRelative(0, 2, 0);
        if (isRail(twoAbove) && isVerticalSlopeUpsideDownB(twoAbove)) {
            return twoAbove;
        }

        return null;
    }

    @Override
    public Block findMinecartPos(Block trackBlock) {
        return trackBlock;
    }

    @Override
    public boolean onBlockCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock, BlockFace hitFace) {
        if (!super.onBlockCollision(member, railsBlock, hitBlock, hitFace)) {
            return false;
        }
        // Verify the x/z are the same column as the hit block, as well that the hit block is not a rail
        Block minecartPos = findMinecartPos(railsBlock);
        if (hitBlock.getX() != minecartPos.getX() || hitBlock.getZ() != minecartPos.getZ()) {
            return false;
        }

        // Put a limit on the block offset (larger train models!)
        int dy = hitBlock.getY() - minecartPos.getY();
        if (dy < -1 || dy > 1) {
            return false;
        }

        if (this.isRail(hitBlock)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isHeadOnCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock) {
        if (super.isHeadOnCollision(member, railsBlock, hitBlock)) {
            return true;
        }
        Block minecartPos = findMinecartPos(railsBlock);
        return (hitBlock.getY() - minecartPos.getY()) == member.getDirectionTo().getModY();
    }

    @Override
    public BlockFace[] getPossibleDirections(Block trackBlock) {
        return new BlockFace[]{BlockFace.UP, BlockFace.DOWN};
    }

    @Override
    public boolean onCollide(MinecartMember<?> with, Block block, BlockFace hitFace) {
        return false;
    }

    @Override
    public BlockFace getDirection(Block railsBlock) {
        return BlockFace.UP;
    }

    @Override
    public BlockFace getSignColumnDirection(Block railsBlock) {
        return Util.getVerticalRailDirection(railsBlock);
    }

    @Override
    public BlockFace[] getSignTriggerDirections(Block railBlock, Block signBlock, BlockFace signFacing) {
        return SIGN_TRIGGER_DIRS;
    }

    @Override
    public RailLogic getLogic(RailState state) {
        BlockFace dir = Util.getVerticalRailDirection(state.railBlock());
        if (isVerticalSlopeUpsideDown(state.railBlock())) {
            // Check if there is also one upside-down rail below leading to a slope
            // If so, when y-position is below a threshold, pick UpsideDownB instead.
            if (state.railPosition().getY() < 0.0 && isVerticalSlopeUpsideDownB(state.railBlock())) {
                return RailLogicVerticalSlopeUpsideDownB.get(dir.getOppositeFace());
            }

            if (isVerticalSlopeUpsideDownB(state.railBlock())) {
                return RailLogicVerticalSlopeUpsideDownD.get(dir.getOppositeFace());
            } else {
                return RailLogicVerticalSlopeUpsideDownC.get(dir.getOppositeFace());
            }
        } else if (isVerticalSlopeUpsideDownB(state.railBlock())) {
            return RailLogicVerticalSlopeUpsideDownB.get(dir.getOppositeFace());
        } else if (getAfterSlope(state.railBlock()) != null) {
            return RailLogicVerticalSlopeNormalB.get(dir);
        } else {
            return RailLogicVertical.get(dir);
        }
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        BlockFace dir = Util.getVerticalRailDirection(railsBlock);
        double dx = 0.5 + RailLogicVertical.XZ_POS_OFFSET * dir.getModX();
        double dz = 0.5 + RailLogicVertical.XZ_POS_OFFSET * dir.getModZ();
        return new Location(railsBlock.getWorld(),
                railsBlock.getX() + dx,
                railsBlock.getY() + 0.5,
                railsBlock.getZ() + dz,
                FaceUtil.faceToYaw(dir),
                -90.0f);
    }

    private boolean isVerticalSlopeUpsideDown(Block railsBlock) {
        // When there is an upside-down sloped rail above this position, it has preference
        Block above = railsBlock.getRelative(BlockFace.UP);
        return isUpsideDownRail(above);
    }

    /**
     * Checks if an upside-down sloped rail is connecting it
     * 
     * @param railsBlock
     * @return
     */
    private boolean isVerticalSlopeUpsideDownB(Block railsBlock) {
        BlockFace dir = Util.getVerticalRailDirection(railsBlock);
        Block slopeBlock = railsBlock.getRelative(dir.getModX(), -1, dir.getModZ());
        return isUpsideDownRail(slopeBlock);
    }

    private boolean isUpsideDownRail(Block railsBlock) {
        RailType railType = RailType.getType(railsBlock);
        if (railType instanceof RailTypeRegular) {
            return ((RailTypeRegular) railType).isUpsideDown(railsBlock);
        } else {
            return false;
        }
    }
    
    /**
     * Gets a sloped rail that connects to a vertical rail, if one exists and is connected
     * 
     * @param verticalRail to get the sloped rails block of that leads to it
     * @return the block of the sloped rail, or null if not found
     */
    private Block getAfterSlope(Block verticalRail) {
        // New logic that allows any rails leading towards the edge to go down onto a vertical rail
        if (!this.isRail(verticalRail)) {
            return null;
        }
        Block above = verticalRail.getRelative(BlockFace.UP);
        if (BlockUtil.isSolid(above)) {
            return null;
        }
        BlockFace dir = Util.getVerticalRailDirection(verticalRail);
        Block possible = above.getRelative(dir);

        RailPiece railPiece = RailType.findRailPiece(possible);
        if (railPiece != null && LogicUtil.contains(dir.getOppositeFace(), railPiece.type().getPossibleDirections(railPiece.block()))) {
            return railPiece.block();
        }

        return null;

        // Old logic that only allowed sloped rails to go down onto a vertical rail
        /*
        if (!this.isRail(verticalRail)) {
            return null;
        }
        Block above = verticalRail.getRelative(BlockFace.UP);
        if (MaterialUtil.ISSOLID.get(above)) {
            return null;
        }
        BlockFace dir = Util.getVerticalRailDirection(verticalRail);
        Block possible = above.getRelative(dir);
        Rails rails = BlockUtil.getRails(possible);
        if (rails != null && rails.isOnSlope() && rails.getDirection() == dir) {
            return possible;
        } else {
            return null;
        }
        */
    }
}
