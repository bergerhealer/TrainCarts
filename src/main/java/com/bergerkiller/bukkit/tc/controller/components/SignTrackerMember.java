package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.modlist.ModificationTrackedArrayList;
import com.bergerkiller.bukkit.tc.utils.modlist.ModificationTrackedList;

import org.bukkit.block.Block;

import java.util.List;

/**
 * Keeps track of the active rails, signs and detector regions below a MinecartMember.
 * This tracker is routinely updated by the BlockTracker of the MinecartGroup.
 */
public class SignTrackerMember extends SignTracker {
    private final MinecartMember<?> owner;
    protected final ModificationTrackedList<ActiveSign> liveActiveSigns = new ModificationTrackedArrayList<>();

    public SignTrackerMember(MinecartMember<?> owner) {
        super(owner);
        this.owner = owner;
    }

    /**
     * Gets the owner of this Block Tracker
     *
     * @return the Owner
     */
    @Override
    public MinecartMember<?> getOwner() {
        return owner;
    }

    /**
     * Adds this member to a region. Returns false if already added.
     * 
     * @param region
     * @return True if member was added to the region, False if not
     */
    public boolean addToDetectorRegion(DetectorRegion region) {
        if (!region.add(this.owner)) {
            return false;
        }

        // Add to list of regions of this member
        this.detectorRegions.add(region);

        // Add to list of regions of the entire group
        List<DetectorRegion> groupRegions = this.owner.getGroup().getSignTracker().detectorRegions;
        if (!groupRegions.contains(region)) {
            groupRegions.add(region);
        }
        return true;
    }

    @Override
    public void addOfflineActiveSignKey(Object signUniqueKey) {
        super.addOfflineActiveSignKey(signUniqueKey);
        owner.getGroup().getSignTracker().addOfflineActiveSignKey(signUniqueKey);
    }

    @Override
    public void clear(ClearMode clearMode) {
        super.clear(clearMode);
        if (!detectorRegions.isEmpty()) {
            for (DetectorRegion region : detectorRegions.cloneAsIterable()) {
                region.remove(owner);
            }
            detectorRegions.clear();
        }
    }

    @Override
    @Deprecated
    public boolean isOnRails(Block railsBlock) {
        return owner.getRailTracker().isOnRails(railsBlock);
    }

    @Override
    protected void onSignChange(ActiveSign sign, boolean active) {
        sign.executeEventForMember(active ? SignActionType.MEMBER_ENTER : SignActionType.MEMBER_LEAVE, owner);
    }

    @Override
    protected void onLoadedChange(ActiveSign sign, boolean loaded) {
        // No events are fired for carts.
    }

    @Override
    public void update() {
        super.update();
        if (!owner.isUnloaded()) {
            MinecartGroup group = owner.getGroup();
            // Member owner could be dead and have no group
            if (group != null) {
                group.getSignTracker().update();
            }
        }
    }
}
