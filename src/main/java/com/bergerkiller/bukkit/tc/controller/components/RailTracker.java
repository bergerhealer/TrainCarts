package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.RailInfo;
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
        public final BlockFace enterFace;
        /** Whether this rail is disconnected from the previous rails */
        public final boolean disconnected;
        /** Cached rail path taken on this rail */
        public RailPath cachedPath = null;

        public TrackedRail(MinecartMember<?> member, TrackMovingPoint point, boolean disconnected) {
            this(member, point.current, point.currentTrack, point.currentRail, disconnected, point.currentDirection);
        }

        public TrackedRail(MinecartMember<?> member, Block minecartBlock, Block railsBlock,RailType railsType, boolean disconnected, BlockFace direction) {
            this.member = member;
            this.minecartBlock = minecartBlock;
            this.block = railsBlock;
            this.type = railsType;
            this.enterFace = direction;
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
         * Creates new rail information with a changed member owner
         * 
         * @param member to set to
         * @return rail information with changed member
         */
        public TrackedRail changeMember(MinecartMember<?> member) {
            return new TrackedRail(member, minecartBlock, block, type, this.disconnected, this.enterFace);
        }

        public RailLogic getLogic() {
            return this.type.getLogic(null, this.block, this.enterFace);
        }

        public RailPath getPath() {
            if (this.cachedPath == null) {
                this.cachedPath = getLogic().getPath();
            }
            return this.cachedPath;
        }

        /**
         * Computes Tracked Rail information for a Minecart that is off the rails
         * 
         * @param member
         * @return detailed tracked rail information
         */
        public static TrackedRail createDerailed(MinecartMember<?> member) {
            // When derailed, we must rely on relative positioning to figure out the direction
            // This only works when the minecart has a direct neighbor
            // If no direct neighbor is available, it will default to using its own velocity
            Vector movement = member.getEntity().getVelocity();
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

            // Take the movement vector and turn it into a BlockFace
            BlockFace direction;
            if (movement.getX() == 0.0 && movement.getZ() == 0.0) {
                direction = FaceUtil.getVertical(movement.getY());
            } else {
                direction = FaceUtil.getDirection(movement, false);
            }

            Block posBlock = member.getEntity().loc.toBlock();
            return new TrackedRail(member, posBlock, posBlock, RailType.NONE, false, direction);
        }

        /**
         * Evaluates the Minecart position information and creates Tracked rail information for it.
         * 
         * @param member to get the tracked rail information for
         * @param disconnected whether the rail is disconnected from the previous
         * @return tracked rail information for the member
         */
        public static TrackedRail create(MinecartMember<?> member, boolean disconnected) {
            RailInfo railInfo = member.discoverRail();
            if (railInfo == null) {
                Block posBlock = member.getEntity().loc.toBlock();
                railInfo = new RailInfo(posBlock, posBlock, RailType.NONE);
            }
            return create(member, railInfo, disconnected);
        }

        /**
         * Evaluates the Minecart position and rail information and creates Tracked rail information for it.
         * 
         * @param member to get the tracked rail information for
         * @param railInfo of the rails previously calculated for the member
         * @param disconnected whether the rail is disconnected from the previous
         * @return tracked rail information for the member
         */
        public static TrackedRail create(MinecartMember<?> member, RailInfo railInfo, boolean disconnected) {
            final Block block = railInfo.posBlock;
            RailType railType = railInfo.railType;
            Block railsBlock = railInfo.railBlock;

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
                CommonMinecart<?> entity = member.getEntity();
                Vector pos = new Vector(entity.loc.getX() - block.getX(),
                                        entity.loc.getY() - block.getY(),
                                        entity.loc.getZ() - block.getZ());
                //TODO: Use railsBlock and rail type bounding box instead
                direction = Util.calculateEnterFace(pos, entity.vel.vector());
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
