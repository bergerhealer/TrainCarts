package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
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

    @CommandMethod("cart info|i")
    @CommandDescription("Displays the properties of the cart")
    private void commandInfo(
            final Player player,
            final CartProperties properties
    ) {
        info(player, properties);
    }

    @CommandMethod("cart destroy|remove")
    @CommandDescription("Destroys the single cart that is selected")
    private void commandDestroy(
            final Player player,
            final CartProperties properties
    ) {
        Permission.COMMAND_DESTROY.handle(player);

        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            CartPropertiesStore.remove(properties.getUUID());
            OfflineGroupManager.removeMember(properties.getUUID());
        } else {
            member.onDie();
        }
        player.sendMessage(ChatColor.YELLOW + "The selected cart has been destroyed!");
    }

    @CommandMethod("cart teleport|tp")
    @CommandDescription("Teleports the player to where the cart is")
    private void commandTeleport(
            final Player player,
            final CartProperties properties
    ) {
        Permission.COMMAND_TELEPORT.handle(player);

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

    @CommandMethod("cart animate [options]")
    @CommandDescription("Plays an animation for a single cart")
    private void commandAnimate(
            final Player player,
            final CartProperties properties,
            final @Argument("options") String[] options
    ) {
        Permission.COMMAND_ANIMATE.handle(player);

        if (!properties.hasHolder()) {
            player.sendMessage(ChatColor.RED + "Can not animate the minecart: it is not loaded");
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

    @CommandMethod("cart displayedblock clear")
    @CommandDescription("Clears the displayed block in the Minecart, making it empty")
    private void commandClearDisplayedBlock(
            final CommandSender sender,
            final CartProperties properties
    ) {
        Permission.COMMAND_CHANGEBLOCK.handle(sender);

        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            member.getEntity().setBlock(Material.AIR);
            sender.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block cleared!");
        }
    }

    @CommandMethod("cart displayedblock <block>")
    @CommandDescription("Clears the displayed block in the Minecart, making it empty")
    private void commandChangeDisplayedBlock(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("block") @Greedy String blockName
    ) {
        Permission.COMMAND_CHANGEBLOCK.handle(sender);

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

    @CommandMethod("cart displayedblock offset reset")
    @CommandDescription("Resets the height offset at which a block is displayed in a Minecart to the defaults")
    private void commandResetDisplayedBlockOffset(
            final CommandSender sender,
            final CartProperties properties
    ) {
        Permission.COMMAND_CHANGEBLOCK.handle(sender);

        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            member.getEntity().setBlockOffset(9);
            sender.sendMessage(ChatColor.YELLOW + "The selected minecart has its block offset reset!");
        }
    }

    @CommandMethod("cart displayedblock offset <offset>")
    @CommandDescription("Sets the height offset at which a block is displayed in a Minecart")
    private void commandSetDisplayedBlockOffset(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("offset") int offset
    ) {
        Permission.COMMAND_CHANGEBLOCK.handle(sender);

        MinecartMember<?> member = properties.getHolder();
        if (member == null) {
            Localization.EDIT_NOTLOADED.message(sender);
        } else {
            member.getEntity().setBlockOffset(offset);
            sender.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block offset updated!");
        }
    }

    @CommandMethod("cart <property> <value>")
    @CommandDescription("Updates the value of a property of a cart by name")
    private void commandCart(
              final CommandSender sender,
              final CartProperties properties,
              final @Argument("property") String propertyName,
              final @Argument("value") @Greedy String value
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
        }

        help(new MessageBuilder()).send(sender);
    }

    public static MessageBuilder help(MessageBuilder builder) {
        builder.green("Available commands: ").yellow("/cart ").red("[info");
        builder.setSeparator(ChatColor.WHITE, "/").setIndent(10);
        builder.red("mobenter").red("playerenter").red("playerexit").red("claim").red("addowners").red("setowners");
        builder.red("addtags").red("settags").red("destination").red("destroy").red("public").red("private");
        builder.red("pickup").red("break");
        return builder.setSeparator(null).red("]");
    }

    public static void info(Player p, CartProperties prop) {
        MessageBuilder message = new MessageBuilder();

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
        p.sendMessage(" ");
        message.send(p);
    }

}
