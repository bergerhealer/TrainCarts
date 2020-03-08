package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author reeZZer
 * Me (Bergerkiller) gives a big thank-you to him for writing the economics for this feature :)
 */
public class SignActionTicket extends SignAction {

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("ticket");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (TrainCarts.getEconomy() == null) {
            return;
        }

        final boolean isTrain;
        if (info.isCartSign() && info.isAction(SignActionType.MEMBER_ENTER, SignActionType.REDSTONE_ON)) {
            isTrain = false;
        } else if (info.isTrainSign() && info.isAction(SignActionType.GROUP_ENTER, SignActionType.REDSTONE_ON)) {
            isTrain = true;
        } else {
            return;
        }

        if ((info.hasMember() && info.isPowered())) {
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

                for (Player player : member.getEntity().getPlayerPassengers()) {
                    if (mode.equalsIgnoreCase("add")) {
                        TrainCarts.getEconomy().depositPlayer(player, money);
                    } else if (mode.equalsIgnoreCase("check")) {
                        Localization.TICKET_CHECK.message(player, TrainCarts.getCurrencyText(TrainCarts.getEconomy().getBalance(player)));
                    } else if (mode.equalsIgnoreCase("buy") || mode.equalsIgnoreCase("pay")) {
                        if (TrainCarts.getEconomy().has(player, money)) {
                            TrainCarts.getEconomy().withdrawPlayer(player, money);
                            Localization.TICKET_BUY.message(player, TrainCarts.getCurrencyText(money));
                            if (mode.equalsIgnoreCase("pay")) {
                                Set<String> owners = member.getProperties().getOwners();
                                if (owners.size() > 0) {
                                    double ownerMoney = money / owners.size();
                                    for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                                        for (String owner : owners) {
                                            if (owner.equalsIgnoreCase(offlinePlayer.getName())) {
                                                TrainCarts.getEconomy().depositPlayer(offlinePlayer, ownerMoney);
                                                if (offlinePlayer.isOnline()) {
                                                    Localization.TICKET_BUYOWNER.message(offlinePlayer.getPlayer(), player.getDisplayName(), TrainCarts.getCurrencyText(money), member.getProperties().getTrainProperties().getTrainName());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Localization.TICKET_BUYFAIL.message(player, TrainCarts.getCurrencyText(money));
                            member.eject();
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return handleBuild(event, Permission.BUILD_TICKET, "ticket system", "charges the passengers of a train");
    }
}