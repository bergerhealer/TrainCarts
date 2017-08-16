package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.List2D;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Keeps track of the active rails, signs and detector regions below a
 * MinecartGroup
 */
public class BlockTrackerGroup extends BlockTracker {
    private static final List<Block> signListBuffer = new ArrayList<Block>();
    private final MinecartGroup owner;
    private final ToggledState needsPositionUpdate = new ToggledState(true);

    public BlockTrackerGroup(MinecartGroup owner) {
        this.owner = owner;
    }

    /**
     * Gets the owner of this Block Tracker
     *
     * @return the Owner
     */
    public MinecartGroup getOwner() {
        return owner;
    }

    @Override
    protected void onSignChange(TrackedSign sign, boolean active) {
        SignActionEvent event = new SignActionEvent(sign.signBlock, sign.railsBlock, owner);
        event.setAction(active ? SignActionType.GROUP_ENTER : SignActionType.GROUP_LEAVE);
        SignAction.executeAll(event);
    }

    /**
     * Gets the Minecart Member part of this Group that is traveling on the
     * rails block specified.<br>
     * <br>
     * <b>Deprecated:</b> use the {@link MinecartGroup#getRailTracker()} for this instead.
     *
     * @param railsBlock to get the Minecart Member for
     * @return the Minecart Member, or null if not found
     */
    @Deprecated
    public MinecartMember<?> getMemberFromRails(Block railsBlock) {
        return owner.getRailTracker().getMemberFromRails(railsBlock);
    }

    /**
     * Gets the Minecart Member part of this Group that is traveling on the
     * rails block specified.<br>
     * <br>
     * <b>Deprecated:</b> use the {@link MinecartGroup#getRailTracker()} for this instead.
     *
     * @param railsBlockPosition to get the Minecart Member for
     * @return the Minecart Member, or null if not found
     */
    @Deprecated
    public MinecartMember<?> getMemberFromRails(IntVector3 railsBlockPosition) {
        return owner.getRailTracker().getMemberFromRails(railsBlockPosition);
    }

    @Override
    public void clear() {
        for (MinecartMember<?> member : owner) {
            member.getBlockTracker().clear();
        }
        super.clear();
        detectorRegions.clear();
    }

    @Override
    public void unload() {
        // Unload in detector regions
        if (!this.detectorRegions.isEmpty()) {
            for (DetectorRegion region : this.detectorRegions) {
                region.unload(owner);
            }
            this.detectorRegions.clear();
        }
        for (MinecartMember<?> member : owner) {
            member.getBlockTracker().unload();
        }
    }

    @Override
    @Deprecated
    public boolean isOnRails(Block railsBlock) {
        return owner.getRailTracker().isOnRails(railsBlock);
    }

    /**
     * Tells that this Block Tracker's Block Space (signs, detectors) needs to be updated at some point
     */
    public void updatePosition() {
        needsPositionUpdate.set();
    }

    @Override
    public boolean removeSign(Block signBlock) {
        if (super.removeSign(signBlock)) {
            for (MinecartMember<?> member : owner) {
                member.getBlockTracker().removeSign(signBlock);
            }
            return true;
        } else {
            return false;
        }
    }

    private List<TrackedSign> getSignList() {
        ArrayList<List<TrackedSign>> signsList = new ArrayList<List<TrackedSign>>(owner.size());
        for (MinecartMember<?> member : owner) {
            List<TrackedSign> memberList = member.getBlockTracker().liveActiveSigns;
            if (!memberList.isEmpty()) {
                signsList.add(memberList); // optimization
            }
        }
        if (signsList.isEmpty()) {
            return Collections.emptyList();
        } else {
            return new List2D<TrackedSign>(signsList);
        }
    }

    /**
     * Refreshes the block space and active signs if required
     */
    public void refresh() {
        // No need to update anything for empty trains
        if (owner.isEmpty()) {
            clear();
            return;
        }

        // Do all active rails, signs and detector regions have to be refreshed?
        if (needsPositionUpdate.clear()) {

            // First clear the live active sign buffer of all members
            for (MinecartMember<?> member : owner) {
                member.getBlockTracker().liveActiveSigns.clear();
            }

            // Add all active signs to the block tracker of all members
            for (TrackedRail info : owner.getRailTracker().getRailInformation()) {
                if (info.type == RailType.NONE) {
                    continue;
                }

                List<TrackedSign> signs = info.member.getBlockTracker().liveActiveSigns;
                Util.addSignsFromRails(signListBuffer, info.block, info.type.getSignColumnDirection(info.block));
                for (Block signBlock : signListBuffer) {
                    signs.add(new TrackedSign(signBlock, info.block));
                }
                signListBuffer.clear();
            }

            // Filter based on cart skip options
            for (MinecartMember<?> member : owner) {
                member.getProperties().getSkipOptions().filterSigns(member.getBlockTracker().liveActiveSigns);
            }

            // Combine all signs into one list and filter based on train options
            List<TrackedSign> groupSignList = getSignList();
            owner.getProperties().getSkipOptions().filterSigns(groupSignList);

            // Update cart signs
            for (MinecartMember<?> member : owner) {
                BlockTrackerMember tracker = member.getBlockTracker();
                tracker.updateActiveSigns(tracker.liveActiveSigns);
            }

            // Update the active signs for this Group
            updateActiveSigns(groupSignList);

            // Update detector regions
            detectorRegions.clear();
            for (MinecartMember<?> member : owner) {
                BlockTrackerMember tracker = member.getBlockTracker();
                tracker.detectorRegions.clear();
                tracker.detectorRegions.addAll(DetectorRegion.handleMove(member, member.getLastBlock(), member.getBlock()));
                detectorRegions.addAll(tracker.detectorRegions);
            }
        }

        // Perform routine update events
        if (needsUpdate.clear()) {
            for (Block signBlock : getActiveSigns()) {
                SignAction.executeAll(new SignActionEvent(signBlock, owner), SignActionType.GROUP_UPDATE);
            }
            for (DetectorRegion region : getActiveDetectorRegions()) {
                region.update(owner);
            }
            // Member updates
            for (MinecartMember<?> member : owner) {
                BlockTrackerMember tracker = member.getBlockTracker();
                if (tracker.needsUpdate.clear()) {
                    for (Block signBlock : tracker.getActiveSigns()) {
                        SignAction.executeAll(new SignActionEvent(signBlock, tracker.getOwner()), SignActionType.MEMBER_UPDATE);
                    }
                    for (DetectorRegion region : tracker.getActiveDetectorRegions()) {
                        region.update(tracker.getOwner());
                    }
                }
            }
        }
    }
}
