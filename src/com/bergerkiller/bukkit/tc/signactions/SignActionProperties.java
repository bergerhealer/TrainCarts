package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public class SignActionProperties extends SignAction {

	public void handleProperties(TrainProperties prop, String mode, String arg) {
		if (mode.equals("collision") || mode.equals("collide")) {
			prop.trainCollision = Util.getBool(arg);
		} else if (mode.equals("linking") || mode.equals("link")) {
			prop.allowLinking = Util.getBool(arg);
		} else if (mode.equals("slow") || mode.equals("slowdown")) {
			prop.slowDown = Util.getBool(arg);
		} else if (mode.equals("setdefault") || mode.equals("default")) {
			prop.setDefault(arg);
		} else if (mode.equals("pushmobs")) {
			prop.pushMobs = Util.getBool(arg);
		} else if (mode.equals("pushplayers")) {
			prop.pushPlayers = Util.getBool(arg);
		} else if (mode.equals("pushmisc")) {
			prop.pushMisc = Util.getBool(arg);
		} else if (mode.equals("push") || mode.equals("pushing")) {
			prop.pushMobs = Util.getBool(arg);
			prop.pushPlayers = prop.pushMobs;
			prop.pushMisc = prop.pushMobs;
		} else if (mode.equals("speedlimit") || mode.equals("maxspeed")) {
			try {
				prop.speedLimit = Double.parseDouble(arg);
			} catch (NumberFormatException ex) {
				prop.speedLimit = 0.4;
			}
		}
	}
	public void handleProperties(CartProperties prop, String mode, String arg) {
		if (mode.equals("addtag")) {
			prop.addTags(arg);
		} else if (mode.equals("settag")) {
			prop.setTags(arg);
		} else if (mode.equals("destination")) {
			prop.destination = arg;
		} else if (mode.equals("remtag")) {
			prop.removeTags(arg);
		} else if (mode.equals("mobenter") || mode.equals("mobsenter")) {
			prop.allowMobsEnter = Util.getBool(arg);
		} else if (mode.equals("playerenter")) {
			prop.allowPlayerEnter = Util.getBool(arg);
		} else if (mode.equals("playerexit")) {
			prop.allowPlayerExit = Util.getBool(arg);
		}
	}
	
	@Override
	public void execute(SignActionEvent info) {
		// TODO Auto-generated method stub
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.isCartSign()) {
			if (info.isType("property")) {
				if (info.isAction(SignActionType.REDSTONE_ON) || (info.isPowered() && info.isFacing())) {
					if (info.getMember() != null) {
						String mode = info.getLine(2).toLowerCase().trim();
						handleProperties(info.getMember().getProperties(), mode, info.getLine(3));
					}
				}
			}
		} else if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.isTrainSign()) {
			if (info.isType("property")) {
				if (info.isAction(SignActionType.REDSTONE_ON) || (info.isPowered() && info.isFacing())) {
					if (info.getGroup() != null) {
						String mode = info.getLine(2).toLowerCase().trim();
						handleProperties(info.getGroup().getProperties(), mode, info.getLine(3));
					}
				}
			}
		}
	}

}
