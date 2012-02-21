package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.BlockFace;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.common.utils.FaceUtil;

public class SignActionBlock extends SignAction {

	public static boolean isHeadingTo(SignActionEvent event) {
		return isHeadingTo(event, event.getMember().getDirection());
	}
	public static boolean isHeadingTo(SignActionEvent event, BlockFace direction) {
		if (event.isLine(2, "n")) {
			return direction == BlockFace.NORTH;
		} else if (event.isLine(2, "e")) {
			return direction == BlockFace.EAST;
		} else if (event.isLine(2, "s")) {
			return direction == BlockFace.SOUTH;
		} else if (event.isLine(2, "w")) {
			return direction == BlockFace.WEST;			
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
		if (info.getMode() != SignActionMode.NONE && info.hasRails() && info.hasGroup() && info.isPowered()) {
			if (isHeadingTo(info)) {
				if (info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_CHANGE)) {
					info.getGroup().stop(false);
				} else if (info.isAction(SignActionType.MEMBER_MOVE)) {
					info.getGroup().stop(true);
				}
			}		
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode != SignActionMode.NONE) {
			if (type.startsWith("blocker")) {
				handleBuild(event, Permission.BUILD_BLOCKER, "train blocker", "block trains coming from a certain direction");
			}
		}
	}

}
