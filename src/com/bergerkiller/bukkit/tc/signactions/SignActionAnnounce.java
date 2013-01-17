package com.bergerkiller.bukkit.tc.signactions;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionAnnounce extends SignAction {

	public static void sendMessage(SignActionEvent info, MinecartGroup group) {
		String msg = getMessage(info);
		for (MinecartMember member : group) {
			if (!member.hasPlayerPassenger()) return;
			Player player = (Player) member.getPassenger();
			sendMessage(msg, player);
		}
	}
	public static void sendMessage(SignActionEvent info, MinecartMember member) {
		if (!member.hasPlayerPassenger()) return;
		Player player = (Player) member.getPassenger();
		sendMessage(getMessage(info), player);
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
		return getMessage(message.toString());
	}
	public static String getMessage(String msg) {
		return StringUtil.ampToColor(TrainCarts.messageShortcuts.replace(msg));
	}
	public static void sendMessage(String msg, Player player) {
		if (TrainCarts.SignLinkEnabled) {
			int startindex, endindex;
			while ((startindex = msg.indexOf('%')) != -1 && (endindex = msg.indexOf('%', startindex + 1)) != -1) {
				String varname = msg.substring(startindex + 1, endindex);
				String value = varname.isEmpty() ? "%" : Variables.get(varname).get(player.getName());
				msg = msg.substring(0, startindex) + value + msg.substring(endindex + 1);
			}
		}
		player.sendMessage(msg);
	}

	@Override
	public void execute(SignActionEvent info) {
		if (!info.isType("announce")) return;
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
}
