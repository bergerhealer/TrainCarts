package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.exception.command.NoPermissionForPropertyException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;
import com.bergerkiller.bukkit.tc.signactions.SignActionBlockChanger;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.specifier.Greedy;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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
            member.onDie();
        }
        sender.sendMessage(ChatColor.YELLOW + "The selected cart has been destroyed!");
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_TELEPORT)
    @CommandMethod("cart teleport|tp")
    @CommandDescription("Teleports the player to where the cart is")
    private void commandTeleport(
            final Player player,
            final CartProperties properties
    ) {
        if (!properties.restore()) {
            player.sendMessage(ChatColor.RED + "Cart location could not be found: Train is lost");
        } else {
            BlockLocation bloc = properties.getLocation();
            World world = bloc.getWorld();
            if (world == null) {
                player.sendMessage(ChatColor.RED + "Train is on a world that is not loaded (" + bloc.world + ")");
            } else {
                EntityUtil.teleport(player, new Location(world, bloc.x + 0.5, bloc.y + 0.5, bloc.z + 0.5));
                player.sendMessage(ChatColor.YELLOW + "Teleported to cart of '" + properties.getTrainProperties().getTrainName() + "'");
            }
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_ANIMATE)
    @CommandMethod("cart animate <animation_name>")
    @CommandDescription("Plays an animation for the cart")
    private void commandAnimate(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument(value="animation_name", suggestions="cartAnimationName", description="Name of the animation to play") String animationName,
            final @Flag(value="speed", aliases="s", description="Speed of the animation, 1.0 is default") Double speed,
            final @Flag(value="delay", aliases="d", description="Delay of the animation, 0.0 is default") Double delay,
            final @Flag(value="loop", aliases="l", description="Loop the animation") boolean setLooping,
            final @Flag(value="noloop", description="Disable looping the animation") boolean setNotLooping,
            final @Flag(value="reset", aliases="r", description="Reset the animation to the beginning") boolean setReset,
            final @Flag(value="queue", aliases="q", description="Play the animation once previous animations have finished") boolean setQueued
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
    @CommandMethod("cart <property> <value>")
    @CommandDescription("Updates the value of a property of a cart by name")
    private void commandCart(
              final CommandSender sender,
              final CartProperties properties,
              final @Argument("property") String propertyName,
              final @Argument("value") String value
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
        if (prop.getHolder() == null) {
            message.newLine().red("The train of this cart is unloaded! To keep it loaded, use:");
            message.newLine().yellow("   /train keepchunksloaded true");
        }

        // Send
        sender.sendMessage(" ");
        message.send(sender);
    }

}
