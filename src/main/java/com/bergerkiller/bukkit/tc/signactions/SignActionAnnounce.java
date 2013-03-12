package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionAnnounce extends SignAction {

	@Override
	public boolean match(SignActionEvent info) {
		return info.isType("announce");
	}

	@Override
	public void execute(SignActionEvent info) {
		if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
			if (!info.hasRailedMember() || !info.isPowered()) return;
			sendMessage(info, info.getGroup());
		} else if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
			if (!info.hasRailedMember() || !info.isPowered()) return;
			sendMessage(info, info.getMember());
		} else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
			for (MinecartGroup group : info.getRCTrainGroups()) {
				sendMessage(info, group);
			}
		}		
	}

	@Override
	public boolean canSupportRC() {
		return true;
	}

	@Override
	public boolean build(SignChangeActionEvent event) {
		if (event.getMode() != SignActionMode.NONE) {
			if (event.isType("announce")) {
				if (event.isRCSign()) {
					return handleBuild(event, Permission.BUILD_ANNOUNCER, "announcer", "remotely send a message to all the players in the train");
				} else {
					return handleBuild(event, Permission.BUILD_ANNOUNCER, "announcer", "send a message to players in a train");
				}
			}
		}
		return false;
	}

	public static void sendMessage(SignActionEvent info, MinecartGroup group) {
		String msg = getMessage(info);
		for (MinecartMember member : group) {
			if (member.hasPlayerPassenger()) {
				TrainCarts.sendMessage((Player) member.getPassenger(), msg);
			}
		}
	}

	public static void sendMessage(SignActionEvent info, MinecartMember member) {
		if (member.hasPlayerPassenger()) {
			TrainCarts.sendMessage((Player) member.getPassenger(), getMessage(info));
		}
	}

	private static void appendWithSpace(StringBuilder builder, String part) {
		if (!part.startsWith(" ") && builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
			builder.append(' ');
		}
		builder.append(part);
	}

	public static String getMessage(SignActionEvent info) {
		StringBuilder message = new StringBuilder(32);
		appendWithSpace(message, info.getLine(2));
		appendWithSpace(message, info.getLine(3));
		for (Sign sign : info.findSignsBelow()) {
			for (String line : sign.getLines()) {
				appendWithSpace(message, line);
			}
		}
		return TrainCarts.getMessage(message.toString());
	}
}
