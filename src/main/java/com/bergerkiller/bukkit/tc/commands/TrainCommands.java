package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.commands.cloud.ArgumentList;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionMobCategory;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlockChanger;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.specifier.Greedy;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.*;

public class TrainCommands {

    @CommandMethod("train info")
    @CommandDescription("Displays the properties of the train")
    private void commandInfo(
            final Player player,
            final TrainProperties properties
    ) {
        MessageBuilder message = new MessageBuilder();

        if (!properties.isOwner(player)) {
            if (!properties.hasOwners()) {
                message.newLine().yellow("Note: This train is not owned, claim it using /train claim!");
            }
        }
        message.newLine().yellow("Train name: ").white(properties.getTrainName());
        message.newLine().yellow("Keep nearby chunks loaded: ").white(properties.isKeepingChunksLoaded());

        infoSlowDown(message.newLine(), properties);

        // Collision states
        message.newLine().yellow("When colliding this train ");
        if (properties.getCollision().equals(CollisionOptions.CANCEL)) {
            message.red("ignores ").yellow("all entities").red(" and ").yellow("blocks");
        } else if (!properties.getCollision().collidesWithEntities()) {
            message.red("ignores ").yellow("all entities").red(" and ");
            message.red(properties.getCollision().blockMode().getOperationName());
            message.yellow(" blocks");
        } else {
            CollisionOptions collisions = properties.getCollision();
            for (CollisionMobCategory collisionConfigObject : CollisionMobCategory.values()) {
                if (collisions.mobMode(collisionConfigObject) != null) {
                    message.red(collisions.mobMode(collisionConfigObject).getOperationName()).yellow(" " +
                            collisionConfigObject.getFriendlyMobName() + ", ");
                }
            }
            message.red(collisions.blockMode().getOperationName()).yellow(" blocks, ");
            message.red(collisions.playerMode().getOperationName()).yellow(" players, ");
            message.red(collisions.miscMode().getOperationName()).yellow(" misc entities and ");
            message.red(collisions.trainMode().getOperationName()).yellow(" other trains");
        }

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

        // Remaining common info
        Commands.info(message, properties);

        // Loaded message
        if (properties.getHolder() == null) {
            message.newLine().red("This train is unloaded! To keep it loaded, use:");
            message.newLine().yellow("   /train keepchunksloaded true");
        }

        // Send
        message.send(player);
    }

    @CommandMethod("train destroy|remove")
    @CommandDescription("Destroys the train, removing all carts")
    private void commandDestroy(
            final Player player,
            final TrainProperties properties
    ) {
        Permission.COMMAND_DESTROY.handle(player);

        MinecartGroup group = properties.getHolder();
        if (group == null) {
            TrainPropertiesStore.remove(properties.getTrainName());
            OfflineGroupManager.removeGroup(properties.getTrainName());
        } else {
            group.destroy();
        }
        player.sendMessage(ChatColor.YELLOW + "The selected train has been destroyed!");
    }

    @CommandMethod("train save <name>")
    @CommandDescription("Saves the train under a name")
    private void commandSave(
            final Player player,
            final TrainCarts plugin,
            final TrainProperties properties,
            final @Argument("name") String name,
            final @Flag(value="force", description="Force saving when the train is claimed by someone else") boolean force,
            final @Flag(value="module", description="Module to move the saved train to") String module
    ) {
        Permission.COMMAND_SAVE_TRAIN.handle(player);

        MinecartGroup group = properties.getHolder();
        if (group == null) {
            player.sendMessage(ChatColor.YELLOW + "The train you are editing is not loaded and can not be saved");
            return;
        }

        if (!plugin.getSavedTrains().hasPermission(player, name)) {
            // Check that the player has global editing permission
            if (!Permission.COMMAND_SAVEDTRAIN_GLOBAL.has(player)) {
                player.sendMessage(ChatColor.RED + "You do not have permission to overwrite saved train " + name);
                return;
            }

            // Check that a second argument, 'forced', is specified
            if (!force) {
                player.sendMessage(ChatColor.RED + "The saved train '" + name + "' already exists, but it is not yours!");
                player.sendMessage(ChatColor.RED + "Here are some options:");
                player.sendMessage(ChatColor.RED + "/savedtrain " + name + " info  -  See who claimed it");
                player.sendMessage(ChatColor.RED + "/savedtrain " + name + " claim  -  Claim it yourself");
                player.sendMessage(ChatColor.RED + "/train save " + name + " --force  -  Force a save and overwrite");
                return;
            }
        }

        boolean wasContained = TrainCarts.plugin.getSavedTrains().getConfig(name) != null;
        try {
            TrainCarts.plugin.getSavedTrains().saveGroup(name, group);
            String moduleString = "";
            if (module != null && !module.isEmpty()) {
                moduleString = " in module " + module;
                TrainCarts.plugin.getSavedTrains().setModuleNameOfTrain(name, module);
            }

            if (wasContained) {
                player.sendMessage(ChatColor.GREEN + "The train was saved as " + name + moduleString + ", a previous train was overwritten");
            } else {
                player.sendMessage(ChatColor.GREEN + "The train was saved as " + name + moduleString);
                if (TCConfig.claimNewSavedTrains) {
                    TrainCarts.plugin.getSavedTrains().setClaim(name, player);
                }
            }
        } catch (IllegalNameException ex) {
            player.sendMessage(ChatColor.RED + "The train could not be saved under this name: " + ex.getMessage());
        }
    }

