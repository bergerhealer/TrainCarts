package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailTracker.TrackedRail;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.modlist.ModificationTrackedEmptyList;
import com.bergerkiller.bukkit.tc.utils.modlist.ModificationTrackedList;
import com.bergerkiller.bukkit.tc.utils.modlist.ModificationTrackedList2D;

import org.bukkit.block.Block;

import java.util.*;

/**
 * Keeps track of the active rails, signs and detector regions below a
 * MinecartGroup
 */
public class SignTrackerGroup extends SignTracker {
    private final MinecartGroup owner;
    private final ToggledState needsPositionUpdate = new ToggledState(true);
    private final ModificationTrackedList2D<ActiveSign> liveActiveSigns = new ModificationTrackedList2D<>();

    public SignTrackerGroup(MinecartGroup owner) {
        super(owner);
        this.owner = owner;
    }

    /**
     * Gets the owner of this Block Tracker
     *
     * @return the Owner
     */
    @Override
    public MinecartGroup getOwner() {
        return owner;
    }

    @Override
    protected void onSignChange(ActiveSign sign, boolean active) {
        sign.executeEventForGroup(active ? SignActionType.GROUP_ENTER : SignActionType.GROUP_LEAVE, owner);
    }

    /**
     * Gets the Minecart Member part of this Group that is traveling on the
     * rails block specified.
     *
     * @param railsBlock to get the Minecart Member for
     * @return the Minecart Member, or null if not found
     * @deprecated Use the {@link MinecartGroup#getRailTracker()} for this instead.
     */
    @Deprecated
    public MinecartMember<?> getMemberFromRails(Block railsBlock) {
        return owner.getRailTracker().getMemberFromRails(railsBlock);
    }

    /**
     * Gets the Minecart Member part of this Group that is traveling on the
     * rails block specified.
     *
     * @param railsBlockPosition to get the Minecart Member for
     * @return the Minecart Member, or null if not found
     * @deprecated Use the {@link MinecartGroup#getRailTracker()} for this instead.
     */
    @Deprecated
    public MinecartMember<?> getMemberFromRails(IntVector3 railsBlockPosition) {
        return owner.getRailTracker().getMemberFromRails(railsBlockPosition);
    }

    @Override
    public void clear() {
        for (MinecartMember<?> member : owner) {
            member.getSignTracker().clear();
        }
        super.clear();
        detectorRegions.clear();
    }

    /**
     * Tells detector regions (and signs?) that the tracker owner has unloaded
     */
    public void unload() {
        // Unload in detector regions
        if (!this.detectorRegions.isEmpty()) {
            for (DetectorRegion region : this.detectorRegions) {
                region.unload(owner);
            }
            this.detectorRegions.clear();
        }

        // Send leave events to all signs
        this.clear();

        // Clear skip tracking data
        this.signSkipTracker.unloadSigns();
        for (MinecartMember<?> member : owner) {
            member.getSignTracker().signSkipTracker.unloadSigns();
        }
    }

    @Override
    @Deprecated
    public boolean isOnRails(Block railsBlock) {
        return owner.getRailTracker().isOnRails(railsBlock);
    }

    /**
     * Called when a member is removed from the group. Should update internal state
     * here.
     *
     * @param member
     */
    public void onMemberRemoved(MinecartMember<?> member) {
        removeDetectorRegionsOf(member);
        updatePosition();
    }

