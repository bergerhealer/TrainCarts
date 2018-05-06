package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath.Position;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackMovingPoint;
import com.bergerkiller.bukkit.tc.utils.TrackWalkingPoint;

/**
 * Uses a track iterator to keep track of the rails a train is driving on.
 * This information is then used to update minecart rails information,
 * handle the detection of signs, update minecart movement directions and
 * detect splitting of trains
 */
public class RailTrackerGroup extends RailTracker {
    private static final int LOOP_LIMIT = 10; // This amount of tracks iterated w/o movement = ABORT
    private final MinecartGroup owner;
    private final ArrayList<TrackedRail> prevRails = new ArrayList<TrackedRail>();
    private final ArrayList<TrackedRail> rails = new ArrayList<TrackedRail>();

    public RailTrackerGroup(MinecartGroup owner) {
        this.owner = owner;
    }

    /**
     * Gets a list of all rails blocks that the train occupies.
     * Each item contains information about the rails and the minecart that is 'on' it.
     * 
     * @return List of rails block information
     */
    public List<TrackedRail> getRailInformation() {
        return this.rails;
    }

    @Override
    public boolean isOnRails(Block railsBlock) {
        return getMemberFromRails(railsBlock) != null;
    }

    /**
     * Gets the Minecart Member part of this Group that is traveling on the
     * rails block specified
     *
     * @param railsBlock to get the Minecart Member for
     * @return the Minecart Member, or null if not found
     */
    public MinecartMember<?> getMemberFromRails(Block railsBlock) {
        if (railsBlock.getWorld() != owner.getWorld()) {
            return null;
        }
        return getMemberFromRails(new IntVector3(railsBlock));
    }

    /**
     * Gets the Minecart Member part of this Group that is traveling on the
     * rails block specified
     *
     * @param railsBlockPosition to get the Minecart Member for
     * @return the Minecart Member, or null if not found
     */
    public MinecartMember<?> getMemberFromRails(IntVector3 railsBlockPosition) {
        //TODO: Is keeping a hashmap up to date a good idea? This loop works just fine, too.
        for (TrackedRail info : rails) {
            if (info.position.equals(railsBlockPosition)) {
                return info.member;
            }
        }
        return null;
    }

    /**
     * Refreshes rail information, recalculating rail positions, directions and disconnect states
     */
    public void refresh() {
        final boolean DEBUG_RAILS = false;

        try (Timings t = TCTimings.RAILTRACKER_REFRESH.start()) {
            this.prevRails.clear();
            this.prevRails.addAll(this.rails);
            this.rails.clear();
            refreshFrom(this.owner.size() - 1, false);

            if (DEBUG_RAILS) {
                List<TrackedRail> behindRails = new ArrayList<TrackedRail>();
                List<TrackedRail> midRails = new ArrayList<TrackedRail>(this.rails);
                List<TrackedRail> aheadRails = new ArrayList<TrackedRail>();

                calcWheelTracks();

                boolean gotToAhead = false;
                for (TrackedRail rail : this.rails) {
                    if (midRails.contains(rail)) {
                        gotToAhead = true;
                    } else if (gotToAhead) {
                        aheadRails.add(rail);
                    } else {
                        behindRails.add(rail);
                    }
                }

                // Red: Behind tracks
                for (int i = 0; i < behindRails.size(); i++) {
                    Location loc = behindRails.get(i).block.getLocation().add(0.5, 0.5, 0.5);
                    double theta =  (double) i / (double) (behindRails.size() - 1);

                    Util.spawnDustParticle(loc, 0.5 * theta + 0.5, 0.0, 0.0);
                }
                // Red-Green with blueish: Middle tracks
                for (int i = 0; i < midRails.size(); i++) {
                    Location loc = midRails.get(i).block.getLocation().add(0.5, 0.5, 0.5);
                    double theta = (double) i / (double) (midRails.size() - 1);

                    Util.spawnDustParticle(loc, 0.5 * (1.0 - theta), 0.5 * theta, 1.0);
                }
                // Green: Ahead tracks
                for (int i = 0; i < aheadRails.size(); i++) {
                    Location loc = aheadRails.get(i).block.getLocation().add(0.5, 0.5, 0.5);
                    double theta = (double) i / (double) (aheadRails.size() - 1);

                    Util.spawnDustParticle(loc, 0.0, 0.5 * (1.0 - theta) + 0.5, 0.0);
                }

            } else {
                calcWheelTracks();
            }

            Collections.reverse(this.rails);

            // Log the rail information
            /*
            String s = "";
            for (TrackedRail rail : this.rails) {
                s += "[" + rail.member.getIndex() + " " + rail.position + "]";
            }
            System.out.println(s);
            */

            // TODO: Detect when the rails are changed
            // Compare rails with prevRails to do so
            owner.getSignTracker().updatePosition();
        }
    }

