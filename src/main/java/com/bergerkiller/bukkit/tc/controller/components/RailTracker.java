package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
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
        /** Block position of the minecart on the rails */
        public final Block minecartBlock;
        /** Position of the rails (same as {@link #block}) */
        public final IntVector3 position;
        /** Whether this rail is disconnected from the previous rails */
        public final boolean disconnected;
        /** Cached rail path taken on this rail */
        public RailPath cachedPath = null;
        /** State on the rail when this TrackedRail was created */
        public final RailState state;

        public TrackedRail(MinecartMember<?> member, TrackWalkingPoint point, boolean disconnected) {
            this(member, point.state.clone(), disconnected);
        }

        public TrackedRail(MinecartMember<?> member, RailState state, boolean disconnected) {
            state.position().assertAbsolute();
            this.member = member;
            this.state = state;
            this.state.setMember(member);
            this.minecartBlock = state.positionBlock();
            this.disconnected = disconnected;

            Block railBlock = this.state.railBlock();
            if (railBlock == null) {
                this.position = new IntVector3(0, 0, 0);
            } else {
                this.position = new IntVector3(railBlock.getX(), railBlock.getY(), railBlock.getZ());
            }
        }

        // This constructor is used by RailTrackerMember for the uninitialized rail
        public TrackedRail(MinecartMember<?> member) {
            this.member = member;
            this.state = new RailState();
            this.state.setMember(member);
            this.state.setRailPiece(RailPiece.NONE);
            this.minecartBlock = null;
            this.disconnected = false;
            this.position = new IntVector3(0, 0, 0);
            this.state.position().setMotion(new Vector(0, -1, 0));
            this.state.initEnterDirection();
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
            state.initEnterDirection();
            return new TrackedRail(member, state, this.disconnected);
        }

        /**
         * Creates new rail information with a changed member owner
         * 
         * @param member to set to
         * @return rail information with changed member
         */
        public TrackedRail changeMember(MinecartMember<?> member) {
            return new TrackedRail(member, this.state, this.disconnected);
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
            state.setRailPiece(RailPiece.create(RailType.NONE, loc.getBlock()));
            state.initEnterDirection();
            return new TrackedRail(member, state, false);
        }

        /**
         * Evaluates the Minecart position information and creates Tracked rail information for it.
         * 
         * @param member to get the tracked rail information for
         * @param disconnected whether the rail is disconnected from the previous
         * @return tracked rail information for the member
         */
        public static TrackedRail create(MinecartMember<?> member, boolean disconnected) {
            return new TrackedRail(member, member.discoverRail(), disconnected);
        }

        @Override
        public String toString() {
            RailPath.Position start = getPath().getStartPosition();
            RailPath.Position end = getPath().getEndPosition();
            start.makeAbsolute(state.railBlock());
            end.makeAbsolute(state.railBlock());
            RailPath.Position pos = state.position();
            return "POS{x=" + pos.posX + ",y=" + pos.posY + ",z=" + pos.posZ + "} "
                   + "START{x=" + start.posX + ",y=" + start.posY + ",z=" + start.posZ + "} "
                   + "END{x=" + end.posX + ",y=" + end.posY + ",z=" + end.posZ + "}";
        }
    }
}
