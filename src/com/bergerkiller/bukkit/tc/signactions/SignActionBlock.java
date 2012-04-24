package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.actions.GroupActionWaitState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.common.utils.EnumUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

public class SignActionBlock extends SignAction {

	public static boolean isHeadingTo(SignActionEvent event) {
		return isHeadingTo(event, event.getMember().getDirection());
	}
	public static boolean isHeadingTo(SignActionEvent event, BlockFace direction) {
		if (event.isLine(2, "n")) {
			return direction == BlockFace.SOUTH;
		} else if (event.isLine(2, "e")) {
			return direction == BlockFace.WEST;
		} else if (event.isLine(2, "s")) {
			return direction == BlockFace.NORTH;
		} else if (event.isLine(2, "w")) {
			return direction == BlockFace.EAST;			
		} else if (event.isLine(2, "l")) {
			return direction == FaceUtil.rotate(event.getFacing(), -2);
		} else if (event.isLine(2, "r")) {
			return direction == FaceUtil.rotate(event.getFacing(), 2);
		} else if (event.isLine(2, "b")) {
			return direction != event.getFacing().getOppositeFace();
		} else {
			return direction != event.getFacing();
		}
	}
	
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
					if (isHeadingTo(info)) {
						BlockFace trainDirection = null;
						Direction direction = Direction.parse(info.getLine(3));
						trainDirection = direction.convert(info.getFacing(), info.getCartDirection());
						
						if (info.isAction(SignActionType.GROUP_ENTER) && direction != Direction.NONE) {
							info.getGroup().clearActions();
							info.getGroup().addActionWaitState();
							info.getMember().addActionLaunch(trainDirection, 2.0, info.getGroup().getAverageForce());
						}
						info.getGroup().stop(info.isAction(SignActionType.MEMBER_MOVE));
					}
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
	public boolean build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("blocker")) {
				return handleBuild(event, Permission.BUILD_BLOCKER, "train blocker", "block trains coming from a certain direction");
			}
		}
		return false;
	}

}
