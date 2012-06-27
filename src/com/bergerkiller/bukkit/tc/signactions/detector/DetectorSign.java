package com.bergerkiller.bukkit.tc.signactions.detector;

import net.minecraft.server.ChunkCoordinates;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineSign;

public class DetectorSign extends OfflineSign {

	public DetectorSign(DetectorSignPair detector, Block signBlock) {
		this(detector,  BlockUtil.getCoordinates(signBlock));
	}

	public DetectorSign(DetectorSignPair detector, ChunkCoordinates signLocation) {
		super(signLocation);
		this.detector = detector;
	}

	public final DetectorSignPair detector;
	public boolean wasDown = false;

	@Override
	public boolean validate(SignActionEvent event) {
		return SignActionDetector.isValid(event);
	}

	@Override
	public void onRemove(Block signblock) {
		if (this.detector.region != null) {
			DetectorRegion region = this.detector.region;
			region.unregister(this.detector);
			if (!region.isRegistered()) region.remove();
		}
	}

	public void onLeave(MinecartGroup group) {
		if (this.wasDown) {
			SignActionEvent event = getSignEvent(group.getWorld());
			if (event != null && event.isTrainSign() && isDown(event, null, group)) {
				this.wasDown = updateGroups(event);
			}
		}
	}

	public void onEnter(MinecartGroup group) {
		if (!this.wasDown) {
			SignActionEvent event = getSignEvent(group.getWorld());
			if (event != null && event.isTrainSign() && isDown(event, null, group)) {
				this.wasDown = true;
				event.setLevers(true);
			}
		}
	}

	public void onLeave(MinecartMember member) {
		if (this.wasDown) {
			SignActionEvent event = getSignEvent(member.getWorld());
			if (event != null && event.isCartSign() && isDown(event, member, null)) {
				this.wasDown = updateMembers(event);
			}
		}
	}

	public void onEnter(MinecartMember member) {
		if (!this.wasDown) {
			SignActionEvent event = getSignEvent(member.getWorld());
			if (event != null && event.isCartSign() && isDown(event, member, null)) {
				this.wasDown = true;
				event.setLevers(true);
			}
		}
	}

	public boolean updateMembers(SignActionEvent event) {
		for (MinecartMember mm : this.detector.region.getMembers()) {
			if (isDown(event, mm, null)) {
				event.setLevers(true);
				return true;
			}
		}
		event.setLevers(false);
		return false;
	}

	public boolean updateGroups(SignActionEvent event) {
		for (MinecartGroup g : this.detector.region.getGroups()) {
			if (isDown(event, null, g)) {
				event.setLevers(true);
				return true;
			}
		}
		event.setLevers(false);
		return false;
	}

	public void onUpdate(MinecartMember member) {
		SignActionEvent event = this.getSignEvent(member.getWorld());
		if (event != null) this.updateMembers(event);
	}

	public void onUpdate(MinecartGroup group) {
		SignActionEvent event = this.getSignEvent(group.getWorld());
		if (event != null) this.updateMembers(event);
	}

	public boolean isDown(SignActionEvent event, MinecartMember member, MinecartGroup group) {
		boolean state = false;
		boolean firstEmpty = false;
		if (event.getLine(2).isEmpty()) {
			firstEmpty = true;
		} else if (member == null) {
			state |= Statement.has(group, event.getLine(2), event);
		} else {
			state |= Statement.has(member, event.getLine(2), event);
		}
		if (event.getLine(3).isEmpty()) {
			state = firstEmpty;
		} else if (member == null) {
			state |= Statement.has(group, event.getLine(3), event);
		} else {
			state |= Statement.has(member, event.getLine(3), event);
		}
		return state;
	}
}
