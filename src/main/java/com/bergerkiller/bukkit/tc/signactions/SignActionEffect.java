package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Plays or stops sound effects configured in the trains' attachments
 */
public class SignActionEffect extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("effect");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isPowered()) return;

        if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.hasGroup()) {
            EffectAction.parse(info).run(info.getGroup().getAttachments().getNameLookup());
        } else if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.hasMember()) {
            EffectAction.parse(info).run(info.getMember().getAttachments().getNameLookup());
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            EffectAction action = EffectAction.parse(info);
            for (MinecartGroup group : info.getRCTrainGroups()) {
                action.run(group.getAttachments().getNameLookup());
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_EFFECT)
                .setName(event.isCartSign() ? "cart effect player" : "train effect player")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Effect");

        if (event.isTrainSign()) {
            opt.setDescription("play effects configured in attachments of all carts of the train");
        } else if (event.isCartSign()) {
            opt.setDescription("play effects configured in attachments of the cart");
        } else if (event.isRCSign()) {
            opt.setDescription("remotely play effects configured in attachments of all carts of the train");
        }
        return opt.handle(event);
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }

    private static class EffectAction {
        public final Consumer<Attachment.EffectAttachment> action;
        public final List<String> effects;

        public static EffectAction parse(SignActionEvent event) {
            return new EffectAction(event);
        }

        private EffectAction(SignActionEvent event) {
            // Decode the action to perform
            {
                String[] args = StringUtil.getAfter(event.getLine(1), " ").trim().split(" ", -1);
                double speed = 1.0;
                double intensity = 1.0;
                boolean decodedSpeed = false;
                boolean stop = false;
                for (String arg : args) {
                    if (arg.equalsIgnoreCase("stop")) {
                        stop = true;
                        break;
                    } else if (ParseUtil.isNumeric(arg)) {
                        if (decodedSpeed) {
                            intensity = ParseUtil.parseDouble(arg, 1.0);
                        } else {
                            decodedSpeed = true;
                            speed = ParseUtil.parseDouble(arg, 1.0);
                        }
                    }
                }
                if (stop) {
                    action = Attachment.EffectAttachment::stopEffect;
                } else {
                    final Attachment.EffectAttachment.EffectOptions opt =
                            Attachment.EffectAttachment.EffectOptions.of(intensity, speed);
                    action = e -> e.playEffect(opt);
                }
            }

            // Decode the names of effects to play, excluding empty ones
            effects = Stream.of(event.getLine(2), event.getLine(3))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        public void run(AttachmentNameLookup lookup) {
            for (String effect : effects) {
                lookup.getOfType(effect, Attachment.EffectAttachment.class).forEach(action);
            }
        }
    }
}
