package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionBlocker extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("blocker");
	}

	@Override
	public void execute(SignActionEvent info) {
		if (info.getMode() != SignActionMode.NONE && info.hasRailedMember()) {
			if (info.isAction(SignActionType.GROUP_LEAVE) || (info.isAction(SignActionType.REDSTONE_CHANGE) && !info.isPowered())) {
				// Remove the wait state when the train leaves or the sign lost power to block
				GroupActionWaitState action = CommonUtil.tryCast(info.getGroup().getActions().getCurrentAction(), GroupActionWaitState.class);
				if (action != null) {
					action.stop();
				}
			} else if (info.isPowered() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_CHANGE, SignActionType.MEMBER_MOVE)) {
				// Set the next direction based on the sign
				// Don't do this in the move event as that one fires too often (performance issue)
				if (!info.isAction(SignActionType.MEMBER_MOVE)) {
					Direction direction = Direction.parse(info.getLine(3));
					if (direction != Direction.NONE) {
						long delay = ParseUtil.parseTime(info.getLine(2));
						BlockFace trainDirection = direction.getDirection(info.getFacing(), info.getCartDirection());
						info.getGroup().getActions().clear();
						info.getGroup().getActions().addActionWaitState();
						if (delay > 0) {
							info.getGroup().getActions().addActionWait(delay);
						}
						info.getMember().getActions().addActionLaunch(trainDirection, 2.0, info.getGroup().getAverageForce());
					}
				}
				// Stop the train, if right after moving, also cancel a previous positional change
				info.getGroup().stop(info.isAction(SignActionType.MEMBER_MOVE));
			}
		}
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			return handleBuild(event, Permission.BUILD_BLOCKER, "train blocker", "block trains coming from a certain direction");
		}
		return false;
	}
}
