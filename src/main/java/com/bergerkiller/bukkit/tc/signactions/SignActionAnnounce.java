package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.block.Sign;

public class SignActionAnnounce extends SignAction {

    public static void sendMessage(SignActionEvent info, MinecartGroup group, String message) {
        for (MinecartMember<?> member : group) {
            sendMessage(info, member, message);
        }
    }

    public static void sendMessage(SignActionEvent info, MinecartMember<?> member, String message) {
        member.getEntity().getPlayerPassengers().forEach(player -> TrainCarts.sendMessage(player, message));
    }

    public static String getMessage(SignActionEvent info) {
        StringBuilder message = new StringBuilder(32);
        message.append(info.getLine(2));
        message.append(info.getLine(3));
        for (Sign sign : info.findSignsBelow()) {
            for (String line : sign.getLines()) {
                message.append(line);
            }
        }
        return TrainCarts.getMessage(message.toString());
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("announce");
    }

    @Override
    public void execute(SignActionEvent info) {
        String message = getMessage(info);
        if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
            if (!info.hasRailedMember() || !info.isPowered()) return;
            sendMessage(info, info.getGroup(), message);
        } else if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
            if (!info.hasRailedMember() || !info.isPowered()) return;
            sendMessage(info, info.getMember(), message);
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (MinecartGroup group : info.getRCTrainGroups()) {
                sendMessage(info, group, message);
            }
        }
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (!event.isType("announce")) {
            return false;
        }

        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_ANNOUNCER)
                .setName("announcer")
                .setDescription(event.isRCSign() ?
                        "remotely send a message to all the players in the train" :
                        "send a message to players in a train")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Announce")
                .handle(event.getPlayer());
    }
}