    private final void calcWheelTracks() {
        // Error condition
        if (this.rails.isEmpty()) {
            return;
        }

        // Go by all the Minecarts and walk additional tracks when their wheels are potentially not found
        boolean hasPreviousMember = false;
        for (int i = 0; i < this.rails.size(); i++) {
            TrackedRail rail = this.rails.get(i);

            // Skip derailed rails
            // If a previous Minecart did exist, we must recalculate the rails after
            if (rail.type == RailType.NONE) {
                if (hasPreviousMember) {
                    calcWheelTracksAhead(i - 1);
                    hasPreviousMember = false;

                    // Make sure to continue iteration after these rails again
                    while (this.rails.get(i) != rail && i < this.rails.size()) {
                        i++;
                    }
                }
                continue;
            }

            // If we have no previous member, recalculate the tracks behind
            if (!hasPreviousMember || rail.disconnected) {
                calcWheelTracksBehind(i);
                hasPreviousMember = true;

                // Make sure to continue iteration after these rails again
                while (this.rails.get(i) != rail && i < this.rails.size()) {
                    i++;
                }
            }
        }

        // We must always calculate the tracks ahead for the last minecart
        calcWheelTracksAhead(this.rails.size() - 1);
    }

    private final void calcWheelTracksAhead(int railIndex) {
        TrackedRail startInfo = this.rails.get(railIndex);
        MinecartMember<?> tail = startInfo.member;
        if (startInfo.type == RailType.NONE) {
            return;
        }

        // Don't do anything if no wheel distance is set
        if (!tail.getWheels().hasWheelDistance()) {
            return;
        }

        // Retrieve start position
        Position position = startInfo.state.position().clone();

        // Find the forwards wheel distance
        double wheelDistance;
        if ((position.motDot(tail.getOrientationForward()) > 0.0)) {
            wheelDistance = tail.getWheels().front().getDistance();
        } else {
            wheelDistance = tail.getWheels().back().getDistance();
        }

        // Walk the distance from the current position (and rails) in the direction
        if (wheelDistance > WheelTrackerMember.MIN_WHEEL_DISTANCE) {
            TrackWalkingPoint p = new TrackWalkingPoint(startInfo.state);

            int loopCtr = 0; // This is to prevent infinite loops
            while (true) {
                RailPath path = p.currentRailLogic.getPath();
                double moved = path.move(position, p.state.railBlock(), wheelDistance);

                if (moved > 0.0) {
                    wheelDistance -= moved;
                    loopCtr = 0;
                } else if (++loopCtr > LOOP_LIMIT) {
                    System.err.println("Loop detected [1] logic=" + p.currentRailLogic + " rail=" + startInfo.block);
                    break;
                }

                if (wheelDistance <= WheelTrackerMember.MIN_WHEEL_DISTANCE || !p.moveFull()) {
                    break;
                }

                // More rails. Add the rail.
                this.rails.add(++railIndex, new TrackedRail(tail, p.state, false));
            }

            //Location loc = position.toLocation(owner.getWorld());
            //Util.spawnParticle(loc, Particle.WATER_BUBBLE);

            /*
            Location loc = new Location(tail.getEntity().getWorld(), position.posX, position.posY, position.posZ);
            loc.setY(loc.getY() - 1.0);
            Util.spawnParticle(loc, Particle.WATER_BUBBLE);
            loc.add(new Vector(position.upX, position.upY, position.upZ).multiply(0.5));
            Util.spawnParticle(loc, Particle.WATER_BUBBLE);
            */
        }
    }

