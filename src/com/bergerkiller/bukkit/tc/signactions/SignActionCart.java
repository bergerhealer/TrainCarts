package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public class SignActionCart extends SignAction {

	@Override
	public void execute(SignActionEvent info) {
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
			if (!info.isCartSign()) return;
			if (info.getMember() == null) return;
			if (info.isAction(SignActionType.REDSTONE_ON) || (info.isFacing() && info.isPowered())) {
				if (info.isType("destroy")) {
					info.getMember().destroy();
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
						info.getMember().eject();
					} else {
						info.getMember().eject(offset);
					}
				}
			}
		}
	}

}
