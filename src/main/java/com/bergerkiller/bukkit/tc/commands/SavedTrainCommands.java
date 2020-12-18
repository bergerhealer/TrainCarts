package com.bergerkiller.bukkit.tc.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.Hastebin.DownloadResult;
import com.bergerkiller.bukkit.common.Hastebin.UploadResult;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.BasicConfiguration;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.SavedTrainRequiresAccess;
import com.bergerkiller.bukkit.tc.commands.parsers.LocalizedParserException;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.exception.command.InvalidClaimPlayerNameException;
import com.bergerkiller.bukkit.tc.properties.SavedTrainProperties;
import com.bergerkiller.bukkit.tc.properties.SavedTrainPropertiesStore;

import cloud.commandframework.CommandManager;
import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.InitializationMethod;
import cloud.commandframework.annotations.suggestions.Suggestions;
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
        manager.registerCommandPostProcessor((context) -> {
            // Check if command uses saved train properties
            Object raw_arg = context.getCommandContext().getOrDefault("savedtrainname", (Object) null);
            if (!(raw_arg instanceof SavedTrainProperties)) {
                return;
            }
            SavedTrainProperties savedTrain = (SavedTrainProperties) raw_arg;

            // Check if SavedTrainRequiresAccess is set
            if (!context.getCommand().getArguments().stream().filter(arg -> {
                return arg.getParser() instanceof SavedTrainPropertiesParser
                        && ((SavedTrainPropertiesParser) arg.getParser()).isMustHaveAccess();
            }).findAny().isPresent()) {
                return;
            }

            // Check whether sender has permission to modify it
            CommandSender sender = context.getCommandContext().getSender();
            if (savedTrain.hasPermission(sender)) {
                return;
            }

            boolean force = context.getCommandContext().flags().hasFlag("force");
            if (!checkAccess(sender, savedTrain, force)) {
                ConsumerService.interrupt();
            }
        });

        // Token specified when a command requires write access to a saved train
        manager.getParserRegistry().registerAnnotationMapper(SavedTrainRequiresAccess.class, (a, typeToken) -> {
            return ParserParameters.single(SavedTrainRequiresAccess.PARAM, Boolean.TRUE);
        });

        // Create parsers, take @SavedTrainRequiresAccess flag into account
        manager.getParserRegistry().registerParserSupplier(TypeToken.get(SavedTrainProperties.class), (parameters) -> {
            //parameters.get(parameter, defaultValue)
            boolean access = parameters.get(SavedTrainRequiresAccess.PARAM, Boolean.FALSE);
            return new SavedTrainPropertiesParser(access);
        });

        /*
        cloud.preprocessAnnotation(SavedTrainRequiresAccess.class, (context, queue) -> {
            Map<String, Object> flags = SafeField.get(context.flags(), "flagValues", Map.class);
            System.out.println("FLAGS=" + String.join(", ", flags.keySet()));
            return ArgumentParseResult.success(true);
        });
        */
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

    @CommandMethod("savedtrain <savedtrainname> export|share|paste|upload")
    @CommandDescription("Exports the saved train configuration to a hastebin server")
    private void commandExport(
            final CommandSender sender,
            final @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        Permission.COMMAND_SAVEDTRAIN_EXPORT.handle(sender);
        ConfigurationNode exportedConfig = savedTrain.getConfig().clone();
        exportedConfig.remove("claims");
        exportedConfig.set("name", savedTrain.getName());
        TCConfig.hastebin.upload(exportedConfig.toString()).thenAccept(new Consumer<UploadResult>() {
            @Override
            public void accept(UploadResult t) {
                if (t.success()) {
                    sender.sendMessage(ChatColor.GREEN + "Train '" + ChatColor.YELLOW + savedTrain.getName() +
                            ChatColor.GREEN + "' exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to export train '" + savedTrain.getName() + "': " + t.error());
                }
            }
        });
    }

    @CommandMethod("savedtrain <savedtrainname> rename|changename|move <newsavedtrainname>")
    @CommandDescription("Renames a saved train")
    private void commandRename(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument("newsavedtrainname") String newSavedTrainName,
            final @Flag("force") boolean force
    ) {
        Permission.COMMAND_SAVEDTRAIN_RENAME.handle(sender);

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

    @CommandMethod("savedtrain <savedtrainname> reverse|flip")
    @CommandDescription("Reverse and flips the carts so it is moving in reverse when spawned")
    private void commandRename(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        Permission.COMMAND_SAVEDTRAIN_REVERSE.handle(sender);

        savedTrain.reverse();
        sender.sendMessage(ChatColor.GREEN + "Saved train '" + ChatColor.WHITE + savedTrain.getName() +
                ChatColor.GREEN + "' has been reversed!");
    }

    @CommandMethod("savedtrain <savedtrainname> delete|remove")
    @CommandDescription("Deletes a saved train permanently")
    private void commandDelete(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        Permission.COMMAND_SAVEDTRAIN_DELETE.handle(sender);

        String name = savedTrain.getName();
        plugin.getSavedTrains().remove(name);
        sender.sendMessage(ChatColor.YELLOW + "Saved train '" + ChatColor.WHITE + name +
                ChatColor.YELLOW + "' has been deleted!");
    }

    @CommandMethod("savedtrain <savedtrainname> claim")
    @CommandDescription("Claims a saved train so that the player has exclusive access")
    private void commandClaimSelf(
            final Player sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain
    ) {
        Permission.COMMAND_SAVEDTRAIN_CLAIM.handle(sender);

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

    @CommandMethod("savedtrain <savedtrainname> claim add <player>")
    @CommandDescription("Adds a claim to a saved train so that the added player has exclusive access")
    private void commandClaimAdd(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument(value="player", suggestions="playername") String player, //TODO: Support multiple players WITH flags
            final @Flag("force") boolean force
    ) {
        Permission.COMMAND_SAVEDTRAIN_CLAIM.handle(sender);

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

    @CommandMethod("savedtrain <savedtrainname> claim remove <player>")
    @CommandDescription("Removes a claim from a saved train so that the player no longer has exclusive access")
    private void commandClaimRemove(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Argument(value="player", suggestions="playername") String player, //TODO: Support multiple players WITH flags
            final @Flag("force") boolean force
    ) {
        Permission.COMMAND_SAVEDTRAIN_CLAIM.handle(sender);

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

    @CommandMethod("savedtrain <savedtrainname> claim clear")
    @CommandDescription("Clears all the claims set for the saved train, allowing anyone to access it")
    private void commandClaimClear(
            final CommandSender sender,
            final @SavedTrainRequiresAccess @Argument("savedtrainname") SavedTrainProperties savedTrain,
            final @Flag("force") boolean force
    ) {
        Permission.COMMAND_SAVEDTRAIN_CLAIM.handle(sender);

        // Retrieve current list of claims
        Set<SavedTrainPropertiesStore.Claim> oldClaims = savedTrain.getClaims();

        // Update
        updateClaimList(sender, savedTrain, oldClaims, Collections.emptySet());
    }   

    @CommandMethod("savedtrain <savedtrainname> import <url>")
    @CommandDescription("Imports a saved train from an online hastebin server by url")
    private void commandImport(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Argument(value="savedtrainname") String savedTrainName,
            final @Argument(value="url", description="The URL to a Hastebin-hosted paste to download from") String url,
            final @Flag("force") boolean force
    ) {
        Permission.COMMAND_SAVEDTRAIN_IMPORT.handle(sender);

        TCConfig.hastebin.download(url).thenAccept(new Consumer<DownloadResult>() {
            @Override
            public void accept(DownloadResult result) {
                // Check successful
                if (!result.success()) {
                    sender.sendMessage(ChatColor.RED + "Failed to import train: " + result.error());
                    return;
                }

                // Parse the String contents as YAML
                BasicConfiguration config;
                try {
                    config = result.contentYAML();
                } catch (IOException ex) {
                    sender.sendMessage(ChatColor.RED + "Failed to import train because of YAML decode error: " + ex.getMessage());
                    return;
                }

                // Retrieve previous train properties
                boolean isNewTrain = false;
                SavedTrainPropertiesStore savedTrains = plugin.getSavedTrains();
                SavedTrainProperties savedTrain = savedTrains.getProperties(savedTrainName);
                if (savedTrain == null) {
                    isNewTrain = true;
                } else if (!checkAccess(sender, savedTrain, force)) {
                    return;
                }

                // Update configuration
                try {
                    savedTrains.setConfig(savedTrainName, config);
                } catch (IllegalNameException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid train name: " + savedTrainName);
                    return;
                }
                if (isNewTrain) {
                    sender.sendMessage(ChatColor.GREEN + "The train was imported and saved as " + savedTrainName);
                    if (TCConfig.claimNewSavedTrains && sender instanceof Player) {
                        savedTrains.setClaim(savedTrainName, (Player) sender);
                    }
                } else {
                    sender.sendMessage(ChatColor.GREEN + "The train was imported and saved as " + savedTrainName + ", a previous train was overwritten");
                }
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

        public SavedTrainPropertiesParser(boolean mustHaveAccess) {
            this.mustHaveAccess = mustHaveAccess;
        }

        public boolean isMustHaveAccess() {
            return this.mustHaveAccess;
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

            SavedTrainProperties properties = plugin.getSavedTrains().getProperties(input);
            if (properties == null) {
                return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                        Localization.COMMAND_SAVEDTRAIN_NOTFOUND, input));
            }

            /*
            System.out.println("CHECK " + mustHaveAccess + " force=" + commandContext.flags().hasFlag("force"));

            if (mustHaveAccess && !properties.hasPermission(commandContext.getSender())) {
                // Check if --force was specified
                boolean force = commandContext.flags().hasFlag("force");
                if (!Permission.COMMAND_SAVEDTRAIN_GLOBAL.has(commandContext.getSender())) {
                    if (force) {
                        // Force was specified, but player has no permission for that
                        return ArgumentParseResult.failure(new SavedTrainPropertiesParseException(input, commandContext,
                                Localization.COMMAND_SAVEDTRAIN_GLOBAL_NOPERM));
                    } else {
                        // No force was specified, was claimed and player has no permission
                        return ArgumentParseResult.failure(new SavedTrainPropertiesParseException(input, commandContext,
                                Localization.COMMAND_SAVEDTRAIN_CLAIMED));
                    }
                } else if (!force) {
                    return ArgumentParseResult.failure(new SavedTrainPropertiesParseException(input, commandContext,
                            Localization.COMMAND_SAVEDTRAIN_FORCE));
                }
            }
            */

            inputQueue.remove();
            return ArgumentParseResult.success(properties);
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
