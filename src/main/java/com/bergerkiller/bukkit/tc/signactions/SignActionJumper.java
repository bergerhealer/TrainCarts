package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.util.Vector;

public class SignActionJumper extends SignAction {

    public static void jump(MinecartMember<?> member, Vector offset) {
        //TODO: Proper jumping action for accurate (block location) jump
        member.getEntity().vel.set(offset);
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("jump") && info.getMode() != SignActionMode.NONE;
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isPowered() || !info.hasMember()) {
            return;
        }
        final boolean isCart = info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER);
        final boolean isTrain = info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER);
        if (!isCart && !isTrain) {
            return;
        }
        // Parse offset
        Vector offset = Util.parseVector(info.getLine(2), new Vector(0.0, 0.0, 0.0));
        if (offset.lengthSquared() == 0.0) {
            return;
        }
        // Rotate the offset so it becomes aligned with the sign
        float yaw = FaceUtil.faceToYaw(info.getFacing().getOppositeFace());
        offset = MathUtil.rotate(yaw, 0.0f, offset);
        // Jump
        if (isCart) {
            jump(info.getMember(), offset);
        } else {
            for (MinecartMember<?> member : info.getGroup()) {
                jump(member, offset.clone());
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_JUMPER)
                .setName(event.isCartSign() ? "cart jumper" : "train jumper");

        if (event.isTrainSign()) {
            opt.setDescription("cause a minecart to jump towards a certain direction");
        } else {
            opt.setDescription("cause an entire train to jump towards a certain direction");
        }
        return opt.handle(event.getPlayer());
    }
}
