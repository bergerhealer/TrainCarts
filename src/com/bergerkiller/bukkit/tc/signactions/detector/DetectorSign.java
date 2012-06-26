package com.bergerkiller.bukkit.tc.signactions.detector;

import net.minecraft.server.ChunkCoordinates;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.statements.Statement;

public class DetectorSign {

	public DetectorSign(DetectorSignPair detector, Block signBlock) {
		this(detector,  BlockUtil.getCoordinates(signBlock));
	}

	public DetectorSign(DetectorSignPair detector, ChunkCoordinates signLocation) {
		this.detector = detector;
		this.signLocation = signLocation;
	}

	public final DetectorSignPair detector;
	public boolean wasDown = false;
	public ChunkCoordinates signLocation;
	public Block getSign(World world) {
		if (world.isChunkLoaded(this.signLocation.x >> 4, this.signLocation.z >> 4)) {
			Block b = BlockUtil.getBlock(world, this.signLocation);
			if (BlockUtil.isSign(b)) return b;
		}
		return null;
	}

	public SignActionEvent getEvent(boolean wasDown, World world) {
		if (wasDown != this.wasDown) {
			return null;
		}
		Block signblock = this.getSign(world);
		if (signblock == null) {
			return null;
		}
		SignActionEvent event = new SignActionEvent(signblock);
		if (!validate(event.getSign())) {
			return null;
		}
		return event;
	}

	public void onLeave(MinecartGroup group) {
		SignActionEvent event = getEvent(true, group.getWorld());
		if (event != null && event.isTrainSign() && isDown(event, group)) {
			this.wasDown = updateGroups(event);
		}
	}

	public void onEnter(MinecartGroup group) {
		SignActionEvent event = getEvent(false, group.getWorld());
		if (event != null && event.isTrainSign() && isDown(event, group)) {
			this.wasDown = true;
			event.setLevers(true);
		}
	}

	public void onLeave(MinecartMember member) {
		SignActionEvent event = getEvent(true, member.getWorld());
		if (event != null && event.isCartSign() && isDown(event, member)) {
			this.wasDown = updateMembers(event);
		}
	}

	public void onEnter(MinecartMember member) {
		SignActionEvent event = getEvent(false, member.getWorld());
		if (event != null && event.isCartSign() && isDown(event, member)) {
			this.wasDown = true;
			event.setLevers(true);
		}
	}

	public boolean updateMembers(SignActionEvent event) {
		for (MinecartMember mm : this.detector.region.getMembers()) {
			if (isDown(event, mm)) {
				event.setLevers(true);
				return true;
			}
		}
		event.setLevers(false);
		return false;
	}

	public boolean updateGroups(SignActionEvent event) {
		for (MinecartGroup g : this.detector.region.getGroups()) {
			if (isDown(event, g)) {
				event.setLevers(true);
				return true;
			}
		}
		event.setLevers(false);
		return false;
	}

	public void onUpdate(MinecartMember member) {
		Sign sign = BlockUtil.getSign(this.getSign(member.getWorld()));
		if (sign != null) this.updateMembers(new SignActionEvent(sign.getBlock()));
	}

	public void onUpdate(MinecartGroup group) {
		Sign sign = BlockUtil.getSign(this.getSign(group.getWorld()));
		if (sign != null) this.updateGroups(new SignActionEvent(sign.getBlock()));
	}

	public boolean validate(Sign sign) {
		if (SignActionDetector.isValid(sign)) {
			return true;
		} else {
			if (this.detector.region != null) {
				DetectorRegion region = this.detector.region;
				region.unregister(this.detector);
				if (!region.isRegistered()) region.remove();
			}
			return false;
		}
	}
	
	public boolean isDown(SignActionEvent event, MinecartMember member) {
		boolean state = false;
		boolean firstEmpty = false;
		if (event.getLine(2).isEmpty()) {
			firstEmpty = true;
		} else {
			state |= Statement.has(member, event.getLine(2), event);
		}
		if (event.getLine(3).isEmpty()) {
			state = firstEmpty;
		} else {
			state |= Statement.has(member, event.getLine(3), event);
		}
		return state;
	}

	public boolean isDown(SignActionEvent event, MinecartGroup group) {
		boolean state = false;
		boolean firstEmpty = false;
		if (event.getLine(2).isEmpty()) {
			firstEmpty = true;
		} else {
			state |= Statement.has(group, event.getLine(2), event);
		}
		if (event.getLine(3).isEmpty()) {
			state = firstEmpty;
		} else {
			state |= Statement.has(group, event.getLine(3), event);
		}
		return state;
	}
}
