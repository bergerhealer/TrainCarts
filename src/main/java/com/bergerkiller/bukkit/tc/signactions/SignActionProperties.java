package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.properties.IParsable;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import java.util.Locale;

public class SignActionProperties extends SignAction {

    private static boolean argsUsesSeparator(String mode) {
        return LogicUtil.contains(mode, "exitoffset", "exitrot", "exitrotation");
    }

    private static boolean parseSet(IParsable properties, SignActionEvent info) {
        String mode = info.getLine(2).toLowerCase(Locale.ENGLISH).trim();
        if (argsUsesSeparator(mode)) {
            return Util.parseProperties(properties, mode, info.getLine(3));
        }
        String[] args = Util.splitBySeparator(info.getLine(3));
        if (args.length >= 2) {
            return Util.parseProperties(properties, mode, info.isPowered() ? args[0] : args[1]);
        } else
            return args.length == 1 && info.isPowered() && Util.parseProperties(properties, mode, args[0]);
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("property");
    }

    @Override
    public void execute(SignActionEvent info) {
        final boolean powerChange = info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF);
        if ((powerChange || info.isAction(SignActionType.MEMBER_ENTER)) && info.isCartSign() && info.hasMember()) {
            parseSet(info.getMember(), info);
        } else if ((powerChange || info.isAction(SignActionType.GROUP_ENTER)) && info.isTrainSign() && info.hasGroup()) {
            parseSet(info.getGroup(), info);
        } else if (powerChange && info.isRCSign()) {
            for (TrainProperties prop : info.getRCTrainProperties()) {
                parseSet(prop, info);
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_PROPERTY)
                .setName(event.isCartSign() ? "cart property setter" : "train property setter")
                .setMinecraftWIKIHelp("Mods/TrainCarts/Signs/Property");

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
