package com.bergerkiller.bukkit.tc.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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
import com.bergerkiller.bukkit.tc.exception.command.InvalidClaimPlayerNameException;
import com.bergerkiller.bukkit.tc.properties.SavedTrainProperties;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore.Claim;

import cloud.commandframework.CommandManager;
import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.InitializationMethod;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.parser.ParserParameters;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import cloud.commandframework.services.types.ConsumerService;
import io.leangen.geantyref.TypeToken;

/**
 * Commands to modify an existing saved train
 */
public class SavedTrainCommands {

    @Suggestions("savedtrainmodules")
    public List<String> getSavedTrainModuleNames(final CommandContext<CommandSender> context, final String input) {
        final TrainCarts plugin = context.inject(TrainCarts.class).get();
        return new ArrayList<String>(plugin.getSavedTrains().getModuleNames());
    }

    @InitializationMethod
    private void init(CommandManager<CommandSender> manager) {
        manager.registerCommandPostProcessor((postProcessContext) -> {
            final CommandContext<CommandSender> context = postProcessContext.getCommandContext();

            // Check if command uses saved train properties
            Object raw_arg = context.getOrDefault("savedtrainname", (Object) null);
            if (!(raw_arg instanceof SavedTrainProperties)) {
                return;
            }

            // Find SavedTrainPropertiesParser. If used, process the post-logic code down below.
            SavedTrainPropertiesParser parser = postProcessContext.getCommand().getArguments().stream()
                    .map(CommandArgument::getParser)
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
                        if (TCConfig.claimNewSavedTrains && context.getSender() instanceof Player) {
                            savedTrain.setClaims(Collections.singleton(new Claim((Player) context.getSender())));
                        }
                    } catch (IllegalNameException e) {
                        Localization.COMMAND_SAVEDTRAIN_INVALID_NAME.message(
                                context.getSender(), savedTrain.getName());
                        ConsumerService.interrupt();
                    }
                } else {
                    // Not found, fail
                    Localization.COMMAND_SAVEDTRAIN_NOTFOUND.message(
                            context.getSender(), savedTrain.getName());
                    ConsumerService.interrupt();
                }
            } else if (parser.isMustHaveAccess()) {
                // Check permissions when access is required
                CommandSender sender = context.getSender();
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
        manager.getParserRegistry().registerAnnotationMapper(SavedTrainRequiresAccess.class, (a, typeToken) -> {
            return ParserParameters.single(SavedTrainRequiresAccess.PARAM, Boolean.TRUE);
        });

        // Token specified when new saved train properties are created when missing
        manager.getParserRegistry().registerAnnotationMapper(SavedTrainImplicitlyCreated.class, (a, typeToken) -> {
            return ParserParameters.single(SavedTrainImplicitlyCreated.PARAM, Boolean.TRUE);
        });

        // Create parsers, take @SavedTrainRequiresAccess flag into account
        manager.getParserRegistry().registerParserSupplier(TypeToken.get(SavedTrainProperties.class), (parameters) -> {
            //parameters.get(parameter, defaultValue)
            boolean access = parameters.get(SavedTrainRequiresAccess.PARAM, Boolean.FALSE);
            boolean implicitlyCreated = parameters.get(SavedTrainImplicitlyCreated.PARAM, Boolean.FALSE);
            return new SavedTrainPropertiesParser(access, implicitlyCreated);
        });
    }

    @CommandMethod("savedtrain")
    @CommandDescription("Shows command usage of /savedtrain, lists saved trains")
    private void commandUsage(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Use /savedtrain <trainname> [command] to modify saved trains");
        sender.sendMessage(ChatColor.YELLOW + "Use /savedtrain list to list all trains");
        this.commandShowInfo(sender, plugin, false, null);
    }

    @CommandMethod("savedtrain <savedtrainname> info")
    @CommandDescription("Shows detailed information about a saved train")
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
        buildClaimList(builder, savedTrain.getClaims());

