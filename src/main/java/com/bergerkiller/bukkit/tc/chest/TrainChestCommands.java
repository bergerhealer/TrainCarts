package com.bergerkiller.bukkit.tc.chest;

import java.util.function.Consumer;

import com.bergerkiller.bukkit.common.utils.StringUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainStorageChestItemException;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import org.incendo.cloud.annotation.specifier.FlagYielding;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;

/**
 * All /train chest commands are implemented here
 */
public class TrainChestCommands {

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest give <target_player>")
    @CommandDescription("Gives a pre-configured train-storing chest item to another player")
    private void commandGiveChestItemToPlayer(
            final TrainCarts plugin,
            final CommandSender sender,
            final @Argument(value="target_player", description="Who to give it to", suggestions="targetplayer") String targetPlayerName,
            final @Flag(value="train", description="Initial train spawn configuration", suggestions="trainspawnpattern") String spawnConfig,
            final @Flag(value="name", description="Display name of the train in the chest item") String name,
            final @Flag(value="locked", description="Whether the train in the item can be changed") boolean locked,
            final @Flag(value="finite", description="Whether to make the item empty when used") boolean finite,
            final @Quoted @Flag(value="spawnmessage", description="Sets a custom successful spawn message") String spawnMessage
    ) {
        Player targetPlayer = Util.findPlayer(sender, targetPlayerName);
        if (targetPlayer == null) {
            return;
        }

        // Build the chest item details from flags as specified
        ItemStack item = TrainChestItemUtil.createItem();
        if (spawnConfig != null && !spawnConfig.isEmpty()) {
            // Types permission check
            if (sender instanceof Player && !SpawnableGroup.parse(plugin, spawnConfig).checkSpawnPermissions((Player) sender)) {
                Localization.SPAWN_FORBIDDEN_CONTENTS.message(sender);
                return;
            }

            // Store config
            TrainChestItemUtil.store(item, spawnConfig);
        }
        if (name != null) {
            TrainChestItemUtil.setName(item, name);
        }
        if (locked) {
            TrainChestItemUtil.setLocked(item, locked);
        }
        if (finite) {
            TrainChestItemUtil.setFiniteSpawns(item, finite);
        }
        if (spawnMessage != null) {
            TrainChestItemUtil.setSpawnMessage(item, StringUtil.ampToColor(spawnMessage));
        }
        targetPlayer.getInventory().addItem(item);
        if (targetPlayer == sender) {
            Localization.CHEST_GIVE.message(sender);
        } else {
            Localization.CHEST_GIVE_TO.message(sender, targetPlayer.getName());
            Localization.CHEST_GIVE.message(targetPlayer);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest [spawnconfig]")
    @CommandDescription("Gives a new train-storing chest item to the sender, train information to store can be specified")
    private void commandGiveChestItem(
            final TrainCarts plugin,
            final Player sender,
            final @Argument("spawnconfig") @Greedy String spawnConfig
    ) {
        // Create a new item and give it to the player
        ItemStack item = TrainChestItemUtil.createItem();
        if (spawnConfig != null && !spawnConfig.isEmpty()) {
            // Types permission check
            if (!SpawnableGroup.parse(plugin, spawnConfig).checkSpawnPermissions(sender)) {
                Localization.SPAWN_FORBIDDEN_CONTENTS.message(sender);
                return;
            }

            // Store config
            TrainChestItemUtil.store(item, spawnConfig);
        }
        sender.getInventory().addItem(item);
        Localization.CHEST_GIVE.message(sender);
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest set [spawnconfig]")
    @CommandDescription("Clears the train-storing chest item the player is currently holding")
    private void commandSetChestItem(
            final TrainCarts plugin,
            final Player player,
            final @Argument(value="spawnconfig", suggestions="trainspawnpattern") @Greedy String spawnConfig
    ) {
        if (spawnConfig != null && !spawnConfig.isEmpty() &&
                !SpawnableGroup.parse(plugin, spawnConfig).checkSpawnPermissions(player)
        ) {
            Localization.SPAWN_FORBIDDEN_CONTENTS.message(player);
            return;
        }

        updateChestItemInInventory(player, item -> {
            TrainChestItemUtil.store(item, spawnConfig==null ? "" : spawnConfig);
        });
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest clear")
    @CommandDescription("Clears the train-storing chest item the player is currently holding")
    private void commandClearChestItem(
            final Player player
    ) {
        updateChestItemInInventory(player, TrainChestItemUtil::clear);
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest lock")
    @CommandDescription("Locks the train-storing chest item so it can not pick up trains by right-clicking")
    private void commandLockChestItem(
            final Player player
    ) {
        updateChestItemInInventory(player, item -> TrainChestItemUtil.setLocked(item, true));
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest unlock")
    @CommandDescription("Unlocks the train-storing chest item so it can pick up trains by right-clicking again")
    private void commandUnlockChestItem(
            final Player player
    ) {
        updateChestItemInInventory(player, item -> TrainChestItemUtil.setLocked(item, false));
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest finite <finite_spawns>")
    @CommandDescription("Sets whether the train-storing chest item has only finite spawns, and becomes empty after spawning")
    private void commandChestItemSetFiniteSpawns(
            final Player player,
            final @Argument("finite_spawns") boolean finite
    ) {
        updateChestItemInInventory(player, item -> TrainChestItemUtil.setFiniteSpawns(item, finite));
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest speed <speed>")
    @CommandDescription("Sets the initial speed of the train when spawning")
    private void commandChestItemSetSpeed(
            final Player player,
            final @Argument("speed") FormattedSpeed speed
    ) {
        updateChestItemInInventory(player, item -> TrainChestItemUtil.setSpeed(item, speed.getValue()));
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest name <name>")
    @CommandDescription("Sets a descriptive name for the train-storing chest item")
    private void commandNameChestItem(
            final Player player,
            final @Argument("name") String name
    ) {
        updateChestItemInInventory(player, item -> TrainChestItemUtil.setName(item, name));
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest spawnmessage <message>")
    @CommandDescription("Sets the message displayed when successfully spawning using the chest item")
    private void commandSetShowMessage(
            final Player player,
            final @Greedy @Argument("message") String message
    ) {
        updateChestItemInInventory(player, item -> TrainChestItemUtil.setSpawnMessage(item,
                StringUtil.ampToColor(message)));
    }

    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest spawnmessage DEFAULT")
    @CommandDescription("Resets the message displayed when successfully spawning using the chest item")
    private void commandSetDefaultShowMessage(
            final Player player
    ) {
        updateChestItemInInventory(player, item -> TrainChestItemUtil.setSpawnMessage(item, null));
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_IMPORT)
    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest import <url>")
    @CommandDescription("Imports a saved train into the chest item from an online hastebin server by url")
    private void commandImportChestItem(
            final Player player,
            final TrainCarts plugin,
            final @Greedy @FlagYielding @Argument("url") String url,
            final @Flag("force") boolean force,
            final @Flag("import-models") boolean importModels
    ) {
        final ItemStack item_when_started = HumanHand.getItemInMainHand(player);

        Commands.importTrain(plugin, player, url, config -> {
            // If used models are declared, import those as well
            Commands.importTrainUsedModels(plugin, player, config, importModels, force);

            if (TrainChestItemUtil.isItem(item_when_started)) {
                // Store into the item held while doing the command
                TrainChestItemUtil.store(item_when_started, config);
            } else {
                // Give a new item
                ItemStack newItem = TrainChestItemUtil.createItem();
                TrainChestItemUtil.store(newItem, config);
                player.getInventory().addItem(newItem);
                Localization.CHEST_GIVE.message(player);
            }
            Localization.CHEST_IMPORTED.message(player);
        });
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_EXPORT)
    @CommandRequiresPermission(Permission.COMMAND_STORAGE_CHEST_CREATE)
    @Command("train chest export")
    @CommandDescription("Exports the train configuration in the chest item to a hastebin server")
    private void commandExportChestItem(
            final TrainCarts plugin,
            final Player player
    ) {
        ItemStack item = HumanHand.getItemInMainHand(player);
        if (!TrainChestItemUtil.isItem(item)) {
            throw new NoTrainStorageChestItemException();
        }

        SpawnableGroup spawnable = TrainChestItemUtil.getSpawnableGroup(plugin, item);
        if (spawnable == null) {
            Localization.CHEST_SPAWN_EMPTY.message(player);
            return;
        }

        Commands.exportTrain(player, spawnable.getSavedName(), spawnable.getFullConfig());
    }

    /**
     * Updates the train storage chest item in the player's main hand. Throws
     * exceptions if this operation fails.
     * 
     * @param player
     * @param consumer Modifying function
     */
    private void updateChestItemInInventory(Player player, Consumer<ItemStack> consumer) {
        ItemStack item = HumanHand.getItemInMainHand(player);
        if (!TrainChestItemUtil.isItem(item)) {
            throw new NoTrainStorageChestItemException();
        }

        item = ItemUtil.cloneItem(item);
        consumer.accept(item);
        HumanHand.setItemInMainHand(player, item);
        Localization.CHEST_UPDATE.message(player);
    }
}
