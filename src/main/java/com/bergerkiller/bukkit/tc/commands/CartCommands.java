package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
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
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.exception.command.NoPermissionForPropertyException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlockChanger;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.storage.OfflineMember;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.specifier.Quoted;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CartCommands {

    @CommandTargetTrain
    @CommandMethod("cart info|i")
    @CommandDescription("Displays the properties of the cart")
    private void commandInfo(
            final CommandSender sender,
            final CartProperties properties
    ) {
        info(sender, properties);
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_DESTROY)
    @CommandMethod("cart destroy|remove")
    @CommandDescription("Destroys the single cart that is selected")
    private void commandDestroy(
            final CommandSender sender,
            final CartProperties properties
    ) {
        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            CartPropertiesStore.remove(properties.getUUID());
            OfflineGroupManager.removeMember(properties.getUUID());
        } else {
            member.onDie(true);
        }
        sender.sendMessage(ChatColor.YELLOW + "The selected cart has been destroyed!");
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVE_TRAIN)
    @CommandRequiresPermission(Permission.COMMAND_SAVEDTRAIN_EXPORT)
    @CommandMethod("cart export|share|paste|upload")
    @CommandDescription("Exports the selected cart's train configuration to a hastebin server")
    private void commandExport(
            final CommandSender sender,
            final MinecartMember<?> member
    ) {
        final String name = member.getGroup().getProperties().getTrainName();

        ConfigurationNode exportedConfig = saveMemberConfig(member);
        exportedConfig.remove("claims");
        Commands.exportTrain(sender, name, exportedConfig);
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_SAVE_TRAIN)
    @CommandMethod("cart save <name>")
    @CommandDescription("Saves the selected cart as a train under a name")
    private void commandSave(
            final TrainCarts plugin,
            final CommandSender sender,
            final MinecartMember<?> member,
            final @Quoted @Argument("name") String name,
            final @Flag(value="force", description="Force saving when the train is claimed by someone else") boolean force,
            final @Flag(value="module", description="Module to move the saved train to") String module
    ) {
        if (!Commands.checkSavePermissions(plugin, sender, name, force)) {
            return;
        }

        boolean wasContained = plugin.getSavedTrains().getConfig(name) != null;
        try {
            {
                ConfigurationNode memberConfig = saveMemberConfig(member);

                // Verify the player can actually create this type of cart. If inventory cloning is disallowed,
                // this check is important.
                if (!SpawnableGroup.fromConfig(plugin, memberConfig).checkSpawnPermissions(sender)) {
                    Localization.COMMAND_SAVE_FORBIDDEN_CONTENTS.message(sender);
                    return;
                }

                plugin.getSavedTrains().setConfig(name, memberConfig);
            }

            String moduleString = "";
            if (module != null && !module.isEmpty()) {
                moduleString = " in module " + module;
                plugin.getSavedTrains().setModuleNameOfTrain(name, module);
            }

            if (wasContained) {
                sender.sendMessage(ChatColor.GREEN + "The cart was saved as train " + name + moduleString + ", a previous train was overwritten");
            } else {
                sender.sendMessage(ChatColor.GREEN + "The cart was saved as train " + name + moduleString);
                if (TCConfig.claimNewSavedTrains && sender instanceof Player) {
                    plugin.getSavedTrains().setClaim(name, (Player) sender);
                }
            }
        } catch (IllegalNameException ex) {
            sender.sendMessage(ChatColor.RED + "The cart could not be saved under this name: " + ex.getMessage());
        }
    }

    private ConfigurationNode saveMemberConfig(MinecartMember<?> member) {
        ConfigurationNode exportedConfig = member.getGroup().getProperties().saveToConfig().clone();
        exportedConfig.remove("carts");
        exportedConfig.setNodeList("carts", Collections.singletonList(member.saveConfig()));
        return exportedConfig;
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_TELEPORT)
    @CommandMethod("cart teleport|tp")
    @CommandDescription("Teleports the player to where the cart is")
    private void commandTeleport(
            final Player player,
            final CartProperties properties
    ) {
        // Check this first, as we cannot load the chunks of an unloaded train
        {
            OfflineMember member = OfflineGroupManager.findMember(properties.getTrainProperties().getTrainName(), properties.getUUID());
            if (member != null && !member.group.world.isLoaded()) {
                player.sendMessage(ChatColor.RED + "Cart is on a world that is not loaded");
                return;
            }
        }

        // Try to load the chunks and when the train is loaded in, teleport to it
        properties.restore().thenAccept(success -> {
            if (!success) {
                player.sendMessage(ChatColor.RED + "Cart location could not be found: Train is lost");
            } else {
                MinecartMember<?> member = properties.getHolder();
                Location location = member.getEntity().getLocation();
                EntityUtil.teleport(player, location);
                player.sendMessage(ChatColor.YELLOW + "Teleported to cart of '" +
                        properties.getTrainProperties().getTrainName() + "'");
            }
        });
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_ANIMATE)
    @CommandMethod("cart animate <animation_name>")
    @CommandDescription("Plays an animation for the cart")
    private void commandAnimate(
            final CommandSender sender,
            final CartProperties properties,
            final @Quoted @Argument(value="animation_name", suggestions="cartAnimationName", description="Name of the animation to play") String animationName,
            final @Flag(value="speed", aliases="s", description="Speed of the animation, 1.0 is default") Double speed,
            final @Flag(value="delay", aliases="d", description="Delay of the animation, 0.0 is default") Double delay,
            final @Flag(value="loop", aliases="l", description="Loop the animation") boolean setLooping,
            final @Flag(value="noloop", description="Disable looping the animation") boolean setNotLooping,
            final @Flag(value="reset", aliases="r", description="Reset the animation to the beginning") boolean setReset,
            final @Flag(value="queue", aliases="q", description="Play the animation once previous animations have finished") boolean setQueued,
            final @Flag(value="scene", suggestions="cartAnimationScene", aliases="m", description="Sets the scene marker name of the animation to play") String sceneMarker,
            final @Flag(value="scene_begin", suggestions="cartAnimationScene", description="Sets the scene marker name from which to start playing") String sceneMarkerBegin,
            final @Flag(value="scene_end", suggestions="cartAnimationScene", description="Sets the scene marker name at which to stop playing (inclusive)") String sceneMarkerEnd
    ) {
        if (!properties.hasHolder()) {
            sender.sendMessage(ChatColor.RED + "Can not animate the minecart: it is not loaded");
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
    @CommandMethod("cart displayedblock clear")
    @CommandDescription("Clears the displayed block in the Minecart, making it empty")
    private void commandClearDisplayedBlock(
            final CommandSender sender,
            final CartProperties properties
    ) {
        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            member.getEntity().setBlock(Material.AIR);
            sender.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block cleared!");
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_CHANGEBLOCK)
    @CommandMethod("cart displayedblock type <block>")
    @CommandDescription("Sets the displayed block type in the Minecart")
    private void commandChangeDisplayedBlock(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("block") @Greedy String blockName
    ) {
        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            List<MinecartMember<?>> members = new ArrayList<>(1);
            members.add(member);
            SignActionBlockChanger.setBlocks(members, blockName, SignActionBlockChanger.BLOCK_OFFSET_NONE);
            sender.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block updated!");
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_CHANGEBLOCK)
    @CommandMethod("cart displayedblock offset reset")
    @CommandDescription("Resets the height offset at which a block is displayed in a Minecart to the defaults")
    private void commandResetDisplayedBlockOffset(
            final CommandSender sender,
            final CartProperties properties
    ) {
        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            member.getEntity().setBlockOffset(Util.getDefaultDisplayedBlockOffset());
            sender.sendMessage(ChatColor.YELLOW + "The selected minecart has its block offset reset!");
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_CHANGEBLOCK)
    @CommandMethod("cart displayedblock offset <offset>")
    @CommandDescription("Sets the height offset at which a block is displayed in a Minecart")
    private void commandSetDisplayedBlockOffset(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("offset") int offset
    ) {
        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            member.getEntity().setBlockOffset(offset);
            sender.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block offset updated!");
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_EJECT)
    @CommandMethod("cart eject")
    @CommandDescription("Ejects the passengers of a cart, ignoring the allow player exit property")
    private void commandEject(
            final CommandSender sender,
            final CartProperties cartProperties,
            final @Flag(value="seat", suggestions="cartSeatAttachments") String seatName
    ) {
        MinecartMember<?> member = cartProperties.getHolder();
        if (member == null || member.isUnloaded()) {
            sender.sendMessage(ChatColor.RED + "Can not eject the cart: it is not loaded");
            return;
        }

        if (seatName != null) {
            // Query seat to eject by name
            List<Attachment> seats = member.getAttachments().getRootAttachment()
                    .getNameLookup().get(seatName, e -> e instanceof CartAttachmentSeat);
            Commands.ejectSeats(sender, seatName, seats);
        } else {
            // All seats of cart
            if (member.getEntity().hasPassenger()) {
                member.eject();
                sender.sendMessage(ChatColor.GREEN + "Selected cart ejected!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Selected cart has no passengers!");
            }
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_FLIP)
    @CommandMethod("cart flip")
    @CommandDescription("Flips the orientation of a cart 180 degrees")
    private void commandFlip(
            final CommandSender sender,
            final CartProperties cartProperties
    ) {
        MinecartMember<?> member = cartProperties.getHolder();
        if (member == null || member.isUnloaded()) {
            sender.sendMessage(ChatColor.RED + "Can not flip the cart: it is not loaded");
            return;
        }

        member.flipOrientation();
        sender.sendMessage(ChatColor.GREEN + "Selected cart flipped!");
    }

    @CommandTargetTrain
    @CommandMethod("cart <property> <value>")
    @CommandDescription("Updates the value of a property of a cart by name")
    private void commandCart(
              final CommandSender sender,
              final CartProperties properties,
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
        builder.green("Available commands: ").yellow("/cart ").red("[info");
        builder.setSeparator(ChatColor.WHITE, "/").setIndent(10);
        builder.red("mobenter").red("playerenter").red("playerexit").red("claim").red("addowners").red("setowners");
        builder.red("addtags").red("settags").red("destination").red("destroy").red("public").red("private");
        builder.red("pickup").red("break");
        return builder.setSeparator(null).red("]");
    }

    public static void info(CommandSender sender, CartProperties prop) {
        MessageBuilder message = new MessageBuilder();

        message.yellow("UUID: ").white(prop.getUUID().toString()).newLine();

        //warning message not taken
        if (prop.hasOwners()) {
            message.newLine().yellow("Note: This minecart is not owned, claim it using /cart claim!");
        }
        message.yellow("Picks up nearby items: ").white(prop.canPickup());
        if (prop.hasBlockBreakTypes()) {
            message.newLine().yellow("Breaks blocks: ").white(StringUtil.combineNames(prop.getBlockBreakTypes()));
        }
        message.newLine().yellow("Enter message: ").white((prop.hasEnterMessage() ? prop.getEnterMessage() : "None"));

        // Remaining common info
        Commands.info(message, prop);

        // Loaded?
        MinecartMember<?> member = prop.getHolder();
        if (member == null) {
            message.newLine().red("The train of this cart is unloaded! To keep it loaded, use:");
            message.newLine().yellow("   /train keepchunksloaded true");
        }

        // Derailment information
        if (member != null) {
            Location loc = member.getFirstKnownDerailedPosition();
            if (loc != null) {
                message.newLine().red("This cart is derailed!");
                message.newLine().yellow("   It likely happened at x=", loc.getBlockX(),
                        " y=", loc.getBlockY(), " z=", loc.getBlockZ());
            }
        }

        // Send
        sender.sendMessage(" ");
        message.send(sender);
    }

}
