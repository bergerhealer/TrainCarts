package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.Location;

public class SignActionSound extends SignAction {
    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("sound", "msound");
    }

    public ResourceKey<SoundEffect> getSound(SignActionEvent info) {
        try {
            return SoundEffect.fromName(info.getLine(2) + info.getLine(3));
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void execute(SignActionEvent info) {
        boolean move = info.isType("msound");
        if (!info.isPowered()) return;

        ResourceKey<SoundEffect> sound = getSound(info);
        if (sound == null) {
            return;
        }

        float pitch = 1f;
        float volume = 1f;

        String[] args = StringUtil.getAfter(info.getLine(1), " ").trim().split(" ", -1);
        try {
            if (args.length >= 1) {
                pitch = (float) ParseUtil.parseDouble(args[0], 1.0);
            }
            if (args.length == 2) {
                volume = (float) ParseUtil.parseDouble(args[1], 1.0);
            }
        } catch (NumberFormatException ignored) {
        }

        if (info.isAction(SignActionType.MEMBER_MOVE)) {
            if (move) {
                if (info.isTrainSign()) {
                    for (MinecartMember<?> member : info.getGroup()) {
                        Location location = member.getEntity().getLocation();
                        WorldUtil.playSound(location, sound, volume, pitch);
                    }
                } else if (info.isCartSign()) {
                    Location location = info.getMember().getEntity().getLocation();
                    WorldUtil.playSound(location, sound, volume, pitch);
                }
            }
            return;
        }
        if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.hasGroup()) {
            for (MinecartMember<?> member : info.getGroup()) {
                Location location = member.getEntity().getLocation();
                WorldUtil.playSound(location, sound, volume, pitch);
            }
        } else if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.hasMember()) {
            Location location = info.getMember().getEntity().getLocation();
            WorldUtil.playSound(location, sound, volume, pitch);
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (MinecartGroup group : info.getRCTrainGroups()) {
                for (MinecartMember<?> member : group) {
                    Location location = member.getEntity().getLocation();
                    WorldUtil.playSound(location, sound, volume, pitch);
                }
            }
        } else if (info.isAction(SignActionType.REDSTONE_ON)) {
            Location location;
            if (info.hasRails()) {
                location = info.getCenterLocation();
            } else {
                location = info.getLocation().add(0.0, 2.0, 0.0);
            }
            WorldUtil.playSound(location, sound, volume, pitch);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (getSound(event) == null) {
            event.getPlayer().sendMessage(ChatColor.RED + "Sound name '" + event.getLine(2) + event.getLine(3) + "' is invalid!");
            return false;
        }
        String app = event.isType("msound") ? " while moving" : "";

        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_SOUND)
                .setName(event.isCartSign() ? "cart sound player" : "train sound player")
                .setMinecraftWIKIHelp("Mods/TrainCarts/Signs/Sound");

        if (event.isCartSign()) {
            opt.setDescription("play a sound in the minecart" + app);
        } else if (event.isTrainSign()) {
            opt.setDescription("play a sound in all minecarts of the train" + app);
        } else if (event.isRCSign()) {
            opt.setDescription("play a sound in all minecarts of the train" + app);
        }
        return opt.handle(event.getPlayer());
    }

    @Override
    public boolean isMemberMoveHandled(SignActionEvent info) {
        return info.isType("msound");
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
