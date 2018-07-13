package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackMovingPoint;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

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
        /** Whether this tracked rail is the base track (where middle of the Minecart is on) */
        public final boolean isBasePoint;
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
        /** State on the rail when this TrackedRail was created */
        public final RailState state;

        @Deprecated
        public TrackedRail(MinecartMember<?> member, Location location, TrackMovingPoint point, boolean isBasePoint, boolean disconnected) {
            this(member, location, point.current, point.currentTrack, point.currentRail, isBasePoint, disconnected, FaceUtil.faceToVector(point.currentDirection), point.currentDirection);
        }

        public TrackedRail(MinecartMember<?> member, TrackWalkingPoint point, boolean isBasePoint, boolean disconnected) {
            this(member, point.state, isBasePoint, disconnected);
        }

        public TrackedRail(MinecartMember<?> member, RailState state, boolean isBasePoint, boolean disconnected) {
            state.position().assertAbsolute();
            this.member = member;
            this.isBasePoint = isBasePoint;
            this.state = state.clone();
            this.state.setMember(member);
            this.minecartBlock = state.positionBlock();
            this.type = state.railType();
            this.block = state.railBlock();
            this.disconnected = disconnected;
            if (this.block == null) {
                this.enterFace = Util.vecToFace(this.state.motionVector(), false);
                this.position = new IntVector3(0, 0, 0);
            } else {
                this.enterFace = state.enterFace();
                this.position = new IntVector3(this.block.getX(), this.block.getY(), this.block.getZ());
            }
        }

        public TrackedRail(MinecartMember<?> member, Location location, Block minecartBlock, Block railsBlock, RailType railsType, boolean isBasePoint, boolean disconnected, Vector motionVector, BlockFace direction) {
            this.member = member;
            this.isBasePoint = isBasePoint;
            this.state = new RailState();
            this.state.setMember(member);
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
            this.state.setRailBlock(railsBlock);
            this.state.setRailType(railsType);
            if (location != null) {
                this.state.position().setLocation(location);
            } else if (railsBlock != null) {
                this.state.position().setLocationMidOf(railsBlock);
            }
            this.state.position().setMotion(motionVector);
        }

        /**
         * Gets the world coordinates of the Minecart, null if no member is on this rail
         * 
         * @return member location
         */
        public Location getMemberLocation() {
            return this.state.positionLocation();
        }

        /**
         * Inverts the motion vector, making the path move in the exact opposite direction
         * 
         * @return tracked rail with inverted motion vector
         */
        public TrackedRail invertMotionVector() {
            RailState state = this.state.clone();
            RailPath.Position p = state.position();
            p.motX = -p.motX;
            p.motY = -p.motY;
            p.motZ = -p.motZ;
            Util.calculateEnterFace(state);
            return new TrackedRail(member, state, this.isBasePoint, this.disconnected);
        }

        /**
         * Creates new rail information with a changed member owner
         * 
         * @param member to set to
         * @return rail information with changed member
         */
        public TrackedRail changeMember(MinecartMember<?> member) {
            return new TrackedRail(member, this.state, this.isBasePoint, this.disconnected);
        }

        /**
         * Creates new rail information with a changed isBasePoint
         * 
         * @param isBasePoint to set to
         * @return rail information with changed isBasePoint
         */
        public TrackedRail setBasePoint(boolean isBasePoint) {
            if (this.isBasePoint == isBasePoint) {
                return this;
            }
            return new TrackedRail(this.member, this.state, isBasePoint, this.disconnected);
        }

        public RailLogic getLogic() {
            return this.state.loadRailLogic();
        }

        public RailPath getPath() {
            if (this.cachedPath == null) {
                this.cachedPath = getLogic().getPath();
            }
            return this.cachedPath;
        }

        public boolean isSameTrack(TrackedRail other) {
            return this.state.isSameRails(other.state) &&
                   this.getPath().equals(other.getPath());
        }

        /**
         * Computes Tracked Rail information for a Minecart that is off the rails
         * 
         * @param member
         * @return detailed tracked rail information
         */
        public static TrackedRail createDerailed(MinecartMember<?> member) {
            Location loc = member.getEntity().getLocation();
            RailState state = new RailState();
            state.position().setLocation(loc);
            state.position().setMotion(member.getEntity().getVelocity());
            state.setRailBlock(loc.getBlock());
            state.setRailType(RailType.NONE);
            Util.calculateEnterFace(state);
            return new TrackedRail(member, state, true, false);
        }

        /**
         * Evaluates the Minecart position information and creates Tracked rail information for it.
         * 
         * @param member to get the tracked rail information for
         * @param disconnected whether the rail is disconnected from the previous
         * @return tracked rail information for the member
         */
        public static TrackedRail create(MinecartMember<?> member, boolean disconnected) {
            return new TrackedRail(member, member.discoverRail(), true, disconnected);
        }

    }
}