    private final void calcWheelTracksBehind(int railIndex) {
        TrackedRail startInfo = this.rails.get(railIndex);
        MinecartMember<?> tail = startInfo.member;
        if (startInfo.type == RailType.NONE) {
            return;
        }
        if (!tail.getWheels().hasWheelDistance()) {
            return;
        }

        // Calculate the actual direction in which the minecart moves
        // This is important when initializing the direction to move over the paths
        // Behind, so invert it (-1)
        Vector movementDirection = startInfo.state.motionVector();
        movementDirection.multiply(-1.0);

        // No next member, so we can't compute a direction from that
        // Simply walk the wheel distance forwards to find out that angle
        // Which wheel is in the direction we are going to be looking at?
        double wheelDistance;
        Vector ownDirection = tail.getOrientationForward();
        if (MathUtil.isHeadingTo(movementDirection, ownDirection)) {
            wheelDistance = tail.getWheels().front().getDistance();
        } else {
            wheelDistance = tail.getWheels().back().getDistance();
        }

        // Use known previous rail information to walk backwards to find the rails of the back wheel
        // Any distance remaining will have to be settled by walking the rails backwards, which might fail
        // First, find the index of the rails in prevRails from which we can start looking
        if (wheelDistance > WheelTrackerMember.MIN_WHEEL_DISTANCE) {
            // Figure out which rail to start looking from
            int prevRailStartIndex = -1;
            for (int i = this.prevRails.size() - 1; i >= 0; --i) {
                if (this.prevRails.get(i).state.isSameRails(startInfo.state)) {
                    prevRailStartIndex = i;
                    break;
                }
            }

            // If we failed to find a start rail, then we have a problem
            // Try and see if the first entry in this.prevRails leads to startInfo
            // If so, we can simply append startInfo as is after that one
            if (prevRailStartIndex == -1 && !this.prevRails.isEmpty()) {
                for (int i = 0; i < this.prevRails.size(); i++) {
                    if (this.prevRails.get(i).member == startInfo.member) {
                        TrackedRail prev = this.prevRails.get(i);
                        TrackWalkingPoint p = new TrackWalkingPoint(prev.state);
                        p.skipFirst();
                        if (p.moveFull()) {
                            if (p.state.isSameRails(startInfo.state)) {
                                this.prevRails.add(i, startInfo);
                                prevRailStartIndex = i;
                            }
                        }
                        break;
                    }
                }
            }

            Position position = Position.fromPosDir(tail.getEntity().loc.vector(), movementDirection);
            position.reverse = true;

            // If previous rails are found, walk them first
            if (prevRailStartIndex != -1) {
                TrackedRail startRail = this.prevRails.get(prevRailStartIndex);

                // Move as much as possible over the current rail
                // This sets our position to the end-position of the current rail
                RailLogic startLogic = startRail.getLogic();
                RailPath startPath = startLogic.getPath();
                double startMoved = startPath.move(position, startRail.block, wheelDistance);
                wheelDistance -= startMoved;
                if (wheelDistance > 0.0001) {
                    // We need to walk more tracks. To do so, we must figure out whether we go +1 or -1.
                    // To find this out, we first obtain the movement direction over the start rails when forwards
                    int order;
                    if (startRail.state.position().motDot(position) > 0.0) {
                        order = -1;
                    } else {
                        order = 1;
                    }

                    for (int prevRailIndex = prevRailStartIndex + order; prevRailIndex >= 0 && prevRailIndex < this.prevRails.size() && wheelDistance > 0.0001; prevRailIndex += order) {
                        TrackedRail rail = this.prevRails.get(prevRailIndex);
                        if (rail.state.isSameRails(startInfo.state)) {
                            continue;
                        }

                        // Walk this rail backwards
                        RailPath path = rail.getPath();
                        double moved = path.move(position, rail.block, wheelDistance);
                        wheelDistance -= moved;

                        // Create a new version of the tracked rail with the correct member
                        if (rail.member != startInfo.member) {
                            rail = rail.changeMember(startInfo.member);
                        }

                        // If the direction of the rail is wrong, fix it
                        if (order < 0) {
                            rail = rail.invertMotionVector();
                        }

                        rail.cachedPath = path;
                        this.rails.add(railIndex, rail);
                        startInfo = rail;
                    }
                }
            }

            // If more wheel distance remains, all we can do is walk the tracks in the opposite direction
            // This can actually be incorrect, for example when taking a junction
            // It will at least resolve correctly for straight rails
            if (wheelDistance > 0.0) {
                int loopCtr = 0;
                boolean first = true;
                TrackMovingPoint p = new TrackMovingPoint(startInfo.block, position.getMotionFace());
                while (p.hasNext() && wheelDistance > 0.0) {
                    p.next();

                    RailLogic logic = p.getState().loadRailLogic(null);
                    RailPath path = logic.getPath();
                    double moved = path.move(position, p.currentTrack, wheelDistance);

                    if (moved > 0.0) {
                        wheelDistance -= moved;
                        loopCtr = 0;
                    } else if (++loopCtr > LOOP_LIMIT) {
                        System.err.println("Loop detected [2] logic=" + logic + " rail=" + p.currentTrack);
                        break;
                    }

                    if (first) {
                        first = false;
                    } else {
                        // Add rail information
                        TrackedRail rail = new TrackedRail(tail, p.getState(), false);
                        rail = rail.invertMotionVector();
                        rail.cachedPath = path;
                        this.rails.add(railIndex, rail);
                    }
                }
            }

            if (position != null) {
                //org.bukkit.Location loc = position.toLocation(owner.getWorld());
                //com.bergerkiller.bukkit.tc.Util.spawnParticle(loc, org.bukkit.Particle.WATER_BUBBLE);
                
                //Util.spawnParticle(owner.get(0).getEntity().getLocation(), Particle.REDSTONE);
            }
        }
    }

