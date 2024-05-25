package com.bergerkiller.bukkit.tc.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import com.bergerkiller.bukkit.common.cloud.parsers.QuotedArgumentParser;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.annotations.SavedTrainImplicitlyCreated;
import com.bergerkiller.bukkit.tc.commands.annotations.SavedTrainRequiresAccess;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.properties.SavedTrainProperties;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.SavedClaim;
import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;

import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserParameters;
import org.incendo.cloud.services.type.ConsumerService;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

/**
 * Commands to modify an existing saved train
 */
public class SavedTrainCommands {

    @Suggestions("savedtrainmodules")
    public List<String> getSavedTrainModuleNames(final CommandContext<CommandSender> context, final String input) {
        final TrainCarts plugin = context.inject(TrainCarts.class).get();
        return new ArrayList<String>(plugin.getSavedTrains().getModuleNames());
    }

    @Suggestions("savedtrainname")
    public List<String> getSavedTrainNames(final CommandContext<CommandSender> context, final String input) {
        final TrainCarts plugin = context.inject(TrainCarts.class).get();
        return plugin.getSavedTrains().getNames();
    }

    public void init(CommandManager<CommandSender> manager) {
        manager.registerCommandPostProcessor((postProcessContext) -> {
            final CommandContext<CommandSender> context = postProcessContext.commandContext();

            // Check if command uses saved train properties
            Object raw_arg = context.getOrDefault("savedtrainname", (Object) null);
            if (!(raw_arg instanceof SavedTrainProperties)) {
                return;
            }

            // Find SavedTrainPropertiesParser. If used, process the post-logic code down below.
            SavedTrainPropertiesParser parser = postProcessContext.command().components().stream()
                    .map(CommandComponent::parser)
                    .filter(SavedTrainPropertiesParser.class::isInstance)
                    .map(SavedTrainPropertiesParser.class::cast)
                    .findFirst().orElse(null);
            if (parser == null) {
                return;
            }

            SavedTrainProperties savedTrain = (SavedTrainProperties) raw_arg;
            if (savedTrain.isNone()) {
                if (parser.isImplicitlyCreated()) {
                    // Implicitly create new properties if needed
                    TrainCarts plugin = context.inject(TrainCarts.class).get();
                    try {
                        // Create new configuration
                        savedTrain = plugin.getSavedTrains().setConfig(
                                savedTrain.getName(), new ConfigurationNode());
                        context.set("savedtrainname", savedTrain);

                        // Add claim if configured this should happen
                        if (TCConfig.claimNewSavedTrains && context.sender() instanceof Player) {
                            savedTrain.setClaims(Collections.singleton(new SavedClaim((Player) context.sender())));
                        }
                    } catch (IllegalNameException e) {
                        Localization.COMMAND_SAVEDTRAIN_INVALID_NAME.message(
                                context.sender(), savedTrain.getName());
                        ConsumerService.interrupt();
                    }
                } else {
                    // Not found, fail
                    Localization.COMMAND_SAVEDTRAIN_NOTFOUND.message(
                            context.sender(), savedTrain.getName());
                    ConsumerService.interrupt();
                }
            } else if (parser.isMustHaveAccess()) {
                // Check permissions when access is required
                CommandSender sender = context.sender();
                if (savedTrain.hasPermission(sender)) {
                    return;
                }

                boolean force = context.flags().hasFlag("force");
                if (!checkAccess(sender, savedTrain, force)) {
                    ConsumerService.interrupt();
                }
            }
        });

        // Token specified when a command requires write access to a saved train
        manager.parserRegistry().registerAnnotationMapper(SavedTrainRequiresAccess.class, (a, typeToken) -> {
            return ParserParameters.single(SavedTrainRequiresAccess.PARAM, Boolean.TRUE);
        });

        // Token specified when new saved train properties are created when missing
        manager.parserRegistry().registerAnnotationMapper(SavedTrainImplicitlyCreated.class, (a, typeToken) -> {
            return ParserParameters.single(SavedTrainImplicitlyCreated.PARAM, Boolean.TRUE);
        });

        // Create parsers, take @SavedTrainRequiresAccess flag into account
        manager.parserRegistry().registerParserSupplier(TypeToken.get(SavedTrainProperties.class), (parameters) -> {
            //parameters.get(parameter, defaultValue)
            boolean access = parameters.get(SavedTrainRequiresAccess.PARAM, Boolean.FALSE);
            boolean implicitlyCreated = parameters.get(SavedTrainImplicitlyCreated.PARAM, Boolean.FALSE);
            return new SavedTrainPropertiesParser(access, implicitlyCreated).createParser();
        });
    }

