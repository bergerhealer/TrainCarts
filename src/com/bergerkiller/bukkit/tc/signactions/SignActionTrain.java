package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public class SignActionTrain extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
			if (!info.isTrainSign()) return;
			if (info.isPoweredFacing()) {
				if (info.isType("destroy")) {
					if (!info.hasRailedMember()) return;
					info.getGroup().playLinkEffect();
					info.getGroup().destroy();
				} else if (info.isType("eject")) {
					if (!info.hasRailedMember()) return;
					String[] offsettext = info.getLine(2).split("/");
					Vector offset = new Vector();
					if (offsettext.length == 3) {
						offset.setX(StringUtil.tryParse(offsettext[0], 0));
						offset.setY(StringUtil.tryParse(offsettext[1], 0));
						offset.setZ(StringUtil.tryParse(offsettext[2], 0));
					} else if (offsettext.length == 1) {
						offset.setY(StringUtil.tryParse(offsettext[0], 0));
					}
					if (offset.equals(new Vector())) {
						info.getGroup().eject();
					} else {
						info.getGroup().eject(offset);
					}
				}
			}
		}
	}
	
	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("destroy")) {
				handleBuild(event, Permission.BUILD_DESTRUCTOR, "train destructor", "destroy an entire train");
			} else if (type.startsWith("eject")) {
				handleBuild(event, Permission.BUILD_EJECTOR, "train ejector", "eject all passengers of a train");
			}
		}
	}

}
