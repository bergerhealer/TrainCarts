package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyInputContext;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

public class SignActionProperties extends SignAction {

    private static PropertyParseResult.Reason parseAndSet(IProperties properties, SignActionEvent info, final boolean conditional) {
        return properties.parseAndSet(info.getLine(2),
                PropertyInputContext.of(info.getLine(3))
                                    .signEvent(info)
                                    .beforeSet(result -> {
                                        if (conditional && !result.getInputContext().hasParsedStatements()) {
                                            return PropertyParseResult.failSuppressed(result.getInputContext(),
                                                    result.getProperty(), result.getName());
                                        }

                                        return result;
                                    })).getReason();
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("property");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isPowered()) return;

        // These trigger moments only get used when a conditional statement is used as value on the property sign
        // We abort the set operation if after parsing we find out this isn't the case
        // This prevents a property being applied multiple times when, for example, players enter/exit a train.
        boolean isConditionalCart = false;
        boolean isConditionalTrain = false;
        if (!info.getHeader().onPowerFalling() && !info.getHeader().onPowerRising()) {
            if (info.isAction(SignActionType.REDSTONE_CHANGE)) {
                isConditionalCart = true;
                isConditionalTrain = true;
            } else if (info.isAction(SignActionType.MEMBER_UPDATE)) {
                isConditionalCart = true;
            } else if (info.isAction(SignActionType.GROUP_UPDATE)) {
                isConditionalTrain = true;
            }
        }

        PropertyParseResult.Reason result;
        if ((isConditionalCart || info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) && info.isCartSign() && info.hasMember()) {
            // Sign activation with redstone / member entering it
            result = parseAndSet(info.getMember().getProperties(), info, isConditionalCart);
        } else if ((isConditionalTrain || info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) && info.isTrainSign() && info.hasGroup()) {
            // Sign activation with redstone / group entering it
            result = parseAndSet(info.getGroup().getProperties(), info, isConditionalTrain);
        } else if (info.isAction(SignActionType.REDSTONE_ON) && info.isRCSign()) {
            result = PropertyParseResult.Reason.NONE;
            for (TrainProperties prop : info.getRCTrainProperties()) {
                PropertyParseResult.Reason singleResult = parseAndSet(prop, info, false);
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
        case SUPPRESSED:
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

        // Check permission to modify properties at all
        if (!Permission.COMMAND_PROPERTIES.has(event.getPlayer()) &&
            !Permission.COMMAND_GLOBALPROPERTIES.has(event.getPlayer()))
        {
            Localization.PROPERTY_NOPERM_ANY.message(event.getPlayer());
            return false;
        }

        // Validate the property and value on the sign exist/are correct
        // We do this first so we can figure out the permission that may be required for it
        PropertyParseResult<Object> result = IPropertyRegistry.instance().parse(null, event.getLine(2), event.getLine(3));
        if (!result.hasPermission(event.getPlayer())) {
            Localization.PROPERTY_NOPERM.message(event.getPlayer(), result.getName());
            return false;
        }

        if (event.isTrainSign()) {
            opt.setDescription("set properties on the train above");
        } else if (event.isCartSign()) {
            opt.setDescription("set properties on the cart above");
        } else if (event.isRCSign()) {
            opt.setDescription( "remotely set properties on the train specified");
        }
        if (!opt.handle(event)) {
            return false;
        }

        // Warn about incorrect syntax
        if (!result.isSuccessful()) {
            event.getPlayer().sendMessage(result.getMessage());
        }

        return true;
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
