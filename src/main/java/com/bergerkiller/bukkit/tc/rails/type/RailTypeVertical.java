package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVertical;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeNormalB;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeUpsideDownA;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeUpsideDownB;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class RailTypeVertical extends RailType {

    @Override
    public boolean isRail(BlockData blockData) {
        return Util.ISVERTRAIL.get(blockData);
    }

    @Override
    public Block findRail(Block pos) {
        return findRailTmp(false, pos);
    }

    @Override
    public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
        Block rail = findRailTmp(true, pos.toBlock(world));
        return (rail == null) ? null : new IntVector3(rail);
    }

    private Block findRailTmp(boolean forMinecart, Block pos) {
        // At self position
        if (isRail(pos)) {
            // When there is an upside-down sloped rail above this position, it has preference
            // Only do this when doing member logic (TODO: FIX!)
            boolean allow = true;
            if (forMinecart) {
                Block above = pos.getRelative(BlockFace.UP);
                RailType aboveType = RailType.getType(above);
                if (aboveType instanceof RailTypeRegular && ((RailTypeRegular) aboveType).isUpsideDown(above)) {
                    allow = false;
                }
            }
            if (allow) {
                return pos;
            }
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
    public Block getNextPos(Block currentTrack, BlockFace currentDirection) {
        if (currentDirection == BlockFace.UP) {
            Block next = currentTrack.getRelative(BlockFace.UP);
            if (!Util.ISTCRAIL.get(next)) {
                // Check for a possible sloped rail leading up from next
                Block afterSlope = getAfterSlope(currentTrack);
                if (afterSlope != null) {
                    return afterSlope;
                }
            }
            return next;
        } else {
            // Check if an upside-down sloped rail below us
            if (isVerticalSlopeUpsideDownB(currentTrack)) {
                // When moving into the same direction, we go up the vertical rail
                BlockFace dir = Util.getVerticalRailDirection(currentTrack);
                if (currentDirection == BlockFace.UP || dir == currentDirection.getOppositeFace()) {
                    return currentTrack.getRelative(BlockFace.UP);
                }

                // Otherwise, we move down onto the sloped rail below
                return currentTrack.getRelative(dir.getModX(), -1, dir.getModZ());
            }

            // Go down straight
            return currentTrack.getRelative(BlockFace.DOWN);
        }
    }

    @Override
    public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
        BlockFace dir = Util.getVerticalRailDirection(railsBlock);
        if (isVerticalSlopeUpsideDown(railsBlock)) {
            return RailLogicVerticalSlopeUpsideDownA.get(dir);
        } else if (isVerticalSlopeUpsideDownB(railsBlock)) {
            return RailLogicVerticalSlopeUpsideDownB.get(dir.getOppositeFace());
        } else if (getAfterSlope(railsBlock) != null) {
            return RailLogicVerticalSlopeNormalB.get(dir);
        } else {
            return RailLogicVertical.get(dir);
        }
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        BlockFace dir = Util.getVerticalRailDirection(railsBlock);
        return new Location(railsBlock.getWorld(),
                railsBlock.getX() + 0.5,
                railsBlock.getY() + 0.5,
                railsBlock.getZ() + 0.5,
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
        if (MaterialUtil.ISSOLID.get(above)) {
            return null;
        }
        BlockFace dir = Util.getVerticalRailDirection(verticalRail);
        Block possible = above.getRelative(dir);

        for (RailType type : RailType.values()) {
            try {
                Block rail = type.findRail(possible);
                if (rail != null && LogicUtil.contains(dir.getOppositeFace(), type.getPossibleDirections(rail))) {
                    return rail;
                }
            } catch (Throwable t) {
                RailType.handleCriticalError(type, t);
                break;
            }
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
