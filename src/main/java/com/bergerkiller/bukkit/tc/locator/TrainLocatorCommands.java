package com.bergerkiller.bukkit.tc.locator;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;
import net.md_5.bungee.api.ChatColor;

/**
 * The commands used to start and stop train locating operations
 */
public class TrainLocatorCommands {

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_LOCATE)
    @CommandMethod("cart locate")
    @CommandDescription("Toggles locating a single cart of a train. Stops locating other carts.")
    private void commandLocateCart(
            final Player sender,
            final TrainCarts plugin,
            final MinecartMember<?> member,
            final @Flag(value="timeout",
                        description="Timeout in seconds after which locating automatically stops") Double timeout
    ) {
        if (plugin.getTrainLocator().isLocating(sender, member)) {
            plugin.getTrainLocator().stopAll(sender);
            sender.sendMessage(ChatColor.YELLOW + "Stopped locating the cart(s)");
        } else {
            if (plugin.getTrainLocator().stopAll(sender)) {
                sender.sendMessage(ChatColor.YELLOW + "Stopped locating the previous cart(s)");
            }
            this.commandStartLocatingCart(sender, plugin, member, timeout);
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_LOCATE)
    @CommandMethod("train locate")
    @CommandDescription("Toggles locating a single train. Stops locating other trains.")
    private void commandLocateTrain(
            final Player sender,
            final TrainCarts plugin,
            final MinecartGroup group,
            final @Flag(value="timeout",
                        description="Timeout in seconds after which locating automatically stops") Double timeout
    ) {
        if (plugin.getTrainLocator().isLocating(sender, group)) {
            plugin.getTrainLocator().stopAll(sender);
            sender.sendMessage(ChatColor.YELLOW + "Stopped locating the train(s)");
        } else {
            if (plugin.getTrainLocator().stopAll(sender)) {
                sender.sendMessage(ChatColor.YELLOW + "Stopped locating the previous train(s)");
            }
            this.commandStartLocatingTrain(sender, plugin, group, timeout);
        }
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_LOCATE)
    @CommandMethod("cart locate start")
    @CommandDescription("Starts locating a single cart of a train. Keeps locating previous trains.")
    private void commandStartLocatingCart(
            final Player sender,
            final TrainCarts plugin,
            final MinecartMember<?> member,
            final @Flag(value="timeout",
                        description="Timeout in seconds after which locating automatically stops") Double timeout
    ) {
        MessageBuilder message = new MessageBuilder();
        if (timeout == null) {
            if (plugin.getTrainLocator().start(sender, member)) {
                message.green("Locating the cart for indefinite time");
                message.newLine();
                message.green("To stop locating, use /cart locate stop");
            } else {
                message.red("Failed to locate this cart (different world?)");
            }
        } else {
            int numTicks = MathUtil.ceil(timeout.doubleValue() * 20.0);
            if (plugin.getTrainLocator().start(sender, member, numTicks)) {
                message.green("Locating the cart for ", timeout, " seconds");
                message.newLine();
                message.green("To stop locating, use /cart locate stop");
            } else {
                message.red("Failed to locate this cart (different world?)");
            }
        }
        message.send(sender);
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_LOCATE)
    @CommandMethod("train locate start")
    @CommandDescription("Starts locating a single cart of a train. Keeps locating previous carts.")
    private void commandStartLocatingTrain(
            final Player sender,
            final TrainCarts plugin,
            final MinecartGroup group,
            final @Flag(value="timeout",
                        description="Timeout in seconds after which locating automatically stops") Double timeout
    ) {
        MessageBuilder message = new MessageBuilder();
        if (timeout == null) {
            if (plugin.getTrainLocator().start(sender, group)) {
                message.green("Locating the train for indefinite time");
                message.newLine();
                message.green("To stop locating, use /train locate stop");
            } else {
                message.red("Failed to locate this train (different world?)");
            }
        } else {
            int numTicks = MathUtil.ceil(timeout.doubleValue() * 20.0);
            if (plugin.getTrainLocator().start(sender, group, numTicks)) {
                message.green("Locating the train for ", timeout, " seconds");
                message.newLine();
                message.green("To stop locating, use /train locate stop");
            } else {
                message.red("Failed to locate this train (different world?)");
            }
        }
        message.send(sender);
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_LOCATE)
    @CommandMethod("cart locate stop")
    @CommandDescription("Stops locating the selected cart, keeps locating other carts")
    private void commandStopLocatingCart(
            final Player sender,
            final TrainCarts plugin,
            final MinecartMember<?> member
    ) {
        MessageBuilder message = new MessageBuilder();
        String name = member.getGroup().getProperties().getTrainName();
        if (plugin.getTrainLocator().stop(sender, member)) {
            message.yellow("No longer locating cart of train ").white(name);
        } else {
            message.red("You were not locating this cart of train ", name);
        }
        message.send(sender);
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_LOCATE)
    @CommandMethod("train locate stop")
    @CommandDescription("Stops locating the selected train, keeps locating other train")
    private void commandStopLocatingTrain(
            final Player sender,
            final TrainCarts plugin,
            final MinecartGroup group
    ) {
        MessageBuilder message = new MessageBuilder();
        String name = group.getProperties().getTrainName();
        if (plugin.getTrainLocator().stop(sender, group)) {
            message.yellow("No longer locating train ").white(name);
        } else {
            message.red("You were not locating the train ", name);
        }
        message.send(sender);
    }

    @CommandRequiresPermission(Permission.COMMAND_LOCATE)
    @CommandMethod("cart locate stop_all")
    @CommandDescription("Stops locating all carts")
    private void commandStopLocatingAllCartAlias(
            final Player sender,
            final TrainCarts plugin
    ) {
        commandStopLocatingAll(sender, plugin);
    }

    @CommandRequiresPermission(Permission.COMMAND_LOCATE)
    @CommandMethod("train locate stop_all")
    @CommandDescription("Stops locating all trains")
    private void commandStopLocatingAll(
            final Player sender,
            final TrainCarts plugin
    ) {
        MessageBuilder message = new MessageBuilder();
        if (plugin.getTrainLocator().stopAll(sender)) {
            message.green("Stopped locating the trains");
        } else {
            message.red("You were not locating any trains");
        }
        message.send(sender);
    }
}