    private void removeDetectorRegionsOf(MinecartMember<?> member) {
        // Nothing to do here if none of our members entered any detector regions
        if (this.detectorRegions.isEmpty()) {
            return;
        }

        // Remove from any detectors to properly fire events
        // A re-enter might occur if the member is reassigned to a different group
        for (DetectorRegion region : member.getSignTracker().detectorRegions.cloneAsIterable()) {
            region.remove(member);
        }
        member.getSignTracker().detectorRegions.clear();

        // Update detectorRegions list of this group based on remaining members
        for (Iterator<DetectorRegion> iter = this.detectorRegions.iterator(); iter.hasNext();) {
            DetectorRegion region = iter.next();
            boolean used = false;
            for (MinecartMember<?> otherMember : owner) {
                if (otherMember != member && otherMember.getSignTracker().detectorRegions.contains(region)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                iter.remove();
            }
        }
    }

    /**
     * Tells that this Block Tracker's Block Space (signs, detectors) needs to be updated at some point
     */
    public void updatePosition() {
        needsPositionUpdate.set();
    }

    @Override
    @Deprecated
    public boolean removeSign(Block signBlock) {
        if (super.removeSign(signBlock)) {
            for (MinecartMember<?> member : owner) {
                member.getSignTracker().removeSign(signBlock);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean removeSign(TrackedSign sign) {
        if (super.removeSign(sign)) {
            for (MinecartMember<?> member : owner) {
                member.getSignTracker().removeSign(sign);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Refreshes the block space and active signs if required
     */
    public void refresh() {
        /* Timings: refreshSigns  (Train Physics, Sign Tracker) */
        {
            // No need to update anything for empty trains
            if (owner.isEmpty()) {
                clear();
                return;
            }

            // Do all active rails, signs and detector regions have to be refreshed?
            if (needsPositionUpdate.clear()) {

                // First clear the live active sign buffer of all members
                for (MinecartMember<?> member : owner) {
                    member.getSignTracker().liveActiveSigns.clear();
                }

                // Add all active signs to the block tracker of all members
                // This stores, by member, the signs active and the state on the rails when activating
                for (TrackedRail info : owner.getRailTracker().getRailInformation()) {
                    if (info.state.railType() != RailType.NONE) {
                        TrackedSign[] signs = info.state.railSigns();
                        if (signs.length > 0) {
                            ModificationTrackedList<ActiveSign> memberSigns = info.member.getSignTracker().liveActiveSigns;
                            for (TrackedSign sign : signs) {
                                if (sign.getAction() != null || !sign.getHeader().isEmpty()) {
                                    memberSigns.add(new ActiveSign(sign, info.state));
                                }
                            }
                        }
                    }
                }

                // Filter based on cart skip options
                for (MinecartMember<?> member : owner) {
                    member.getSignTracker().signSkipTracker.filterSigns(member.getSignTracker().liveActiveSigns);
                }

                // Synchronize the list of active signs using the liveActiveSigns of the members
                this.liveActiveSigns.resetLists();
                for (MinecartMember<?> member : owner) {
                    this.liveActiveSigns.addListIfNotEmpty(member.getSignTracker().liveActiveSigns);
                }

                // Filter the list based on sign skip options before returning
                // This will remove elements from the lists in the member sign tracker!
                // That way, telling a train to skip signs will make it skip [cart] signs just the same
                this.signSkipTracker.filterSigns(this.liveActiveSigns);

                // Update cart signs
                // Activating a sign might cause a change to this train, make a defensive copy
                for (MinecartMember<?> member : owner.toArray()) {
                    if (!member.isUnloaded() && member.getGroup() == owner) {
                        final SignTrackerMember tracker = member.getSignTracker();
                        tracker.updateActiveSigns(() -> {
                            return tracker.getOwner().isUnloaded() ?
                                    ModificationTrackedEmptyList.emptyList() : tracker.liveActiveSigns;
                        });
                    }
                }

                // Update the active signs for this Group
                updateActiveSigns(() -> {
                    return owner.isUnloaded() ? ModificationTrackedEmptyList.emptyList() : liveActiveSigns;
                });

                // Update existing detector regions that are in use.
                // Here we add members to regions other members were on, and
                // remove members when they are no longer on a region. When all
                // members of the group left a region, remove the region from the
                // group region list entirely. When no detector regions are below
                // the train, this piece of code causes zero performance hit.
                List<TrackedRail> rails = this.getOwner().getRailTracker().getRailInformation();
                if (!this.detectorRegions.isEmpty()) {
                    // Secure copy
                    MinecartMember<?>[] members = this.getOwner().toArray();

                    // Clear detector regions set for members
                    for (MinecartMember<?> member : members) {
                        member.getSignTracker().detectorRegions.clear();
                    }

                    // Remove detector regions on the wrong world
                    String currentWorldName = this.getOwner().getWorld().getName();
                    for (int i = this.detectorRegions.size() - 1; i >= 0; i--) {
                        DetectorRegion region = this.detectorRegions.get(i);
                        if (!region.getWorldName().equals(currentWorldName)) {
                            for (MinecartMember<?> member : members) {
                                region.remove(member);
                            }
                            this.detectorRegions.remove(i);
                        }
                    }

                    // For all detector regions we already know, re-add those for members on them
                    for (TrackedRail rail : rails) {
                        for (DetectorRegion region : this.detectorRegions.cloneAsIterable()) {
                            if (region.getCoordinates().contains(rail.state.railPiece().blockPosition())) {
                                List<DetectorRegion> memberRegions = rail.member.getSignTracker().detectorRegions;
                                if (!memberRegions.contains(region)) {
                                    memberRegions.add(region);
                                    region.add(rail.member);
                                }
                            }
                        }
                    }

                    // Remove member from region when no longer on it
                    // When all members are removed from a region, remove from the master list of regions
                    Iterator<DetectorRegion> iter = this.detectorRegions.iterator();
                    while (iter.hasNext()) {
                        DetectorRegion region = iter.next();
                        boolean foundMember = false;
                        for (MinecartMember<?> member : members) {
                            if (member.getSignTracker().detectorRegions.contains(region)) {
                                foundMember = true;
                            } else {
                                region.remove(member);
                            }
                        }
                        if (!foundMember) {
                            iter.remove();
                        }
                    }
                }

                // Detect new detector regions on the rails, add to member detector regions, and own detector regions list
                for (TrackedRail rail : rails) {
                    for (DetectorRegion region : rail.state.railPiece().detectorRegions()) {
                        rail.member.getSignTracker().addToDetectorRegion(region);
                    }
                }
            }

            // Perform routine update events
            if (needsUpdate.clear()) {
                for (ActiveSign activeSign : this.getActiveTrackedSigns().cloneAsIterable()) {
                    activeSign.executeEventForGroup(SignActionType.GROUP_UPDATE, owner);
                }
                for (DetectorRegion region : detectorRegions.cloneAsIterable()) {
                    region.update(owner);
                }
                // Member updates
                for (MinecartMember<?> member : owner) {
                    SignTrackerMember tracker = member.getSignTracker();
                    if (tracker.needsUpdate.clear()) {
                        for (ActiveSign activeSign : tracker.getActiveTrackedSigns()) {
                            activeSign.executeEventForMember(SignActionType.MEMBER_UPDATE, tracker.getOwner());
                        }
                        for (DetectorRegion region : tracker.detectorRegions.cloneAsIterable()) {
                            region.update(tracker.getOwner());
                        }
                    }
                }
            }
        }
    }
}
