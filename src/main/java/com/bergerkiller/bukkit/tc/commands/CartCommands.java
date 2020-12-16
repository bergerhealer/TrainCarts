package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.commands.cloud.ArgumentList;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.exception.command.NoPermissionForPropertyException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
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
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @CommandMethod("cart <arguments>")
    @CommandDescription("Performs commands that operate individual carts of a train")
    private void commandCart(
              final Player player,
              final CartProperties properties,
              final ArgumentList arguments,
              final @Argument("arguments") @Greedy String unused_arguments
    ) {
        Permission.COMMAND_PROPERTIES.handle(player);
        execute(player, properties, arguments.get(1), arguments.range(2).array());
    }

    public static void execute(Player p, CartProperties prop, String cmd, String[] args) throws NoPermissionException {
        TrainPropertiesStore.markForAutosave();
        if (cmd.equalsIgnoreCase("claim")) {
            Permission.COMMAND_SETOWNERS.handle(p);
            prop.clearOwners();
            prop.setOwner(p, true);
            p.sendMessage(ChatColor.YELLOW + "You claimed this minecart your own!");
        } else if (LogicUtil.containsIgnoreCase(cmd, "addowner", "addowners")) {
            Permission.COMMAND_SETOWNERS.handle(p);
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "Please specify the player names to set as owner!");
            } else {
                for (String owner : args) {
                    prop.setOwner(owner.toLowerCase());
                }
                p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " as owners of this minecart!");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "setowner", "setowners")) {
            Permission.COMMAND_SETOWNERS.handle(p);
            prop.clearOwners();
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "All owners for this minecart have been cleared!");
            } else {
                for (String owner : args) {
                    prop.setOwner(owner.toLowerCase());
                }
                p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " as owners of this minecart!");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "setownerperm", "setownerpermission", "setownerpermissions")) {
            Permission.COMMAND_SETOWNERS.handle(p);
            prop.clearOwnerPermissions();
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "All owner permissions for this minecart have been cleared!");
            } else {
                for (String ownerPerm : args) {
                    prop.addOwnerPermission(ownerPerm);
                }
                p.sendMessage(ChatColor.YELLOW + "You set the owner permissions " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " for this minecart");
                p.sendMessage(ChatColor.YELLOW + "Players that have these permission nodes are considered owners of this Minecart");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "addownerperm", "addownerpermission", "addownerpermissions")) {
            Permission.COMMAND_SETOWNERS.handle(p);
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "Please specify the permission nodes to add!");
            } else {
                for (String ownerPerm : args) {
                    prop.removeOwnerPermission(ownerPerm);
                }
                p.sendMessage(ChatColor.YELLOW + "You added the owner permissions " + ChatColor.WHITE + StringUtil.combineNames(args) + ChatColor.YELLOW + " to this minecart");
                p.sendMessage(ChatColor.YELLOW + "Players that have these permission nodes are considered owners of this Minecart");
            }
        } else if (cmd.equalsIgnoreCase("break")) {
            Permission.COMMAND_BREAKBLOCK.handle(p);
            if (args.length == 0) {
                p.sendMessage(ChatColor.YELLOW + "This cart breaks: " + ChatColor.WHITE + StringUtil.combineNames(prop.getBlockBreakTypes()));
            } else {
                if (ParseUtil.isBool(args[0]) && !ParseUtil.parseBool(args[0])) {
                    prop.clearBlockBreakTypes();
                    p.sendMessage(ChatColor.YELLOW + "Block break types have been cleared!");
                } else {
                    boolean asBreak = true;
                    boolean lastIsBool = ParseUtil.isBool(args[args.length - 1]);
                    if (lastIsBool) asBreak = ParseUtil.parseBool(args[args.length - 1]);
                    int count = lastIsBool ? args.length - 1 : args.length;
                    Set<Material> mats = new HashSet<>();
                    for (int i = 0; i < count; i++) {
                        Material mat = ParseUtil.parseMaterial(args[i], null);
                        if (mat != null) {
                            if (Permission.COMMAND_BREAKBLOCK_ADMIN.has(p) || TrainCarts.canBreak(mat)) {
                                mats.add(mat);
                            } else {
                                p.sendMessage(ChatColor.RED + "You are not allowed to make this cart break '" + mat.toString() + "'!");
                            }
                        }
                    }
                    if (mats.isEmpty()) {
                        p.sendMessage(ChatColor.RED + "Failed to find possible and allowed block types in the list given.");
                        return;
                    }
                    if (asBreak) {
                        prop.getBlockBreakTypes().addAll(mats);
                        p.sendMessage(ChatColor.YELLOW + "This cart can now (also) break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
                    } else {
                        prop.getBlockBreakTypes().removeAll(mats);
                        p.sendMessage(ChatColor.YELLOW + "This cart can no longer break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
                    }
                }
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "setblock", "setblocks", "changeblock", "changeblocks", "blockchanger")) {
            Permission.COMMAND_CHANGEBLOCK.handle(p);
            MinecartMember<?> member = prop.getHolder();
            if (member == null) {
                p.sendMessage(ChatColor.RED + "The selected minecart is unloaded: we can not change it at this time!");
            } else if (args.length == 0) {
                member.getEntity().setBlock(Material.AIR);
                p.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block cleared!");
            } else {
                List<MinecartMember<?>> members = new ArrayList<>(1);
                members.add(member);
                SignActionBlockChanger.setBlocks(members, StringUtil.join(" ", args), SignActionBlockChanger.BLOCK_OFFSET_NONE);
                p.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block updated!");
            }
        } else if (LogicUtil.containsIgnoreCase(cmd, "setblockoffset", "changeblockoffset", "blockoffset")) {
            Permission.COMMAND_CHANGEBLOCK.handle(p);
            MinecartMember<?> member = prop.getHolder();
            if (member == null) {
                p.sendMessage(ChatColor.RED + "The selected minecart is unloaded: we can not change it at this time!");
            } else if (args.length == 0) {
                member.getEntity().setBlockOffset(9);
                p.sendMessage(ChatColor.YELLOW + "The selected minecart has its block offset reset!");
            } else {
                int offset = ParseUtil.parseInt(args[0], 9);
                member.getEntity().setBlockOffset(offset);
                p.sendMessage(ChatColor.YELLOW + "The selected minecart has its displayed block offset updated!");
            }
        } else {
            if (args.length >= 1) {
                PropertyParseResult<Object> parseResult = IPropertyRegistry.instance().parseAndSet(
                        prop, cmd, String.join(" ", args),
                        (result) -> {
                            if (!result.hasPermission(p)) {
                                throw new NoPermissionForPropertyException(result.getName());
                            }
                        });

                if (parseResult.getReason() != PropertyParseResult.Reason.PROPERTY_NOT_FOUND) {
                    if (parseResult.isSuccessful()) {
                        p.sendMessage(ChatColor.GREEN + "Property has been updated!");
                    } else {
                        p.sendMessage(parseResult.getMessage());
                    }
                    return;
                }
            }

            p.sendMessage(ChatColor.RED + "Unknown cart command: '" + cmd + "'!");
            help(new MessageBuilder()).send(p);
        }

        prop.tryUpdate();
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
        if (!prop.hasOwners()) {
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
