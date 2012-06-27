package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionBlock extends SignAction {
	
	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("blocker")) return;
		if (info.getMode() != SignActionMode.NONE) {
			if (info.isAction(SignActionType.GROUP_LEAVE)) {
				Action action = info.getGroup().getCurrentAction();
				if (action != null && action instanceof GroupActionWaitState) {
					info.getGroup().clearActions();
				}
			} else if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_CHANGE, SignActionType.MEMBER_MOVE)) {
				if (!info.hasRailedMember()) return;
				if (info.isPowered()) {
					BlockFace trainDirection = null;
					Direction direction = Direction.parse(info.getLine(3));
					trainDirection = direction.getDirection(info.getFacing(), info.getCartDirection());
					
					info.getGroup().clearActions();
					info.getGroup().addActionWaitState();
					info.getMember().addActionLaunch(trainDirection, 2.0, info.getGroup().getAverageForce());
					info.getGroup().stop(info.isAction(SignActionType.MEMBER_MOVE));
				} else if (info.isAction(SignActionType.REDSTONE_CHANGE)) {
					Action action = info.getGroup().getCurrentAction();
					if (action != null && action instanceof GroupActionWaitState) {
						((GroupActionWaitState) action).stop();
					}
				}
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			if (event.isType("blocker")) {
				return handleBuild(event, Permission.BUILD_BLOCKER, "train blocker", "block trains coming from a certain direction");
			}
		}
		return false;
	}
}