    @Command("savedtrain")
    @CommandDescription("Shows command usage of /savedtrain, lists saved trains")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    private void commandUsage(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Use /savedtrain <trainname> [command] to modify saved trains");
        sender.sendMessage(ChatColor.YELLOW + "Use /savedtrain list to list all trains");
        this.commandShowInfo(sender, plugin, false, null);
    }

    @Command("savedtrain <savedtrainname> info")
    @CommandDescription("Shows detailed information about a saved train")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    private void commandShowInfo(
            final CommandSender sender,
            final @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        MessageBuilder builder = new MessageBuilder();
        builder.newLine();
        builder.green("Properties of saved train '").white(savedTrain.getName()).green("':").newLine();
        if (!savedTrain.getModule().isDefault()) {
            builder.yellow("Stored in module: ").white(savedTrain.getModule().getName()).newLine();
        }
        builder.yellow("Number of carts: ").white(savedTrain.getNumberOfCarts()).newLine();
        builder.yellow("Number of seats: ").white(savedTrain.getNumberOfSeats()).newLine();
        builder.yellow("Total train length: ").white(savedTrain.getTotalTrainLength()).newLine();

        builder.yellow("Claimed by: ");
        SavedClaim.buildClaimList(builder, savedTrain.getClaims());

        builder.send(sender);
    }

    @Command("savedtrain <savedtrainname> defaultmodule")
    @CommandDescription("Moves a saved train to the default module")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    private void commandSetDefaultModule(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        if (savedTrain.getModule().isDefault()) {
            sender.sendMessage(ChatColor.YELLOW + "Train '" + savedTrain.getName() + "' is already stored in the default module");
        } else {
            plugin.getSavedTrains().setModuleNameOfTrain(savedTrain.getName(), null);
            sender.sendMessage(ChatColor.GREEN + "Train '" + savedTrain.getName() + "' is now stored in the default module!");
        }
    }

    @Command("savedtrain <savedtrainname> module <newmodulename>")
    @CommandDescription("Moves a saved train to a new or existing module")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    private void commandSetModule(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess  @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument(value="newmodulename", suggestions="savedtrainmodules") String newModuleName
    ) {
        if (newModuleName.isEmpty()) {
            commandSetDefaultModule(sender, plugin, savedTrain);
        } else if (newModuleName.equals(savedTrain.getModule().getName())) {
            sender.sendMessage(ChatColor.YELLOW + "Train '" + savedTrain.getName() + "' is already stored in module '" + newModuleName + "'");
        } else {
            plugin.getSavedTrains().setModuleNameOfTrain(savedTrain.getName(), newModuleName);
            sender.sendMessage(ChatColor.GREEN + "Train '" + savedTrain.getName() + "' is now stored in module '" + newModuleName + "'!");
        }
    }

