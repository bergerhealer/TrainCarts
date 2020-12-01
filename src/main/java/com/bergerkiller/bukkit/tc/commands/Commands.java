package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.SavedTrainRequiresAccess;
import com.bergerkiller.bukkit.tc.commands.cloud.CloudHandler;
import com.bergerkiller.bukkit.tc.commands.parsers.SavedTrainPropertiesParser;
import com.bergerkiller.bukkit.tc.exception.command.InvalidClaimPlayerNameException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainSelectedException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainStorageChestItemException;
import com.bergerkiller.bukkit.tc.exception.command.SelectedTrainNotOwnedException;
import com.bergerkiller.bukkit.tc.pathfinding.PathConnection;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.SavedTrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.services.types.ConsumerService;

import java.util.ArrayList;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands {
    private final CloudHandler cloud = new CloudHandler();

    // Command handlers
    private final CartCommands commands_cart = new CartCommands();
    private final TrainCommands commands_train = new TrainCommands();
    private final GlobalCommands commands_train_global = new GlobalCommands();
    private final RouteCommands commands_train_route = new RouteCommands();
    private final TicketCommands commands_train_ticket = new TicketCommands();
    private final SavedTrainCommands commands_savedtrain = new SavedTrainCommands();

    public void enable(TrainCarts plugin) {
        cloud.enable(plugin);

        // Localization
        cloud.captionFromLocalization(Localization.class);

        // Plugin instance
        cloud.inject(TrainCarts.class, plugin);

        // Handle train not found exception
        cloud.injector(CartProperties.class, (context, annotations) -> {
            if (!(context.getSender() instanceof Player)) {
                throw new NoTrainSelectedException();
            }
            Player player = (Player) context.getSender();
            CartProperties properties = CartPropertiesStore.getEditing(player);
            if (properties == null) {
                throw new NoTrainSelectedException();
            }
            if (!properties.hasOwnership(player)) {
                throw new SelectedTrainNotOwnedException();
            }
            return properties;
        });
        cloud.injector(TrainProperties.class, (context, annotations) -> {
            if (!(context.getSender() instanceof Player)) {
                throw new NoTrainSelectedException();
            }
            Player player = (Player) context.getSender();
            CartProperties properties = CartPropertiesStore.getEditing(player);
            if (properties == null) {
                throw new NoTrainSelectedException();
            }
            TrainProperties trainProperties = properties.getTrainProperties();
            if (!trainProperties.hasOwnership(player)) {
                throw new SelectedTrainNotOwnedException();
            }
            return trainProperties;
        });

        // Token specified when a command requires write access to a saved train
        cloud.annotationParameter(SavedTrainRequiresAccess.class, SavedTrainRequiresAccess.PARAM, Boolean.TRUE);

        /*
        cloud.preprocessAnnotation(SavedTrainRequiresAccess.class, (context, queue) -> {
            Map<String, Object> flags = SafeField.get(context.flags(), "flagValues", Map.class);
            System.out.println("FLAGS=" + String.join(", ", flags.keySet()));
            return ArgumentParseResult.success(true);
        });
        */

        cloud.postProcess(context -> {
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
            if (!commands_savedtrain.checkAccess(sender, savedTrain, force)) {
                ConsumerService.interrupt();
            }
        });

        cloud.parse(SavedTrainProperties.class, (parameters) -> {
            //parameters.get(parameter, defaultValue)
            boolean access = parameters.get(SavedTrainRequiresAccess.PARAM, Boolean.FALSE);
            return new SavedTrainPropertiesParser(plugin, access);
        });

        cloud.handleMessage(NoPermissionException.class, Localization.COMMAND_NOPERM.getName());
        cloud.handleMessage(NoTrainSelectedException.class, Localization.EDIT_NOSELECT.getName());
        cloud.handleMessage(SelectedTrainNotOwnedException.class, Localization.EDIT_NOTOWNED.getName());
        cloud.handleMessage(NoTrainStorageChestItemException.class, Localization.CHEST_NOITEM.getName());

        cloud.handle(InvalidClaimPlayerNameException.class, (sender, exception) -> {
            Localization.COMMAND_SAVEDTRAIN_CLAIM_INVALID.message(sender, exception.getArgument());
        });

        // Register provider for saved train module names
        cloud.suggest("savedtrainmodules", (context, input) -> {
            return new ArrayList<String>(plugin.getSavedTrains().getModuleNames());
        });

        // Register provider for train names a player can edit
        cloud.suggest("trainnames", (context, input) -> {
            final CommandSender sender = context.getSender();
            return TrainProperties.getAll().stream()
                .filter(p -> !(sender instanceof Player) || p.hasOwnership((Player) sender))
                .map(TrainProperties::getTrainName)
                .collect(Collectors.toList());
        });

        // Register all the commands
        cloud.annotations(commands_cart);
        cloud.annotations(commands_train);
        cloud.annotations(commands_train_global);
        cloud.annotations(commands_train_route);
        cloud.annotations(commands_train_ticket);
        cloud.annotations(commands_savedtrain);

        cloud.annotations(this);
    }

    @CommandMethod("train")
    @CommandDescription("Displays the TrainCarts plugin about message, with version information")
    private void commandShowAbout(final CommandSender sender) {
        Localization.COMMAND_ABOUT.message(sender, TrainCarts.plugin.getDebugVersion());
    }

    public static void showPathInfo(CommandSender sender, IProperties prop) {
        MessageBuilder msg = new MessageBuilder();
        msg.yellow("This ").append(prop.getTypeName());
        final String lastName = prop.getDestination();
        IPropertiesHolder holder;
        if (LogicUtil.nullOrEmpty(lastName)) {
            msg.append(" is not trying to reach a destination.");
        } else if ((holder = prop.getHolder()) == null) {
            msg.append(" is not currently loaded.");
        } else {
            msg.append(" is trying to reach ").green(lastName).newLine();

            PathWorld pathWorld = TrainCarts.plugin.getPathProvider().getWorld(holder.getWorld());
            final PathNode first = pathWorld.getNodeByName(prop.getLastPathNode());
            if (first == null) {
                msg.yellow("It has not yet visited a routing node, so no route is available yet.");
            } else {
                PathNode last = pathWorld.getNodeByName(lastName);
                if (last == null) {
                    msg.red("The destination position to reach can not be found!");
                } else {
                    // Calculate the exact route taken from first to last
                    PathConnection[] route = first.findRoute(last);
                    msg.yellow("Route: ");
                    if (route.length == 0) {
                        msg.red(first.getDisplayName() + " /=/ " + last.getDisplayName() + " (not found)");
                    } else {
                        msg.setSeparator(ChatColor.YELLOW, " -> ");
                        for (PathConnection connection : route) {
                            msg.green(connection.destination.getDisplayName());
                        }
                    }
                }
            }
        }
        msg.send(sender);
    }

    public static void info(MessageBuilder message, IProperties prop) {
        // Ownership information
        message.newLine();
        if (!prop.hasOwners() && !prop.hasOwnerPermissions()) {
            message.yellow("Owned by: ").white("Everyone");
        } else {
            if (prop.hasOwners()) {
                message.yellow("Owned by: ").white(StringUtil.combineNames(prop.getOwners()));
            }
            if (prop.hasOwnerPermissions()) {
                message.yellow("Owned by players with the permissions: ");
                message.setSeparator(ChatColor.YELLOW, " / ").setIndent(4);
                for (String ownerPerm : prop.getOwnerPermissions()) {
                    message.white(ownerPerm);
                }
                message.clearSeparator().setIndent(0);
            }
        }

        // Tags and other information
        message.newLine().yellow("Tags: ").white((prop.hasTags() ? StringUtil.combineNames(prop.getTags()) : "None"));
        if (prop.hasDestination()) {
            message.newLine().yellow("This minecart will attempt to reach: ").white(prop.getDestination());
        }
        message.newLine().yellow("Players entering trains: ").white(prop.getPlayersEnter() ? "Allowed" : "Denied");
        message.newLine().yellow("Can be exited by players: ").white(prop.getPlayersExit());
        BlockLocation loc = prop.getLocation();
        if (loc != null) {
            message.newLine().yellow("Current location: ").white("[", loc.x, "/", loc.y, "/", loc.z, "] in world ", loc.world);
        }
    }
}
