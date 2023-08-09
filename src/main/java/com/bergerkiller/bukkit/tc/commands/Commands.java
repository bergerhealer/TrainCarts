package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.cloud.CloudSimpleHandler;
import com.bergerkiller.bukkit.common.config.BasicConfiguration;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.Hastebin.DownloadResult;
import com.bergerkiller.bukkit.common.Hastebin.UploadResult;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;
import com.bergerkiller.bukkit.tc.chest.TrainChestCommands;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresMultiplePermissions;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.commands.argument.DirectionOrFormattedSpeed;
import com.bergerkiller.bukkit.tc.commands.parsers.AccelerationParser;
import com.bergerkiller.bukkit.tc.commands.parsers.DirectionOrFormattedSpeedParser;
import com.bergerkiller.bukkit.tc.commands.parsers.DirectionParser;
import com.bergerkiller.bukkit.tc.commands.parsers.LocalizedParserException;
import com.bergerkiller.bukkit.tc.commands.parsers.TrainNameFormatParser;
import com.bergerkiller.bukkit.tc.commands.parsers.FormattedSpeedParser;
import com.bergerkiller.bukkit.tc.commands.parsers.TrainTargetingFlags;
import com.bergerkiller.bukkit.tc.commands.suggestions.AnimationNameSuggestionProvider;
import com.bergerkiller.bukkit.tc.commands.suggestions.AnimationSceneSuggestionProvider;
import com.bergerkiller.bukkit.tc.commands.suggestions.TrainListFilterSuggestionProvider;
import com.bergerkiller.bukkit.tc.commands.suggestions.TrainNameSuggestionProvider;
import com.bergerkiller.bukkit.tc.commands.suggestions.TrainSpawnPatternSuggestionProvider;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.global.TrainCartsPlayer;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.debug.DebugCommands;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.exception.command.CommandOnlyForPlayersException;
import com.bergerkiller.bukkit.tc.exception.command.InvalidClaimPlayerNameException;
import com.bergerkiller.bukkit.tc.exception.command.NoPermissionForAnyPropertiesException;
import com.bergerkiller.bukkit.tc.exception.command.NoPermissionForPropertyException;
import com.bergerkiller.bukkit.tc.exception.command.NoTicketSelectedException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainNearbyException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainSelectedException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainStorageChestItemException;
import com.bergerkiller.bukkit.tc.exception.command.SelectedTrainNotLoadedException;
import com.bergerkiller.bukkit.tc.exception.command.SelectedTrainNotOwnedException;
import com.bergerkiller.bukkit.tc.locator.TrainLocatorCommands;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import com.bergerkiller.mountiplex.MountiplexUtil;

