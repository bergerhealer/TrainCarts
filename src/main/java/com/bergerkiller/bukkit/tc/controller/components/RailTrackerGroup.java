package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;

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
    private final ArrayList<RailInfo> rails = new ArrayList<RailInfo>();

    public RailTrackerGroup(MinecartGroup owner) {
        this.owner = owner;
    }

    public void refresh() {
        refreshFrom(this.owner.size() - 1, false);
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

    private void refreshFrom(int memberIndex, boolean disconnected) {
        // Iterate the tracks from the minecart from the tail to the front
        // If we fail to find the next minecart in the chain within a limit
        // amount of blocks, assume the train has split at that minecart.
        MinecartMember<?> tail = this.owner.get(memberIndex);
        final RailInfo startInfo = findInfo(tail, disconnected);

        // Next minecart to be looking for
        int nextMemberIndex = (memberIndex - 1);
        if (nextMemberIndex < 0) {
            // No next member! Train stops here.
            tail.getRailTracker().refresh(startInfo);
            return;
        }

        // If derailed, skip checking the tracks for this minecart
        if (startInfo.railsType == RailType.NONE) {
            tail.getRailTracker().refresh(startInfo);
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
        RailInfo moveInfo = startInfo;
        while (true) {
            if (nextPos == null) {
                break; // member is not on a rail. Do not look for it.
            }
            TrackMovingPoint p = new TrackMovingPoint(moveInfo.railsBlock, moveInfo.direction);
            if (p.hasNext()) {
                p.next();
                moveLimitCtr = 0;
                while (true) {
                    if (p.currentTrack.getX() == nextPos.x && p.currentTrack.getY() == nextPos.y && p.currentTrack.getZ() == nextPos.z) {
                        // If we found the next member for the first time, also update the starting minecart with the correct info
                        if (!foundNextMember) {
                            foundNextMember = true;
                            tail.getRailTracker().refresh(moveInfo);
                        }

                        // Refresh the next minecart with the information currently iterating at
                        nextMember.getRailTracker().refresh(new RailInfo(p.currentTrack, p.currentRail, false, 
                                p.currentRail.getLeaveDirection(p.currentTrack, p.currentDirection)));

                        // Continue looking for more minecarts
                        if (--nextMemberIndex < 0) {
                            nextMember = null;
                            break; // we're done!
                        }
                        moveLimitCtr = 0;
                        nextMember = owner.get(nextMemberIndex);
                        nextPos = getRailPos(nextMember);
                        if (nextPos == null) {
                            break; // member is derailed
                        }
                    } else if (p.hasNext() && (++moveLimitCtr) <= 6) {
                        p.next();
                    } else {
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
                possible = startInfo.railsType.getPossibleDirections(startInfo.railsBlock);
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
        }

        // If there are more minecarts remaining in the chain, these could not be found using the iterator
        // We will have to disconnect these from the train later, and they have to be iterated by themselves
        // Mark disconnected when the next rail pos is known (minecart is railed), but was not found before
        if (nextMemberIndex >= 0) {
            refreshFrom(nextMemberIndex, nextPos != null);
        }
    }
}
