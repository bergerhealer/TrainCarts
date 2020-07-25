package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

public class SignActionDestroy extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("destroy");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isPowered()) return;
        if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.hasGroup()) {
            if (TCConfig.playHissWhenDestroyedBySign) {
                info.getGroup().playLinkEffect();
            }
            info.getGroup().destroy();
        } else if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.hasMember()) {
            info.getMember().onDie();
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (MinecartGroup group : info.getRCTrainGroups()) {
                if (TCConfig.playHissWhenDestroyedBySign) {
                    group.playLinkEffect();
                }
                group.destroy();
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_DESTRUCTOR)
                .setName(event.isCartSign() ? "cart destructor" : "train destructor")
                .setMinecraftWIKIHelp("Mods/TrainCarts/Signs/Destroyer");

        if (event.isTrainSign()) {
            opt.setDescription("destroy an entire train");
        } else if (event.isCartSign()) {
            opt.setDescription("destroy minecarts");
        } else if (event.isRCSign()) {
            opt.setDescription("destroy an entire train remotely");
        }
        return opt.handle(event.getPlayer());
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
