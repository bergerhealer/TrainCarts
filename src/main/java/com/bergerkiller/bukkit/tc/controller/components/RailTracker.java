package com.bergerkiller.bukkit.tc.controller.components;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
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
    public static class TrackedRail implements Cloneable {
        /** The minecart that uses this rail */
        public final MinecartMember<?> member;
        /** Block position of the minecart on the rails */
        public final Block minecartBlock;
        /** Whether this rail is disconnected from the previous rails */
        public final boolean disconnected;
        /** Cached rail path taken on this rail */
        public RailPath cachedPath = null;
        /** State on the rail when this TrackedRail was created */
        public final RailState state;
        /** Helps with assigning/un-assigning the member from the RailPiece members list */
        protected boolean memberAddedToRailPiece;

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
            this.memberAddedToRailPiece = false;
        }

        // This constructor is used by RailTrackerMember for the uninitialized rail
        public TrackedRail(MinecartMember<?> member) {
            this.member = member;
            this.state = new RailState();
            this.state.setMember(member);
            this.state.setRailPiece(RailPiece.NONE);
            this.minecartBlock = null;
            this.disconnected = false;
            this.state.position().setMotion(new Vector(0, -1, 0));
            this.state.initEnterDirection();
            this.memberAddedToRailPiece = false;
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

        @Override
        public TrackedRail clone() {
            return new TrackedRail(this.member, this.state, this.disconnected);
        }

        void handleMemberRemove() {
            memberAddedToRailPiece = false;
            try {
                state.railPiece().mutableMembers().remove(member);
            } catch (RailLookup.RailTypeNotRegisteredException ex) {
                /* ignore */
            }
        }

        void handleMemberAdd() {
            memberAddedToRailPiece = true;
            List<MinecartMember<?>> members = state.railPiece().mutableMembers();
            if (!members.contains(member)) {
                members.add(member);
            }
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

    /**
     * Walks distances along train tracked rail information
     */
    public static class TrackedRailWalker {
        private final List<TrackedRail> rails;
        private TrackedRail currentRail;
        private int currentRailIndex;
        private int order;
        private final RailState state;

        public TrackedRailWalker(List<TrackedRail> rails, TrackedRail currentRail) {
            this.rails = rails;
            this.currentRail = currentRail;
            this.currentRailIndex = this.rails.indexOf(currentRail);
            this.order = -1;
            this.state = currentRail.state.clone();
            this.state.position().makeAbsolute(this.state.railBlock());
        }

        public RailState state() {
            return this.state;
        }

        public RailPath.Position position() {
            return this.state.position();
        }

        public void invertMotion() {
            this.state.position().invertMotion();
            this.order = -this.order;
        }

        /**
         * Moves a distance on the tracked rails
         *
         * @param distance Distance to move
         * @return Actual distance moved. 0.0 if no more movement is possible.
         */
        public double move(double distance) {
            // Guard against init failures when we don't have tracked rails at all
            if (currentRailIndex == -1) {
                return 0.0;
            }

            double movedTotal = 0.0;
            double distanceRemaining = distance;
            while (true) {
                double moved = currentRail.getPath().move(state, distanceRemaining);
                movedTotal += moved;
                if (moved >= distanceRemaining) {
                    return distance; //movedTotal;
                }

                // Next rail, keep within bounds so invertMotion() stays functional
                currentRailIndex += order;
                if (currentRailIndex < 0) {
                    currentRailIndex = 0;
                    break;
                } else if (currentRailIndex >= rails.size()) {
                    currentRailIndex = rails.size() - 1;
                    break;
                }
                currentRail = rails.get(currentRailIndex);
                state.setRailPiece(currentRail.state.railPiece());
                distanceRemaining = distance - movedTotal;
            }

            return movedTotal;
        }
    }
}
