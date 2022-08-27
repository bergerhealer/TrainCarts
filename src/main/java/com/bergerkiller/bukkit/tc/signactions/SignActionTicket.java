package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        Economy economy = info.getTrainCarts().getEconomy();
        if (economy == null) {
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
            final String mode = info.getLine(2).toLowerCase(Locale.ENGLISH).trim();
            final double money = ParseUtil.parseDouble(info.getLine(3), 0.0);

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

                Set<String> owners = member.getProperties().getOwners();

                for (Player player : member.getEntity().getPlayerPassengers()) {
                    if (mode.equals("add") && money > 0) {
                        // Give money to the player
                        economy.depositPlayer(player, money);
                        Localization.TICKET_ADD.message(player, TrainCarts.getCurrencyText(money));
                    } else if (mode.equals("check")) {
                        // Check and show current balance
                        Localization.TICKET_CHECK.message(player, TrainCarts.getCurrencyText(economy.getBalance(player)));
                    } else if (mode.equals("buy") && money > 0) {
                        // Withdraw money from the player's account. Eject if insufficient.
                        if (economy.has(player, money)) {
                            economy.withdrawPlayer(player, money);
                            Localization.TICKET_BUY.message(player, TrainCarts.getCurrencyText(money));
                        } else {
                            Localization.TICKET_BUYFAIL.message(player, TrainCarts.getCurrencyText(money));
                            member.getEntity().removePassenger(player);
                        }
                    } else if (mode.equals("pay") && money > 0) {
                        // Take from player, pay it to the owners of the train
                        // If passenger is an owner himself, don't charge anything
                        if (member.getProperties().isOwner(player)) {
                            continue;
                        }
                        if (economy.has(player, money)) {
                            economy.withdrawPlayer(player, money);
                            Localization.TICKET_BUY.message(player, TrainCarts.getCurrencyText(money));

                            // Money disappears into the aether if train has no owners, I guess?
                            if (owners.size() > 0) {
                                double ownerPayment = money / owners.size();
                                for (String owner : owners) {
                                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(owner);
                                    economy.depositPlayer(offlinePlayer, ownerPayment);
                                    if (offlinePlayer.isOnline()) {
                                        Localization.TICKET_BUYOWNER.message(offlinePlayer.getPlayer(), player.getDisplayName(), TrainCarts.getCurrencyText(money), member.getProperties().getTrainProperties().getTrainName());
                                    }
                                }
                            }
                        } else {
                            Localization.TICKET_BUYFAIL.message(player, TrainCarts.getCurrencyText(money));
                            member.getEntity().removePassenger(player);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(Permission.BUILD_TICKET)
                .setName("ticket system")
                .setDescription("charges the passengers of a train")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Ticket")
                .handle(event.getPlayer());
    }
}