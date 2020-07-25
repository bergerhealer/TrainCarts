package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class SignActionEject extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("eject");
    }

    @Override
    public boolean click(SignActionEvent info, Player player) {
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(player.getVehicle());
        if (member == null) {
            return false;
        }
        info.setMember(member);
        eject(info);
        return true;
    }

    public void eject(SignActionEvent info) {
        final boolean hasSettings = !info.getLine(2).isEmpty() || !info.getLine(3).isEmpty();
        Vector offset = new Vector();
        float yaw = 0F;
        float pitch = 0F;
        if (hasSettings) {
            // Check if absolute (second line has 'at')
            boolean isAbsolute = info.getLine(1).toLowerCase(Locale.ENGLISH).contains(" at");

            // Read the offset
            offset = Util.parseVector(info.getLine(2), null);
            if (offset == null) {
                isAbsolute = false;
                offset = new Vector();
            } else if (!isAbsolute) {
                if (offset.length() > TCConfig.maxEjectDistance) {
                    offset.normalize().multiply(TCConfig.maxEjectDistance);
                }
            }

            // Read the rotation
            boolean usePlayerRotation = false;
            if (!info.getLine(3).isEmpty()) {
                String[] angletext = Util.splitBySeparator(info.getLine(3));
                if (angletext.length == 2) {
                    yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
                    pitch = ParseUtil.parseFloat(angletext[1], 0.0f);
                } else if (angletext.length == 1) {
                    yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
                }
            } else if (isAbsolute) {
                usePlayerRotation = true;
            }

            // Convert to sign-relative-space
            if (!isAbsolute) {
                float signyawoffset = (float) FaceUtil.faceToYaw(info.getFacing().getOppositeFace());
                offset = MathUtil.rotate(signyawoffset, 0F, offset);
                yaw += signyawoffset + 90F;
            }

            // Actually eject
            if (isAbsolute) {
                Location at = new Location(info.getWorld(), offset.getX(), offset.getY(), offset.getZ());
                for (MinecartMember<?> mm : info.getMembers()) {
                    if (usePlayerRotation) {
                        at.setYaw(0.0f);
                        at.setPitch(0.0f);
                        for (Entity e : mm.getEntity().getPassengers()) {
                            if (e instanceof LivingEntity) {
                                Location eyeLoc = ((LivingEntity) e).getEyeLocation();
                                at.setYaw(eyeLoc.getYaw());
                                at.setPitch(eyeLoc.getPitch());
                                break;
                            }
                        }
                    }
                    mm.eject(at);
                }
            } else {
                for (MinecartMember<?> mm : info.getMembers()) {
                    mm.eject(offset, yaw, pitch);
                }
            }
        } else {
            // Actually eject
            for (MinecartMember<?> mm : info.getMembers()) {
                mm.eject();
            }
        }
    }

    @Override
    public void execute(SignActionEvent info) {
        boolean isRemote = false;
        if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
            //TODO: Add something here or remove
        } else if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
            //TODO: Add something here or remove
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            isRemote = true;
        } else {
            return;
        }
        if (isRemote || (info.hasMember() && info.isPowered())) {
            eject(info);
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        // Extra permissions apply for absolute world coordinates teleportation, as it could be abused
        if (event.getLine(1).toLowerCase(Locale.ENGLISH).contains(" at")) {
            if (!Permission.BUILD_EJECTOR_ABSOLUTE.handleMsg(event.getPlayer(), ChatColor.RED + "You do not have permission to build eject signs that teleport to world coordinates")) {
                return false;
            }
        }

        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_EJECTOR)
                .setName("train ejector")
                .setDescription("eject the passengers of a " + (event.isRCSign() ? "remote train" : "train"))
                .setMinecraftWIKIHelp("Mods/TrainCarts/Signs/Ejector")
                .handle(event.getPlayer());
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
