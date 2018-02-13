package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath.Position;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.RailInfo;
import com.bergerkiller.bukkit.tc.utils.TrackMovingPoint;

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
        try (Timings t = TCTimings.RAILTRACKER_REFRESH.start()) {
            this.prevRails.clear();
            this.prevRails.addAll(this.rails);
            this.rails.clear();
            refreshFrom(this.owner.size() - 1, false);
            calcWheelTracks();
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

        // Walk 0.0 distance forwards to calculate the orientation of the start rails
        RailLogic startLogic = startInfo.getLogic();
        BlockFace movementDirectionFace = startLogic.getMovementDirection(startInfo.enterFace);
        Position position = Position.fromPosDir(tail.getEntity().loc.vector(), FaceUtil.faceToVector(movementDirectionFace));
        startLogic.getPath().move(position, startInfo.block, 0.0);

        // Find the forwards wheel distance
        double wheelDistance;
        if ((position.motDot(tail.getOrientationForward()) > 0.0)) {
            wheelDistance = tail.getWheels().front().getDistance();
        } else {
            wheelDistance = tail.getWheels().back().getDistance();
        }

        // Walk the distance from the current position (and rails) in the direction
        if (wheelDistance > WheelTrackerMember.MIN_WHEEL_DISTANCE) {
            TrackMovingPoint p = new TrackMovingPoint(startInfo.block, startInfo.enterFace);

            int loopCtr = 0; // This is to prevent infinite loops
            boolean first = true;
            while (p.hasNext() && wheelDistance > WheelTrackerMember.MIN_WHEEL_DISTANCE) {
                p.next();

                RailLogic logic = p.currentRail.getLogic(null, p.currentTrack, p.currentDirection);
                RailPath path = logic.getPath();
                double moved = path.move(position, p.currentTrack, wheelDistance);

                if (moved > 0.0) {
                    wheelDistance -= moved;
                    loopCtr = 0;
                } else if (++loopCtr > LOOP_LIMIT) {
                    System.err.println("Loop detected [1] logic=" + startLogic + " rail=" + startInfo.block);
                    break;
                }

                if (first) {
                    first = false;
                } else {
                    // Add rail information
                    this.rails.add(++railIndex, new TrackedRail(tail, p, false));
                }
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

        BlockFace movementDirectionFace = startInfo.getLogic().getMovementDirection(startInfo.enterFace);

        // No next member, so we can't compute a direction from that
        // Simply walk the wheel distance forwards to find out that angle
        // Which wheel is in the direction we are going to be looking at?
        double wheelDistance;
        Vector ownDirection = tail.getOrientationForward();
        if (MathUtil.isHeadingTo(movementDirectionFace, ownDirection)) {
            wheelDistance = tail.getWheels().back().getDistance();
        } else {
            wheelDistance = tail.getWheels().front().getDistance();
        }

        // Use known previous rail information to walk backwards to find the rails of the back wheel
        // Any distance remaining will have to be settled by walking the rails backwards, which might fail
        // First, find the index of the rails in prevRails from which we can start looking
        if (wheelDistance > WheelTrackerMember.MIN_WHEEL_DISTANCE) {
            // Figure out which rail to start looking from
            int prevRailStartIndex = -1;
            for (int i = 0; i < this.prevRails.size(); i++) {
                if (this.prevRails.get(i).position.equals(startInfo.position)) {
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
                        Block nextPos = prev.type.getNextPos(prev.block, prev.enterFace);
                        if (nextPos != null && nextPos.equals(startInfo.minecartBlock)) {
                            this.prevRails.add(i, startInfo);
                            prevRailStartIndex = i;
                        }
                        break;
                    }
                }
            }

            // Calculate the actual direction in which the minecart moves
            // This is important when initializing the direction to move over the paths
            Vector movementDirection = FaceUtil.faceToVector(movementDirectionFace);
            movementDirection.multiply(-1.0);

            Position position = Position.fromPosDir(tail.getEntity().loc.vector(), movementDirection);
            position.reverse = true;

            // If previous rails are found, walk them first
            if (prevRailStartIndex != -1) {
                TrackedRail startRail = this.prevRails.get(prevRailStartIndex);
                BlockFace prevStartDirection = startRail.getLogic().getMovementDirection(startRail.enterFace);

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
                    if (MathUtil.isHeadingTo(prevStartDirection, new Vector(position.motX, position.motY, position.motZ))) {
                        order = -1;
                    } else {
                        order = 1;
                    }
                    for (int prevRailIndex = prevRailStartIndex + order; prevRailIndex >= 0 && prevRailIndex < this.prevRails.size() && wheelDistance > 0.0001; prevRailIndex += order) {
                        TrackedRail rail = this.prevRails.get(prevRailIndex);

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
                            rail = rail.changeDirection(position.getMotionFace().getOppositeFace());
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

                    RailLogic logic = p.currentRail.getLogic(null, p.currentTrack, p.currentDirection);
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
                        TrackedRail rail = new TrackedRail(tail, p, false);
                        rail = rail.changeDirection(p.currentDirection.getOppositeFace());
                        rail.cachedPath = path;
                        this.rails.add(railIndex, rail);
                    }
                }
            }

            /*
            if (position != null) {
                org.bukkit.Location loc = position.toLocation(owner.getWorld());
                com.bergerkiller.bukkit.tc.Util.spawnParticle(loc, org.bukkit.Particle.WATER_BUBBLE);
                
                //Util.spawnParticle(owner.get(0).getEntity().getLocation(), Particle.REDSTONE);
            }
            */
        }
    }

    private final void refreshFrom(int memberIndex, boolean disconnected) {
        // Iterate the tracks from the minecart from the tail to the front
        // If we fail to find the next minecart in the chain within a limit
        // amount of blocks, assume the train has split at that minecart.
        MinecartMember<?> tail = this.owner.get(memberIndex);
        final TrackedRail startInfo = TrackedRail.create(tail, disconnected);

        // Next minecart to be looking for
        int nextMemberIndex = (memberIndex - 1);
        if (nextMemberIndex < 0) {
            // No next member! Train stops here.
            tail.getRailTracker().refresh(startInfo);
            this.rails.add(startInfo);
            return;
        }

        // If derailed, skip checking the tracks for this minecart
        if (startInfo.type == RailType.NONE) {
            tail.getRailTracker().refresh(startInfo);
            this.rails.add(startInfo);
            refreshFrom(nextMemberIndex, false);
            return;
        }

        MinecartMember<?> nextMember = this.owner.get(nextMemberIndex);
        Block nextPos = getRailPos(nextMember);

        // First use the current direction we know to find the next member in the train
        // If this fails, switch to using all possible directions of the current track
        int firstEntryIndex = this.rails.size();
        boolean foundNextMember = false;
        int maximumDistanceBlocks = tail.getMaximumBlockDistance(nextMember);
        int moveLimitCtr = 0;
        int possibleDirIdx = 0;
        BlockFace[] possible = null;
        TrackedRail moveInfo = startInfo;
        while (true) {
            if (nextPos == null) {
                break; // member is not on a rail. Do not look for it.
            }
            TrackMovingPoint p = new TrackMovingPoint(moveInfo.block, moveInfo.enterFace);
            if (p.hasNext()) {
                p.next();
                moveLimitCtr = 0;
                boolean isFirstBlock = true;
                int nrCachedRails = 0; // rails added without certainty of being correct
                while (true) {
                    if (p.currentTrack.getX() == nextPos.getX() && p.currentTrack.getY() == nextPos.getY() && p.currentTrack.getZ() == nextPos.getZ()) {
                        // If we found the next member for the first time, also update the starting minecart with the correct info
                        if (!foundNextMember) {
                            foundNextMember = true;
                            tail.getRailTracker().refresh(moveInfo);
                            this.rails.add(firstEntryIndex, moveInfo);
                        }

                        // Refresh the next minecart with the information currently iterating at
                        nrCachedRails = 0;
                        TrackedRail currInfo = new TrackedRail(nextMember, p, false);
                        nextMember.getRailTracker().refresh(currInfo);
                        this.rails.add(currInfo);

                        // Continue looking for more minecarts
                        if (--nextMemberIndex < 0) {
                            nextMember = null;
                            break; // we're done!
                        }
                        moveLimitCtr = 0;
                        nextMember = owner.get(nextMemberIndex);
                        nextPos = getRailPos(nextMember);
                        maximumDistanceBlocks = currInfo.member.getMaximumBlockDistance(nextMember);
                        isFirstBlock = true;
                        if (nextPos == null) {
                            break; // member is derailed
                        }
                    } else if (p.hasNext() && (++moveLimitCtr) <= maximumDistanceBlocks) {
                        if (isFirstBlock) {
                            isFirstBlock = false;
                        } else {
                            // Keep track of the Minecart we are trying to find for the in-between blocks
                            // This is important for the block space
                            TrackedRail currInfo = new TrackedRail(nextMember, p, false);
                            this.rails.add(currInfo);
                            nrCachedRails++;
                        }
                        p.next();
                    } else {
                        // Remove all cached rails - rails iteration failed
                        while (nrCachedRails > 0) {
                            nrCachedRails--;
                            this.rails.remove(this.rails.size() - 1);
                        }
                        break; // out of track
                    }
                }
            } else {
                //System.out.println("COULD NOT FIND RAILS");
            }

            // If we found a next member and iterating the track was done, quit looking
            if (foundNextMember) {
                break;
            }

            // Attempt to look from other directions
            if (possible == null) {
                possible = startInfo.type.getPossibleDirections(startInfo.block);
            }
            if (possibleDirIdx >= possible.length) {
                break; // out of directions to try!
            }
            moveInfo = moveInfo.changeDirection(possible[possibleDirIdx++]);
        }

        // If we did not find the very next minecart from looking at the tail, we must refresh it
        if (!foundNextMember) {
            foundNextMember = true;
            tail.getRailTracker().refresh(startInfo);
            this.rails.add(startInfo);
        }

        // If there are more minecarts remaining in the chain, these could not be found using the iterator
        // We will have to disconnect these from the train later, and they have to be iterated by themselves
        // Mark disconnected when the next rail pos is known (minecart is railed), but was not found before
        if (nextMemberIndex >= 0) {
            refreshFrom(nextMemberIndex, nextPos != null);
        }
    }

    private static Block getRailPos(MinecartMember<?> member) {
        RailInfo railInfo = member.discoverRail();
        return (railInfo == null) ? null : railInfo.railBlock;
    }
}
