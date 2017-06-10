package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVertical;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

public class RailTypeVertical extends RailType {

    @Override
    public boolean isRail(BlockData blockData) {
        return Util.ISVERTRAIL.get(blockData);
    }

    @Override
    public Block findRail(Block pos) {
        if (isRail(pos)) {
            return pos;
        } else if (isRail(pos, BlockFace.DOWN) && !MaterialUtil.ISSOLID.get(pos)) {
            return pos.getRelative(BlockFace.DOWN);
        }
        return null;
    }

    @Override
    public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
        if (isRail(world, pos.x, pos.y, pos.z)) {
            return pos;
        } else if (member.getRailTracker().getLastRailType() != RailType.VERTICAL && 
                isRail(world, pos.x, pos.y - 1, pos.z) && 
                !MaterialUtil.ISSOLID.get(world, pos.x, pos.y, pos.z))
        {
            return pos.add(BlockFace.DOWN);
        } else {
            return null;
        }
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
                BlockFace dir = Util.getVerticalRailDirection(currentTrack);
                Block possible = next.getRelative(dir);
                Rails rails = BlockUtil.getRails(possible);
                if (rails != null && rails.isOnSlope() && rails.getDirection() == dir) {
                    return possible;
                }
            }
            return next;
        } else {
            return currentTrack.getRelative(BlockFace.DOWN);
        }
    }

    @Override
    public RailLogic getLogic(MinecartMember<?> member, Block railsBlock) {
        return RailLogicVertical.get(Util.getVerticalRailDirection(railsBlock));
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
}
