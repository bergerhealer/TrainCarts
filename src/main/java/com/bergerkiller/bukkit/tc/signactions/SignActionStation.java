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
		return info.isType("station") && info.getMode() != SignActionMode.NONE;
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER, SignActionType.GROUP_LEAVE)) {
			return;
		}
		if (info.isAction(SignActionType.GROUP_LEAVE)) {
			if (info.getGroup().getActions().isWaitAction()) {
				info.getGroup().getActions().clear();
			}
			info.setLevers(false);
			return;
		}
		if (!info.hasRails() || !info.hasGroup() || info.getGroup().isEmpty()) {
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
			info.getGroup().getActions().clear();
		} else if (station.getInstruction() == BlockFace.SELF) {
			MinecartMember<?> centerMember = station.getCenterCart();
			// Do not allow redstone changes to center a launching train
			if (info.isAction(SignActionType.REDSTONE_CHANGE) && (centerMember.isMovementControlled() || info.getGroup().isMoving())) {
				return;
			}

			//Brake
			//TODO: ADD CHECK?!
			group.getActions().clear();		
			BlockFace trainDirection = station.getNextDirection().getDirection(info.getFacing(), centerMember.getDirectionTo());
			if (station.getNextDirection() == Direction.NONE || trainDirection != group.head().getDirectionTo()) {
				centerMember.getActions().addActionLaunch(info.getCenterLocation(), 0);
			}
			if (station.getNextDirection() != Direction.NONE) {
				//Actual launching here
				if (station.hasDelay()) {
					centerMember.getActions().addActionLaunch(info.getCenterLocation(), 0);
					if (TrainCarts.playSoundAtStation) {
						group.getActions().addActionSizzle();
					}
					info.getGroup().getActions().addAction(new BlockActionSetLevers(info.getAttachedBlock(), true));
					group.getActions().addActionWait(station.getDelay());
				} else if (group.head().getDirectionTo() != trainDirection) {
					centerMember.getActions().addActionLaunch(info.getCenterLocation(), 0);
				}
				if (TrainCarts.refillAtStations) {
					group.getActions().addActionRefill();
				}
				centerMember.getActions().addActionLaunch(trainDirection, station.getLength(), TrainCarts.launchForce);
			} else {
				centerMember.getActions().addActionLaunch(info.getCenterLocation(), 0);
				group.getActions().addAction(new BlockActionSetLevers(info.getAttachedBlock(), true));
				if (TrainCarts.playSoundAtStation) {
					group.getActions().addActionSizzle();
				}
				group.getActions().addActionWaitForever();
			}
		} else {
			//Launch
			group.getActions().clear();
			MinecartMember<?> head = group.head();

			if (station.hasDelay() || (head.isMoving() && head.getDirection() != station.getInstruction())) {
				//Reversing or has delay, need to center it in the middle first
				station.getCenterCart().getActions().addActionLaunch(info.getCenterLocation(), 0);
			}
			if (station.hasDelay()) {
				if (TrainCarts.playSoundAtStation) {
					group.getActions().addActionSizzle();
				}
				group.getActions().addAction(new BlockActionSetLevers(info.getAttachedBlock(), true));
				group.getActions().addActionWait(station.getDelay());
			}
			if (TrainCarts.refillAtStations) {
				group.getActions().addActionRefill();
			}
			station.getCenterCart().getActions().addActionLaunch(station.getInstruction(), station.getLength(), TrainCarts.launchForce);
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			return handleBuild(event, Permission.BUILD_STATION, "station", "stop, wait and launch trains");
		}
		return false;
	}

	@Override
	public boolean overrideFacing() {
		return true;
	}
}
