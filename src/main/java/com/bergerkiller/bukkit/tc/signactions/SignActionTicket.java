package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static com.bergerkiller.bukkit.tc.TrainCarts.getCurrencyText;

/**
 * @author reeZZer
 *         Me (Bergerkiller) gives a big thank-you to him for writing the economics for this feature :)
 */
public class SignActionTicket extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return TrainCarts.EssentialsEnabled && info.isType("ticket");
    }

    @Override
    public void execute(SignActionEvent info) {
        final boolean isTrain;
        if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
            isTrain = false;
        } else if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
            isTrain = true;
        } else {
            return;
        }
        if ((info.hasMember() && info.isPowered()) && TrainCarts.getEco() != null) {
            double money;
            if (info.getLine(3).isEmpty()) {
                money = 30.0;
            } else {
                money = ParseUtil.parseDouble(info.getLine(3), 0.0);
            }
            final String mode = info.getLine(2);
            List<MinecartMember<?>> members;
            if (isTrain) {
                members = info.getGroup();
            } else {
                members = new ArrayList<>(1);
                members.add(info.getMember());
            }
            for (MinecartMember<?> member : members) {
                if (!member.getEntity().hasPlayerPassenger()) {
                    continue;
                }
                Player player = member.getEntity().getPlayerPassenger();
                //actually perform something with it here
                if (mode.equalsIgnoreCase("add")) {
                    TrainCarts.getEco().depositPlayer(player, money);
                    Localization.TICKET_ADD.message(player, getCurrencyText(money));
                } else if (mode.equalsIgnoreCase("check")) {
                    Localization.TICKET_CHECK.message(player, getCurrencyText(TrainCarts.getEco().getBalance(player)));
                } else if (mode.equalsIgnoreCase("buy")) {
                    if (!TrainCarts.getEco().withdrawPlayer(player, money).transactionSuccess()) {
                        Localization.TICKET_BUYFAIL.message(player, getCurrencyText(money));
                        member.eject();
                    } else {
                        Localization.TICKET_BUY.message(player, getCurrencyText(money));
                    }
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return event.getMode() != SignActionMode.NONE && handleBuild(event, Permission.BUILD_TICKET, "ticket system", "charges the passengers of a train");
    }
}
