package com.bergerkiller.bukkit.tc.signactions.detector;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineSign;

public class DetectorSign extends OfflineSign {

	public DetectorSign(DetectorSignPair detector, Block signBlock) {
		this(detector, new IntVector3(signBlock));
	}

	public DetectorSign(DetectorSignPair detector, IntVector3 signLocation) {
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
	public boolean isLoaded(World world) {
		return world != null;
	}

	@Override
	public SignActionEvent getSignEvent(World world) {
		if (world == null) {
			return null;
		}
		this.loadChunks(world);
		return super.getSignEvent(world);
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

	public void onLeave(MinecartMember<?> member) {
		if (this.wasDown) {
			SignActionEvent event = getSignEvent(member.getEntity().getWorld());
			if (event != null && event.isCartSign() && isDown(event, member, null)) {
				this.wasDown = updateMembers(event);
			}
		}
	}

	public void onEnter(MinecartMember<?> member) {
		if (!this.wasDown) {
			SignActionEvent event = getSignEvent(member.getEntity().getWorld());
			if (event != null && event.isCartSign() && isDown(event, member, null)) {
				this.wasDown = true;
				event.setLevers(true);
			}
		}
	}

	public boolean updateMembers(SignActionEvent event) {
		for (MinecartMember<?> mm : this.detector.region.getMembers()) {
			if (isDown(event, mm, null)) {
				this.wasDown = true;
				event.setLevers(true);
				return true;
			}
		}
		this.wasDown = false;
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

	public void onUpdate(MinecartMember<?> member) {
		SignActionEvent event = this.getSignEvent(member.getEntity().getWorld());
		if (event != null) this.updateMembers(event);
	}

	public void onUpdate(MinecartGroup group) {
		SignActionEvent event = this.getSignEvent(group.getWorld());
		if (event != null) this.updateMembers(event);
	}

	public boolean isDown(SignActionEvent event, MinecartMember<?> member, MinecartGroup group) {
		boolean firstEmpty = false;
		if (event.getLine(2).isEmpty()) {
			firstEmpty = true;
		} else if (Statement.has(member, group, event.getLine(2), event)) {
			return true;
		}
		if (event.getLine(3).isEmpty()) {
			return firstEmpty; //two empty lines, no statements, simple 'has'
		} else {
			return Statement.has(member, group, event.getLine(3), event);
		}
	}
}