    private final void refreshFrom(int memberIndex, boolean disconnected) {
        // Helper object for doing this algorithm
        RailFinder finder = new RailFinder(memberIndex, disconnected);

        // No next member! Train stops here.
        if (finder.startIndex < 0) {
            finder.tail.getRailTracker().refresh(finder.startInfo);
            this.rails.add(finder.startInfo);
            return;
        }

        // If derailed, skip checking the tracks for this minecart
        if (finder.startInfo.state.railType() == RailType.NONE) {
            finder.tail.getRailTracker().refresh(finder.startInfo);
            this.rails.add(finder.startInfo);
            refreshFrom(finder.startIndex, false);
            return;
        }

        // Number of remaining carts to be found
        int remainingCnt = memberIndex;

        // First, test the startInfo, which is the direction in which the cart is moving
        // If this yields insufficient number of carts, try the opposite direction
        // Select the one with the most detected carts and use the rails contained
        RailFinderResult result = finder.test(finder.startInfo);
        if (result.numMembers < remainingCnt && !result.endIsDerailed) {
            RailFinderResult alter = finder.test(finder.startInfo.invertMotionVector());
            if (alter.numMembers > result.numMembers) {
                result = alter;
            }
        }

        // Apply found rails to the members themselves
        for (int i = 0; i < result.rails.size(); i++) {
            TrackedRail rail = result.rails.get(i);
            if (i == (result.rails.size() - 1) || rail.member != result.rails.get(i + 1).member) {
                rail.member.getRailTracker().refresh(rail);
            }
        }

        // Add the rails result
        this.rails.addAll(result.rails);

        // If not all members are found, continue looking for more (= disconnected)
        // If there are more minecarts remaining in the chain, these could not be found using the iterator
        // We will have to disconnect these from the train later, and they have to be iterated by themselves
        // Mark disconnected when the next minecart isn't derailed, indicating the two carts are on different
        // tracks that can not reach each other.
        if (result.nextMemberIndex >= 0) {
            refreshFrom(result.nextMemberIndex, !result.endIsDerailed);
        }
    }

