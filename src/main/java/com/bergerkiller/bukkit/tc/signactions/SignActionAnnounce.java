package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import org.bukkit.entity.Player;

public class SignActionAnnounce extends SignAction {

    private static boolean useTitle = false;

    private static void sendMessage(MinecartGroup group, AnnounceMessage message) {
        for (MinecartMember<?> member : group) {
            sendMessage(member, message);
        }
    }

    private static void sendMessage(MinecartMember<?> member, AnnounceMessage message) {
        for (Player player: member.getEntity().getPlayerPassengers()) {
            if (SignActionAnnounce.useTitle) {
                player.sendTitle(message.title, message.subtitle);
            } else {
                TrainCarts.sendMessage(player, message.title);
                TrainCarts.sendMessage(player, message.subtitle);
            }
        }
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("announce");
    }

    @Override
    public void execute(SignActionEvent info) {
        // check if Sign is REDSTONE_ON
        // setup AnnounceMessage and useTitle if so
        if (info.isAction(SignActionType.REDSTONE_ON)) {

            AnnounceMessage message = new AnnounceMessage(info);
            SignActionAnnounce.useTitle = isTitleAnnounce(info);

            if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER)) {
                if (!info.hasRailedMember() || !info.isPowered()) return;
                sendMessage(info.getGroup(), message);
            } else if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER)) {
                if (!info.hasRailedMember() || !info.isPowered()) return;
                sendMessage(info.getMember(), message);
            } else if (info.isRCSign()) {
                info.getRCTrainGroups().forEach(group -> sendMessage(group, message));
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
                        "announce a message to all the players in the train" :
                        "announce message to players in a train")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Announce")
                .handle(event.getPlayer());
    }

    private static boolean isTitleAnnounce(SignActionEvent info) {
        for (String part : info.getLine(1).split(" ")) {
            if (part.equalsIgnoreCase("title")) {
                return true;
            }
        }
        return false;
    }

    private static class AnnounceMessage {

        public AnnounceMessage(SignActionEvent info) {
            this.title = TrainCarts.getMessage(info.getLine(2));
            this.subtitle = TrainCarts.getMessage(info.getLine(3));
        }

        public String title;
        public String subtitle;
    }
}
