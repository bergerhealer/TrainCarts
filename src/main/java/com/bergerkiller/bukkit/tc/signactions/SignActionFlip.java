package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;

public class SignActionFlip extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("flip");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isPowered()) {
            return;
        }

        if (info.isTrainSign() && info.hasGroup() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
            for (MinecartMember<?> member : info.getGroup()) {
                member.flipOrientation();
            }
        } else if (info.isCartSign() && info.hasMember() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
            info.getMember().flipOrientation();
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (MinecartGroup group : info.getRCTrainGroups()) {
                for (MinecartMember<?> member : group) {
                    member.flipOrientation();
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        if (event.isCartSign()) {
            return handleBuild(event, Permission.BUILD_FLIPPER, "cart flipper", "flip the orientation of a Minecart");
        } else if (event.isTrainSign()) {
            return handleBuild(event, Permission.BUILD_FLIPPER, "train cart flipper", "flip the orientation of all Minecarts in a train");
        } else if (event.isRCSign()) {
            return handleBuild(event, Permission.BUILD_FLIPPER, "train cart flipper", "flip the orientation of all Minecarts in a train remotely");
        }
        return false;
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
