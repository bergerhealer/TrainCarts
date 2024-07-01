package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModelStore;
import com.bergerkiller.bukkit.tc.commands.annotations.SavedModelImplicitlyCreated;
import com.bergerkiller.bukkit.tc.commands.annotations.SavedModelRequiresAccess;
import com.bergerkiller.bukkit.tc.controller.global.TrainCartsPlayer;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.properties.SavedClaim;
import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;
import io.leangen.geantyref.TypeToken;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotation.specifier.FlagYielding;
import org.incendo.cloud.annotation.specifier.Greedy;
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
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserParameters;
import org.incendo.cloud.services.type.ConsumerService;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Houses all commands to do with (item) models. "Model" attachment names can be managed here,
 * such as listing/importing/exporting. As well all resource pack-declared item models can be listed.
 */
@Command("train model")
public class ModelStoreCommands {

    @Suggestions("savedmodelmodules")
    public List<String> getSavedModelConfigModuleNames(final CommandContext<CommandSender> context, final String input) {
        final TrainCarts plugin = context.inject(TrainCarts.class).get();
        return new ArrayList<String>(plugin.getSavedAttachmentModels().getModuleNames());
    }

    @Suggestions("savedmodelname")
    public List<String> getSavedModelConfigNames(final CommandContext<CommandSender> context, final String input) {
        final TrainCarts plugin = context.inject(TrainCarts.class).get();
        return plugin.getSavedAttachmentModels().getNames();
    }

    public void init(CommandManager<CommandSender> manager) {
        manager.registerCommandPostProcessor((postProcessContext) -> {
            final CommandContext<CommandSender> context = postProcessContext.commandContext();

            // Check if command uses saved model attachments
            Object raw_arg = context.getOrDefault("savedmodelname", (Object) null);
            if (!(raw_arg instanceof SavedAttachmentModel)) {
                return;
            }

            // Find SavedAttachmentModelParser. If used, process the post-logic code down below.
            SavedAttachmentModelParser parser = postProcessContext.command().components().stream()
                    .map(CommandComponent::parser)
                    .filter(SavedAttachmentModelParser.class::isInstance)
                    .map(SavedAttachmentModelParser.class::cast)
                    .findFirst().orElse(null);
            if (parser == null) {
                return;
            }

            SavedAttachmentModel savedModel = (SavedAttachmentModel) raw_arg;
            if (savedModel.isNone()) {
                if (parser.isImplicitlyCreated()) {
                    // Implicitly create new properties if needed
                    TrainCarts plugin = context.inject(TrainCarts.class).get();
                    try {
                        // Create new configuration
                        savedModel = plugin.getSavedAttachmentModels().setConfigAsPlayer(
                                savedModel.getName(), new ConfigurationNode(), context.sender());
                        context.set("savedmodelname", savedModel);
                    } catch (IllegalNameException e) {
                        Localization.COMMAND_MODEL_CONFIG_INVALID_NAME.message(
                                context.sender(), savedModel.getName());
                        ConsumerService.interrupt();
                    }
                } else {
                    // Not found, fail
                    Localization.COMMAND_MODEL_CONFIG_NOTFOUND.message(
                            context.sender(), savedModel.getName());
                    ConsumerService.interrupt();
                }
            } else if (parser.isMustHaveAccess()) {
                // Check permissions when access is required
                CommandSender sender = context.sender();
                if (savedModel.hasPermission(sender)) {
                    return;
                }

                boolean force = context.flags().hasFlag("force");
                if (!checkAccess(sender, savedModel, force)) {
                    ConsumerService.interrupt();
                }
            }
        });

        // Token specified when a command requires write access to a saved model
        manager.parserRegistry().registerAnnotationMapper(SavedModelRequiresAccess.class, (a, typeToken) -> {
            return ParserParameters.single(SavedModelRequiresAccess.PARAM, Boolean.TRUE);
        });

        // Token specified when new saved model configurations are created when missing
        manager.parserRegistry().registerAnnotationMapper(SavedModelImplicitlyCreated.class, (a, typeToken) -> {
            return ParserParameters.single(SavedModelImplicitlyCreated.PARAM, Boolean.TRUE);
        });

        // Create parsers, take @SavedModelRequiresAccess flag into account
        manager.parserRegistry().registerParserSupplier(TypeToken.get(SavedAttachmentModel.class), (parameters) -> {
            //parameters.get(parameter, defaultValue)
            boolean access = parameters.get(SavedModelRequiresAccess.PARAM, Boolean.FALSE);
            boolean implicitlyCreated = parameters.get(SavedModelImplicitlyCreated.PARAM, Boolean.FALSE);
            return new SavedAttachmentModelParser(access, implicitlyCreated);
        });
    }