    @CommandMethod("train teleport|tp")
    @CommandDescription("Teleports the player to where the train is")
    private void commandTeleport(
            final Player player,
            final TrainProperties properties
    ) {
        Permission.COMMAND_TELEPORT.handle(player);

        if (!properties.restore()) {
            player.sendMessage(ChatColor.RED + "Train location could not be found: Train is lost");
        } else {
            BlockLocation bloc = properties.getLocation();
            World world = bloc.getWorld();
            if (world == null) {
                player.sendMessage(ChatColor.RED + "Train is on a world that is not loaded (" + bloc.world + ")");
            } else {
                EntityUtil.teleport(player, new Location(world, bloc.x + 0.5, bloc.y + 0.5, bloc.z + 0.5));
                player.sendMessage(ChatColor.YELLOW + "Teleported to train '" + properties.getTrainName() + "'");
            }
        }
    }

    @CommandMethod("train enter")
    @CommandDescription("Teleports the player to the train and enters an available seat")
    private void commandEnter(
            final Player player,
            final CartProperties cartProperties,
            final TrainProperties trainProperties
    ) {
        Permission.COMMAND_ENTER.handle(player);

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
            if (player.teleport(member.getEntity().getLocation())) {
                member.addPassengerForced(player);
                player.sendMessage(ChatColor.GREEN + "You entered a seat of train '" + trainProperties.getTrainName() + "'!");
            } else {
                player.sendMessage(ChatColor.RED + "Failed to enter train: teleport was denied");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to enter train: no free seat available");
        }
    }

