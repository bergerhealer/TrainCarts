package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.commands.cloud.CloudHandler;
import com.bergerkiller.bukkit.tc.commands.parsers.LocalizedParserException;
import com.bergerkiller.bukkit.tc.commands.parsers.SpeedParser;
import com.bergerkiller.bukkit.tc.commands.parsers.TrainTargetingFlags;
import com.bergerkiller.bukkit.tc.exception.command.InvalidClaimPlayerNameException;
import com.bergerkiller.bukkit.tc.exception.command.NoPermissionForAnyPropertiesException;
import com.bergerkiller.bukkit.tc.exception.command.NoPermissionForPropertyException;
import com.bergerkiller.bukkit.tc.exception.command.NoTicketSelectedException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainNearbyException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainSelectedException;
import com.bergerkiller.bukkit.tc.exception.command.NoTrainStorageChestItemException;
import com.bergerkiller.bukkit.tc.exception.command.SelectedTrainNotOwnedException;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.mountiplex.MountiplexUtil;

import cloud.commandframework.Command;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.arguments.parser.StandardParameters;
import cloud.commandframework.meta.CommandMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands {
    private final CloudHandler cloud = new CloudHandler();

    // Command handlers
    private final CartCommands commands_cart = new CartCommands();
    private final TrainCommands commands_train = new TrainCommands();
    private final GlobalCommands commands_train_global = new GlobalCommands();
    private final TicketCommands commands_train_ticket = new TicketCommands();
    private final SavedTrainCommands commands_savedtrain = new SavedTrainCommands();

    public CloudHandler getHandler() {
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
                (perm, builder) -> builder.permission(perm.value().getName()));

        // Plugin instance
        cloud.inject(TrainCarts.class, plugin);

        // Target a cart or train using added flags at the end of the command
        cloud.getParser().registerBuilderModifier(CommandTargetTrain.class, TrainTargetingFlags.INSTANCE);

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

        cloud.parse(SpeedParser.NAME, (parameters) -> {
            boolean greedy = parameters.get(StandardParameters.GREEDY, false);
            return new SpeedParser(greedy);
        });

        cloud.handleMessage(NoPermissionException.class, Localization.COMMAND_NOPERM.getName());
        cloud.handleMessage(NoTrainSelectedException.class, Localization.EDIT_NOSELECT.getName());
        cloud.handleMessage(SelectedTrainNotOwnedException.class, Localization.EDIT_NOTOWNED.getName());
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

        // Register provider for train names a player can edit
        cloud.suggest("trainnames", (context, input) -> {
            final CommandSender sender = context.getSender();
            return TrainProperties.getAll().stream()
                .filter(p -> !(sender instanceof Player) || p.hasOwnership((Player) sender))
                .map(TrainProperties::getTrainName)
                .collect(Collectors.toList());
        });

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

        // Register all the commands
        cloud.annotations(commands_cart);
        cloud.annotations(commands_train);
        cloud.annotations(commands_train_global);
        cloud.annotations(commands_train_ticket);
        cloud.annotations(commands_savedtrain);

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
    private void commandShowAbout(final CommandSender sender) {
        Localization.COMMAND_ABOUT.message(sender, TrainCarts.plugin.getDebugVersion());
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
        message.newLine().yellow("Can be exited by players: ").white(prop.getPlayersExit());
        BlockLocation loc = prop.getLocation();
        if (loc != null) {
            message.newLine().yellow("Current location: ").white("[", loc.x, "/", loc.y, "/", loc.z, "] in world ", loc.world);
        }
    }
}
