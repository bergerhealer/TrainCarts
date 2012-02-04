package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.permissions.Permission;

public class SignActionCart extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
			if (!info.isCartSign()) return;
			if (info.getMember() == null) return;
			if (info.isPoweredFacing()) {
				if (info.isType("destroy")) {
					info.getMember().die();
				} else if (info.isType("eject")) {
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
						info.getMember().eject();
					} else {
						info.getMember().eject(offset);
					}
				}
			}
		}
	}

	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.CART) {
			if (type.startsWith("destroy")) {
				handleBuild(event, Permission.BUILD_DESTRUCTOR, "cart destructor", "destroy minecarts");
			} else if (type.startsWith("eject")) {
				handleBuild(event, Permission.BUILD_EJECTOR, "cart ejector", "eject the passenger of a minecart");
			}
		}
	}

}
