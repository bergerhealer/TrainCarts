package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Greedy;

/**
 * Houses all commands to do with (item) models. "Model" attachment names can be managed here,
 * such as listing/importing/exporting. As well all resource pack-declared item models can be listed.
 */
@CommandMethod("train model")
public class ModelStoreCommands {

    @CommandRequiresPermission(Permission.COMMAND_LIST_MODELS)
    @CommandMethod("search")
    @CommandDescription("Shows a dialog with all resource pack item models that are available")
    private void commandSearchModels(
            final TrainCarts plugin,
            final Player player
    ) {
        if (plugin.getModelListing().isEmpty()) {
            player.sendMessage(ChatColor.RED + "The currently configured resource pack does not have any custom item models");
        } else {
            plugin.getModelListing().showCreativeDialog(player);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_LIST_MODELS)
    @CommandMethod("search <query>")
    @CommandDescription("Shows a dialog with all resource pack item models that are available")
    private void commandSearchModelsQuery(
            final TrainCarts plugin,
            final Player player,
            final @Argument("query") @Greedy String query
    ) {
        if (plugin.getModelListing().isEmpty()) {
            player.sendMessage(ChatColor.RED + "The currently configured resource pack does not have any custom item models");
        } else {
            plugin.getModelListing().buildDialog(player)
                    .asCreativeMenu()
                    .query(query)
                    .show();
        }
    }
}
