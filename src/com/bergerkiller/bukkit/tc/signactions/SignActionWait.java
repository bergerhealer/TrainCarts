package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.MemberActionWaitOccupied;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionWait extends SignAction {
	
	@Override
	public void execute(SignActionEvent info) {
		if (info.isType("wait")) {
			if (info.isAction(SignActionType.GROUP_ENTER) && info.isPowered()) {
				if (!info.hasRailedMember()) return;
				int dist = Math.min(ParseUtil.parseInt(info.getLine(1), 100), TrainCarts.maxDetectorLength);
				long delay = ParseUtil.parseTime(info.getLine(2));
				double launchDistance = ParseUtil.parseDouble(info.getLine(3), 2.0);

				//allowed?
				BlockFace dir = info.getMember().getDirectionTo();

				//distance
				if (MemberActionWaitOccupied.handleOccupied(info.getRails(), dir, info.getMember(), dist)) {
					info.getGroup().clearActions();
					info.getMember().addActionWaitOccupied(dist, delay, launchDistance);
				}
			} else if (info.isAction(SignActionType.REDSTONE_OFF)) {
				if (!info.hasRailedMember()) return;
				info.getGroup().clearActions();
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			if (event.isType("wait")) {
				return handleBuild(event, Permission.BUILD_WAIT, "train waiter sign", "waits the train until the tracks ahead are clear");
			}
		}
		return false;
	}
}
