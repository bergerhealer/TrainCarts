package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignActionTitle extends SignAction {

    private static void sendTitle(MinecartGroup group, TitleMessage message) {
        for (MinecartMember<?> member : group) {
            sendTitle(member, message);
        }
    }

    private static void sendTitle(MinecartMember<?> member, TitleMessage message) {
        for (Player player : member.getEntity().getPlayerPassengers()) {
            player.sendTitle(message.title, message.subtitle, message.fadeIn, message.stay, message.fadeOut);
        }
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("title");
    }

    @Override
    public void execute(SignActionEvent info) {

        TitleMessage message = new TitleMessage(info);

        if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
            if (info.hasRailedMember() && info.isPowered()) {
                sendTitle(info.getGroup(), message);
            }
        } else if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
            if (info.hasRailedMember() && info.isPowered()) {
                sendTitle(info.getMember(), message);
            }
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (MinecartGroup group : info.getRCTrainGroups()) {
                sendTitle(group, message);
            }
        }
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_TITLE)
                .setName("title")
                .setDescription(event.isRCSign() ?
                        "remotely send title to all the players in the train" :
                        "send a title to players in a train")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Title")
                .handle(event.getPlayer());
    }

    private static class TitleMessage {
        public String title;
        public String subtitle;
        public int fadeIn = 10;
        public int stay = 70;
        public int fadeOut = 10;

        /*
        Parse title line to determine sendTitle options.

        Example:
        [train]
        title 10 70 10
        foo
        bar

        Will parse fadeIn as 10, stay as 70, fadeOut as 10

        Due to defaulting the values in the class, all values are optional
        But are required in the order fadeIn, stay, fadeOut (unable to set just stay or fadeOut)
         */
        private final Pattern TITLE_PATTERN = Pattern.compile("title\\s(\\d+)(?:\\s(\\d+))?(?:\\s(\\d+))?");

        public TitleMessage(SignActionEvent info) {
            this.title = TrainCarts.getMessage(info.getLine(2));
            this.subtitle = TrainCarts.getMessage(info.getLine(3));

            // parse timings for sendTitle
            Matcher matcher = this.TITLE_PATTERN.matcher(info.getLine(1));
            if (matcher.find()) {
                this.fadeIn = ParseUtil.parseInt(matcher.group(1), 10);
                this.stay = ParseUtil.parseInt(matcher.group(2), 70);
                this.fadeOut = ParseUtil.parseInt(matcher.group(3), 10);
            }
        }
    }
}