    @CommandMethod("train launch [options]")
    @CommandDescription("Launches the train into a direction")
    private void commandLaunch(
            final Player player,
            final TrainProperties properties,
            final @Argument("options") String[] options
    ) {
        Permission.COMMAND_LAUNCH.handle(player);

        if (!properties.isLoaded()) {
            player.sendMessage(ChatColor.RED + "Can not launch the train: it is not loaded");
            return;
        }

        // Parse all the arguments specified into launch direction, distance and speed
        double velocity = TCConfig.launchForce;
        LauncherConfig launchConfig = LauncherConfig.createDefault();
        Direction direction = Direction.FORWARD;

        List<String> argsList = new ArrayList<String>((options==null) ?
                Collections.emptyList() : Arrays.asList(options));

        // Go by all arguments and try to parse them as a direction
        // All arguments that fail to parse are considered either velocity or launch config
        for (int i = 0; i < argsList.size(); i++) {
            Direction d = Direction.parse(argsList.get(i));
            if (d != Direction.NONE) {
                direction = d;
                argsList.remove(i);
                break;
            }
        }

        // More than one argument specified, attempt to parse the last argument as a Double
        // This would be the velocity (if it succeeds)
        if (argsList.size() >= 1) {
            String valueStr = argsList.get(argsList.size() - 1);
            double value = Util.parseVelocity(valueStr, Double.NaN);
            if (!Double.isNaN(value)) {
                argsList.remove(argsList.size() - 1);
                velocity = value;

                // If +/- put in front, it's relative to the speed of the cart
                if (valueStr.startsWith("+") || valueStr.startsWith("-")) {
                    velocity += properties.getHolder().getAverageForce();
                }
            }
        }

        // Parse any numbers remaining as the launch config
        if (argsList.size() >= 1) {
            launchConfig = LauncherConfig.parse(argsList.get(0));
        }

        // Resolve the launch direction into a BlockFace (TODO: Vector?) using the player's orientation
        BlockFace facing = Util.vecToFace(player.getEyeLocation().getDirection(), false).getOppositeFace();
        BlockFace directionFace = direction.getDirection(facing);

        // Now we have all the pieces put together, actually launch the train
        properties.getHolder().getActions().clear();
        properties.getHolder().head().getActions().addActionLaunch(directionFace, launchConfig, velocity);

        // Display a message. Yay!
        MessageBuilder msg = new MessageBuilder();
        msg.green("Launching the train ").yellow(direction.name().toLowerCase(Locale.ENGLISH));
        msg.green(" to a speed of ").yellow(velocity);
        if (launchConfig.hasDistance()) {
            msg.green(" over the course of ").yellow(launchConfig.getDistance()).green(" blocks");
        } else if (launchConfig.hasDuration()) {
            msg.green(" over a period of ").yellow(launchConfig.getDuration()).green(" ticks");
        }
        msg.send(player);
    }

    @CommandMethod("train animate [options]")
    @CommandDescription("Plays an animation for the entire train")
    private void commandAnimate(
            final Player player,
            final TrainProperties properties,
            final @Argument("options") String[] options
    ) {
        Permission.COMMAND_ANIMATE.handle(player);

        if (!properties.isLoaded()) {
            player.sendMessage(ChatColor.RED + "Can not animate the train: it is not loaded");
            return;
        }

        AnimationOptions opt = new AnimationOptions();
        opt.loadCommandArgs((options==null) ? StringUtil.EMPTY_ARRAY : options);
        if (properties.getHolder().playNamedAnimation(opt)) {
            player.sendMessage(opt.getCommandSuccessMessage());
        } else {
            player.sendMessage(opt.getCommandFailureMessage());
        }
    }

    @CommandMethod("train <arguments>")
    @CommandDescription("Performs commands that operate on the train currently being edited")
    private void commandTrain(
              final Player player,
              final TrainProperties properties,
              final ArgumentList arguments,
              final @Argument("arguments") @Greedy String unused_arguments
    ) {
        Permission.COMMAND_PROPERTIES.handle(player);
        execute(player, properties, arguments.get(1), arguments.range(2).array());
    }

