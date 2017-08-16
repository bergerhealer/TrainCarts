package com.bergerkiller.bukkit.tc.signactions;

import java.util.Locale;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.utils.SignSkipOptions;

public class SignActionSkip extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("skip");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isPowered()) return;
        if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.MEMBER_ENTER)) {
            if (!info.hasRailedMember()) return;

            if (matchCart(info, info.getMember())) {
                info.getMember().getProperties().setSkipOptions(getOptions(info));
            }
        } else if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_CHANGE, SignActionType.GROUP_ENTER)) {
            if (!info.hasRailedMember()) return;

            if (matchTrain(info, info.getGroup())) {
                info.getGroup().getProperties().setSkipOptions(getOptions(info));
            }
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            SignSkipOptions opt = getOptions(info);
            for (TrainProperties prop : info.getRCTrainProperties()) {
                if (matchTrain(info, prop.getHolder())) {
                    prop.setSkipOptions(opt);
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        boolean succ = false;
        if (event.isTrainSign()) {
            succ = handleBuild(event, Permission.BUILD_SKIPPER, "train skipper", "tell a train to skip upcoming signs");
        } else if (event.isCartSign()) {
            succ = handleBuild(event, Permission.BUILD_SKIPPER, "cart skipper", "tell a cart to skip upcoming signs");
        } else if (event.isRCSign()) {
            succ = handleBuild(event, Permission.BUILD_SKIPPER, "train skipper", "tell a remote train to skip signs");
        }
        return succ;
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }

    private SignSkipOptions getOptions(SignActionEvent info) {
        // Format: skip (filter) (ignore) (skip)
        String[] args = info.getLine(1).toLowerCase(Locale.ENGLISH).split(" ");
        args = StringUtil.remove(args, 0); // remove 'skip'

        SignSkipOptions options = new SignSkipOptions();
        options.skipCtr = 1; // no-args: skip next sign
        if (args.length >= 1) {
            if (args[0].equals("none") || args[0].equals("disable")) {
                options.skipCtr = 0; // disable skipping
            } else {
                // Check for a filter argument, which is not numeric
                if (!ParseUtil.isNumeric(args[0])) {
                    options.filter = args[0];
                    args = StringUtil.remove(args, 0); // remove filter
                }

                // Check for an 'ignore' counter argument
                if (args.length >= 2) {
                    options.ignoreCtr = ParseUtil.parseInt(args[0], 0);
                }

                // Last argument is the number of times to skip
                if (args.length >= 1) {
                    options.skipCtr = ParseUtil.parseInt(args[args.length - 1], 1);
                }
            }
        }

        return options;
    }

    private boolean matchTrain(SignActionEvent info, MinecartGroup group) {
        if (group == null) return false;
        boolean match = true;
        for (int i = 2; i < 4; i++) {
            String line = info.getLine(i);
            if (line.length() > 0) {
                boolean isLogicAnd = true;
                if (line.startsWith("&")) {
                    isLogicAnd = true;
                    line = line.substring(1);
                } else if (line.startsWith("|")) {
                    isLogicAnd = false;
                    line = line.substring(1);
                }
                boolean result = Statement.has(group, line, info);
                if (isLogicAnd) {
                    match &= result;
                } else {
                    match |= result;
                }
            }
        }
        return match;
    }

    private boolean matchCart(SignActionEvent info, MinecartMember<?> member) {
        if (member == null) return false;
        boolean match = true;
        for (int i = 2; i < 4; i++) {
            String line = info.getLine(i);
            if (line.length() > 0) {
                boolean isLogicAnd = true;
                if (line.startsWith("&")) {
                    isLogicAnd = true;
                    line = line.substring(1);
                } else if (line.startsWith("|")) {
                    isLogicAnd = false;
                    line = line.substring(1);
                }
                boolean result = Statement.has(member, line, info);
                if (isLogicAnd) {
                    match &= result;
                } else {
                    match |= result;
                }
            }
        }
        return match;
    }
}