    @Command("savedtrain <savedtrainname> export|share|paste|upload")
    @CommandDescription("Exports the saved train configuration to a hastebin server")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_EXPORT)
    private void commandExport(
            final CommandSender sender,
            final @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        Commands.exportTrain(sender, savedTrain.getName(), savedTrain.getExportedConfig());
    }

    @Command("savedtrain <savedtrainname> rename|changename|move <newsavedtrainname>")
    @CommandDescription("Renames a saved train")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_RENAME)
    private void commandRename(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Quoted @Argument("newsavedtrainname") String newSavedTrainName,
            final @Flag("force") boolean force
    ) {
        if (savedTrain.getName().equals(newSavedTrainName)) {
            sender.sendMessage(ChatColor.RED + "The new name is the same as the current name");
            return;
        }

        // Handle permissions when overwriting an existing train
        if (!Commands.checkSavePermissionsOverwrite(plugin, sender, newSavedTrainName, force)) {
            return;
        }

        // Verify the train we are renaming does not contain stuff this player has no permission for
        // This prevents an exploit where somebody can place a spawn sign with one name, then rename
        // a train the player has no permission to spawn to that name so that it can be spawned anyway.
        if (!savedTrain.toSpawnableGroup().checkSpawnPermissions(sender)) {
            Localization.COMMAND_SAVE_FORBIDDEN_CONTENTS.message(sender);
            return;
        }

        String oldName = savedTrain.getName();
        plugin.getSavedTrains().rename(oldName, newSavedTrainName);
        sender.sendMessage(ChatColor.YELLOW + "Saved train '" + ChatColor.WHITE + oldName +
                ChatColor.YELLOW + "' has been renamed to '" + ChatColor.WHITE + newSavedTrainName +
                ChatColor.YELLOW + "'!");
    }

    @Command("savedtrain <savedtrainname> copy|clone <targetsavedtrainname>")
    @CommandDescription("Copies an existing saved train and saves it as the target saved train name")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_COPY)
    private void commandCopy(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Quoted @Argument(value="targetsavedtrainname", suggestions="savedtrainname") String targetSavedTrainName,
            final @Flag("force") boolean force
    ) {
        if (savedTrain.getName().equals(targetSavedTrainName)) {
            sender.sendMessage(ChatColor.RED + "The target name is the same as the source name");
            return;
        }

        // Handle permissions when overwriting an existing train
        if (!Commands.checkSavePermissionsOverwrite(plugin, sender, targetSavedTrainName, force)) {
            return;
        }

        // Verify the train we are copying does not contain stuff this player has no permission for
        // This prevents an exploit where somebody can place a spawn sign with one name, then copy
        // a train the player has no permission to spawn to that name so that it can be spawned anyway.
        if (!savedTrain.toSpawnableGroup().checkSpawnPermissions(sender)) {
            Localization.COMMAND_SAVE_FORBIDDEN_CONTENTS.message(sender);
            return;
        }

        try {
            plugin.getSavedTrains().setConfig(targetSavedTrainName, savedTrain.getConfig().clone());
        } catch (IllegalNameException e) {
            Localization.COMMAND_INPUT_NAME_INVALID.message(sender, targetSavedTrainName);
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Saved train '" + ChatColor.WHITE + savedTrain.getName() +
                ChatColor.YELLOW + "' copied and saved as '" + ChatColor.WHITE + targetSavedTrainName +
                ChatColor.YELLOW + "'!");
    }

    @Command("savedtrain <savedtrainname> reverse|flip")
    @CommandDescription("Reverse and flips the carts so it is moving in reverse when spawned")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_REVERSE)
    private void commandReverse(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        savedTrain.reverse();
        sender.sendMessage(ChatColor.GREEN + "Saved train '" + ChatColor.WHITE + savedTrain.getName() +
                ChatColor.GREEN + "' has been reversed!");
    }

    @Command("savedtrain <savedtrainname> lockorientation <locked>")
    @CommandDescription("Sets whether the train orientation is locked, so future saved can't change it")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_REVERSE)
    private void commandLockOrientation(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument("locked") boolean locked,
            final @Flag("force") boolean force
    ) {
        savedTrain.setOrientationLocked(locked);
        sender.sendMessage(ChatColor.GREEN + "Saved train '" + ChatColor.WHITE + savedTrain.getName() +
                ChatColor.GREEN + "' spawn orientation is now " +
                (locked ? (ChatColor.RED + "LOCKED") : (ChatColor.GREEN + "UNLOCKED")));
        if (locked) {
            sender.sendMessage(ChatColor.GREEN + "When this saved train is spawned, and players try to save that " +
                    "train, then the forward-orientation of the train will remain unchanged. " +
                    "Regardless of movement direction");
        } else {
            sender.sendMessage(ChatColor.GREEN + "When this saved train is spawned, and players try to save that " +
                    "train, then the movement direction of the train is used to decide the forward-orientation.");
        }
    }

    @Command("savedtrain <savedtrainname> spawnlimit unlimited")
    @CommandDescription("Disables any set spawn limit, allowing the saved train to be spawned an unlimited number of times")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_SPAWNLIMIT)
    private void commandSetUnlimitedSpawnLimit(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        commandSetSpawnLimit(sender, savedTrain, -1);
    }

    @Command("savedtrain <savedtrainname> spawnlimit <limit>")
    @CommandDescription("Sets the maximum number of times this saved train can be spawned using spawn signs or spawn chest")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_SPAWNLIMIT)
    private void commandSetSpawnLimit(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument("limit") int spawnLimit
    ) {
        savedTrain.setSpawnLimit(spawnLimit);

        sender.sendMessage(ChatColor.GREEN + "Saved train '" + ChatColor.WHITE + savedTrain.getName() +
                ChatColor.GREEN + "' now has a spawn limit of " +
                ((spawnLimit >= 0) ? (ChatColor.WHITE.toString() + spawnLimit) : (ChatColor.RED + "UNLIMITED")));
        if (spawnLimit >= 0) {
            int current = savedTrain.getSpawnLimitCurrentCount();
            ChatColor numberColor = (current >= spawnLimit) ? ChatColor.RED : ChatColor.WHITE;
            sender.sendMessage(ChatColor.GREEN + "This train has been spawned " + numberColor +
                    savedTrain.getSpawnLimitCurrentCount() + ChatColor.GREEN + " times so far");
        }
    }

    @Command("savedtrain <savedtrainname> spawnlimit")
    @CommandDescription("Gets the currently configured saved train spawn limit and how many trains have spawned")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_SPAWNLIMIT)
    private void commandGetSpawnLimit(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        int spawnLimit = savedTrain.getSpawnLimit();
        sender.sendMessage(ChatColor.GREEN + "Saved train '" + ChatColor.WHITE + savedTrain.getName() +
                ChatColor.GREEN + "' has a spawn limit of " +
                ((spawnLimit >= 0) ? (ChatColor.WHITE.toString() + spawnLimit) : (ChatColor.RED + "UNLIMITED")));
        if (spawnLimit >= 0) {
            int current = savedTrain.getSpawnLimitCurrentCount();
            ChatColor numberColor = (current >= spawnLimit) ? ChatColor.RED : ChatColor.WHITE;
            sender.sendMessage(ChatColor.GREEN + "This train has been spawned " + numberColor +
                    savedTrain.getSpawnLimitCurrentCount() + ChatColor.GREEN + " times so far");
        }
    }

    @Command("savedtrain <savedtrainname> delete|remove")
    @CommandDescription("Deletes a saved train permanently")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_DELETE)
    private void commandDelete(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        String name = savedTrain.getName();
        if (plugin.getSavedTrains().remove(name)) {
            sender.sendMessage(ChatColor.YELLOW + "Saved train '" + ChatColor.WHITE + name +
                    ChatColor.YELLOW + "' has been deleted!");
        } else {
            sender.sendMessage(ChatColor.RED + "Saved train '" + ChatColor.WHITE + name +
                    ChatColor.RED + "' cannot be removed! (read-only)");
            sender.sendMessage(ChatColor.RED + "You can only override these properties by saving a new configuration");
        }
    }

    @Command("savedtrain <savedtrainname> claim")
    @CommandDescription("Claims a saved train so that the player has exclusive access")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_CLAIM)
    private void commandClaimSelf(
            final Player sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        // Retrieve current list of claims
        Set<SavedClaim> oldClaims = savedTrain.getClaims();

        // Create claim using sender
        SavedClaim selfClaim = new SavedClaim(sender);
        if (oldClaims.contains(selfClaim)) {
            sender.sendMessage(ChatColor.RED + "You have already claimed this saved train!");
        } else {
            Set<SavedClaim> newClaims = new HashSet<>(oldClaims);
            newClaims.add(selfClaim);

            // Update
            updateClaimList(sender, savedTrain, oldClaims, newClaims);
        }
    }

    @Command("savedtrain <savedtrainname> claim add <player>")
    @CommandDescription("Adds a claim to a saved train so that the added player has exclusive access")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_CLAIM)
    private void commandClaimAdd(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Quoted @Argument(value="player", suggestions="playername") String player, //TODO: Support multiple players WITH flags
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedClaim> oldClaims = savedTrain.getClaims();

        // Parse input into player claims, remove elements that are already in the claim list
        Set<SavedClaim> newClaims = new HashSet<>(oldClaims);
        for (SavedClaim addedClaim : SavedClaim.parseClaims(oldClaims, new String[] {player})) {
            if (!newClaims.add(addedClaim)) {
                sender.sendMessage(ChatColor.RED + "- Player " + addedClaim.description() + " was already on the claim list!");
            }
        }

        // Update
        updateClaimList(sender, savedTrain, oldClaims, newClaims);
    }

    @Command("savedtrain <savedtrainname> claim remove <player>")
    @CommandDescription("Removes a claim from a saved train so that the player no longer has exclusive access")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_CLAIM)
    private void commandClaimRemove(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Quoted @Argument(value="player", suggestions="playername") String player, //TODO: Support multiple players WITH flags
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedClaim> oldClaims = savedTrain.getClaims();

        // Parse input into player claims, remove elements that are already in the claim list
        Set<SavedClaim> newClaims = new HashSet<>(oldClaims);
        for (SavedClaim removedClaim : SavedClaim.parseClaims(oldClaims, new String[] {player})) {
            if (!newClaims.remove(removedClaim)) {
                sender.sendMessage(ChatColor.RED + "- Player " + removedClaim.description() + " was not on the claim list");
            }
        }

        // Update
        updateClaimList(sender, savedTrain, oldClaims, newClaims);
    }

    @Command("savedtrain <savedtrainname> claim clear")
    @CommandDescription("Clears all the claims set for the saved train, allowing anyone to access it")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_CLAIM)
    private void commandClaimClear(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedClaim> oldClaims = savedTrain.getClaims();

        // Update
        updateClaimList(sender, savedTrain, oldClaims, Collections.emptySet());
    }   

    @Command("savedtrain <savedtrainname> import <url>")
    @CommandDescription("Imports a saved train from an online hastebin server by url")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_IMPORT)
    private void commandImport(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @SavedTrainImplicitlyCreated @Argument(value="savedtrainname") SavedTrainProperties savedTrain,
            final @Argument(value="url", description="The URL to a Hastebin-hosted paste to download from") String url,
            final @Flag("force") boolean force,
            final @Flag("import-models") boolean importModels
    ) {
        Commands.importTrain(plugin, sender, url, config -> {
            // If used models are declared, import those as well
            Commands.importTrainUsedModels(plugin, sender, config, importModels, force);

            // Retrieve previous train properties
            boolean isNewTrain = savedTrain.isEmpty();

            // Update configuration
            try {
                plugin.getSavedTrains().setConfig(savedTrain.getName(), config);
            } catch (IllegalNameException e) {
                // Should never happen because of pre-validation, but hey!
                Localization.COMMAND_INPUT_NAME_INVALID.message(sender, savedTrain.getName());
                return;
            }
            if (isNewTrain) {
                sender.sendMessage(ChatColor.GREEN + "The train was imported and saved as " + savedTrain.getName());
            } else {
                sender.sendMessage(ChatColor.GREEN + "The train was imported and saved as " + savedTrain.getName() + ", a previous train was overwritten");
            }
        });
    }

    @Command("savedtrain list")
    @CommandDescription("Lists all the saved trains that exist on the server that a player can modify")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    private void commandShowInfo(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Flag(value="all", description="Show all trains on this server, not just those owned by the player") boolean showAll,
            final @Flag(value="module", suggestions="savedtrainmodules", description="Selects a module to list the saved trains of") String moduleName
    ) {
        SavedTrainPropertiesStore module = plugin.getSavedTrains();
        MessageBuilder builder = new MessageBuilder();
        builder.newLine();
        if (moduleName != null) {
            module = module.getModule(moduleName);
            if (module == null) {
                sender.sendMessage(ChatColor.RED + "Module '" + moduleName + "' does not exist");
                return;
            }

            builder.blue("The following saved trains are stored in module '" + moduleName + "':");
        } else {
            builder.yellow("The following saved trains are available:");
        }

        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        for (String name : module.getNames()) {
            if (module.hasPermission(sender, name)) {
                builder.green(name);
            } else if (showAll) {
                builder.red(name);
            }
        }
        builder.send(sender);
    }

    @Command("savedtrain list modules")
    @CommandDescription("Lists all modules in which saved trains are saved")
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_LIST)
    private void commandListModules(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        MessageBuilder builder = new MessageBuilder();
        builder.newLine();
        builder.blue("The following modules are available:");
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        for (String moduleName : plugin.getSavedTrains().getModuleNames()) {
            builder.aqua(moduleName);
        }
        builder.send(sender);
    }

    private static void updateClaimList(CommandSender sender, SavedTrainProperties savedTrain,
            Set<SavedClaim> oldClaims,
            Set<SavedClaim> newClaims
    ) {
        // Show messages for players added
        for (SavedClaim claim : newClaims) {
            if (!oldClaims.contains(claim)) {
                sender.sendMessage(ChatColor.GREEN + "- Player " + claim.description() + " added to claim list");
            }
        }

        // Show messages for players removed
        for (SavedClaim claim : oldClaims) {
            if (!newClaims.contains(claim)) {
                sender.sendMessage(ChatColor.YELLOW + "- Player " + claim.description() + " "
                        + ChatColor.RED + "removed" + ChatColor.YELLOW + " from claim list");
            }
        }

        // Set them
        savedTrain.setClaims(newClaims);

        // Display new values
        MessageBuilder builder = new MessageBuilder();
        builder.newLine();
        if (newClaims.size() > 1) {
            builder.newLine();
        }
        builder.yellow("Saved train '").white(savedTrain.getName()).yellow("' is now claimed by: ");
        SavedClaim.buildClaimList(builder, newClaims);
        builder.send(sender);
    }

    public boolean checkAccess(CommandSender sender, SavedTrainProperties savedTrain, boolean force) {
        if (Permission.COMMAND_SAVEDTRAIN_GLOBAL.has(sender)) {
            if (force) {
                return true;
            } else {
                Localization.COMMAND_SAVEDTRAIN_FORCE.message(sender, savedTrain.getName());
                return false;
            }
        } else {
            if (force) {
                Localization.COMMAND_SAVEDTRAIN_GLOBAL_NOPERM.message(sender);
            } else {
                Localization.COMMAND_SAVEDTRAIN_CLAIMED.message(sender, savedTrain.getName());
            }
            return false;
        }
    }

    /**
     * Parser for SavedTrainProperties
     */
    private static class SavedTrainPropertiesParser implements QuotedArgumentParser<CommandSender, SavedTrainProperties>, BlockingSuggestionProvider.Strings<CommandSender> {
        private final boolean mustHaveAccess;
        private final boolean implicitlyCreated;

        public SavedTrainPropertiesParser(boolean mustHaveAccess, boolean implicitlyCreated) {
            this.mustHaveAccess = mustHaveAccess;
            this.implicitlyCreated = implicitlyCreated;
        }

        public boolean isMustHaveAccess() {
            return this.mustHaveAccess;
        }

        public boolean isImplicitlyCreated() {
            return this.implicitlyCreated;
        }

        @Override
        public @NonNull ArgumentParseResult<@NonNull SavedTrainProperties> parseQuotedString(
                @NonNull CommandContext<@NonNull CommandSender> commandContext,
                @NonNull String input
        ) {
            final TrainCarts plugin = commandContext.inject(TrainCarts.class).get();

            // Verify not an invalid name that will brick YAML
            TrainNameFormat.VerifyResult verify = TrainNameFormat.verify(input);
            if (verify != TrainNameFormat.VerifyResult.OK) {
                return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                        verify.getMessage(), input));
            }

            return ArgumentParseResult.success(plugin.getSavedTrains().getPropertiesOrNone(input));
        }

        @Override
        public @NonNull Iterable<@NonNull String> stringSuggestions(
                @NonNull CommandContext<CommandSender> commandContext,
                @NonNull CommandInput commandInput
        ) {
            final TrainCarts plugin = commandContext.inject(TrainCarts.class).get();
            final String input = commandInput.lastRemainingToken();

            List<String> filtered;
            if (input.isEmpty()) {
                filtered = plugin.getSavedTrains().getNames();
            } else {
                filtered = plugin.getSavedTrains().getNames().stream()
                        .filter(s -> s.startsWith(input)).collect(Collectors.toList());
            }

            List<String> claimed = filtered.stream().filter(name -> {
                return plugin.getSavedTrains().hasPermission(commandContext.sender(), name);
            }).collect(Collectors.toList());

            if (claimed.isEmpty()) {
                return filtered;
            } else {
                return claimed;
            }
        }
    }
}
