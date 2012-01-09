package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;

public class SignActionTrain extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (!info.hasRails()) return;
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
			if (!info.isTrainSign()) return;
			MinecartGroup group = info.getGroup();
			if (group == null) return;
			if (info.isPoweredFacing()) {
				if (info.isType("destroy")) {
					group.playLinkEffect();
					group.destroy();
				} else if (info.isType("eject")) {
					String[] offsettext = info.getLine(2).split("/");
					Vector offset = new Vector();
					if (offsettext.length == 3) {
						offset.setX(Util.tryParse(offsettext[0], 0));
						offset.setY(Util.tryParse(offsettext[1], 0));
						offset.setZ(Util.tryParse(offsettext[2], 0));
					} else if (offsettext.length == 1) {
						offset.setY(Util.tryParse(offsettext[0], 0));
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
