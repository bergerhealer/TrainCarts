package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.MemberActionWaitOccupied;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class SignActionWait extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isType("wait")) {
			if (info.isAction(SignActionType.GROUP_ENTER) && info.isPowered()) {
				if (!info.hasRailedMember()) return;
				int dist = Math.min(Util.parse(info.getLine(1), 100), TrainCarts.maxDetectorLength);				
				
				//allowed?
				BlockFace dir = info.getMember().getDirectionTo();
				
				//distance
				if (MemberActionWaitOccupied.handleOccupied(info.getRails(), dir, info.getMember(), dist)) {
					info.getGroup().clearActions();
					//info.getGroup().stop(true);
					info.getMember().addActionWaitOccupied(dist);
				}
			} else if (info.isAction(SignActionType.REDSTONE_OFF)) {
				if (!info.hasRailedMember()) return;
				info.getGroup().clearActions();
			}
		}
	}

	@Override
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("wait")) {
				return handleBuild(event, Permission.BUILD_WAIT, "train wait sign", "waits the train until the tracks ahead are clear");
			}
		}
		return false;
	}

}
