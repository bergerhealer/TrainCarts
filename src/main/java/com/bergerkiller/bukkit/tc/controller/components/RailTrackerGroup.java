package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.utils.TrackMovingPoint;

/**
 * Uses a track iterator to keep track of the rails a train is driving on.
 * This information is then used to update minecart rails information,
 * handle the detection of signs, update minecart movement directions and
 * detect splitting of trains
 */
public class RailTrackerGroup extends RailTracker {
    private final MinecartGroup owner;
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
        this.rails.clear();
        refreshFrom(this.owner.size() - 1, false);
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
            this.rails.add(0, startInfo);
            return;
        }

        // If derailed, skip checking the tracks for this minecart
        if (startInfo.type == RailType.NONE) {
            tail.getRailTracker().refresh(startInfo);
            this.rails.add(0, startInfo);
            refreshFrom(nextMemberIndex, false);
            return;
        }

        MinecartMember<?> nextMember = this.owner.get(nextMemberIndex);
        IntVector3 nextPos = getRailPos(nextMember);

        // First use the current direction we know to find the next member in the train
        // If this fails, switch to using all possible directions of the current track
        boolean foundNextMember = false;
        int moveLimitCtr = 0;
        int possibleDirIdx = 0;
        BlockFace[] possible = null;
        TrackedRail moveInfo = startInfo;
        while (true) {
            if (nextPos == null) {
                break; // member is not on a rail. Do not look for it.
            }
            TrackMovingPoint p = new TrackMovingPoint(moveInfo.block, moveInfo.direction);
            if (p.hasNext()) {
                p.next();
                moveLimitCtr = 0;
                boolean isFirstBlock = true;
                TrackedRail currInfo;
                int nrCachedRails = 0; // rails added without certainty of being correct
                while (true) {
                    if (p.currentTrack.getX() == nextPos.x && p.currentTrack.getY() == nextPos.y && p.currentTrack.getZ() == nextPos.z) {
                        // If we found the next member for the first time, also update the starting minecart with the correct info
                        if (!foundNextMember) {
                            foundNextMember = true;
                            tail.getRailTracker().refresh(moveInfo);
                            this.rails.add(0, moveInfo);
                        }

                        // Refresh the next minecart with the information currently iterating at
                        nrCachedRails = 0;
                        currInfo = new TrackedRail(nextMember, p, false);
                        nextMember.getRailTracker().refresh(currInfo);
                        this.rails.add(0, currInfo);

                        // Continue looking for more minecarts
                        if (--nextMemberIndex < 0) {
                            nextMember = null;
                            break; // we're done!
                        }
                        moveLimitCtr = 0;
                        nextMember = owner.get(nextMemberIndex);
                        nextPos = getRailPos(nextMember);
                        isFirstBlock = true;
                        if (nextPos == null) {
                            break; // member is derailed
                        }
                    } else if (p.hasNext() && (++moveLimitCtr) <= 6) {
                        if (isFirstBlock) {
                            isFirstBlock = false;
                        } else {
                            // Keep track of the Minecart we are trying to find for the in-between blocks
                            // This is important for the block space
                            currInfo = new TrackedRail(nextMember, p, false);
                            this.rails.add(0, currInfo);
                            nrCachedRails++;
                        }
                        p.next();
                    } else {
                        // Remove all cached rails - rails iteration failed
                        while (nrCachedRails > 0) {
                            nrCachedRails--;
                            this.rails.remove(0);
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

    private static IntVector3 getRailPos(MinecartMember<?> member) {
        IntVector3 block = member.getEntity().loc.block();
        for (RailType type : RailType.values()) {
            IntVector3 rail = type.findRail(member, member.getEntity().getWorld(), block);
            if (rail != null) {
                return rail;
            }
        }
        return null;
    }
}
