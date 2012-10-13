package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
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
			if (!info.hasRailedMember()) return;
			if (info.isAction(SignActionType.GROUP_LEAVE) || (info.isAction(SignActionType.REDSTONE_CHANGE) && !info.isPowered())) {
				// Remove the wait state when the train leaves or the sign lost power to block
				Action action = info.getGroup().getCurrentAction();
				if (action != null && action instanceof GroupActionWaitState) {
					((GroupActionWaitState) action).stop();
				}
			} else if (info.isPowered() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_CHANGE, SignActionType.MEMBER_MOVE)) {
				// Set the next direction based on the sign
				// Don't do this in the move event as that one fires too often (performance issue)
				if (!info.isAction(SignActionType.MEMBER_MOVE)) {
					Direction direction = Direction.parse(info.getLine(3));
					if (direction != Direction.NONE) {
						long delay = ParseUtil.parseTime(info.getLine(2));
						BlockFace trainDirection = direction.getDirection(info.getFacing(), info.getCartDirection());
						info.getGroup().clearActions();
						info.getGroup().addActionWaitState();
						if (delay > 0) {
							info.getGroup().addActionWait(delay);
						}
						info.getMember().addActionLaunch(trainDirection, 2.0, info.getGroup().getAverageForce());
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
			if (event.isType("blocker")) {
				return handleBuild(event, Permission.BUILD_BLOCKER, "train blocker", "block trains coming from a certain direction");
			}
		}
		return false;
	}
}
