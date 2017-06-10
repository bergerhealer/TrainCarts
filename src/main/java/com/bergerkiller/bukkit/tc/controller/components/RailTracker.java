package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackMovingPoint;

public abstract class RailTracker {

    /**
     * Checks whether the Minecart or Train drives on a particular rail block
     * 
     * @param railsBlock to check
     * @return True if driving on that rails block, False if not
     */
    public abstract boolean isOnRails(Block railsBlock);


    /**
     * Represents a single Rails block that is tracked by the Rail Tracker.
     * Rail state information can be retrieved in relation to the minecarts on them.
     */
    public static class TrackedRail {
        /** The minecart that uses this rail */
        public final MinecartMember<?> member;
        /** Block position of the minecart on the rails */
        public final Block minecartBlock;
        /** Position of the rails (same as {@link #block}) */
        public final IntVector3 position;
        /** Block the rails is at */
        public final Block block;
        /** Type of rails */
        public final RailType type;
        /** The direction at which the rail was entered by the minecart */
        public final BlockFace direction;
        /** Whether this rail is disconnected from the previous rails */
        public final boolean disconnected;

        public TrackedRail(MinecartMember<?> member, TrackMovingPoint point, boolean disconnected) {
            this(member, point.current, point.currentTrack, point.currentRail, disconnected, point.currentDirection);
        }

        public TrackedRail(MinecartMember<?> member, Block minecartBlock, Block railsBlock,RailType railsType, boolean disconnected, BlockFace direction) {
            this.member = member;
            this.minecartBlock = minecartBlock;
            this.block = railsBlock;
            this.type = railsType;
            this.direction = direction;
            this.disconnected = disconnected;
            if (railsBlock == null) {
                this.position = new IntVector3(0, 0, 0);
            } else {
                this.position = new IntVector3(railsBlock.getX(), railsBlock.getY(), railsBlock.getZ());
            }
        }

        /**
         * Creates new rail information with a changed movement direction
         * 
         * @param dir to set the direction to
         * @return rail information with changed direction
         */
        public TrackedRail changeDirection(BlockFace dir) {
            return new TrackedRail(member, minecartBlock, block, type, this.disconnected, dir);
        }

        /**
         * Evaluates the Minecart position information and creates Tracked rail information for it.
         * 
         * @param member to get the tracked rail information for
         * @param disconnected whether the rail is disconnected from the previous
         * @return tracked rail information for the member
         */
        public static TrackedRail create(MinecartMember<?> member, boolean disconnected) {
            final IntVector3 blockPos = member.getEntity().loc.block();
            final Block block = blockPos.toBlock(member.getEntity().getWorld());
            Block railsBlock = block;

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

            BlockFace direction = null;
            Vector movement = member.getEntity().getVelocity();

            if (railType == RailType.NONE || member.getRailTracker().getLastRailType() == RailType.NONE) {
                // When derailed, we must rely on relative positioning to figure out the direction
                // This only works when the minecart has a direct neighbor
                // If no direct neighbor is available, it will default to using its own velocity
                if (!member.isSingle()) {
                    MinecartMember<?> next = member.getNeighbour(-1);
                    if (next != null) {
                        movement = member.getEntity().last.offsetTo(next.getEntity().last);
                    } else {
                        MinecartMember<?> prev = member.getNeighbour(1);
                        if (prev != null) {
                            movement = prev.getEntity().last.offsetTo(member.getEntity().last);
                        }
                    }
                }
            } else {
                // Track back the location of the minecart using the velocity towards the edge of the current block
                // The edge we encounter is the direction we have to use
                // For x/y/z, see how many times we have to inversely multiply the velocity to get to it
                // The one with the lowest multiplication indicates the edge we will hit first
                // Imagine taking steps back at the current velocity until the minecart hits an edge of the current block
                // The first edge encountered is the edge the minecart came from
                double minFact = Double.MAX_VALUE;
                CommonMinecart<?> entity = member.getEntity();
                for (BlockFace dir : FaceUtil.BLOCK_SIDES) {
                    double a, b, c;
                    if (dir.getModX() != 0) {
                        // x
                        a = 0.5 * (1 + dir.getModX());
                        b = entity.loc.getX() - blockPos.x;
                        c = entity.vel.getX();
                    } else if (dir.getModY() != 0) {
                        // y
                        a = 0.5 * (1 + dir.getModY());
                        b = entity.loc.getY() - blockPos.y;
                        c = entity.vel.getY();
                    } else {
                        // z
                        a = 0.5 * (1 + dir.getModZ());
                        b = entity.loc.getZ() - blockPos.z;
                        c = entity.vel.getZ();
                    }
                    if (c == 0.0) {
                        continue;
                    }
                    double f = ((b - a) / c);
                    if (f >= 0.0 && f < minFact) {
                        minFact = f;
                        direction = dir.getOppositeFace();
                    }
                }
            }

            // By default we take the movement vector and turn it into a BlockFace
            if (direction == null) {
                if (movement.getX() == 0.0 && movement.getZ() == 0.0) {
                    direction = FaceUtil.getVertical(movement.getY());
                } else {
                    direction = FaceUtil.getDirection(movement, false);
                }
            }

            return new TrackedRail(member, block, railsBlock, railType, disconnected, direction);
        }
    }
}