    public static boolean execute(Player p, TrainProperties prop, String cmd, String[] args) throws NoPermissionException {
        TrainPropertiesStore.markForAutosave();
        if (cmd.equalsIgnoreCase("playerenter")) {
            if (args.length == 1) {
                Permission.COMMAND_PLAYERENTER.handle(p);
                prop.setPlayersEnter(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Players can enter this train: " + ChatColor.WHITE + " " + prop.getPlayersEnter());
        } else if (LogicUtil.containsIgnoreCase(cmd, "playerleave", "playerexit")) {
            if (args.length == 1) {
                Permission.COMMAND_PLAYEREXIT.handle(p);
                prop.setPlayersExit(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Players can exit this train: " + ChatColor.WHITE + " " + prop.getPlayersExit());
        } else if (LogicUtil.containsIgnoreCase(cmd, "sound", "soundenabled")) {
            if (args.length == 1) {
                Permission.COMMAND_SOUND.handle(p);
                prop.setSoundEnabled(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Minecart sound enabled: " + ChatColor.WHITE + prop.isSoundEnabled());
        } else if (LogicUtil.containsIgnoreCase(cmd, "linking", "link")) {
            if (args.length == 1) {
                Permission.COMMAND_SETLINKING.handle(p);
                prop.setLinking(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Can be linked: " + ChatColor.WHITE + (prop.getCollision().trainMode() == CollisionMode.LINK));
        } else if (LogicUtil.containsIgnoreCase(cmd, "playertake", "allowplayertake")) {
            if (args.length == 1) {
                Permission.COMMAND_PLAYERTAKE.handle(p);
                prop.setPlayerTakeable(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Players take Minecart with them: " + ChatColor.WHITE + prop.isPlayerTakeable());
        } else if (cmd.equalsIgnoreCase("keepchunksloaded")) {
            if (args.length == 1) {
                Permission.COMMAND_KEEPCHUNKSLOADED.handle(p);
                prop.setKeepChunksLoaded(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Keep nearby chunks loaded: " + ChatColor.WHITE + prop.isKeepingChunksLoaded());
        } else if (cmd.equalsIgnoreCase("invincible")) {
            if (args.length == 1) {
                Permission.COMMAND_INVINCIBLE.handle(p);
                prop.setInvincible(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Train invincible: " + ChatColor.WHITE + prop.isInvincible());
        } else if (LogicUtil.containsIgnoreCase(cmd, "manualmove", "allowmanual", "manual", "allowmanualmovement")) {
            if (args.length == 1) {
                Permission.COMMAND_MANUALMOVE.handle(p);
                prop.setManualMovementAllowed(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Players can move carts while inside: " + ChatColor.WHITE + prop.isManualMovementAllowed());
        } else if (LogicUtil.containsIgnoreCase(cmd, "mobmanualmove", "allowmobmanual", "mobmanual", "allowmobmanualmovement")) {
            if (args.length == 1) {
                Permission.COMMAND_MANUALMOVE.handle(p);
                prop.setMobManualMovementAllowed(ParseUtil.parseBool(args[0]));
            }
            p.sendMessage(ChatColor.YELLOW + "Mobs can move carts while inside: " + ChatColor.WHITE + prop.isMobManualMovementAllowed());
        } else if (LogicUtil.containsIgnoreCase(cmd, "setownerperm", "setownerpermission", "setownerpermissions")) {
            Permission.COMMAND_SETOWNERS.handle(p);
            prop.clearOwnerPermissions();
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "All owner permissions for this minecart have been cleared!");
            } else {
                int changed = 0;
                for (CartProperties cprop : prop) {
                    if (cprop.hasOwnership(p)) {
                        changed++;
                        cprop.getOwnerPermissions().addAll(Arrays.asList(args));
                    }
                }
                if (changed == 0) {
                    p.sendMessage(ChatColor.RED + "You do not have ownership over any of the carts in the train");
                } else {
                    p.sendMessage(ChatColor.YELLOW + "You set the owner permissions " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " for this minecart");
                    p.sendMessage(ChatColor.YELLOW + "Players that have these permission nodes are considered owners of this Minecart");
                    if (changed < prop.size()) {
                        p.sendMessage(ChatColor.YELLOW + "Some (" + changed + "/" + prop.size() + ") carts have the permission set (lacking ownership)");
                    }
                }
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "addownerperm", "addownerpermission", "addownerpermissions")) {
            Permission.COMMAND_SETOWNERS.handle(p);
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "Please specify the permission nodes to add!");
            } else {
                int changed = 0;
                for (CartProperties cprop : prop) {
                    if (cprop.hasOwnership(p)) {
                        changed++;
                        cprop.getOwnerPermissions().addAll(Arrays.asList(args));
                    }
                }
                if (changed == 0) {
                    p.sendMessage(ChatColor.RED + "You do not have ownership over any of the carts in the train");
                } else {
                    p.sendMessage(ChatColor.YELLOW + "You added the owner permissions " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " to this train");
                    p.sendMessage(ChatColor.YELLOW + "Players that have these permission nodes are considered owners of this train");
                    if (changed < prop.size()) {
                        p.sendMessage(ChatColor.YELLOW + "Some (" + changed + "/" + prop.size() + ") carts have the permission set (lacking ownership)");
                    }
                }
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "claim", "addowner", "setowner", "addowners", "setowners")) {
            Permission.COMMAND_SETOWNERS.handle(p);
            //claim as many carts as possible
            int changed = 0;
            boolean clear = !cmd.equals("addowner") && !cmd.equals("addowners");
            List<String> toadd = new ArrayList<>();
            if (cmd.equals("claim")) {
                toadd.add(p.getName().toLowerCase());
            } else if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "Please specify the player names to make owner!");
                return true;
            } else {
                for (String player : args) {
                    toadd.add(player.toLowerCase());
                }
            }
            for (CartProperties cprop : prop) {
                if (!cprop.hasOwnership(p)) {
                    continue;
                }
                //claim
                if (clear) cprop.clearOwners();
                for (String owner : toadd) {
                    cprop.setOwner(owner);
                }
                changed++;
            }
            if (changed == prop.size()) {
                p.sendMessage(ChatColor.YELLOW + "Owners updated for This entire train!");
            } else if (changed == 1) {
                p.sendMessage(ChatColor.YELLOW + "Owners updated for one train cart your own!");
            } else if (changed > 1) {
                p.sendMessage(ChatColor.YELLOW + "Owners updated for " + changed + " train carts your own!");
            } else {
                p.sendMessage(ChatColor.RED + "You failed to set any owners: you don't own any carts!");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "pushmobs", "pushplayers", "pushmisc")) {
            Permission.COMMAND_PUSHING.handle(p);
            // Parse a new collision mode to set to
            CollisionMode newState = null;
            if (args.length == 1) {
                newState = CollisionMode.fromPushing(ParseUtil.parseBool(args[0]));
            }
            String msg = ChatColor.YELLOW + "Pushes away ";
            if (cmd.equals("pushmobs")) {
                if (newState != null) {
                    prop.setCollisionModeForMobs(newState);
                }
                msg += "mobs: " + ChatColor.WHITE + " " + (newState == CollisionMode.PUSH);
            }
            if (cmd.equals("pushplayers")) {
                if (newState != null) {
                    prop.setCollision(prop.getCollision().setPlayerMode(newState));
                }
                msg += "players: " + ChatColor.WHITE + " " + (prop.getCollision().playerMode() == CollisionMode.PUSH);
            }
            if (cmd.equals("pushmisc")) {
                if (newState != null) {
                    prop.setCollision(prop.getCollision().setMiscMode(newState));
                }
                msg += "misc. entities: " + ChatColor.WHITE + " " + (prop.getCollision().miscMode() == CollisionMode.PUSH);
            }
            CollisionMobCategory result = CollisionMobCategory.findMobType(cmd, "push");
            if (result != null) {
                if (newState != null) {
                    prop.updateCollisionProperties(result.getMobType(), newState);
                }
                msg += result.getFriendlyMobName() + ": " + ChatColor.WHITE + " " + (newState == CollisionMode.PUSH);
            }
            p.sendMessage(msg);
        } else if (LogicUtil.containsIgnoreCase(cmd, "slowdown", "slow", "setslow", "setslowdown")) {
            Permission.COMMAND_SLOWDOWN.handle(p);

            // Get the mode queried, optionally set it if a boolean parameter is specified
            // Legacy true/false/enable/disable still works, too
            SlowdownMode mode = null;
            if (args.length >= 1) {
                if (ParseUtil.isBool(args[0])) {
                    prop.setSlowingDown(ParseUtil.parseBool(args[0]));
                } else {
                    // Parse the mode
                    mode = ParseUtil.parseEnum(SlowdownMode.class, args[0], null);
                    if (mode == null) {
                        p.sendMessage(ChatColor.RED + "Unknown slowdown mode: " + args[0]);
                        return true;
                    }

                    // If specified, set it
                    if (args.length >= 2) {
                        prop.setSlowingDown(mode, ParseUtil.parseBool(args[1]));
                    }
                }
            }

            // Display result that was set
            MessageBuilder message = new MessageBuilder();
            infoSlowDown(message, prop);
            message.send(p);
        } else if (LogicUtil.containsIgnoreCase(cmd, "setcollide", "setcollision", "collision", "collide")) {
            Permission.COMMAND_SETCOLLIDE.handle(p);
            if (args.length == 2) {
                CollisionMode mode = CollisionMode.parse(args[1]);
                if (mode != null) {
                    String typeName = args[0].toLowerCase(Locale.ENGLISH);
                    if (prop.updateCollisionProperties(typeName, mode)) {
                        p.sendMessage(ChatColor.YELLOW + "When colliding this train " + mode.getOperationName() + " " + typeName);
                    } else if (typeName.contains("player")) {
                        prop.setCollision(prop.getCollision().setPlayerMode(mode));
                        p.sendMessage(ChatColor.YELLOW + "When colliding this train " + prop.getCollision().playerMode().getOperationName() + " players");
                    } else if (typeName.contains("misc")) {
                        prop.setCollision(prop.getCollision().setMiscMode(mode));
                        p.sendMessage(ChatColor.YELLOW + "When colliding this train " + prop.getCollision().miscMode().getOperationName() + " misc entities");
                    } else if (typeName.contains("train")) {
                        prop.setCollision(prop.getCollision().setTrainMode(mode));
                        p.sendMessage(ChatColor.YELLOW + "When colliding this train " + prop.getCollision().trainMode().getOperationName() + " other trains");
                    } else if (typeName.contains("block")) {
                        prop.setCollision(prop.getCollision().setBlockMode(mode));
                        p.sendMessage(ChatColor.YELLOW + "When colliding this train " + prop.getCollision().blockMode().getOperationName() + " blocks");
                    } else {
                        p.sendMessage(ChatColor.RED + "Unknown collidable type: " + args[0]);
                        p.sendMessage(ChatColor.YELLOW + "Allowed types: block, mob, player, misc or train");
                    }
                } else {
                    p.sendMessage(ChatColor.RED + "Unknown collision mode: " + args[1]);
                    ArrayList<String> modes = new ArrayList<>();
                    for (CollisionMode cmode : CollisionMode.values()) {
                        modes.add(cmode.toString().toLowerCase());
                    }
                    p.sendMessage(ChatColor.YELLOW + "Allowed modes: " + StringUtil.combineNames(modes));
                }
            } else {
                if (args.length == 1) {
                    if (ParseUtil.isBool(args[0])) {
                        boolean opt = ParseUtil.parseBool(args[0]);
                        prop.setCollision(opt ? CollisionOptions.DEFAULT : CollisionOptions.CANCEL);
                        if (opt) {
                            p.sendMessage(ChatColor.YELLOW + "Collision configuration reset to collide with entities and blocks");
                        } else {
                            p.sendMessage(ChatColor.YELLOW + "Collision configuration reset to disable all collision with entities and blocks");
                        }
                    } else {
                        p.sendMessage(ChatColor.RED + "Unknown mode: " + args[0]);
                    }
                } else {
                    p.sendMessage(ChatColor.YELLOW + "Can collide with other entities: " + ChatColor.WHITE +
                            prop.getCollision().collidesWithEntities());
                }
            }
            prop.tryUpdate();
        } else if (LogicUtil.containsIgnoreCase(cmd, "speedlimit", "maxspeed")) {
            Permission.COMMAND_SETSPEEDLIMIT.handle(p);
            if (args.length == 1) {
                prop.setSpeedLimit(ParseUtil.parseDouble(args[0], 0.4));
            }
            p.sendMessage(ChatColor.YELLOW + "Maximum speed: " + ChatColor.WHITE + prop.getSpeedLimit() + " blocks/tick");
        } else if (LogicUtil.containsIgnoreCase(cmd, "gravity")) {
            Permission.COMMAND_GRAVITY.handle(p);
            if (args.length == 1) {
                prop.setGravity(ParseUtil.parseDouble(args[0], 0.4));
            }
            p.sendMessage(ChatColor.YELLOW + "Gravity: " + ChatColor.WHITE + prop.getGravity() + " X");
        } else if (LogicUtil.containsIgnoreCase(cmd, "requirepoweredminecart", "requirepowered")) {
            Permission.COMMAND_SETPOWERCARTREQ.handle(p);
            if (args.length == 1) {
                prop.requirePoweredMinecart = ParseUtil.parseBool(args[0]);
            }
            p.sendMessage(ChatColor.YELLOW + "Requires powered minecart to stay alive: " + ChatColor.WHITE + prop.requirePoweredMinecart);
        } else if (LogicUtil.containsIgnoreCase(cmd, "rename", "setname", "name")) {
            Permission.COMMAND_RENAME.handle(p);
            if (args.length == 0) {
                p.sendMessage(ChatColor.RED + "You forgot to pass a name along!");
            } else {
                String newname = StringUtil.join(" ", args);
                if (TrainProperties.exists(newname)) {
                    p.sendMessage(ChatColor.RED + "This name is already taken!");
                } else {
                    prop.setTrainName(newname);
                    p.sendMessage(ChatColor.YELLOW + "This train is now called " + ChatColor.WHITE + newname + ChatColor.YELLOW + "!");
                }
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "displayname", "display", "dname", "setdname", "setdisplayname")) {
            Permission.COMMAND_DISPLAYNAME.handle(p);
            if (args.length == 0) {
                p.sendMessage(ChatColor.RED + "You forgot to pass a name along!");
            } else {
                prop.setDisplayName(StringUtil.ampToColor(StringUtil.join(" ", args)));
                p.sendMessage(ChatColor.YELLOW + "The display name on trigger signs is now " + ChatColor.WHITE + prop.getDisplayName() + ChatColor.YELLOW + "!");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "addtags", "addtag")) {
            Permission.COMMAND_SETTAGS.handle(p);
            if (args.length == 0) {
                p.sendMessage(ChatColor.RED + "You need to give at least one tag to add!");
            } else {
                prop.addTags(args);
                p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " as tags for all minecarts in this train!");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "settags", "settag", "tags", "tag")) {
            Permission.COMMAND_SETTAGS.handle(p);
            prop.clearTags();
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "All tags of all minecarts in this train have been cleared!");
            } else {
                prop.addTags(args);
                p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " as tags for all minecarts in this train!");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "dest", "destination")) {
            Permission.COMMAND_SETDESTINATION.handle(p);
            if (args.length == 0) {
                prop.clearDestination();
                p.sendMessage(ChatColor.YELLOW + "The destination for all minecarts in this train has been cleared!");
            } else {
                String dest = StringUtil.join(" ", args);
                prop.setDestination(dest);
                p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + dest + ChatColor.YELLOW + " as destination for all the minecarts in this train!");
            }
        } else if (cmd.equalsIgnoreCase("public")) {
            Permission.COMMAND_SETPUBLIC.handle(p);
            boolean pub;
            pub = args.length == 0 || ParseUtil.parseBool(args[0]);
            prop.setPublic(pub);
            p.sendMessage(ChatColor.YELLOW + "The selected train can be used by everyone: " + ChatColor.WHITE + pub);
        } else if (LogicUtil.containsIgnoreCase(cmd, "private", "locked", "lock")) {
            Permission.COMMAND_SETPUBLIC.handle(p);
            boolean pub;
            pub = args.length != 0 && !ParseUtil.parseBool(args[0]);
            prop.setPublic(pub);
            p.sendMessage(ChatColor.YELLOW + "The selected train can only be used by the respective owners: " + ChatColor.WHITE + !pub);
        } else if (cmd.equalsIgnoreCase("pickup")) {
            Permission.COMMAND_PICKUP.handle(p);
            boolean mode = true;
            if (args.length > 0) mode = ParseUtil.parseBool(args[0]);
            prop.setPickup(mode);
            p.sendMessage(ChatColor.YELLOW + "The selected train picks up nearby items: " + ChatColor.WHITE + mode);
        } else if (LogicUtil.containsIgnoreCase(cmd, "default", "def")) {
            Permission.COMMAND_DEFAULT.handle(p);
            if (args.length == 0) {
                p.sendMessage(ChatColor.RED + "Not enough args!");
                return true;
            } else {
                prop.setDefault(args[0]);
                p.sendMessage(ChatColor.GREEN + "Train properties has been re-set to the defaults named '" + args[0] + "'!");
            }
        } else if (cmd.equalsIgnoreCase("break")) {
            Permission.COMMAND_BREAKBLOCK.handle(p);
            if (args.length == 0) {
                Set<Material> types = new HashSet<>();
                for (CartProperties cprop : prop) types.addAll(cprop.getBlockBreakTypes());
                p.sendMessage(ChatColor.YELLOW + "This train breaks: " + ChatColor.WHITE + StringUtil.combineNames(types));
            } else {
                if (ParseUtil.isBool(args[0]) && !ParseUtil.parseBool(args[0])) {
                    for (CartProperties cprop : prop) cprop.clearBlockBreakTypes();
                    p.sendMessage(ChatColor.YELLOW + "Train block break types have been cleared!");
                } else {
                    boolean asBreak = true;
                    boolean lastIsBool = ParseUtil.isBool(args[args.length - 1]);
                    if (lastIsBool) asBreak = ParseUtil.parseBool(args[args.length - 1]);
                    int count = lastIsBool ? args.length - 1 : args.length;
                    Set<Material> mats = new HashSet<>();
                    for (int i = 0; i < count; i++) {
                        Material mat = ParseUtil.parseMaterial(args[i], null);
                        if (mat != null) {
                            if (p.hasPermission("train.command.break.admin") || TrainCarts.canBreak(mat)) {
                                mats.add(mat);
                            } else {
                                p.sendMessage(ChatColor.RED + "You are not allowed to make this train break '" + mat.toString() + "'!");
                            }
                        }
                    }
                    if (mats.isEmpty()) {
                        p.sendMessage(ChatColor.RED + "Failed to find possible and allowed block types in the list given.");
                        return true;
                    }
                    if (asBreak) {
                        for (CartProperties cprop : prop) {
                            cprop.getBlockBreakTypes().addAll(mats);
                        }
                        p.sendMessage(ChatColor.YELLOW + "This cart can now (also) break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
                    } else {
                        for (CartProperties cprop : prop) {
                            cprop.getBlockBreakTypes().removeAll(mats);
                        }
                        p.sendMessage(ChatColor.YELLOW + "This cart can no longer break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
                    }
                }
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "setblock", "setblocks", "changeblock", "changeblocks", "blockchanger")) {
            Permission.COMMAND_CHANGEBLOCK.handle(p);
            MinecartGroup members = prop.getHolder();
            if (members == null) {
                p.sendMessage(ChatColor.RED + "The selected train is unloaded: we can not change it at this time!");
            } else if (args.length == 0) {
                for (MinecartMember<?> member : members) {
                    member.getEntity().setBlock(Material.AIR);
                }
                p.sendMessage(ChatColor.YELLOW + "The selected train has its displayed blocks cleared!");
            } else {
                SignActionBlockChanger.setBlocks(members, StringUtil.join(" ", args), SignActionBlockChanger.BLOCK_OFFSET_NONE);
                p.sendMessage(ChatColor.YELLOW + "The selected train has its displayed blocks updated!");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "setblockoffset", "changeblockoffset", "blockoffset")) {
            Permission.COMMAND_CHANGEBLOCK.handle(p);
            MinecartGroup members = prop.getHolder();
            if (members == null) {
                p.sendMessage(ChatColor.RED + "The selected train is unloaded: we can not change it at this time!");
            } else if (args.length == 0) {
                for (MinecartMember<?> member : members) {
                    member.getEntity().setBlockOffset(9);
                }
                p.sendMessage(ChatColor.YELLOW + "The selected train has its block offset reset!");
            } else {
                int offset = ParseUtil.parseInt(args[0], 9);
                for (MinecartMember<?> member : members) {
                    member.getEntity().setBlockOffset(offset);
                }
                p.sendMessage(ChatColor.YELLOW + "The selected train has its displayed block offset updated!");
            }
        } else if (args.length >= 1 && Util.parseProperties(prop, cmd, String.join(" ", args))) {
            p.sendMessage(ChatColor.GREEN + "Property has been updated!");
            return true;
        } else {
            if (!cmd.equalsIgnoreCase("help") && !cmd.equalsIgnoreCase("?")) {
                p.sendMessage(ChatColor.RED + "Unknown cart command: '" + cmd + "'!");
            }
            help(new MessageBuilder()).send(p);
            return true;
        }
        prop.tryUpdate();
        return true;
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

    private static void infoSlowDown(MessageBuilder message, TrainProperties prop) {
        message.yellow("Slow down over time: ");
        if (prop.isSlowingDownAll()) {
            message.green("Yes (All)");
        } else if (prop.isSlowingDownNone()) {
            message.red("No (None)");
        } else {
            message.setSeparator(", ");
            for (SlowdownMode mode : SlowdownMode.values()) {
                if (prop.isSlowingDown(mode)) {
                    message.green(mode.getKey() + "[Yes]");
                } else {
                    message.red(mode.getKey() + "[No]");
                }
            }
            message.clearSeparator();
        }
    }
}