        builder.send(sender);
    }

    @CommandMethod("savedtrain <savedtrainname> defaultmodule")
    @CommandDescription("Moves a saved train to the default module")
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

    @CommandMethod("savedtrain <savedtrainname> module <newmodulename>")
    @CommandDescription("Moves a saved train to a new or existing module")
    private void commandSetModule(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess  @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument(value="newmodulename", suggestions="savedtrainmodules") String newModuleName
    ) {
        if (newModuleName.isEmpty()) {
            commandSetDefaultModule(sender, plugin, savedTrain);
        } else if (newModuleName.equals(savedTrain.getModule().getName())) {
            sender.sendMessage(ChatColor.YELLOW + "Train '" + savedTrain.getName() + "' is already stored module '" + newModuleName + "'");
        } else {
            plugin.getSavedTrains().setModuleNameOfTrain(savedTrain.getName(), newModuleName);
            sender.sendMessage(ChatColor.GREEN + "Train '" + savedTrain.getName() + "' is now stored in module '" + newModuleName + "'!");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_EXPORT)
    @CommandMethod("savedtrain <savedtrainname> export|share|paste|upload")
    @CommandDescription("Exports the saved train configuration to a hastebin server")
    private void commandExport(
            final CommandSender sender,
            final @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        ConfigurationNode exportedConfig = savedTrain.getConfig().clone();
        exportedConfig.remove("claims");
        exportedConfig.set("name", savedTrain.getName());
        Commands.exportTrain(sender, savedTrain.getName(), exportedConfig);
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_RENAME)
    @CommandMethod("savedtrain <savedtrainname> rename|changename|move <newsavedtrainname>")
    @CommandDescription("Renames a saved train")
    private void commandRename(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument("newsavedtrainname") String newSavedTrainName,
            final @Flag("force") boolean force
    ) {
        if (savedTrain.getName().equals(newSavedTrainName)) {
            sender.sendMessage(ChatColor.RED + "The new name is the same as the current name");
            return;
        }

        String oldName = savedTrain.getName();
        plugin.getSavedTrains().rename(oldName, newSavedTrainName);
        sender.sendMessage(ChatColor.YELLOW + "Saved train '" + ChatColor.WHITE + oldName +
                ChatColor.YELLOW + "' has been renamed to '" + ChatColor.WHITE + newSavedTrainName +
                ChatColor.YELLOW + "'!");
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_REVERSE)
    @CommandMethod("savedtrain <savedtrainname> reverse|flip")
    @CommandDescription("Reverse and flips the carts so it is moving in reverse when spawned")
    private void commandRename(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        savedTrain.reverse();
        sender.sendMessage(ChatColor.GREEN + "Saved train '" + ChatColor.WHITE + savedTrain.getName() +
                ChatColor.GREEN + "' has been reversed!");
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_DELETE)
    @CommandMethod("savedtrain <savedtrainname> delete|remove")
    @CommandDescription("Deletes a saved train permanently")
    private void commandDelete(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        String name = savedTrain.getName();
        plugin.getSavedTrains().remove(name);
        sender.sendMessage(ChatColor.YELLOW + "Saved train '" + ChatColor.WHITE + name +
                ChatColor.YELLOW + "' has been deleted!");
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_CLAIM)
    @CommandMethod("savedtrain <savedtrainname> claim")
    @CommandDescription("Claims a saved train so that the player has exclusive access")
    private void commandClaimSelf(
            final Player sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        // Retrieve current list of claims
        Set<SavedTrainPropertiesStore.Claim> oldClaims = savedTrain.getClaims();

        // Create claim using sender
        SavedTrainPropertiesStore.Claim selfClaim = new SavedTrainPropertiesStore.Claim(sender);
        if (oldClaims.contains(selfClaim)) {
            sender.sendMessage(ChatColor.RED + "You have already claimed this saved train!");
        } else {
            Set<SavedTrainPropertiesStore.Claim> newClaims = new HashSet<>(oldClaims);
            newClaims.add(selfClaim);

            // Update
            updateClaimList(sender, savedTrain, oldClaims, newClaims);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_CLAIM)
    @CommandMethod("savedtrain <savedtrainname> claim add <player>")
    @CommandDescription("Adds a claim to a saved train so that the added player has exclusive access")
    private void commandClaimAdd(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument(value="player", suggestions="playername") String player, //TODO: Support multiple players WITH flags
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedTrainPropertiesStore.Claim> oldClaims = savedTrain.getClaims();

        // Parse input into player claims, remove elements that are already in the claim list
        Set<SavedTrainPropertiesStore.Claim> newClaims = new HashSet<>(oldClaims);
        for (SavedTrainPropertiesStore.Claim addedClaim : parseClaims(sender, oldClaims, new String[] {player})) {
            if (!newClaims.add(addedClaim)) {
                sender.sendMessage(ChatColor.RED + "- Player " + addedClaim.description() + " was already on the claim list!");
            }
        }

        // Update
        updateClaimList(sender, savedTrain, oldClaims, newClaims);
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_CLAIM)
    @CommandMethod("savedtrain <savedtrainname> claim remove <player>")
    @CommandDescription("Removes a claim from a saved train so that the player no longer has exclusive access")
    private void commandClaimRemove(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument(value="player", suggestions="playername") String player, //TODO: Support multiple players WITH flags
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedTrainPropertiesStore.Claim> oldClaims = savedTrain.getClaims();

        // Parse input into player claims, remove elements that are already in the claim list
        Set<SavedTrainPropertiesStore.Claim> newClaims = new HashSet<>(oldClaims);
        for (SavedTrainPropertiesStore.Claim removedClaim : parseClaims(sender, oldClaims, new String[] {player})) {
            if (!newClaims.remove(removedClaim)) {
                sender.sendMessage(ChatColor.RED + "- Player " + removedClaim.description() + " was not on the claim list");
            }
        }

        // Update
        updateClaimList(sender, savedTrain, oldClaims, newClaims);
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_CLAIM)
    @CommandMethod("savedtrain <savedtrainname> claim clear")
    @CommandDescription("Clears all the claims set for the saved train, allowing anyone to access it")
    private void commandClaimClear(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedTrainPropertiesStore.Claim> oldClaims = savedTrain.getClaims();

        // Update
        updateClaimList(sender, savedTrain, oldClaims, Collections.emptySet());
    }   

    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_IMPORT)
    @CommandMethod("savedtrain <savedtrainname> import <url>")
    @CommandDescription("Imports a saved train from an online hastebin server by url")
    private void commandImport(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @SavedTrainImplicitlyCreated @Argument(value="savedtrainname") SavedTrainProperties savedTrain,
            final @Argument(value="url", description="The URL to a Hastebin-hosted paste to download from") String url,
            final @Flag("force") boolean force
    ) {
        Commands.importTrain(sender, url, config -> {
            // Retrieve previous train properties
            boolean isNewTrain = savedTrain.isEmpty();

            // Update configuration
            try {
                plugin.getSavedTrains().setConfig(savedTrain.getName(), config);
            } catch (IllegalNameException e) {
                // Should never happen because of pre-validation, but hey!
                sender.sendMessage(ChatColor.RED + "Invalid train name: " + savedTrain.getName());
                return;
            }
            if (isNewTrain) {
                sender.sendMessage(ChatColor.GREEN + "The train was imported and saved as " + savedTrain.getName());
            } else {
                sender.sendMessage(ChatColor.GREEN + "The train was imported and saved as " + savedTrain.getName() + ", a previous train was overwritten");
            }
        });
    }

    @CommandMethod("savedtrain list")
    @CommandDescription("Lists all the train that exist on the server that a player can modify")
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

    @CommandMethod("savedtrain list modules")
    @CommandDescription("Lists all modules in which saved trains are saved")
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
            Set<SavedTrainPropertiesStore.Claim> oldClaims,
            Set<SavedTrainPropertiesStore.Claim> newClaims
    ) {
        // Show messages for players added
        for (SavedTrainPropertiesStore.Claim claim : newClaims) {
            if (!oldClaims.contains(claim)) {
                sender.sendMessage(ChatColor.GREEN + "- Player " + claim.description() + " added to claim list");
            }
        }

        // Show messages for players removed
        for (SavedTrainPropertiesStore.Claim claim : oldClaims) {
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
        buildClaimList(builder, newClaims);
        builder.send(sender);
    }

    private static Set<SavedTrainPropertiesStore.Claim> parseClaims(
            CommandSender sender,
            Set<SavedTrainPropertiesStore.Claim> oldClaims,
            String[] players
    ) {
        Set<SavedTrainPropertiesStore.Claim> result = new HashSet<>(players.length);
        for (String playerArg : players) {
            try {
                UUID queryUUID = UUID.fromString(playerArg);

                // Look this player up by UUID
                // If not a Player, hasPlayedBefore() checks whether offline player data is available
                // If this is not the case, then the offline player returned was made up, and the name is unreliable
                OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(queryUUID);
                if (!(player instanceof Player) && (player.getName() == null || !player.hasPlayedBefore())) {
                    player = null;
                }
                if (player != null) {
                    result.add(new SavedTrainPropertiesStore.Claim(player));
                } else {
                    // Try to find in old claims first
                    boolean uuidMatchesOldClaim = false;
                    for (SavedTrainPropertiesStore.Claim oldClaim : oldClaims) {
                        if (oldClaim.playerUUID.equals(queryUUID)) {
                            result.add(oldClaim);
                            uuidMatchesOldClaim = true;
                            break;
                        }
                    }

                    // Add without a name
                    if (!uuidMatchesOldClaim) {
                        result.add(new SavedTrainPropertiesStore.Claim(queryUUID));
                    }
                }
            } catch (IllegalArgumentException ex) {
                // Check old claim values for a match first
                boolean nameMatchesOldClaim = false;
                for (SavedTrainPropertiesStore.Claim oldClaim : oldClaims) {
                    if (oldClaim.playerName != null && oldClaim.playerName.equals(playerArg)) {
                        result.add(oldClaim);
                        nameMatchesOldClaim = true;
                        break;
                    }
                }
                if (nameMatchesOldClaim) {
                    continue; // skip search by name
                }

                // Argument is a player name. Look it up. Verify the offline player is that of an actual player.
                // It comes up with a fake runtime-generated 'OfflinePlayer' UUID that isn't actually valid
                // In Offline mode though, this is valid, and hasPlayedBefore() should indicate that.
                OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(playerArg);
                if (!(player instanceof Player) && (player.getName() == null || !player.hasPlayedBefore())) {
                    throw new InvalidClaimPlayerNameException(playerArg);
                }
                result.add(new SavedTrainPropertiesStore.Claim(player));
            }
        }
        return result;
    }

    private static void buildClaimList(MessageBuilder builder, Set<SavedTrainPropertiesStore.Claim> claims) {
        if (claims.isEmpty()) {
            builder.red("Not Claimed");
        } else {
            builder.setSeparator(ChatColor.WHITE, ", ");
            for (SavedTrainPropertiesStore.Claim claim : claims) {
                OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(claim.playerUUID);
                String name = player.getName();
                if (name == null) {
                    // Player not known on the server, possibly imported from elsewhere
                    name = claim.playerName;
                    if (name == null) {
                        name = claim.playerUUID.toString();
                    }
                    builder.red(name);
                } else if (player.isOnline()) {
                    builder.aqua(name);
                } else {
                    builder.white(name);
                }
            }
        }
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
    private static class SavedTrainPropertiesParser implements ArgumentParser<CommandSender, SavedTrainProperties> {
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
        public ArgumentParseResult<SavedTrainProperties> parse(CommandContext<CommandSender> commandContext, Queue<String> inputQueue) {
            final TrainCarts plugin = commandContext.inject(TrainCarts.class).get();
            final String input = inputQueue.peek();
            if (input == null) {
                return ArgumentParseResult.failure(new NoInputProvidedException(
                        SavedTrainPropertiesParser.class,
                        commandContext
                ));
            }

            inputQueue.remove();
            SavedTrainProperties properties = plugin.getSavedTrains().getProperties(input);
            if (properties != null) {
                return ArgumentParseResult.success(properties);
            } else {
                return ArgumentParseResult.success(SavedTrainProperties.none(input));
            }
        }

        @Override
        public List<String> suggestions(
                final CommandContext<CommandSender> commandContext,
                final String input
        ) {
            final TrainCarts plugin = commandContext.inject(TrainCarts.class).get();

            List<String> filtered;
            if (input.isEmpty()) {
                filtered = plugin.getSavedTrains().getNames();
            } else {
                filtered = plugin.getSavedTrains().getNames().stream()
                        .filter(s -> s.startsWith(input)).collect(Collectors.toList());
            }

            List<String> claimed = filtered.stream().filter(name -> {
                return plugin.getSavedTrains().hasPermission(commandContext.getSender(), name);
            }).collect(Collectors.toList());

            if (claimed.isEmpty()) {
                return filtered;
            } else {
                return claimed;
            }
        }
    }
}