    private static RailState getRailPos(MinecartMember<?> member) {
        RailState state = member.discoverRail();
        return (state.railType() == RailType.NONE) ? null : state;
    }

    private class RailFinder {
        private MinecartMember<?> tail;
        private final TrackedRail startInfo;
        private final int startIndex;

        public RailFinder(int index, boolean disconnected) {
            // Iterate the tracks from the minecart from the tail to the front
            // If we fail to find the next minecart in the chain within a limit
            // amount of blocks, assume the train has split at that minecart.
            this.tail = owner.get(index);
            this.startInfo = TrackedRail.create(tail, disconnected);
            this.startIndex = (index - 1);
        }

        public RailFinderResult test(TrackedRail moveInfo) {
            RailFinderResult result = new RailFinderResult(this.startIndex);
            result.rails.add(moveInfo);

            MinecartMember<?> nextMember = owner.get(result.nextMemberIndex);
            RailState nextPos = getRailPos(nextMember);
            if (nextPos == null) {
                result.endIsDerailed = true;
                return result;
            }

            int moveLimitCtr = 0;
            int maximumDistanceBlocks = tail.getMaximumBlockDistance(nextMember);

            TrackWalkingPoint p = new TrackWalkingPoint(moveInfo.state);
            if (p.moveFull()) {
                moveLimitCtr = 0;
                boolean isFirstBlock = true;
                int nrCachedRails = 0; // rails added without certainty of being correct
                while (true) {
                    if (p.state.isSameRails(nextPos)) {
                        // If we found the next member for the first time, also update the starting minecart with the correct info
                        result.numMembers++;

                        // Preserve motion vector from walking point
                        // TODO: Should we instead 'move' towards nextPos from p.state?
                        //       This would better handle curved paths
                        if (p.state.position().motDot(nextPos.motionVector()) < 0.0) {
                            nextPos.position().invertMotion();
                        }

                        // Refresh the next minecart with the information currently iterating at
                        nrCachedRails = 0;
                        TrackedRail currInfo = new TrackedRail(nextMember, nextPos, false);
                        result.rails.add(currInfo);

                        // Continue looking for more minecarts
                        if (--result.nextMemberIndex < 0) {
                            nextMember = null;
                            nextPos = null;
                            break; // we're done!
                        }
                        moveLimitCtr = 0;
                        nextMember = owner.get(result.nextMemberIndex);
                        nextPos = getRailPos(nextMember);
                        maximumDistanceBlocks = currInfo.member.getMaximumBlockDistance(nextMember);
                        isFirstBlock = true;
                        if (nextPos == null) {
                            result.endIsDerailed = true;
                            break; // member is derailed
                        }
                    } else {
                        if (isFirstBlock) {
                            isFirstBlock = false;
                        } else {
                            // Keep track of the Minecart we are trying to find for the in-between blocks
                            // This is important for the block space
                            result.rails.add(new TrackedRail(nextMember, p, false));
                            nrCachedRails++;
                        }
                        if (++moveLimitCtr > maximumDistanceBlocks || !p.moveFull()) {
                            // Remove all cached rails - rails iteration failed
                            while (nrCachedRails > 0) {
                                nrCachedRails--;
                                result.rails.remove(result.rails.size() - 1);
                            }
                            break; // out of track
                        }
                    }
                }
            }
            return result;
        }
    }

    public static class RailFinderResult {
        public List<TrackedRail> rails;
        public int numMembers;
        public int nextMemberIndex;
        public boolean endIsDerailed;

        public RailFinderResult(int nextMemberIndex) {
            this.rails = new ArrayList<TrackedRail>();
            this.numMembers = 0;
            this.nextMemberIndex = nextMemberIndex;
            this.endIsDerailed = false;
        }
    }
}
