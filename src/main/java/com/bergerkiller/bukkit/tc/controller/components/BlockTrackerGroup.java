package com.bergerkiller.bukkit.tc.controller.components;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

/**
 * Keeps track of the active rails, signs and detector regions below a
 * MinecartGroup
 */
public class BlockTrackerGroup extends BlockTracker {
	private static final Set<Block> groupSignBuffer = new LinkedHashSet<Block>();
	private final MinecartGroup owner;
	private final Map<IntVector3, MinecartMember<?>> blockSpace = new LinkedHashMap<IntVector3, MinecartMember<?>>();
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
	protected void onSignChange(Block signblock, boolean active) {
		SignActionEvent event = new SignActionEvent(signblock, owner);
		event.setAction(active ? SignActionType.GROUP_ENTER : SignActionType.GROUP_LEAVE);
		SignAction.executeAll(event);
	}

	/**
	 * Gets the Minecart Member part of this Group that is traveling on the
	 * rails block specified
	 * 
	 * @param railsBlock
	 *            to get the Minecart Member for
	 * @return the Minecart Member, or null if not found
	 */
	public MinecartMember<?> getMemberFromRails(Block railsBlock) {
		return getMemberFromRails(new IntVector3(railsBlock));
	}

	/**
	 * Gets the Minecart Member part of this Group that is traveling on the
	 * rails block specified
	 * 
	 * @param railsBlockPosition
	 *            to get the Minecart Member for
	 * @return the Minecart Member, or null if not found
	 */
	public MinecartMember<?> getMemberFromRails(IntVector3 railsBlockPosition) {
		return blockSpace.get(railsBlockPosition);
	}

	@Override
	public void clear() {
		for (MinecartMember<?> member : owner) {
			member.getBlockTracker().clear();
		}
		super.clear();
		detectorRegions.clear();
		blockSpace.clear();
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
	public boolean isOnRails(Block railsBlock) {
		return blockSpace.containsKey(new IntVector3(railsBlock));
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

			// Update member block space
			blockSpace.clear();
			if (owner.size() == 1) {
				MinecartMember<?> member = owner.head();
				blockSpace.put(member.getBlockPos(), member);
			} else {
				int k;
				// Go member by member, starting at the tail, ending at the head
				for (int i = 0; i < owner.size() - 1; i++) {
					MinecartMember<?> member = owner.get(i);
					MinecartMember<?> toMember = owner.get(i + 1);
					IntVector3 from = member.getBlockPos();
					IntVector3 to = toMember.getBlockPos();
					IntVector3 diff = to.subtract(from);

					// Map the member to blocks in between, except 'to'
					blockSpace.put(from, member);
					if (!member.isOnSlope()) {
						if (diff.x == 0 && diff.z == 0) {
							// Along y-axis
							for (k = 1; k < diff.y; k++) {
								blockSpace.put(from.add(0, k, 0), member);
							}
							for (k = -1; k > diff.y; k--) {
								blockSpace.put(from.add(0, k, 0), member);
							}
							continue;
						} else if (diff.y == 0 && diff.x == 0) {
							// Along z-axis
							for (k = 1; k < diff.z; k++) {
								blockSpace.put(from.add(0, 0, k), member);
							}
							for (k = -1; k > diff.z; k--) {
								blockSpace.put(from.add(0, 0, k), member);
							}
							continue;
						} else if (diff.y == 0 && diff.z == 0) {
							// Along x-axis
							for (k = 1; k < diff.x; k++) {
								blockSpace.put(from.add(k, 0, 0), member);
							}
							for (k = -1; k > diff.x; k--) {
								blockSpace.put(from.add(k, 0, 0), member);
							}
							continue;
						}
					}
					// Curve or other logic - use a Block Iterator for this
					TrackIterator iter = toMember.getRailTracker().getTrackIterator();
					if (iter.hasNext()) {
						// Skip the first block
						iter.next();

						// Go and find the other blocks
						final int maxLength = Math.abs(diff.x) + Math.abs(diff.y) + Math.abs(diff.z);
						for (k = 0; k < maxLength && iter.hasNext(); k++) {
							final Block block = iter.next();
							if (from.x == block.getX() && from.y == block.getY() && from.z == block.getZ()) {
								// Found the end block
								break;
							}
							// Put the member
							blockSpace.put(new IntVector3(block), member);
						}
					}
				}
				blockSpace.put(owner.tail().getBlockPos(), owner.tail());
			}

			// First clear the live active sign buffer of all members
			for (MinecartMember<?> member : owner) {
				member.getBlockTracker().liveActiveSigns.clear();
			}

			// Add all active signs to the block tracker of all members
			World world = owner.getWorld();
			for (Entry<IntVector3, MinecartMember<?>> entry : blockSpace.entrySet()) {
				IntVector3 pos = entry.getKey();
				for (RailType type : RailType.values()) {
					if (type.isRail(world, pos.x, pos.y, pos.z)) {
						Block block = pos.toBlock(world);
						List<Block> signs = entry.getValue().getBlockTracker().liveActiveSigns;
						Util.addSignsFromRails(signs, block, type.getSignColumnDirection(block));
					}
				}
			}

			// Perform update events of sign changes
			groupSignBuffer.clear();
			for (MinecartMember<?> member : owner) {
				BlockTrackerMember tracker = member.getBlockTracker();
				groupSignBuffer.addAll(tracker.liveActiveSigns);
				tracker.updateActiveSigns(tracker.liveActiveSigns);
			}
			// Update the active signs for this Group
			updateActiveSigns(groupSignBuffer);

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
