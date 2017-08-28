package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public abstract class RailTypeHorizontal extends RailType {

    @Override
    public Block findMinecartPos(Block trackBlock) {
        return trackBlock;
    }

    @Override
    public boolean onBlockCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock, BlockFace hitFace) {
        if (!super.onBlockCollision(member, railsBlock, hitBlock, hitFace)) {
            return false;
        }

        Block posBlock = findMinecartPos(railsBlock);

        // Ignore blocks that are one level below the tracks. This includes mostly fences.
        if (hitBlock.getY() < posBlock.getY()) {
            return false;
        }

        // Handle collision (ignore UP/DOWN, recalculate hitFace for this)
        hitFace = FaceUtil.getDirection(hitBlock, posBlock, false);
        final BlockFace hitToFace = hitFace.getOppositeFace();
        if (posBlock.getY() == hitBlock.getY()) {
            // If the hit face is not a valid direction to go to, ignore it, except if this rail is sub-cardinal
            int dx = hitBlock.getX() - posBlock.getX();
            int dz = hitBlock.getZ() - posBlock.getZ();
            if (Math.abs(dx) > 0 && Math.abs(dz) > 0) {
                BlockFace railDir = this.getDirection(railsBlock);
                if (FaceUtil.isSubCardinal(railDir)) {
                    BlockFace f = FaceUtil.rotate(railDir, 2);
                    BlockFace hitDir = null;

                    // Hit a block on the outer edge of a curve
                    // Check if there is a rail in this direction
                    // If not, we want to collide to prevent entering this block out of the curve
                    if (f.getModX() == dx && f.getModZ() == dz) {
                        hitDir = FaceUtil.rotate(railDir, 3);
                    } else if (f.getModX() == -dx && f.getModZ() == -dz) {
                        hitDir = FaceUtil.rotate(railDir, -3);
                    }
                    if (hitDir != null) {
                        // Check if there is rails in the next direction of the current rail
                        // If not, then we would go into the block diagonally as nothing re-routes it
                        // If there is, and the rails direct the cart into the block, prevent it as well
                        Block dirBlock = railsBlock.getRelative(hitDir);
                        RailType dirRail = RailType.getType(dirBlock);
                        if (dirRail == RailType.NONE) {
                            dirBlock = dirBlock.getRelative(BlockFace.DOWN);
                            dirRail  = RailType.getType(dirBlock);
                        }

                        if (dirRail != RailType.NONE) {
                            Block nextPosBlock = dirRail.getNextPos(dirBlock, hitDir);
                            if (nextPosBlock != null) {
                                nextPosBlock = dirRail.findMinecartPos(nextPosBlock);
                            }
                            if (nextPosBlock != null && hitBlock.equals(nextPosBlock)) {
                                return true; // will enter the block, prevent with collision
                            }
                        } else {
                            return true; // no rails here, will enter the block, prevent with collision
                        }
                    }
                    return false; // cancel the collision
                }
            } else {
                // Hit the block head-on or is on the side of the rails
                BlockFace[] possible = this.getPossibleDirections(railsBlock);
                if (!LogicUtil.contains(hitToFace, possible)) {
                    // CANCEL: we hit a block that is not an end-direction
                    return false;
                }
            }
        }

        if (member.isOnSlope()) {
            // Cancel collisions with blocks two above this sloped rail
            if (hitBlock.getX() == posBlock.getX() && hitBlock.getZ() == posBlock.getZ()) {
                int dy = hitBlock.getY() - posBlock.getY();
                if (dy >= 2) {
                    return false;
                }
            }

            // Cancel collisions with blocks at the heading of sloped rails when going up vertically
            BlockFace railDirection = this.getDirection(railsBlock);
            if (hitToFace == railDirection) {
                // Going up a vertical rail? Check here.
                if (Util.isVerticalAbove(posBlock, railDirection)) {
                    return false;
                }
                // If on the same level as the minecart, ignore. (going up a slope should not hit the slope)
                // If there is also a solid block above the block, then allow this collision to occur
                if (posBlock.getY() == hitBlock.getY()) {
                    Block above = hitBlock.getRelative(BlockFace.UP);
                    if (!MaterialUtil.ISSOLID.get(above)) {
                        return false;
                    }
                }
            }

            // Cancel collisions with blocks 'right above' the next rail when going down the slope
            if (!TrainCarts.enableCeilingBlockCollision) {
                IntVector3 diff = new IntVector3(hitBlock).subtract(posBlock.getX(), posBlock.getY(), posBlock.getZ());
                if (diff.x == hitToFace.getModX() && diff.z == hitToFace.getModZ() &&
                        (diff.y > 1 || (diff.y == 1 && railDirection != hitToFace))) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean isHeadOnCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock) {
        if (super.isHeadOnCollision(member, railsBlock, hitBlock)) {
            return true;
        }

        Block minecartPos = findMinecartPos(railsBlock);
        IntVector3 delta = new IntVector3(hitBlock).subtract(new IntVector3(minecartPos));
        BlockFace direction = member.getDirectionTo();

        // Hitting a block head-on, straight or on a slope down
        if (delta.x == direction.getModX() && delta.z == direction.getModZ()) {
            return true;
        }

        // Hitting a block above the minecart, going up a slope
        if (member.isOnSlope() && delta.x == 0 && delta.z == 0 && delta.y == 1) {
            if (direction == this.getDirection(railsBlock)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public BlockFace getSignColumnDirection(Block railsBlock) {
        return BlockFace.DOWN;
    }

    @Override
    public Block findRail(Block pos) {
        // Try to find the rail at the current position or one below
        if (isRail(pos)) {
            return pos;
        }
        if (isRail(pos, BlockFace.DOWN)) {
            return pos.getRelative(BlockFace.DOWN);
        }
        return null;
    }

    @Override
    public IntVector3 findRail(MinecartMember<?> member, World world, IntVector3 pos) {
        // Try to find the rail at the current position or one below
        if (isRail(world, pos.x, pos.y, pos.z)) {
            return pos;
        }
        if (isRail(world, pos.x, pos.y - 1, pos.z)) {
            return pos.add(BlockFace.DOWN);
        }
        return null;
    }
}
