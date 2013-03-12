package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Station;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.BlockActionSetLevers;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionStation extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("station");
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE)) {
			return;
		}
		if ((!info.isTrainSign() && !info.isCartSign()) || !info.hasRails() || !info.hasGroup()) {
			return;
		}
		if (info.isAction(SignActionType.GROUP_LEAVE)) {
			info.setLevers(false);
			return;
		}
		//Check if not already targeting
		MinecartGroup group = info.getGroup();
		Station station = new Station(info);
		if (!station.isValid()) {
			return;
		}

		//What do we do?
		if (station.getInstruction() == null) {
			info.getGroup().clearActions();
		} else if (station.getInstruction() == BlockFace.SELF) {
			MinecartMember centerMember = station.getCenterCart();
			// Do not allow redstone changes to center a launching train
			if (info.isAction(SignActionType.REDSTONE_CHANGE) && (info.getGroup().isMovementControlled() || info.getGroup().isMoving())) {
				return;
			}

			//Brake
			//TODO: ADD CHECK?!
			group.clearActions();		
			BlockFace trainDirection = station.getNextDirection().getDirection(info.getFacing(), centerMember.getDirectionTo());
			if (station.getNextDirection() == Direction.NONE || trainDirection != group.head().getDirectionTo()) {
				centerMember.addActionLaunch(info.getRailLocation(), 0);
			}
			if (station.getNextDirection() != Direction.NONE) {
				//Actual launching here
				if (station.hasDelay()) {
					centerMember.addActionLaunch(info.getRailLocation(), 0);
					if (TrainCarts.playSoundAtStation) group.addActionSizzle();
					info.getGroup().addAction(new BlockActionSetLevers(info.getAttachedBlock(), true));
					group.addActionWait(station.getDelay());
				} else if (group.head().getDirectionTo() != trainDirection) {
					centerMember.addActionLaunch(info.getRailLocation(), 0);
				}
				if (TrainCarts.refillAtStations) group.addActionRefill();
				centerMember.addActionLaunch(trainDirection, station.getLength(), TrainCarts.launchForce);
			} else {
				centerMember.addActionLaunch(info.getRailLocation(), 0);
				group.addAction(new BlockActionSetLevers(info.getAttachedBlock(), true));
				if (TrainCarts.playSoundAtStation) group.addActionSizzle();
				group.addActionWaitForever();
			}
		} else {
			//Launch
			group.clearActions();
			MinecartMember head = group.head();

			if (station.hasDelay() || (head.isMoving() && head.getDirection() != station.getInstruction())) {
				//Reversing or has delay, need to center it in the middle first
				station.getCenterCart().addActionLaunch(info.getRailLocation(), 0);
			}
			if (station.hasDelay()) {
				if (TrainCarts.playSoundAtStation) group.addActionSizzle();
				group.addAction(new BlockActionSetLevers(info.getAttachedBlock(), true));
				group.addActionWait(station.getDelay());
			}
			if (TrainCarts.refillAtStations) group.addActionRefill();
			station.getCenterCart().addActionLaunch(station.getInstruction(), station.getLength(), TrainCarts.launchForce);
		}
	}

	@Override
	public boolean overrideFacing() {
		return true;
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			return handleBuild(event, Permission.BUILD_STATION, "station", "stop, wait and launch trains");
		}
		return false;
	}
}