    @Command("config")
    @CommandDescription("Shows command usage of /train model config, lists saved model configurations")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    private void commandUsage(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        sender.sendMessage(ChatColor.YELLOW + "Use /train model config <modelname> [command] to modify saved models");
        sender.sendMessage(ChatColor.YELLOW + "Use /train model config list to list all models");
        this.commandShowInfo(sender, plugin, false, null);
    }

    @Command("config <savedmodelname> info")
    @CommandDescription("Shows detailed information about a saved model configuration")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    private void commandShowInfo(
            final CommandSender sender,
            final @Argument("savedmodelname") SavedAttachmentModel savedModel
    ) {
        MessageBuilder builder = new MessageBuilder();
        builder.newLine();
        builder.green("Properties of saved model configuration '").white(savedModel.getName()).green("':").newLine();
        if (!savedModel.getModule().isDefault()) {
            builder.yellow("Stored in module: ").white(savedModel.getModule().getName()).newLine();
        }
        builder.yellow("Number of seats: ").white(savedModel.getSeatCount()).newLine();

        builder.yellow("Claimed by: ");
        SavedClaim.buildClaimList(builder, savedModel.getClaims());

        builder.send(sender);
    }

    @Command("config <savedmodelname> defaultmodule")
    @CommandDescription("Moves a saved model configuration to the default module")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    private void commandSetDefaultModule(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedModelRequiresAccess @Argument("savedmodelname") SavedAttachmentModel savedModel
    ) {
        if (savedModel.getModule().isDefault()) {
            sender.sendMessage(ChatColor.YELLOW + "Model configuration '" + savedModel.getName() + "' is already stored in the default module");
        } else {
            plugin.getSavedAttachmentModels().setModuleNameOfModel(savedModel.getName(), null);
            sender.sendMessage(ChatColor.GREEN + "Model configuration '" + savedModel.getName() + "' is now stored in the default module!");
        }
    }

    @Command("config <savedmodelname> module <newmodulename>")
    @CommandDescription("Moves a saved model configuration to a new or existing module")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    private void commandSetModule(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedModelRequiresAccess  @Argument("savedmodelname") SavedAttachmentModel savedModel,
            final @Argument(value="newmodulename", suggestions="savedmodelmodules") String newModuleName
    ) {
        if (newModuleName.isEmpty()) {
            commandSetDefaultModule(sender, plugin, savedModel);
        } else if (newModuleName.equals(savedModel.getModule().getName())) {
            sender.sendMessage(ChatColor.YELLOW + "Model configuration '" + savedModel.getName() + "' is already stored in module '" + newModuleName + "'");
        } else {
            plugin.getSavedAttachmentModels().setModuleNameOfModel(savedModel.getName(), newModuleName);
            sender.sendMessage(ChatColor.GREEN + "Model configuration '" + savedModel.getName() + "' is now stored in module '" + newModuleName + "'!");
        }
    }

