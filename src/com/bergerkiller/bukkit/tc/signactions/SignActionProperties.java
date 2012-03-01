package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.CartProperties;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainProperties;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;

public class SignActionProperties extends SignAction {

	public void handleProperties(TrainProperties prop, String mode, String arg) {
		if (mode.equals("collision") || mode.equals("collide")) {
			prop.trainCollision = StringUtil.getBool(arg);
		} else if (mode.equals("linking") || mode.equals("link")) {
			prop.allowLinking = StringUtil.getBool(arg);
		} else if (mode.equals("slow") || mode.equals("slowdown")) {
			prop.slowDown = StringUtil.getBool(arg);
		} else if (mode.equals("setdefault") || mode.equals("default")) {
			prop.setDefault(arg);
		} else if (mode.equals("pushmobs")) {
			prop.pushMobs = StringUtil.getBool(arg);
		} else if (mode.equals("pushplayers")) {
			prop.pushPlayers = StringUtil.getBool(arg);
		} else if (mode.equals("pushmisc")) {
			prop.pushMisc = StringUtil.getBool(arg);
		} else if (mode.equals("push") || mode.equals("pushing")) {
			prop.pushMobs = StringUtil.getBool(arg);
			prop.pushPlayers = prop.pushMobs;
			prop.pushMisc = prop.pushMobs;
		} else if (mode.equals("speedlimit") || mode.equals("maxspeed")) {
			try {
				prop.speedLimit = Double.parseDouble(arg);
			} catch (NumberFormatException ex) {
				prop.speedLimit = 0.4;
			}
		} else if (mode.equals("addtag")) {
			prop.addTags(arg);
		} else if (mode.equals("settag")) {
			prop.setTags(arg);
		} else if (mode.equals("destination")) {
			prop.setDestination(arg);
		} else if (mode.equals("remtag")) {
			prop.removeTags(arg);
		} else if (mode.equals("mobenter") || mode.equals("mobsenter")) {
			prop.setAllowMobsEnter(StringUtil.getBool(arg));
		} else if (mode.equals("playerenter")) {
			prop.setAllowPlayerEnter(StringUtil.getBool(arg));
		} else if (mode.equals("playerexit")) {
			prop.setAllowPlayerExit(StringUtil.getBool(arg));
		} else if (mode.equals("setowner")) {
			arg = arg.toLowerCase();
			for (CartProperties cprop : prop.getCarts()) {
				cprop.getOwners().clear();
				cprop.getOwners().add(arg);
			}
		} else if (mode.equals("addowner")) {
			arg = arg.toLowerCase();
			for (CartProperties cprop : prop.getCarts()) {
				cprop.getOwners().add(arg);
			}
		} else if (mode.equals("remowner")) {
			arg = arg.toLowerCase();
			for (CartProperties cprop : prop.getCarts()) {
				cprop.getOwners().remove(arg);
			}
		} else {
			return;
		}
		prop.tryUpdate();
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
			prop.allowMobsEnter = StringUtil.getBool(arg);
		} else if (mode.equals("playerenter")) {
			prop.allowPlayerEnter = StringUtil.getBool(arg);
		} else if (mode.equals("playerexit")) {
			prop.allowPlayerExit = StringUtil.getBool(arg);
		} else if (mode.equals("setowner")) {
			arg = arg.toLowerCase();
			prop.getOwners().clear();
			prop.getOwners().add(arg);
		} else if (mode.equals("addowner")) {
			arg = arg.toLowerCase();
			prop.getOwners().add(arg);
		} else if (mode.equals("remowner")) {
			arg = arg.toLowerCase();
			prop.getOwners().remove(arg);
		} else {
			return;
		}
		prop.tryUpdate();
	}
	
	@Override
	public void execute(SignActionEvent info) {
		// TODO Auto-generated method stub
		if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.isCartSign()) {
			if (info.isType("property")) {
				if (info.isPoweredFacing()) {
					if (info.hasRailedMember()) {
						String mode = info.getLine(2).toLowerCase().trim();
						handleProperties(info.getMember().getProperties(), mode, info.getLine(3));
					}
				}
			}
		} else if (info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.isTrainSign()) {
			if (info.isType("property")) {
				if (info.isPoweredFacing()) {
					if (info.hasRailedMember()) {
						String mode = info.getLine(2).toLowerCase().trim();
						handleProperties(info.getGroup().getProperties(), mode, info.getLine(3));
					}
				}
			}
		}
	}
	@Override
	public void build(SignChangeEvent event, String type, SignActionMode mode) {
		if (mode == SignActionMode.CART) {
			if (type.startsWith("property")) {
				handleBuild(event, Permission.BUILD_PROPERTY, "cart property setter", "set properties on the cart above");
			}
		} else if (mode == SignActionMode.TRAIN) {
			if (type.startsWith("property")) {
				handleBuild(event, Permission.BUILD_PROPERTY, "train property setter", "set properties on the train above");
			}
		}
	}

}
