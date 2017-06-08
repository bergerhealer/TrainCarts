package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class RailTracker {

    protected static RailInfo findInfo(MinecartMember<?> member) {
        final IntVector3 blockPos = member.getEntity().loc.block();
        Block railsBlock = blockPos.toBlock(member.getEntity().getWorld());

        RailType railType = RailType.NONE;
        for (RailType type : RailType.values()) {
            try {
                IntVector3 pos = type.findRail(member, member.getEntity().getWorld(), blockPos);
                if (pos != null) {
                    railType = type;
                    railsBlock = pos.toBlock(member.getEntity().getWorld());
                    break;
                }
            } catch (Throwable t) {
                RailType.handleCriticalError(type, t);
                break;
            }
        }

        BlockFace direction = member.getDirectionTo();

        if (railType == RailType.NONE) {
            // When derailed, we must rely on relative positioning to figure out the direction
            Vector movement = member.getEntity().getVelocity();
            if (member.isSingle()) {
                movement = member.getEntity().getVelocity();
            } else {
                MinecartMember<?> next = member.getNeighbour(-1);
                if (next != null) {
                    movement = member.getEntity().last.offsetTo(next.getEntity().last);
                } else {
                    MinecartMember<?> prev = member.getNeighbour(1);
                    if (prev != null) {
                        movement = prev.getEntity().last.offsetTo(member.getEntity().last);
                    } else {
                        // Should not happen, but fallback to velocity again
                        movement = member.getEntity().getVelocity();
                    }
                }
            }

            if (movement.getX() == 0.0 && movement.getZ() == 0.0) {
                direction = FaceUtil.getVertical(movement.getY());
            } else {
                direction = FaceUtil.getDirection(movement, false);
            }
        } else {
            // Track back the location of the minecart using the velocity towards the edge of the current block
            // The edge we encounter is the direction we have to use
            // For x/y/z, see how many times we have to multiply the velocity to get to it
            // The one with the lowest multiplication indicates the edge we will hit first
            double minFact = -Double.MAX_VALUE;
            CommonMinecart<?> entity = member.getEntity();
            for (BlockFace dir : FaceUtil.BLOCK_SIDES) {
                double a, b, c;
                if (dir.getModX() != 0) {
                    // x
                    a = -0.5 * (1 + dir.getModX());
                    b = entity.loc.getX() - blockPos.x;
                    c = entity.vel.getX();
                } else if (dir.getModY() != 0) {
                    // y
                    a = -0.5 * (1 + dir.getModY());
                    b = entity.loc.getY() - blockPos.y;
                    c = entity.vel.getY();
                } else {
                    // z
                    a = -0.5 * (1 + dir.getModZ());
                    b = entity.loc.getZ() - blockPos.z;
                    c = entity.vel.getZ();
                }
                double f = (c == 0.0) ? -Double.MAX_VALUE : ((b - a) / c);
                if (f >= minFact) {
                    minFact = f;
                    direction = dir;
                }
            }
        }

        return new RailInfo(railsBlock, railType, railType.getLeaveDirection(railsBlock, direction));
    }

    public static class RailInfo {
        public final IntVector3 railsPos;
        public final Block railsBlock;
        public final RailType railsType;
        public final BlockFace direction;

        public RailInfo(Block railsBlock, RailType railsType, BlockFace direction) {
            this.railsBlock = railsBlock;
            this.railsType = railsType;
            this.direction = direction;
            if (railsBlock == null) {
                this.railsPos = new IntVector3(0, 0, 0);
            } else {
                this.railsPos = new IntVector3(railsBlock.getX(), railsBlock.getY(), railsBlock.getZ());
            }
        }

        public RailInfo changeDirection(BlockFace dir) {
            return new RailInfo(railsBlock, railsType, dir);
        }
    }
}
