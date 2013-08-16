package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

/**
 * Keeps track of the active rails, signs and detector regions below a MinecartMember.
 * This tracker is routinely updated by the BlockTracker of the MinecartGroup.
 */
public class BlockTrackerMember extends BlockTracker {
	private final MinecartMember<?> owner;
	protected List<Block> liveActiveSigns = new ArrayList<Block>();

	public BlockTrackerMember(MinecartMember<?> owner) {
		this.owner = owner;
	}

	/**
	 * Gets the owner of this Block Tracker
	 * 
	 * @return the Owner
	 */
	public MinecartMember<?> getOwner() {
		return owner;
	}

	@Override
	public void clear() {
		super.clear();
		if (!detectorRegions.isEmpty()) {
			for (DetectorRegion region : detectorRegions) {
				region.remove(owner);
			}
			detectorRegions.clear();
		}
	}

	@Override
	public boolean isOnRails(Block railsBlock) {
		return owner.getGroup().getBlockTracker().getMemberFromRails(railsBlock) == owner;
	}

	@Override
	protected void onSignChange(Block signblock, boolean active) {
		SignActionEvent event = new SignActionEvent(signblock, owner);
		event.setAction(active ? SignActionType.MEMBER_ENTER : SignActionType.MEMBER_LEAVE);
		SignAction.executeAll(event);
	}

	@Override
	public void update() {
		super.update();
		if (!owner.isUnloaded()) {
			MinecartGroup group = owner.getGroup();
			// Member owner could be dead and have no group
			if (group != null) {
				group.getBlockTracker().update();
			}
		}
	}
}
