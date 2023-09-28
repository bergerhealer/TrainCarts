package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.commands.argument.DirectionOrFormattedSpeed;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.exception.command.NoPermissionForPropertyException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.SaveLockOrientationMode;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlockChanger;
import com.bergerkiller.bukkit.tc.storage.OfflineGroup;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.FormattedSpeed;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.specifier.Quoted;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TrainCommands {

    @CommandTargetTrain
    @CommandMethod("train info|i")
    @CommandDescription("Displays the properties of the train")
    private void commandInfo(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MessageBuilder message = new MessageBuilder();

        if (sender instanceof Player && !properties.isOwner((Player) sender)) {
            if (!properties.hasOwners()) {
                message.yellow("Note: This train is not owned, claim it using /train claim!");
                message.newLine().newLine();
            }
        }

        StandardProperties.TRAIN_NAME_FORMAT.appendNameInfo(message, properties, "Train name: ");
        StandardProperties.SLOWDOWN.appendSlowdownInfo(message.newLine(), properties);
        StandardProperties.COLLISION.appendCollisionInfo(message.newLine(), properties);

        message.newLine().yellow("Keep nearby chunks loaded: ").white(properties.isKeepingChunksLoaded());

        if (properties.getHolder() != null) {
            message.newLine().yellow("Current speed: ");

            double speedUnclipped = properties.getHolder().getAverageForce();
            double speedClipped = Math.min(speedUnclipped, properties.getSpeedLimit());
            double speedMomentum = (speedUnclipped - speedClipped);

            message.white(MathUtil.round(speedClipped, 3));
            message.white(" blocks/tick");
            if (speedMomentum > 0.0) {
                message.white(" (+" + MathUtil.round(speedMomentum, 3) + " energy)");
            }
        }

        message.newLine().yellow("Maximum speed: ").white(properties.getSpeedLimit(), " blocks/tick");

        message.newLine().yellow("Realtime physics: ");
        if (properties.hasRealtimePhysics()) {
            message.green("Enabled");
        } else {
            message.red("Disabled");
        }

        // Remaining common info
        Commands.info(message, properties);

        // Loaded message
        MinecartGroup group = properties.getHolder();
        if (group == null) {
            message.newLine().red("This train is unloaded! To keep it loaded, use:");
            message.newLine().yellow("   /train keepchunksloaded true");
        }

        // Derailment information
        if (group != null) {
            for (MinecartMember<?> member : group) {
                Location loc = member.getFirstKnownDerailedPosition();
                if (loc != null) {
                    message.newLine().red("A cart of this train is derailed!");
                    message.newLine().yellow("   It likely happened at x=", loc.getBlockX(),
                            " y=", loc.getBlockY(), " z=", loc.getBlockZ());
                }
            }
        }

        // Send
        message.send(sender);
    }

    @CommandTargetTrain
    @CommandMethod("train status")
    @CommandDescription("Gives a summary about the train's behavior and actions")
    private void commandTrainStatus(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MinecartGroup group = properties.getHolder();
        if (group == null) {
            sender.sendMessage(ChatColor.RED + "The train is not loaded");
        } else {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "---- Status of " + properties.getTrainName() + " ----");;
            for (TrainStatus status : group.getStatusInfo()) {
                status.getChatMessage().sendTo(sender);
            }
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_DESTROY)
    @CommandMethod("train destroy|remove")
    @CommandDescription("Destroys the train, removing all carts")
    private void commandDestroy(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MinecartGroup group = properties.getHolder();
        CompletableFuture<Boolean> future;
        if (group == null) {
            future = OfflineGroupManager.destroyGroupAsync(properties.getTrainName());
        } else {
            group.destroy();
            future = CompletableFuture.completedFuture(Boolean.TRUE);
        }

        future.thenAccept(success -> {
            if (success) {
                sender.sendMessage(ChatColor.YELLOW + "The selected train has been destroyed!");
            } else {
                sender.sendMessage(ChatColor.RED + "The selected train could not be located! Mapping removed.");
            }
        });
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVE_TRAIN)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_EXPORT)
    @CommandMethod("train export|share|paste|upload")
    @CommandDescription("Exports the train configuration to a hastebin server")
    private void commandExport(
            final CommandSender sender,
            final MinecartGroup group
    ) {
        final String name = group.getProperties().getTrainName();

        Commands.exportTrain(sender, name, group.exportConfig());
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_SAVE_TRAIN)
    @CommandMethod("train save <name>")
    @CommandDescription("Saves the train under a name")
    private void commandSave(
            final CommandSender sender,
            final TrainCarts plugin,
            final MinecartGroup group,
            final @Quoted @Argument("name") String name,
            final @Flag(value="force", description="Force saving when the train is claimed by someone else") boolean force,
            final @Flag(value="lockorientation",
                        description="Locks the current forward direction of the train so future saves use it") boolean lockOrientation,
            final @Flag(value="module", description="Module to move the saved train to", suggestions="savedtrainmodules") String module
    ) {
        if (!Commands.checkSavePermissions(plugin, sender, name, force)) {
            return;
        }

        boolean wasContained = plugin.getSavedTrains().getConfig(name) != null;
        try {
            // Saves train configuration. If --lockorientation was specified, enable this mode and overwrite the
            // locked orientation flipped state with the current flipped state. This makes it easier to reverse
            // a train again later, by running the same command again.
            //
            // There's two other SaveLockOrientationMode we aren't using, which is DISABLED and ENABLED.
            // They enable/disable the lock orientation mode respectively. Instead of complicating the /save command
            // I've decided to add a separate setter for this under /savedtrain people can use.
            {
                ConfigurationNode config;
                config = group.saveConfig(lockOrientation ? SaveLockOrientationMode.ENABLED_OVERRIDE
                                                          : SaveLockOrientationMode.AUTOMATIC);

                // Verify the player can actually create this type of train. If inventory cloning is disallowed,
                // this check is important.
                if (!SpawnableGroup.fromConfig(plugin, config).checkSpawnPermissions(sender)) {
                    Localization.COMMAND_SAVE_FORBIDDEN_CONTENTS.message(sender);
                    return;
                }

                // Success!
                plugin.getSavedTrains().setConfig(name, config);
            }

            String moduleString = "";
            if (module != null && !module.isEmpty()) {
                moduleString = " in module " + module;
                plugin.getSavedTrains().setModuleNameOfTrain(name, module);
            }

            if (wasContained) {
                Localization.COMMAND_SAVE_OVERWRITTEN.message(sender, name + moduleString);
            } else {
                Localization.COMMAND_SAVE_NEW.message(sender, name + moduleString);
                if (TCConfig.claimNewSavedTrains && sender instanceof Player) {
                    plugin.getSavedTrains().setClaim(name, (Player) sender);
                }
            }

            if (lockOrientation) {
                Localization.COMMAND_SAVE_LOCK_ORIENTATION.message(sender, name);
            }
        } catch (IllegalNameException ex) {
            sender.sendMessage(ChatColor.RED + "The train could not be saved under this name: " + ex.getMessage());
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_TELEPORT)
    @CommandMethod("train teleport|tp")
    @CommandDescription("Teleports the player to where the train is")
    private void commandTeleport(
            final Player player,
            final TrainProperties properties
    ) {
        // Check this first, as we cannot load the chunks of an unloaded train
        {
            OfflineGroup group = OfflineGroupManager.findGroup(properties.getTrainName());
            if (group != null && !group.world.isLoaded()) {
                player.sendMessage(ChatColor.RED + "Train is on a world that is not loaded");
                return;
            }
        }

        // Try to load the chunks and when the train is loaded in, teleport to it
        properties.restore().thenAccept(success -> {
            MinecartGroup group = properties.getHolder();
            if (!success || group.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Train location could not be found: Train is lost");
            } else {
                Location location = group.head().getEntity().getLocation();
                EntityUtil.teleport(player, location);
                player.sendMessage(ChatColor.YELLOW + "Teleported to train '" + properties.getTrainName() + "'");
            }
        });
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_ENTER)
    @CommandMethod("train enter")
    @CommandDescription("Teleports the player to the train and enters an available seat")
    private void commandEnter(
            final Player player,
            final CartProperties cartProperties,
            final TrainProperties trainProperties
    ) {
        if (!trainProperties.isLoaded()) {
            player.sendMessage(ChatColor.RED + "Can not enter the train: it is not loaded");
            return;
        }

        MinecartMember<?> member = (cartProperties == null) ? null : cartProperties.getHolder();
        if (member != null && member.getAvailableSeatCount(player) == 0) {
            member = null;
        }
        if (member == null) {
            for (MinecartMember<?> groupMember : trainProperties.getHolder()) {
                if (groupMember.getAvailableSeatCount(player) > 0) {
                    member = groupMember;
                    break;
                }
            }
        }
        if (member != null) {
            Location entityLoc = member.getEntity().getLocation();
            boolean mustTeleport = (player.getWorld() != entityLoc.getWorld())
                    || (player.getLocation().distance(entityLoc) > 64.0);
            if (!mustTeleport || player.teleport(member.getEntity().getLocation())) {
                if (member.addPassengerForced(player)) {
                    player.sendMessage(ChatColor.GREEN + "You entered a seat of train '" + trainProperties.getTrainName() + "'!");
                } else if (mustTeleport) {
                    player.sendMessage(ChatColor.YELLOW + "Selected cart has no available seat. Teleported to the train instead.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Selected cart has no available seat.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Failed to enter train: teleport was denied");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to enter train: no free seat available");
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_EJECT)
    @CommandMethod("train eject")
    @CommandDescription("Ejects the passengers of all the carts of a train, ignoring the allow player exit property")
    private void commandEject(
            final CommandSender sender,
            final TrainProperties trainProperties,
            final @Flag(value="seat", suggestions="trainSeatAttachments") String seatName
    ) {
        if (!trainProperties.isLoaded()) {
            sender.sendMessage(ChatColor.RED + "Can not eject the train: it is not loaded");
            return;
        }
        MinecartGroup group = trainProperties.getHolder();

        if (seatName != null) {
            // Query seat to eject by name
            List<Attachment> seats = new ArrayList<>();
            for (MinecartMember<?> member : group) {
                seats.addAll(member.getAttachments().getRootAttachment()
                        .getNameLookup().get(seatName, e -> e instanceof CartAttachmentSeat));
            }
            Commands.ejectSeats(sender, seatName, seats);
        } else {
            // All seats of train
            if (group.hasPassenger()) {
                group.eject();
                sender.sendMessage(ChatColor.GREEN + "Selected train ejected!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Selected train has no passengers!");
            }
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_FLIP)
    @CommandMethod("train flip")
    @CommandDescription("Flips the orientation of an entire train 180 degrees")
    private void commandFlip(
            final CommandSender sender,
            final TrainProperties trainProperties,
            final @Flag(value="percart", description="Flip individual carts instead of the entire train") boolean perCart
    ) {
        MinecartGroup group = trainProperties.getHolder();
        if (group == null || group.isUnloaded()) {
            sender.sendMessage(ChatColor.RED + "Can not flip the train: it is not loaded");
            return;
        }

        if (perCart) {
            for (MinecartMember<?> member : group) {
                member.flipOrientation();
            }
            sender.sendMessage(ChatColor.GREEN + "Carts of the selected train flipped!");
        } else {
            group.flipOrientation();
            sender.sendMessage(ChatColor.GREEN + "Selected train flipped!");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_LAUNCH)
    @CommandMethod("train launch")
    @CommandDescription("Launches the train forwards at station launch speed")
    private void commandTrainLaunchNoArg(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        commandTrainLaunch(sender, properties,
                new DirectionOrFormattedSpeed(Direction.FORWARD),
                null, null, null, null);
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_LAUNCH)
    @CommandMethod("train launch <speed_or_direction>")
    @CommandDescription("Launches the train into a direction")
    private void commandTrainLaunch(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("speed_or_direction") DirectionOrFormattedSpeed directionOrSpeed,
            final @Flag(value="direction", aliases="d") Direction directionFlag,
            final @Flag(value="speed", aliases="s") FormattedSpeed speedFlag,
            final @Flag(value="limit", aliases="l") FormattedSpeed speedLimitFlag,
            final @Flag(value="options", aliases="o") String launchOptions
    ) {
        if (!properties.isLoaded()) {
            sender.sendMessage(ChatColor.RED + "Can not launch the train: it is not loaded");
            return;
        }

        MinecartGroup group = properties.getHolder();
        commandCartLaunch(sender, group.head().getProperties(),
                directionOrSpeed,
                directionFlag,
                speedFlag,
                speedLimitFlag,
                launchOptions);
    }

    @CommandRequiresPermission(Permission.COMMAND_LAUNCH)
    @CommandMethod("cart launch")
    @CommandDescription("Launches the cart forwards at station launch speed")
    private void commandCartLaunchNoArg(
            final CommandSender sender,
            final CartProperties properties
    ) {
        commandCartLaunch(sender, properties,
                new DirectionOrFormattedSpeed(Direction.FORWARD),
                null, null, null, null);
    }

    @CommandRequiresPermission(Permission.COMMAND_LAUNCH)
    @CommandMethod("cart launch <speed_or_direction>")
    @CommandDescription("Launches the train into a direction")
    private void commandCartLaunch(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("speed_or_direction") DirectionOrFormattedSpeed directionOrSpeed,
            final @Flag(value="direction", aliases="d") Direction directionFlag,
            final @Flag(value="speed", aliases="s") FormattedSpeed speedFlag,
            final @Flag(value="limit", aliases="l") FormattedSpeed speedLimitFlag,
            final @Flag(value="options", aliases="o") String launchOptions
    ) {
        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            sender.sendMessage(ChatColor.RED + "Can not launch the cart: it is not loaded");
            return;
        }

        // Choose the speed from flag, or the direction/speed argument value, or the default
        // Oh lawd this code, cover your eyes!
        final FormattedSpeed speed = (speedFlag != null) ? speedFlag :
            (directionOrSpeed.hasFormattedSpeed() ?
                    directionOrSpeed.getFormattedSpeed() :
                        FormattedSpeed.of(TCConfig.launchForce));

        // Choose direction from flag, or the direction/speed argument value, or the default
        // Oof.
        final Direction direction = (directionFlag != null) ? directionFlag :
            (directionOrSpeed.hasDirection() ?
                    directionOrSpeed.getDirection() : Direction.FORWARD);

        // Velocity, if relative, add current speed
        double velocity = speed.getValue();
        if (speed.isRelative()) {
            velocity += member.getGroup().getAverageForce();
        }

        // Optional launch configuration (distance / curve / acceleration)
        // TODO: Create parser for LauncherConfig
        LauncherConfig launchConfig = LauncherConfig.createDefault();
        if (launchOptions != null && !launchOptions.isEmpty()) {
            launchConfig = LauncherConfig.parse(launchOptions);
        }

        // Resolve the launch direction into a BlockFace (TODO: Vector?) using the player's orientation
        BlockFace facing = (sender instanceof Player)
                ? Util.vecToFace(Util.getRealEyeLocation(((Player) sender)).getDirection(), false).getOppositeFace()
                        : BlockFace.UP;
        BlockFace directionFace = direction.getDirection(facing, member.getDirectionTo());

        // Now we have all the pieces put together, actually launch the train
        properties.getGroup().getActions().clear();
        if (speedLimitFlag != null) {
            double newSpeedLimit = speedLimitFlag.getValue();
            if (speedLimitFlag.isRelative()) {
                newSpeedLimit += member.getGroup().getProperties().getSpeedLimit();
            }
            member.getActions().addActionLaunch(directionFace, launchConfig, velocity, newSpeedLimit);
        } else {
            member.getActions().addActionLaunch(directionFace, launchConfig, velocity);
        }

        // Display a message. Yay!
        MessageBuilder msg = new MessageBuilder();
        msg.green("Launching the train ").yellow(direction.name().toLowerCase(Locale.ENGLISH));
        msg.green(" to a speed of ").yellow(formatNumber(velocity));
        if (launchConfig.hasDistance()) {
            msg.green(" over the course of ").yellow(formatNumber(launchConfig.getDistance())).green(" blocks");
        } else if (launchConfig.hasDuration()) {
            msg.green(" over a period of ").yellow(formatNumber(launchConfig.getDuration())).green(" ticks");
        } else if (launchConfig.hasAcceleration()) {
            msg.green(" at an acceleration of ").yellow(formatNumber(launchConfig.getAcceleration())).green(" b/t\u00B2");
        }
        msg.send(sender);
    }

    private static String formatNumber(double value) {
        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(value);
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_ANIMATE)
    @CommandMethod("train animate <animation_name>")
    @CommandDescription("Plays an animation for the entire train")
    private void commandAnimate(
            final CommandSender sender,
            final TrainProperties properties,
            final @Quoted @Argument(value="animation_name", suggestions="trainAnimationName", description="Name of the animation to play") String animationName,
            final @Flag(value="speed", aliases="s", description="Speed of the animation, 1.0 is default") Double speed,
            final @Flag(value="delay", aliases="d", description="Delay of the animation, 0.0 is default") Double delay,
            final @Flag(value="loop", aliases="l", description="Loop the animation") boolean setLooping,
            final @Flag(value="noloop", description="Disable looping the animation") boolean setNotLooping,
            final @Flag(value="reset", aliases="r", description="Reset the animation to the beginning") boolean setReset,
            final @Flag(value="queue", aliases="q", description="Play the animation once previous animations have finished") boolean setQueued,
            final @Flag(value="scene", suggestions="trainAnimationScene", aliases="m", description="Sets the scene marker name of the animation to play") String sceneMarker,
            final @Flag(value="scene_begin", suggestions="trainAnimationScene", description="Sets the scene marker name from which to start playing") String sceneMarkerBegin,
            final @Flag(value="scene_end", suggestions="trainAnimationScene", description="Sets the scene marker name at which to stop playing (inclusive)") String sceneMarkerEnd
    ) {
        if (!properties.isLoaded()) {
            sender.sendMessage(ChatColor.RED + "Can not animate the train: it is not loaded");
            return;
        }

        AnimationOptions opt = new AnimationOptions();
        opt.setName(animationName);
        if (speed != null) opt.setSpeed(speed);
        if (delay != null) opt.setDelay(delay);
        if (setReset) opt.setReset(true);
        if (setQueued) opt.setQueue(true);
        if (setLooping) opt.setLooped(true);
        if (setNotLooping) opt.setLooped(false);

        if (sceneMarker != null) {
            opt.setScene(sceneMarker);
        }
        if (sceneMarkerBegin != null) {
            opt.setScene(sceneMarkerBegin, opt.getSceneEnd());
        }
        if (sceneMarkerEnd != null) {
            opt.setScene(opt.getSceneBegin(), sceneMarkerEnd);
        }

        if (properties.getHolder().playNamedAnimation(opt)) {
            sender.sendMessage(opt.getCommandSuccessMessage());
        } else {
            sender.sendMessage(opt.getCommandFailureMessage());
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_CHANGEBLOCK)
    @CommandMethod("train displayedblock clear")
    @CommandDescription("Clears the displayed block in Minecart carts of the train, making it empty")
    private void commandClearDisplayedBlock(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MinecartGroup members = properties.getHolder();
        if (members == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            for (MinecartMember<?> member : members) {
                member.getEntity().setBlock(Material.AIR);
            }
            sender.sendMessage(ChatColor.YELLOW + "The selected train has its displayed blocks cleared!");
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_CHANGEBLOCK)
    @CommandMethod("train displayedblock type <blocks>")
    @CommandDescription("Sets the displayed block in the Minecart carts of the train")
    private void commandChangeDisplayedBlock(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("blocks") @Greedy String blockNames
    ) {
        MinecartGroup members = properties.getHolder();
        if (members == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            SignActionBlockChanger.setBlocks(members, blockNames, SignActionBlockChanger.BLOCK_OFFSET_NONE);
            sender.sendMessage(ChatColor.YELLOW + "The selected train has its displayed blocks updated!");
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_CHANGEBLOCK)
    @CommandMethod("train displayedblock offset reset")
    @CommandDescription("Resets the height offset at which blocks are displayed in Minecarts of a train to the defaults")
    private void commandResetDisplayedBlockOffset(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MinecartGroup members = properties.getHolder();
        if (members == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            for (MinecartMember<?> member : members) {
                member.getEntity().setBlockOffset(Util.getDefaultDisplayedBlockOffset());
            }
            sender.sendMessage(ChatColor.YELLOW + "The selected train has its block offset reset!");
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_CHANGEBLOCK)
    @CommandMethod("train displayedblock offset <offset>")
    @CommandDescription("Sets the height offset at which blocks are displayed in Minecarts of a train")
    private void commandSetDisplayedBlockOffset(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("offset") int offset
    ) {
        MinecartGroup members = properties.getHolder();
        if (members == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            for (MinecartMember<?> member : members) {
                member.getEntity().setBlockOffset(offset);
            }
            sender.sendMessage(ChatColor.YELLOW + "The selected train has its displayed block offset updated!");
        }
    }

    @CommandTargetTrain
    @CommandMethod("train <property> <value>")
    @CommandDescription("Updates the value of a property of a train by name")
    private void commandCart(
              final CommandSender sender,
              final TrainProperties properties,
              final @Argument("property") String propertyName,
              final @Quoted @Argument("value") String value
    ) {
        PropertyParseResult<Object> parseResult = IPropertyRegistry.instance().parseAndSet(
                properties, propertyName, value,
                (result) -> {
                    if (!result.hasPermission(sender)) {
                        throw new NoPermissionForPropertyException(result.getName());
                    }
                });

        if (parseResult.isSuccessful()) {
            sender.sendMessage(ChatColor.GREEN + "Property has been updated!");
        } else {
            sender.sendMessage(parseResult.getMessage());

            if (parseResult.getReason() == PropertyParseResult.Reason.PROPERTY_NOT_FOUND) {
                help(new MessageBuilder()).send(sender);
            }
        }
    }

    public static MessageBuilder help(MessageBuilder builder) {
        builder.green("Available commands: ").yellow("/train ").red("[");
        builder.setSeparator(ChatColor.WHITE, "/").setIndent(10);
        builder.red("info").red("linking").red("keepchunksloaded").red("claim").red("addowners").red("setowners");
        builder.red("addtags").red("settags").red("destination").red("destroy").red("public").red("private");
        builder.red("pickup").red("break").red("default").red("rename").red("speedlimit").red("setcollide").red("slowdown");
        builder.red("mobcollision").red("animalcollision").red("monstercollision").red("npccollision");
        builder.red("passivecollision").red("neutralcollision").red("hostilecollision").red("tameablecollision");
        builder.red("utilitycollision").red("bosscollision").red("jockeycollision").red("petcollision").red("killer_bunnycollision");
        return builder.red("pushplayers").red("pushmobs").red("pushmisc").setSeparator(null).red("]");
    }
}
