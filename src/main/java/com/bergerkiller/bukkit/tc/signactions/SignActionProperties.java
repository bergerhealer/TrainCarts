package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class SignActionProperties extends SignAction {

    private static PropertyParseResult.Reason parseAndSet(IProperties properties, SignActionEvent info) {
        return properties.parseAndSet(info.getLine(2), info.getLine(3)).getReason();
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("property");
    }

    @Override
    public void execute(SignActionEvent info) {
        final boolean powerChange = info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF);

        PropertyParseResult.Reason result;
        if ((powerChange || info.isAction(SignActionType.MEMBER_ENTER)) && info.isCartSign() && info.hasMember()) {
            result = parseAndSet(info.getMember().getProperties(), info);
        } else if ((powerChange || info.isAction(SignActionType.GROUP_ENTER)) && info.isTrainSign() && info.hasGroup()) {
            result = parseAndSet(info.getGroup().getProperties(), info);
        } else if (powerChange && info.isRCSign()) {
            result = PropertyParseResult.Reason.NONE;
            for (TrainProperties prop : info.getRCTrainProperties()) {
                PropertyParseResult.Reason singleResult = parseAndSet(prop, info);
                if (singleResult != PropertyParseResult.Reason.NONE) {
                    result = singleResult;
                }
            }
        } else {
            return;
        }

        // When not successful, display particles at the sign to indicate such
        BlockFace facingInv = info.getFacing().getOppositeFace();
        Location effectLocation = info.getSign().getLocation()
                .add(0.5, 0.5, 0.5)
                .add(0.3 * facingInv.getModX(), 0.0, 0.3 * facingInv.getModZ());

        switch (result) {
        case PROPERTY_NOT_FOUND:
            // Spawn black dust particles when property is not found
            Util.spawnDustParticle(effectLocation, 0.0, 0.0, 0.0);
            WorldUtil.playSound(effectLocation, SoundEffect.EXTINGUISH, 1.0f, 2.0f);
            break;
        case INVALID_INPUT:
            // Spawn yellow dust particles when there is a syntax error on the input value
            Util.spawnDustParticle(effectLocation, 255.0, 255.0, 0.0);
            WorldUtil.playSound(effectLocation, SoundEffect.EXTINGUISH, 1.0f, 2.0f);
            break;
        case ERROR:
            // Spawn red dust particles when errors occur
            Util.spawnDustParticle(effectLocation, 255.0, 0.0, 0.0);
            WorldUtil.playSound(effectLocation, SoundEffect.EXTINGUISH, 1.0f, 2.0f);
            break;
        default:
            break;
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_PROPERTY)
                .setName(event.isCartSign() ? "cart property setter" : "train property setter")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Property");

        if (event.isTrainSign()) {
            opt.setDescription("set properties on the train above");
        } else if (event.isCartSign()) {
            opt.setDescription("set properties on the cart above");
        } else if (event.isRCSign()) {
            opt.setDescription( "remotely set properties on the train specified");
        }
        return opt.handle(event.getPlayer());
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
