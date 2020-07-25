package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicHorizontal;

import java.util.HashSet;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Rails;

public abstract class RailTypeHorizontal extends RailType {

    /**
     * Must be implemented to set the (vanilla) movement direction of this rail
     */
    public abstract BlockFace getDirection(Block railBlock);

    @Override
    public Block findMinecartPos(Block trackBlock) {
        if (isUpsideDown(trackBlock)) {
            return trackBlock.getRelative(BlockFace.DOWN);
        } else {
            return trackBlock;
        }
    }

    @Override
    public boolean onBlockCollision(MinecartMember<?> member, Block railsBlock, final Block hitBlock, BlockFace hitFace) {
        if (!super.onBlockCollision(member, railsBlock, hitBlock, hitFace)) {
            return false;
        }

        boolean upsideDown = this.isUpsideDown(railsBlock);
        final Block posBlock = findMinecartPos(railsBlock);

        final int dx = hitBlock.getX() - posBlock.getX();
        final int dy = hitBlock.getY() - posBlock.getY();
        final int dz = hitBlock.getZ() - posBlock.getZ();

        // If distance exceeds limit, ignore the collision
        // This is needed in case larger models (cart length!) are used
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || dz < -1 || dz > 1) {
            return false;
        }

        if (upsideDown) {

            // Directly before and after, or same block, on the same height level
            BlockFace railDir = this.getDirection(railsBlock);
            Block blockFwd = posBlock.getRelative(railDir);
            if (BlockUtil.equals(posBlock, hitBlock) || BlockUtil.equals(blockFwd, hitBlock)) {
                return true;
            }

            // Collide with block down the slope, only when there is no vertical rails there
            Block blockBwd = posBlock.getRelative(railDir.getOppositeFace());
            if (BlockUtil.equals(blockBwd, hitBlock)) {
                // When not sloped, hitting a block head-on is always a collision
                if (!member.isOnSlope()) {
                    return true;
                }

                // Down a slope. Check if we are going down (or up) a vertical rail
                if (!RailType.VERTICAL.isRail(posBlock.getRelative(BlockFace.DOWN)) &&
                    !RailType.VERTICAL.isRail(blockBwd.getRelative(railDir)))
                {
                    return true;
                }
            }

            // Block directly below
            if (member.isOnSlope() && dx == 0 && dy == -1 && dz == 0) {
                return true;
            }

            // Below, one down the slope
            if (member.isOnSlope() && railDir.getModX() == -dx && railDir.getModZ() == -dz && dy == -1) {
                return true;
            }

            return false;
        } else {
            // Ignore blocks that are one level below where the Minecart is. This includes mostly fences.
            if (hitBlock.getY() < posBlock.getY()) {
                return false;
            }

            // Handle collision (ignore UP/DOWN, recalculate hitFace for this)
            hitFace = FaceUtil.getDirection(hitBlock, posBlock, false);
            final BlockFace hitToFace = hitFace.getOppositeFace();
            if (posBlock.getY() == hitBlock.getY()) {
                // If the hit face is not a valid direction to go to, ignore it, except if this rail is sub-cardinal
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
                            // TODO: This is more than likely broken.
                            Block dirBlock = railsBlock.getRelative(hitDir);
                            RailType dirRail = RailType.getType(dirBlock);
                            if (dirRail == RailType.NONE) {
                                dirBlock = dirBlock.getRelative(BlockFace.DOWN);
                                dirRail  = RailType.getType(dirBlock);
                            }

                            if (dirRail != RailType.NONE) {
                                Block nextPosBlock = Util.getNextPos(dirBlock, hitDir);
                                if (nextPosBlock != null && hitBlock.equals(nextPosBlock)) {
                                    return true; // will enter the block, prevent with collision
                                }
                            } else {
                                return true; // no rails here, will enter the block, prevent with collision
                            }
                        }
                        return false; // cancel the collision
                    } else {
                        return false; // sub-cardinal on a straight rail, cancel the collision
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
                        if (!BlockUtil.isSolid(above)) {
                            return false;
                        }
                    }
                }

                // Cancel collisions left/right of the slope
                if (FaceUtil.isAlongX(railDirection) && dz != 0) {
                    return false;
                } else if (FaceUtil.isAlongZ(railDirection) && dx != 0) {
                    return false;
                }

                // Cancel collisions with blocks 'right above' the next rail when going down the slope
                if (!TCConfig.enableCeilingBlockCollision) {
                    IntVector3 diff = new IntVector3(hitBlock).subtract(posBlock.getX(), posBlock.getY(), posBlock.getZ());
                    if (diff.x == hitToFace.getModX() && diff.z == hitToFace.getModZ() &&
                            (diff.y > 1 || (diff.y == 1 && railDirection != hitToFace))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public Location getSpawnLocation(Block railsBlock, BlockFace orientation) {
        BlockFace[] faces = this.getPossibleDirections(railsBlock);
        if (faces != null && faces.length >= 2) {
            if (faces[0] == faces[1].getOppositeFace()) {
                // Straight horizontal rail
                // Force one of the faces if neither matches
                if (orientation != faces[0] && orientation != faces[1]) {
                    orientation = faces[0];
                }
            } else {
                // Curved rail
                BlockFace direction = FaceUtil.combine(faces[0], faces[1]);
                direction = FaceUtil.rotate(direction, 2);
                int diff_a = FaceUtil.getFaceYawDifference(direction, orientation);
                int diff_b = FaceUtil.getFaceYawDifference(direction.getOppositeFace(), orientation);
                if (diff_a < diff_b) {
                    orientation = direction;
                } else {
                    orientation = direction.getOppositeFace();
                }
            }
        }

        Location at = this.findMinecartPos(railsBlock).getLocation();
        at.setDirection(FaceUtil.faceToVector(orientation));
        if (this.isUpsideDown(railsBlock)) {
            at.add(0.5, 1.0 + RailLogicHorizontal.Y_POS_OFFSET_UPSIDEDOWN, 0.5);
            at.setPitch(-180.0F);
        } else {
            at.add(0.5, RailLogicHorizontal.Y_POS_OFFSET, 0.5);
            at.setPitch(0.0F);
        }
        return at;
    }

    @Override
    public boolean isHeadOnCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock) {
        if (super.isHeadOnCollision(member, railsBlock, hitBlock)) {
            return true;
        }

        Block minecartPos = findMinecartPos(railsBlock);
        IntVector3 delta = new IntVector3(hitBlock).subtract(new IntVector3(minecartPos));
        BlockFace direction = FaceUtil.getDirection(member.getEntity().getVelocity(), false);

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

        // Hitting block on self position when upside-down is always full stop
        if (delta.x == 0 && delta.z == 0 && delta.y == 0 && this.isUpsideDown(railsBlock)) {
            return true;
        }

        return false;
    }

    @Override
    public BlockFace getSignColumnDirection(Block railsBlock) {
        if (isUpsideDown(railsBlock)) {
            return BlockFace.UP;
        } else {
            return BlockFace.DOWN;
        }
    }

    @Override
    public BlockFace[] getSignTriggerDirections(Block railBlock, Block signBlock, BlockFace signFacing) {
        RailPiece rail = RailPiece.create(this, railBlock);
        HashSet<BlockFace> watchedFaces = new HashSet<>(4);
        if (FaceUtil.isSubCardinal(signFacing)) {
            // More advanced corner checks - NE/SE/SW/NW
            // Use rail directions validated against sign facing to
            // find out what directions are watched
            BlockFace[] faces = FaceUtil.getFaces(signFacing);
            for (BlockFace face : faces) {
                if (Util.isConnectedRails(rail, face)) {
                    watchedFaces.add(face.getOppositeFace());
                }
            }
            // Try an inversed version, maybe rails can be found there
            if (watchedFaces.isEmpty()) {
                for (BlockFace face : faces) {
                    if (Util.isConnectedRails(rail, face.getOppositeFace())) {
                        watchedFaces.add(face);
                    }
                }
            }
        } else {
            // Sloped rails also include UP/DOWN, handling from/to vertical rail movement
            Rails rails = Util.getRailsRO(railBlock);
            if (rails != null && rails.isOnSlope()) {
                watchedFaces.add(BlockFace.UP);
                watchedFaces.add(BlockFace.DOWN);
            }

            // Simple facing checks - NESW
            BlockFace facing = signFacing;
            if (Util.isConnectedRails(rail, facing)) {
                watchedFaces.add(facing.getOppositeFace());
            } else if (Util.isConnectedRails(rail, facing.getOppositeFace())) {
                watchedFaces.add(facing);
            } else {
                watchedFaces.add(FaceUtil.rotate(facing, -2));
                watchedFaces.add(FaceUtil.rotate(facing, 2));
            }
        }
        return watchedFaces.toArray(new BlockFace[watchedFaces.size()]);
    }

    @Override
    public Block findRail(Block pos) {
        Block tmp;

        // Try to find the rail at the current position or one below
        // This only counts for normal rails, not for upside-down rails
        tmp = pos;
        if (isRail(tmp)) {
            return tmp;
        }
        tmp = pos.getRelative(0, -1, 0);
        if (isRail(tmp) && !isUpsideDown(tmp)) {
            return tmp;
        }

        // Try to find the rail one and two positions above
        // This only counts for upside-down rails
        tmp = pos.getRelative(0, 1, 0);
        if (isRail(tmp) && isUpsideDown(tmp)) {
            return tmp;
        }
        tmp = pos.getRelative(0, 2, 0);
        if (isRail(tmp) && isUpsideDown(tmp)) {
            return tmp;
        }

        // Not found
        return null;
    }

}