import cloud.commandframework.Command;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.arguments.parser.StandardParameters;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.permission.AndPermission;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands {
    private final CloudSimpleHandler cloud = new CloudSimpleHandler();

    // Command handlers
    private final CartCommands commands_cart = new CartCommands();
    private final TrainCommands commands_train = new TrainCommands();
    private final GlobalCommands commands_train_global = new GlobalCommands();
    private final DebugCommands commands_train_debug = new DebugCommands();
    private final TrainChestCommands commands_train_chest = new TrainChestCommands();
    private final TrainLocatorCommands commands_train_locator = new TrainLocatorCommands();
    private final TicketCommands commands_train_ticket = new TicketCommands();
    private final SavedTrainCommands commands_savedtrain = new SavedTrainCommands();
    private final ModelStoreCommands commands_modelstore = new ModelStoreCommands();

    public CloudSimpleHandler getHandler() {
        return cloud;
    }

    public void enable(TrainCarts plugin) {
        cloud.enable(plugin);

        // Override syntax formatter to hide excess flags for targeting a train or cart
        cloud.getManager().setCommandSyntaxFormatter(new TCSyntaxFormatter<CommandSender>());

        // Localization
        cloud.captionFromLocalization(Localization.class);

        // TrainCarts Permissions
        cloud.getParser().registerBuilderModifier(CommandRequiresPermission.class,
                (perm, builder) -> builder.permission(perm.value().cloudPermission()));
        cloud.getParser().registerBuilderModifier(CommandRequiresMultiplePermissions.class, (multi, builder) -> {
            final List<cloud.commandframework.permission.CommandPermission> perms = Stream.of(multi.value())
                    .map(CommandRequiresPermission::value)
                    .map(Permission::cloudPermission)
                    .collect(Collectors.toList());
            if (perms.isEmpty()) {
                return builder;
            } else if (perms.size() == 1) {
                return builder.permission(perms.get(0));
            } else {
                return builder.permission(AndPermission.of(perms));
            }
        });

        // Target a cart or train using added flags at the end of the command
        cloud.getParser().registerBuilderModifier(CommandTargetTrain.class, TrainTargetingFlags.INSTANCE);

        // Convert Player -> TrainCarts Player
        cloud.injector(TrainCartsPlayer.class, (context, annotations) -> {
            if (context.getSender() instanceof Player) {
                return plugin.getPlayer((Player) context.getSender());
            } else {
                throw new CommandOnlyForPlayersException();
            }
        });

        // Handle train not found exception
        cloud.injector(CartProperties.class, (context, annotations) -> {
            final CartProperties cartProperties = TrainTargetingFlags.INSTANCE.findCartProperties(context);

            // Check ownership permissions
            if (context.getSender() instanceof Player) {
                Player p = (Player) context.getSender();
                if (!cartProperties.hasOwnership(p)) {
                    throw new SelectedTrainNotOwnedException();
                }
            }

            return cartProperties;
        });
        cloud.injector(TrainProperties.class, (context, annotations) -> {
            final CartProperties cartProperties = TrainTargetingFlags.INSTANCE.findCartProperties(context);
            final TrainProperties trainProperties = cartProperties.getTrainProperties();

            // Check ownership permissions
            if (context.getSender() instanceof Player) {
                Player p = (Player) context.getSender();
                if (!trainProperties.hasOwnership(p)) {
                    throw new SelectedTrainNotOwnedException();
                }
            }

            return trainProperties;
        });

        // Getting the loaded MinecartMember from potentially not loaded CartProperties
        cloud.injector(MinecartMember.class, (context, annotations) -> {
            CartProperties properties = context.inject(CartProperties.class).get();
            MinecartMember<?> member = properties.getHolder();
            if (member == null || member.isUnloaded()) {
                throw new SelectedTrainNotLoadedException();
            }
            return member;
        });

        // Getting the loaded MinecartGroup from potentially not loaded TrainProperties
        cloud.injector(MinecartGroup.class, (context, annotations) -> {
            TrainProperties properties = context.inject(TrainProperties.class).get();
            MinecartGroup group = properties.getHolder();
            if (group == null) {
                throw new SelectedTrainNotLoadedException();
            }
            return group;
        });

        cloud.parse(FormattedSpeed.class, (parameters) -> {
            boolean greedy = parameters.get(StandardParameters.GREEDY, false);
            return new FormattedSpeedParser(greedy);
        });

        cloud.parse(AccelerationParser.NAME, (parameters) -> {
            boolean greedy = parameters.get(StandardParameters.GREEDY, false);
            return new AccelerationParser(greedy);
        });

        cloud.parse(Direction.class, p -> new DirectionParser());
        cloud.parse(TrainNameFormat.class, p -> new TrainNameFormatParser());
        cloud.parse(DirectionOrFormattedSpeed.class, p -> new DirectionOrFormattedSpeedParser());

        cloud.handleMessage(NoPermissionException.class, Localization.COMMAND_NOPERM.getName());
        cloud.handleMessage(NoTrainSelectedException.class, Localization.EDIT_NOSELECT.getName());
        cloud.handleMessage(SelectedTrainNotOwnedException.class, Localization.EDIT_NOTOWNED.getName());
        cloud.handleMessage(SelectedTrainNotLoadedException.class, Localization.EDIT_NOTLOADED.getName());
        cloud.handleMessage(NoTrainNearbyException.class, Localization.COMMAND_CART_NOT_FOUND_NEARBY.getName());
        cloud.handleMessage(NoTrainStorageChestItemException.class, Localization.CHEST_NOITEM.getName());
        cloud.handleMessage(NoTicketSelectedException.class, Localization.COMMAND_TICKET_NOTEDITING.getName());
        cloud.handleMessage(NoPermissionForAnyPropertiesException.class, Localization.PROPERTY_NOPERM_ANY.getName());
        cloud.handle(NoPermissionForPropertyException.class, (sender, ex) -> {
            Localization.PROPERTY_NOPERM.message(sender, ex.getName());
        });
        cloud.handle(LocalizedParserException.class, (sender, ex) -> {
            sender.sendMessage(ex.getMessage());
        });

        cloud.handle(InvalidClaimPlayerNameException.class, (sender, exception) -> {
            Localization.COMMAND_SAVEDTRAIN_CLAIM_INVALID.message(sender, exception.getArgument());
        });

        cloud.handle(CommandOnlyForPlayersException.class, (sender, exception) -> {
            sender.sendMessage("Only players can execute this command");
        });

        // Provides names of animations stored in trains/carts
        cloud.suggest("cartAnimationName", AnimationNameSuggestionProvider.CART_ANIMATION_NAME);
        cloud.suggest("trainAnimationName", AnimationNameSuggestionProvider.TRAIN_ANIMATION_NAME);
        cloud.suggest("cartAnimationScene", AnimationSceneSuggestionProvider.CART_ANIMATION_SCENE);
        cloud.suggest("trainAnimationScene", AnimationSceneSuggestionProvider.TRAIN_ANIMATION_SCENE);

        // Register provider for train names a player can edit
        cloud.suggest("trainnames", new TrainNameSuggestionProvider());
        cloud.suggest("trainlistfilter", new TrainListFilterSuggestionProvider());

        // Register provider for spawn patterns
        cloud.suggest("trainspawnpattern", new TrainSpawnPatternSuggestionProvider());

        // Register provider for destination names
        cloud.suggest("destinations", (context, input) -> {
            Stream<PathWorld> worlds;
            if (context.getSender() instanceof Player) {
                // Only of one world the player is on
                World world = ((Player) context.getSender()).getWorld();
                worlds = MountiplexUtil.toStream(plugin.getPathProvider().getWorld(world));
            } else {
                // Combine all worlds' unique destinations
                worlds = plugin.getPathProvider().getWorlds().stream();
            }
            return worlds.flatMap(world -> world.getNodes().stream())
                         .flatMap(node -> node.getNames().stream())
                         .distinct()
                         .collect(Collectors.toList());
        });

        // Suggests a player name of a player currently online, or @p
        cloud.suggest("targetplayer", (context, input) -> {
            List<String> result = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toCollection(ArrayList::new));
            result.add("@p");
            return result;
        });

        // Register all the commands
        cloud.annotations(commands_cart);
        cloud.annotations(commands_train);
        cloud.annotations(commands_train_global);
        cloud.annotations(commands_train_debug);
        cloud.annotations(commands_train_chest);
        cloud.annotations(commands_train_locator);
        cloud.annotations(commands_train_ticket);
        cloud.annotations(commands_savedtrain);
        cloud.annotations(commands_modelstore);

        cloud.annotations(this);

        // Help menus
        cloud.helpCommand(Collections.singletonList("cart"), "Shows help for commands that target carts");
        cloud.helpCommand(Collections.singletonList("train"), "Shows help for global commands and commands that target trains");
        cloud.helpCommand(Collections.singletonList("savedtrain"), "Shows help for commands that manage saved trains");
        cloud.helpCommand(Arrays.asList("cart", "route"), "Shows help for commands that modify the route set for carts");
        cloud.helpCommand(Arrays.asList("train", "route"), "Shows help for commands that modify the route set for trains");

        // The /train debug and /train debug help command
        {
            final Command<CommandSender> debugHelpCommand = cloud.helpCommand(
                    Arrays.asList("train", "debug"),
                    "Shows help about the debugging commands",
                    builder -> {
                        return builder.permission(Permission.DEBUG_COMMAND_DEBUG.getName());
                    });

            cloud.getManager().command(Command.<CommandSender>newBuilder("train", CommandMeta.simple().build())
                    .literal("debug")
                    .hidden()
                    .proxies(debugHelpCommand));
        }
    }

    @CommandMethod("train")
    @CommandDescription("Displays the TrainCarts plugin about message, with version information")
    private void commandShowAbout(final TrainCarts plugin, final CommandSender sender) {
        // Build a message showing 'TrainCarts <version>, followed by a clickable link to the wiki
        // with a message (usage). We try to find 'wiki' in the message, and make
        sender.sendMessage(ChatColor.BLUE + "TrainCarts " + plugin.getDebugVersion());

        // Experimental link syntax in localization, may eventually be moved to BKCommonLib if it proves useful elsewhere
        String message = Localization.COMMAND_USAGE.get();
        Pattern urlPattern = Pattern.compile("\\[(.*)\\]\\(([\\w\\/\\.:\\&\\?=]+)\\)");
        Matcher matcher = urlPattern.matcher(message);

        // Build ChatText
        ChatText text = ChatText.empty();
        int currentIndex = 0;
        while (matcher.find()) {
            int startIndex = matcher.start();
            if (startIndex > currentIndex) {
                text.append(message.substring(currentIndex, startIndex));
            }
            text.appendClickableURL(matcher.group(1), matcher.group(2));
            currentIndex = matcher.end();
        }
        if (currentIndex < message.length()) {
            text.append(message.substring(currentIndex));
        }

        if (sender instanceof Player) {
            text.sendTo((Player) sender);
        } else {
            sender.sendMessage(text.getMessage());
        }
    }

    public static void info(MessageBuilder message, IProperties prop) {
        // Ownership information
        message.newLine();
        StandardProperties.OWNERS.addOwnerInfo(message, prop);

        // Tags and other information
        message.newLine().yellow("Tags: ").white((prop.hasTags() ? StringUtil.combineNames(prop.getTags()) : "None"));
        if (prop.hasDestination()) {
            message.newLine().yellow("This minecart will attempt to reach: ").white(prop.getDestination());
        }
        message.newLine().yellow("Players entering trains: ").white(prop.getPlayersEnter() ? "Allowed" : "Denied");
        message.newLine().yellow("Players exiting trains: ").white(prop.getPlayersExit() ? "Allowed" : "Denied");
        BlockLocation loc = prop.getLocation();
        if (loc != null) {
            message.newLine().yellow("Current location: ").white("[", loc.x, "/", loc.y, "/", loc.z, "] in world ", loc.world);
        }
    }

    /**
     * Checks permissions for overwriting a saved train, and handles when the command must be forced
     * to overwrite an existing train.
     *
     * @param plugin
     * @param sender
     * @param trainName
     * @param force
     * @return True if saving to this train name is allowed
     */
    public static boolean checkSavePermissions(TrainCarts plugin, CommandSender sender, String trainName, boolean force) {
        // Verify name isn't invalid
        {
            TrainNameFormat.VerifyResult verify = TrainNameFormat.verify(trainName);
            if (verify != TrainNameFormat.VerifyResult.OK) {
                verify.getMessage().message(sender, trainName);
                return false;
            }
        }

        // Actual checks
        if (!plugin.getSavedTrains().hasPermission(sender, trainName)) {
            // Check that the player has global editing permission
            if (!Permission.COMMAND_SAVEDTRAIN_GLOBAL.has(sender)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to overwrite saved train " + trainName);
                return false;
            }

            // Check that a second argument, 'forced', is specified
            if (!force) {
                sender.sendMessage(ChatColor.RED + "The saved train '" + trainName + "' already exists, but it is not yours!");
                sender.sendMessage(ChatColor.RED + "Here are some options:");
                sender.sendMessage(ChatColor.RED + "/savedtrain " + trainName + " info  -  See who claimed it");
                sender.sendMessage(ChatColor.RED + "/savedtrain " + trainName + " claim  -  Claim it yourself");
                sender.sendMessage(ChatColor.RED + "/train save " + trainName + " --force  -  Force a save and overwrite");
                return false;
            }
        }
        return true;
    }

    /**
     * Same as {@link #checkSavePermissions(TrainCarts, CommandSender, String, boolean)}, but prompts the player
     * to overwrite an existing train when force is false.
     *
     * @param plugin
     * @param sender
     * @param trainName
     * @param force
     * @return True if saving to this train name is allowed
     */
    public static boolean checkSavePermissionsOverwrite(TrainCarts plugin, CommandSender sender, String trainName, boolean force) {
        // Verify name isn't invalid
        {
            TrainNameFormat.VerifyResult verify = TrainNameFormat.verify(trainName);
            if (verify != TrainNameFormat.VerifyResult.OK) {
                verify.getMessage().message(sender, trainName);
                return false;
            }
        }

        // If the saved train doesn't even exist yet, always allow
        if (!plugin.getSavedTrains().containsTrain(trainName)) {
            return true;
        }

        boolean isFromOtherPlayer = false;
        if (!plugin.getSavedTrains().hasPermission(sender, trainName)) {
            // Check that the player has global editing permission
            if (!Permission.COMMAND_SAVEDTRAIN_GLOBAL.has(sender)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to overwrite saved train " + trainName);
                return false;
            }

            isFromOtherPlayer = true;
        }

        if (!force) {
            if (isFromOtherPlayer) {
                sender.sendMessage(ChatColor.RED + "The saved train '" + trainName + "' already exists, and it is not yours!");
            } else {
                sender.sendMessage(ChatColor.RED + "The saved train '" + trainName + "' already exists!");
            }
            sender.sendMessage(ChatColor.RED + "/savedtrain " + trainName + " info  -  View saved train details");
            sender.sendMessage(ChatColor.RED + "If you are sure you want to overwrite it, pass --force");
            return false;
        }

        return true;
    }

    /**
     * Downloads the full YAML configuration of a model configuration from hastebin, and
     * calls the callback with the configuration if successful.
     *
     * @param plugin TrainCarts plugin instance
     * @param sender Command Sender to send a message to when problems occur
     * @param url The URL of the hastebin paste to download
     * @param callback The callback to call when successfully downloaded
     */
    public static void importModel(final TrainCarts plugin, final CommandSender sender, final String url, final Consumer<ConfigurationNode> callback) {
        TCConfig.hastebin.download(url).thenAccept(new Consumer<DownloadResult>() {
            @Override
            public void accept(DownloadResult result) {
                // Check successful
                if (!result.success()) {
                    sender.sendMessage(ChatColor.RED + "Failed to import model: " + result.error());
                    return;
                }

                // Parse the String contents as YAML
                BasicConfiguration config;
                try {
                    config = result.contentYAML();
                } catch (IOException ex) {
                    sender.sendMessage(ChatColor.RED + "Failed to import model configuration because of YAML decode error: " + ex.getMessage());
                    return;
                } catch (Throwable t) {
                    sender.sendMessage(ChatColor.RED + "An error occurred trying to import the model configuration YAML: " + t.getMessage());
                    plugin.getLogger().log(Level.SEVERE, "Import error for " + url, t);
                    return;
                }

                // Callback time!
                callback.accept(config);
            }
        });
    }

    /**
     * Downloads the full YAML configuration of a train from hastebin, and
     * calls the callback with the configuration if successful.
     *
     * @param plugin TrainCarts plugin instance
     * @param sender Command Sender to send a message to when problems occur
     * @param url The URL of the hastebin paste to download
     * @param callback The callback to call when successfully downloaded
     */
    public static void importTrain(final TrainCarts plugin, final CommandSender sender, final String url, final Consumer<ConfigurationNode> callback) {
        TCConfig.hastebin.download(url).thenAccept(new Consumer<DownloadResult>() {
            @Override
            public void accept(DownloadResult result) {
                // Check successful
                if (!result.success()) {
                    Localization.COMMAND_IMPORT_ERROR.message(sender, result.error());
                    return;
                }

                // Parse the String contents as YAML
                BasicConfiguration config;
                try {
                    config = result.contentYAML();
                } catch (IOException ex) {
                    Localization.COMMAND_IMPORT_ERROR.message(sender, "YAML decode error: " + ex.getMessage());
                    return;
                } catch (Throwable t) {
                    Localization.COMMAND_IMPORT_ERROR.message(sender, t.getMessage());
                    plugin.getLogger().log(Level.SEVERE, "Import error for " + url, t);
                    return;
                }

                // Verify the configuration does not include stuff the player has no permission for, such as
                // command minecarts or chest minecarts with (illegal) inventory items
                SpawnableGroup group = SpawnableGroup.fromConfig(plugin, config);
                if (group.getMembers().isEmpty()) {
                    Localization.COMMAND_IMPORT_NO_CARTS.message(sender);
                    return;
                }
                if (!group.checkSpawnPermissions(sender)) {
                    Localization.COMMAND_IMPORT_FORBIDDEN_CONTENTS.message(sender);
                    return;
                }

                // Callback time!
                callback.accept(config);
            }
        });
    }

    /**
     * If a previously imported train configuration includes used models, imports those models as well.
     * Tells the Player these were imported. The input configuration is modified to remove the
     * "usedModels" field after processing.
     *
     * @param plugin TrainCarts plugin instance
     * @param sender Command Sender to send a message to when problems occur
     * @param savedTrainConfig Previously imported train configuration
     * @param doImport Whether to actually do the importing of models (true), or to warn that these
     *                 models were not imported and can be with a flag (false)
     * @param force Whether to force importing models when the sender does not have a claim, but
     *              does have global permission to modify it
     * @see #importTrain(TrainCarts, CommandSender, String, Consumer) 
     */
    public static void importTrainUsedModels(
            final TrainCarts plugin,
            final CommandSender sender,
            final ConfigurationNode savedTrainConfig,
            final boolean doImport,
            final boolean force
    ) {
        ConfigurationNode usedModels = savedTrainConfig.getNodeIfExists("usedModels");
        if (usedModels != null) {
            usedModels.remove();
        } else {
            return;
        }

        // If not actually importing, only show the models that it wants to import
        if (!doImport) {
            String message = usedModels.getKeys().stream()
                    .map(name -> (plugin.getSavedAttachmentModels().containsModel(name)
                                ? ChatColor.GREEN : ChatColor.RED) + name)
                    .collect(Collectors.joining(ChatColor.YELLOW + ", "));
            Localization.COMMAND_IMPORT_MISSING_MODELS.message(sender, message);
            return;
        }

        if (!Permission.COMMAND_MODEL_CONFIG_LIST.has(sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to read or write model configurations");
            return;
        }

        boolean checkPerms = !force || !Permission.COMMAND_MODEL_CONFIG_GLOBAL.has(sender);
        boolean warnedAboutForce = false;
        List<String> imported = new ArrayList<>();
        for (String key : usedModels.getKeys()) {
            SavedAttachmentModel model = plugin.getSavedAttachmentModels().getModel(key);
            if (model != null && checkPerms && !model.hasPermission(sender)) {
                if (force && !warnedAboutForce) {
                    Localization.COMMAND_MODEL_CONFIG_GLOBAL_NOPERM.message(sender);
                    warnedAboutForce = true;
                }
                Localization.COMMAND_MODEL_CONFIG_FORCE.message(sender, key);
                continue;
            }
            try {
                plugin.getSavedAttachmentModels().setConfig(key, usedModels.getNode(key));
                imported.add(key);
            } catch (IllegalNameException e) {
                Localization.COMMAND_MODEL_CONFIG_INVALID_NAME.message(sender, key);
            }
        }
        if (!imported.isEmpty()) {
            Localization.COMMAND_IMPORT_UPDATED_MODELS.message(sender, String.join(", ", imported));
        }
    }

    /**
     * Uploads the full YAML configuration of a model configuration to hastebin, using the
     * configured hastebin server.
     *
     * @param sender Command Sender to send a message to once completed
     * @param name Name of the model exported, part of the completion message
     * @param exportedConfig Configuration to upload
     */
    public static void exportModel(final CommandSender sender, final String name, final ConfigurationNode exportedConfig) {
        TCConfig.hastebin.upload(exportedConfig.toString()).thenAccept(new Consumer<UploadResult>() {
            @Override
            public void accept(UploadResult t) {
                if (t.success()) {
                    sender.sendMessage(ChatColor.GREEN + "Model configuration '" + ChatColor.YELLOW + name +
                            ChatColor.GREEN + "' exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to export model configuration '" + name + "': " + t.error());
                }
            }
        });
    }

    /**
     * Uploads the full YAML configuration of a train to hastebin, using the
     * configured hastebin server.
     *
     * @param sender Command Sender to send a message to once completed
     * @param name Name of the train exported, part of the completion message
     * @param exportedConfig Configuration to upload
     */
    public static void exportTrain(final CommandSender sender, final String name, final ConfigurationNode exportedConfig) {
        TCConfig.hastebin.upload(exportedConfig.toString()).thenAccept(new Consumer<UploadResult>() {
            @Override
            public void accept(UploadResult t) {
                if (t.success()) {
                    sender.sendMessage(ChatColor.GREEN + "Train '" + ChatColor.YELLOW + name +
                            ChatColor.GREEN + "' exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to export train '" + name + "': " + t.error());
                }
            }
        });
    }
}