    @Command("config <savedmodelname> export|share|paste|upload")
    @CommandDescription("Exports the saved model configuration configuration to a hastebin server")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_EXPORT)
    private void commandExport(
            final CommandSender sender,
            final @Argument("savedmodelname") SavedAttachmentModel savedModel
    ) {
        ConfigurationNode exportedConfig = savedModel.getConfig().clone();
        exportedConfig.remove("claims");
        Commands.exportModel(sender, savedModel.getName(), exportedConfig);
    }

    @Command("config <savedmodelname> rename|changename|move <newsavedmodelname>")
    @CommandDescription("Renames a saved model configuration")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_RENAME)
    private void commandRename(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedModelRequiresAccess @Argument("savedmodelname") SavedAttachmentModel savedModel,
            final @Quoted @Argument("newsavedmodelname") String newSavedModelName,
            final @Flag("force") boolean force
    ) {
        if (savedModel.getName().equals(newSavedModelName)) {
            sender.sendMessage(ChatColor.RED + "The new name is the same as the current name");
            return;
        }

        // Handle permissions when overwriting an existing model
        if (!checkSavePermissionsOverwrite(plugin, sender, newSavedModelName, force)) {
            return;
        }

        String oldName = savedModel.getName();
        plugin.getSavedAttachmentModels().rename(oldName, newSavedModelName);
        sender.sendMessage(ChatColor.YELLOW + "saved model configuration '" + ChatColor.WHITE + oldName +
                ChatColor.YELLOW + "' has been renamed to '" + ChatColor.WHITE + newSavedModelName +
                ChatColor.YELLOW + "'!");
    }

    @Command("config <savedmodelname> copy|clone <targetsavedmodelname>")
    @CommandDescription("Copies an existing saved model configuration and saves it as the target saved model configuration name")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_COPY)
    private void commandCopy(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Argument("savedmodelname") SavedAttachmentModel savedModel,
            final @Quoted @Argument(value="targetsavedmodelname", suggestions="savedmodelname") String targetSavedModelName,
            final @Flag("force") boolean force
    ) {
        if (savedModel.getName().equals(targetSavedModelName)) {
            sender.sendMessage(ChatColor.RED + "The target name is the same as the source name");
            return;
        }

        // Handle permissions when overwriting an existing model
        if (!checkSavePermissionsOverwrite(plugin, sender, targetSavedModelName, force)) {
            return;
        }

        try {
            plugin.getSavedAttachmentModels().setConfigAsPlayer(
                    targetSavedModelName, savedModel.getConfig().clone(), sender);
        } catch (IllegalNameException e) {
            Localization.COMMAND_MODEL_CONFIG_INVALID_NAME.message(sender, targetSavedModelName);
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "saved model configuration '" + ChatColor.WHITE + savedModel.getName() +
                ChatColor.YELLOW + "' copied and saved as '" + ChatColor.WHITE + targetSavedModelName +
                ChatColor.YELLOW + "'!");
    }

    @Command("config <savedmodelname> delete|remove")
    @CommandDescription("Deletes a saved model configuration permanently")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_DELETE)
    private void commandDelete(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedModelRequiresAccess @Argument("savedmodelname") SavedAttachmentModel savedModel,
            final @Flag("force") boolean force
    ) {
        String name = savedModel.getName();
        if (plugin.getSavedAttachmentModels().remove(name)) {
            sender.sendMessage(ChatColor.YELLOW + "saved model configuration '" + ChatColor.WHITE + name +
                    ChatColor.YELLOW + "' has been deleted!");
        } else {
            sender.sendMessage(ChatColor.RED + "saved model configuration '" + ChatColor.WHITE + name +
                    ChatColor.RED + "' cannot be removed! (read-only)");
            sender.sendMessage(ChatColor.RED + "You can only override these properties by saving a new configuration");
        }
    }

    @Command("config <savedmodelname> claim")
    @CommandDescription("Claims a saved model configuration so that the player has exclusive access")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_CLAIM)
    private void commandClaimSelf(
            final Player sender,
            final @SavedModelRequiresAccess @Argument("savedmodelname") SavedAttachmentModel savedModel
    ) {
        // Retrieve current list of claims
        Set<SavedClaim> oldClaims = savedModel.getClaims();

        // Create claim using sender
        SavedClaim selfClaim = new SavedClaim(sender);
        if (oldClaims.contains(selfClaim)) {
            sender.sendMessage(ChatColor.RED + "You have already claimed this saved model configuration!");
        } else {
            Set<SavedClaim> newClaims = new HashSet<>(oldClaims);
            newClaims.add(selfClaim);

            // Update
            updateClaimList(sender, savedModel, oldClaims, newClaims);
        }
    }

    @Command("config <savedmodelname> claim add <player>")
    @CommandDescription("Adds a claim to a saved model configuration so that the added player has exclusive access")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_CLAIM)
    private void commandClaimAdd(
            final CommandSender sender,
            final @SavedModelRequiresAccess @Argument("savedmodelname") SavedAttachmentModel savedModel,
            final @Quoted @Argument(value="player", suggestions="playername") String player, //TODO: Support multiple players WITH flags
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedClaim> oldClaims = savedModel.getClaims();

        // Parse input into player claims, remove elements that are already in the claim list
        Set<SavedClaim> newClaims = new HashSet<>(oldClaims);
        for (SavedClaim addedClaim : SavedClaim.parseClaims(oldClaims, new String[] {player})) {
            if (!newClaims.add(addedClaim)) {
                sender.sendMessage(ChatColor.RED + "- Player " + addedClaim.description() + " was already on the claim list!");
            }
        }

        // Update
        updateClaimList(sender, savedModel, oldClaims, newClaims);
    }

    @Command("config <savedmodelname> claim remove <player>")
    @CommandDescription("Removes a claim from a saved model configuration so that the player no longer has exclusive access")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_CLAIM)
    private void commandClaimRemove(
            final CommandSender sender,
            final @SavedModelRequiresAccess @Argument("savedmodelname") SavedAttachmentModel savedModel,
            final @Quoted @Argument(value="player", suggestions="playername") String player, //TODO: Support multiple players WITH flags
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedClaim> oldClaims = savedModel.getClaims();

        // Parse input into player claims, remove elements that are already in the claim list
        Set<SavedClaim> newClaims = new HashSet<>(oldClaims);
        for (SavedClaim removedClaim : SavedClaim.parseClaims(oldClaims, new String[] {player})) {
            if (!newClaims.remove(removedClaim)) {
                sender.sendMessage(ChatColor.RED + "- Player " + removedClaim.description() + " was not on the claim list");
            }
        }

        // Update
        updateClaimList(sender, savedModel, oldClaims, newClaims);
    }

    @Command("config <savedmodelname> claim clear")
    @CommandDescription("Clears all the claims set for the saved model configuration, allowing anyone to access it")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_CLAIM)
    private void commandClaimClear(
            final CommandSender sender,
            final @SavedModelRequiresAccess @Argument("savedmodelname") SavedAttachmentModel savedModel,
            final @Flag("force") boolean force
    ) {
        // Retrieve current list of claims
        Set<SavedClaim> oldClaims = savedModel.getClaims();

        // Update
        updateClaimList(sender, savedModel, oldClaims, Collections.emptySet());
    }

    @Command("config <savedmodelname> import <url>")
    @CommandDescription("Imports a saved model configuration from an online hastebin server by url")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_IMPORT)
    private void commandImport(
            final CommandSender sender,
            final TrainCarts plugin,
            final @SavedModelRequiresAccess @SavedModelImplicitlyCreated @Argument(value="savedmodelname") SavedAttachmentModel savedModel,
            final @Greedy @FlagYielding @Argument(value="url", description="The URL to a Hastebin-hosted paste to download from") String url,
            final @Flag("force") boolean force
    ) {
        Commands.importModel(plugin, sender, url, config -> {
            // Retrieve previous model configuration
            boolean isNewModel = savedModel.isEmpty();

            // Update configuration
            try {
                plugin.getSavedAttachmentModels().setConfigAsPlayer(savedModel.getName(), config, sender);
            } catch (IllegalNameException e) {
                // Should never happen because of pre-validation, but hey!
                Localization.COMMAND_MODEL_CONFIG_INVALID_NAME.message(sender, savedModel.getName());
                return;
            }
            if (isNewModel) {
                sender.sendMessage(ChatColor.GREEN + "The model configuration was imported and saved as " + savedModel.getName());
            } else {
                sender.sendMessage(ChatColor.GREEN + "The model configuration was imported and saved as " + savedModel.getName() + ", a previous model was overwritten");
            }
        });
    }

    @Command("config <savedmodelname> edit")
    @CommandDescription("Switches the attachment editor to edit this model configuration")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_IMPORT)
    private void commandEdit(
            final TrainCartsPlayer player,
            final TrainCarts plugin,
            final @SavedModelRequiresAccess @SavedModelImplicitlyCreated @Argument(value="savedmodelname") SavedAttachmentModel savedModel,
            final @Flag("force") boolean force
    ) {
        // If creating a new model configuration, initialize a default configuration for it
        boolean isNewModel = savedModel.isEmpty();

        // Ensure saved model is created in the store
        try {
            plugin.getSavedAttachmentModels().setDefaultConfigIfMissing(savedModel.getName(), player.getOnlinePlayer());
        } catch (IllegalNameException e) {
            // Should never happen because of pre-validation, but hey!
            Localization.COMMAND_MODEL_CONFIG_INVALID_NAME.message(player, savedModel.getName());
            return;
        }

        player.editModel(savedModel);
        if (isNewModel) {
            Localization.COMMAND_MODEL_CONFIG_EDIT_NEW.message(player, savedModel.getName());
        } else {
            Localization.COMMAND_MODEL_CONFIG_EDIT_EXISTING.message(player, savedModel.getName());
        }
    }

    @Command("config list")
    @CommandDescription("Lists all the model configurations that exist on the server that a player can modify")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    private void commandShowInfo(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Flag(value="all", description="Show all model configurations on this server, not just those owned by the player") boolean showAll,
            final @Flag(value="module", suggestions="savedmodelmodules", description="Selects a module to list the saved model configurations of") String moduleName
    ) {
        SavedAttachmentModelStore module = plugin.getSavedAttachmentModels();
        MessageBuilder builder = new MessageBuilder();
        builder.newLine();
        if (moduleName != null) {
            module = module.getModule(moduleName);
            if (module == null) {
                sender.sendMessage(ChatColor.RED + "Module '" + moduleName + "' does not exist");
                return;
            }

            builder.blue("The following saved model configurations are stored in module '" + moduleName + "':");
        } else {
            builder.yellow("The following saved model configurations are available:");
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

    @Command("config list modules")
    @CommandDescription("Lists all modules in which saved model configurations are saved")
    @CommandRequiresPermission(Permission.COMMAND_MODEL_CONFIG_LIST)
    private void commandListModules(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        MessageBuilder builder = new MessageBuilder();
        builder.newLine();
        builder.blue("The following modules are available:");
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        for (String moduleName : plugin.getSavedAttachmentModels().getModuleNames()) {
            builder.aqua(moduleName);
        }
        builder.send(sender);
    }

    private static void updateClaimList(CommandSender sender, SavedAttachmentModel savedModel,
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
        savedModel.setClaims(newClaims);

        // Display new values
        MessageBuilder builder = new MessageBuilder();
        builder.newLine();
        if (newClaims.size() > 1) {
            builder.newLine();
        }
        builder.yellow("saved model configuration '").white(savedModel.getName()).yellow("' is now claimed by: ");
        SavedClaim.buildClaimList(builder, newClaims);
        builder.send(sender);
    }

    @CommandRequiresPermission(Permission.COMMAND_MODEL_SEARCH)
    @Command("search")
    @CommandDescription("Shows a dialog with all resource pack item models that are available")
    private void commandSearchModels(
            final TrainCarts plugin,
            final TrainCartsPlayer player
    ) {
        if (plugin.getModelListing().isEmpty()) {
            player.sendMessage(ChatColor.RED + "The currently configured resource pack does not have any custom item models");
        } else {
            plugin.getModelListing().buildDialog(player.getOnlinePlayer())
                    .asCreativeMenu()
                    .setCompactingEnabled(player.getModelSearchCompactFolders())
                    .show();
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_MODEL_SEARCH)
    @Command("search-option compacting")
    @CommandDescription("Toggles whether compacting of folders is enabled for the model search dialog")
    private void commandToggleSearchModels(
            final TrainCarts plugin,
            final TrainCartsPlayer player
    ) {
        commandSearchModels(plugin, player, !player.getModelSearchCompactFolders());
    }

    @CommandRequiresPermission(Permission.COMMAND_MODEL_SEARCH)
    @Command("search-option compacting <enabled>")
    @CommandDescription("Sets whether compacting of folders is enabled for the model search dialog")
    private void commandSearchModels(
            final TrainCarts plugin,
            final TrainCartsPlayer player,
            final @Argument("enabled") boolean enabled
    ) {
        player.setModelSearchCompactFolders(enabled);
        player.sendMessage(ChatColor.YELLOW + "Compacting of models in the search dialog: " + Localization.boolStr(enabled));
    }

    @CommandRequiresPermission(Permission.COMMAND_MODEL_SEARCH)
    @Command("search <query>")
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

    /**
     * Checks whether a model configuration can be saved, and prompts the player
     * to overwrite an existing model when force is false.
     *
     * @param plugin
     * @param sender
     * @param modelName
     * @param force
     * @return True if saving to this model name is allowed
     */
    public static boolean checkSavePermissionsOverwrite(TrainCarts plugin, CommandSender sender, String modelName, boolean force) {
        // Verify name isn't invalid
        {
            TrainNameFormat.VerifyResult verify = TrainNameFormat.verify(modelName);
            if (verify != TrainNameFormat.VerifyResult.OK) {
                verify.getModelMessage().message(sender, modelName);
                return false;
            }
        }

        // If the saved model doesn't even exist yet, always allow
        if (!plugin.getSavedAttachmentModels().containsModel(modelName)) {
            return true;
        }

        boolean isFromOtherPlayer = false;
        if (!plugin.getSavedAttachmentModels().hasPermission(sender, modelName)) {
            // Check that the player has global editing permission
            if (!Permission.COMMAND_MODEL_CONFIG_GLOBAL.has(sender)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to overwrite saved model configuration " + modelName);
                return false;
            }


            isFromOtherPlayer = true;
        }

        if (!force) {
            if (isFromOtherPlayer) {
                sender.sendMessage(ChatColor.RED + "The saved model configuration '" + modelName + "' already exists, and it is not yours!");
            } else {
                sender.sendMessage(ChatColor.RED + "The saved model configuration '" + modelName + "' already exists!");
            }
            sender.sendMessage(ChatColor.RED + "/train model config " + modelName + " info  -  View saved model details");
            sender.sendMessage(ChatColor.RED + "If you are sure you want to overwrite it, pass --force");
            return false;
        }

        return true;
    }

    public boolean checkAccess(CommandSender sender, SavedAttachmentModel savedModel, boolean force) {
        if (Permission.COMMAND_MODEL_CONFIG_GLOBAL.has(sender)) {
            if (force) {
                return true;
            } else {
                Localization.COMMAND_MODEL_CONFIG_FORCE.message(sender, savedModel.getName());
                return false;
            }
        } else {
            if (force) {
                Localization.COMMAND_MODEL_CONFIG_GLOBAL_NOPERM.message(sender);
            } else {
                Localization.COMMAND_MODEL_CONFIG_CLAIMED.message(sender, savedModel.getName());
            }
            return false;
        }
    }

    /**
     * Parser for SavedAttachmentModel
     */
    private static class SavedAttachmentModelParser implements ArgumentParser<CommandSender, SavedAttachmentModel>, BlockingSuggestionProvider.Strings<CommandSender> {
        private final boolean mustHaveAccess;
        private final boolean implicitlyCreated;

        public SavedAttachmentModelParser(boolean mustHaveAccess, boolean implicitlyCreated) {
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
        public @NonNull ArgumentParseResult<@NonNull SavedAttachmentModel> parse(@NonNull CommandContext<@NonNull CommandSender> commandContext, @NonNull CommandInput commandInput) {
            final TrainCarts plugin = commandContext.inject(TrainCarts.class).get();
            final String input = commandInput.lastRemainingToken();

            // Verify not an invalid name that will brick YAML
            TrainNameFormat.VerifyResult verify = TrainNameFormat.verify(input);
            if (verify != TrainNameFormat.VerifyResult.OK) {
                return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                        verify.getModelMessage(), input));
            }

            commandInput.readString();

            return ArgumentParseResult.success(plugin.getSavedAttachmentModels().getModelOrNone(input));
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
                filtered = plugin.getSavedAttachmentModels().getNames();
            } else {
                filtered = plugin.getSavedAttachmentModels().getNames().stream()
                        .filter(s -> s.startsWith(input)).collect(Collectors.toList());
            }

            List<String> claimed = filtered.stream().filter(name -> {
                return plugin.getSavedAttachmentModels().hasPermission(commandContext.sender(), name);
            }).collect(Collectors.toList());

            if (claimed.isEmpty()) {
                return filtered;
            } else {
                return claimed;
            }
        }
    }
}
