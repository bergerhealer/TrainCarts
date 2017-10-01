package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.editor.TCMapControl;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bergerkiller.generated.com.mojang.authlib.GameProfileHandle;
import com.bergerkiller.generated.com.mojang.authlib.properties.PropertyHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityPlayerHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle.EnumPlayerInfoActionHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle.PlayerInfoDataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutScoreboardTeamHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.mountiplex.reflection.SafeConstructor;
import com.bergerkiller.mountiplex.reflection.SafeField;
import com.bergerkiller.mountiplex.reflection.SafeMethod;
import com.bergerkiller.reflection.net.minecraft.server.NMSMobEffect.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class GlobalCommands {

    public static boolean execute(CommandSender sender, String[] args) throws NoPermissionException {
        if (args[0].equals("msg") || args[0].equals("message")) {
            Permission.COMMAND_MESSAGE.handle(sender);
            if (args.length == 1) {
                sender.sendMessage(ChatColor.YELLOW + "/train message [name] [text...]");
            } else if (args.length == 2) {
                String value = TrainCarts.messageShortcuts.get(args[1]);
                if (value == null) {
                    sender.sendMessage(ChatColor.RED + "No shortcut is set for key '" + args[1] + "'");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Shortcut value of '" + args[1] + "' = " + ChatColor.WHITE + value);
                }
            } else {
                StringBuilder valueBuilder = new StringBuilder(100);
                for (int i = 2; i < args.length; i++) {
                    if (i != 2) {
                        valueBuilder.append(' ');
                    }
                    valueBuilder.append(args[i]);
                }
                String value = StringUtil.ampToColor(valueBuilder.toString());
                TrainCarts.messageShortcuts.remove(args[1]);
                TrainCarts.messageShortcuts.add(args[1], value);
                TrainCarts.plugin.saveShortcuts();
                sender.sendMessage(ChatColor.GREEN + "Shortcut '" + args[1] + "' set to: " + ChatColor.WHITE + value);
            }
            return true;
        } else if (args[0].equals("removeall") || args[0].equals("destroyall")) {
            Permission.COMMAND_DESTROYALL.handle(sender);
            if (args.length == 1) {
                // Destroy all trains on the entire server
                int count = OfflineGroupManager.destroyAll();
                sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");
            } else {
                // Destroy the trains on a single world
                String cname = args[1].toLowerCase();
                World w = Bukkit.getWorld(cname);
                if (w == null) {
                    for (World world : Bukkit.getServer().getWorlds()) {
                        if (world.getName().toLowerCase().contains(cname)) {
                            w = world;
                            break;
                        }
                    }
                }
                if (w != null) {
                    int count = OfflineGroupManager.destroyAll(w);
                    sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");
                } else {
                    sender.sendMessage(ChatColor.RED + "World not found!");
                }
            }
            return true;
        } else if (args[0].equals("reroute")) {
            Permission.COMMAND_REROUTE.handle(sender);
            if (args.length >= 2 && args[1].equalsIgnoreCase("lazy")) {
                PathNode.clearAll();
                sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated when needed");
            } else {
                PathNode.reroute();
                sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated");
            }
            return true;
        } else if (args[0].equals("reload")) {
            Permission.COMMAND_RELOAD.handle(sender);
            TrainProperties.loadDefaults();
            TrainCarts.plugin.loadConfig();
            sender.sendMessage(ChatColor.YELLOW + "Configuration has been reloaded.");
            return true;
        } else if (args[0].equals("saveall")) {
            Permission.COMMAND_SAVEALL.handle(sender);
            TrainCarts.plugin.save(false);
            sender.sendMessage(ChatColor.YELLOW + "TrainCarts' information has been saved to file.");
            return true;
        } else if (args[0].equals("fixbugged")) {
            Permission.COMMAND_FIXBUGGED.handle(sender);
            for (World world : WorldUtil.getWorlds()) {
                OfflineGroupManager.removeBuggedMinecarts(world);
            }
            sender.sendMessage(ChatColor.YELLOW + "Bugged minecarts have been forcibly removed.");
            return true;
        } else if (args[0].equals("list")) {
            String listType = (args.length >= 2) ? args[1] : "";
            if (listType.equals("destination") || listType.equals("destinations")) {
                // Destinations
                listDestinations(sender);
            } else if (listType.equals("ticket") || listType.equals("tickets")) {
                // Tickets
                listTickets(sender);
            } else {
                // Trains
                int count = 0, moving = 0;
                for (MinecartGroup group : MinecartGroupStore.getGroups()) {
                    count++;
                    if (group.isMoving()) {
                        moving++;
                    }
                    // Get properties: ensures that ALL trains are listed
                    group.getProperties();
                }
                count += OfflineGroupManager.getStoredCount();
                int minecartCount = 0;
                for (World world : WorldUtil.getWorlds()) {
                    for (org.bukkit.entity.Entity e : WorldUtil.getEntities(world)) {
                        if (e instanceof Minecart) {
                            minecartCount++;
                        }
                    }
                }
                MessageBuilder builder = new MessageBuilder();
                builder.green("There are ").yellow(count).green(" trains on this server (of which ");
                builder.yellow(moving).green(" are moving)");
                builder.newLine().green("There are ").yellow(minecartCount).green(" minecart entities");
                builder.send(sender);
                // Show additional information about owned trains to players
                if (sender instanceof Player) {
                    StringBuilder statement = new StringBuilder();
                    for (int i = 1; i < args.length; i++) {
                        if (i > 1) {
                            statement.append(' ');
                        }
                        statement.append(args[i]);
                    }
                    listTrains((Player) sender, statement.toString());
                }
            }
            return true;
        } else if (args[0].equals("edit")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Can not edit a train through the console!");
                return true;
            }
            if (args.length == 2) {
                String name = args[1];
                TrainProperties prop = TrainProperties.exists(name) ? TrainProperties.get(name) : null;
                if (prop != null && !prop.isEmpty()) {
                    if (prop.hasOwnership((Player) sender)) {
                        CartPropertiesStore.setEditing((Player) sender, prop.get(0));
                        sender.sendMessage(ChatColor.GREEN + "You are now editing train '" + prop.getTrainName() + "'!");
                    } else {
                        sender.sendMessage(ChatColor.RED + "You do not own this train and can not edit it!");
                    }
                    return true;
                } else {
                    sender.sendMessage(ChatColor.RED + "Could not find a valid train named '" + name + "'!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Please enter the exact name of the train to edit");
            }
            listTrains((Player) sender, "");
            return true;
        } else if (args[0].equals("tick")) {
            Permission.COMMAND_CHANGETICK.handle(sender);
            boolean disableTicks = false;
            boolean enableTicks = false;
            for (int i = 1; i <  args.length; i++) {
                String arg = args[i];
                if (arg.equals("disable") || arg.equals("stop")) {
                    disableTicks = true;
                } else if (arg.equals("enable") || arg.equals("start") || arg.equals("resume")) {
                    enableTicks = true;
                }
            }

            if (disableTicks) {
                TrainCarts.tickUpdateDivider = Integer.MAX_VALUE;
                sender.sendMessage(ChatColor.YELLOW + "Train tick updates have been globally " + ChatColor.RED + "disabled");
                return true;
            }
            if (enableTicks) {
                TrainCarts.tickUpdateDivider = 1;
                sender.sendMessage(ChatColor.YELLOW + "Train tick updates have been globally " + ChatColor.GREEN + "enabled");
                return true;
            }

            if (args.length >= 2 && args[1].equals("div")) {
                // Set a tick divider value
                if (args.length == 3) {
                    try {
                        TrainCarts.tickUpdateDivider = Integer.parseInt(args[2]);
                        sender.sendMessage(ChatColor.GREEN + "The tick rate divider has been set to " + ChatColor.YELLOW + TrainCarts.tickUpdateDivider);
                    } catch (NumberFormatException ex) {
                        TrainCarts.tickUpdateDivider = 1;
                        sender.sendMessage(ChatColor.GREEN + "The tick rate divider has been reset to the default");
                    }
                } else {
                    if (TrainCarts.tickUpdateDivider == Integer.MAX_VALUE) {
                        sender.sendMessage(ChatColor.YELLOW + "Automatic train tick updates are globally disabled");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "The tick rate divider is currently set to " + ChatColor.YELLOW + TrainCarts.tickUpdateDivider);
                    }
                }
            } else {
                TrainCarts.tickUpdateNow = 1;
                try {
                    if (args.length >= 2) {
                        TrainCarts.tickUpdateNow = Integer.parseInt(args[1]);
                    }
                } catch (NumberFormatException ex) {}

                if (TrainCarts.tickUpdateNow <= 1) {
                    sender.sendMessage(ChatColor.GREEN + "Trains ticked once");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Trains ticked " + TrainCarts.tickUpdateNow + " times");
                }
            }
            return true;
        } else if (args[0].equals("issue")) {
            Permission.COMMAND_ISSUE.handle(sender);
            MessageBuilder builder = new MessageBuilder();
            builder.yellow("Click here to report an issue with TrainCarts: ");
            try {
                String template = "##### BkCommonLib version: " + CommonPlugin.getInstance().getDebugVersion() +
                        "\n##### TrainCarts version: " + TrainCarts.plugin.getDebugVersion() +
                        "\n##### Spigot version: " + Bukkit.getVersion() +
                        "\n\n" +
                        "<hr>\n" +
                        "\n" +
                        "#### Problem or bug:\n" +
                        "\n" +
                        "#### Expected behaviour:\n" +
                        "\n" +
                        "#### Steps to reproduce:\n";
                builder.white("https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(template, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                builder.white("https://github.com/bergerhealer/TrainCarts/issues/new");
            }
            builder.send(sender);
            return true;
        } else if (args[0].equals("editor")) {

            Player player = (Player) sender;

            String fakerDisplayName = "bergerkiller";
            UUID fakerUUID = UUID.fromString("61699b2e-d327-2a01-9f1e-0ea8c3f06bc6"); //UUID.randomUUID();
            
            GameProfileHandle fakeGameProfile = GameProfileHandle.createNew(fakerUUID, "Dinnerbone");
            if (aaa) {
                aaa = false;
                fakeGameProfile.setAllProperties(GameProfileHandle.getForPlayer(player));
            }

            int entityId = EntityUtil.getUniqueEntityId();
            
            PacketPlayOutPlayerInfoHandle infoPacket = PacketPlayOutPlayerInfoHandle.createNew();
            infoPacket.setAction(EnumPlayerInfoActionHandle.ADD_PLAYER);
            PlayerInfoDataHandle playerInfo = PlayerInfoDataHandle.createNew(
                    infoPacket,
                    fakeGameProfile,
                    50,
                    GameMode.CREATIVE,
                    ChatText.fromMessage(fakerDisplayName)
            );
            infoPacket.getPlayers().add(playerInfo);
            PacketUtil.sendPacket(player, infoPacket.getRaw());

            // Create a named entity spawn packet
            PacketPlayOutNamedEntitySpawnHandle packet = PacketPlayOutNamedEntitySpawnHandle.T.newHandleNull();
            packet.setEntityId(entityId);
            packet.setPosX(player.getLocation().getX());
            packet.setPosY(player.getLocation().getY());
            packet.setPosZ(player.getLocation().getZ());
            packet.setYaw(player.getLocation().getYaw());
            packet.setPitch(player.getLocation().getPitch());
            packet.setEntityUUID(fakerUUID);

            // Copy data watcher data from the original player
            DataWatcher data_in = EntityHandle.fromBukkit(player).getDataWatcher();
            DataWatcher data = new DataWatcher();
            for (DataWatcher.Item<?> item : data_in.getWatchedItems()) {
                data.watch((DataWatcher.Key) item.getKey(), item.getValue());
            }
            //data.set(EntityHandle.DATA_CUSTOM_NAME, "berger");
            //data.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, true);
            packet.setDataWatcher(data);

            // Finally send the packet
            PacketUtil.sendPacket(player, packet.getRaw());
            
            
            // Set team
            PacketPlayOutScoreboardTeamHandle teamPacket = PacketPlayOutScoreboardTeamHandle.T.newHandleNull();
            teamPacket.setName("Dinner");
            teamPacket.setDisplayName("Dinner");
            teamPacket.setPrefix("");
            teamPacket.setSuffix("");
            teamPacket.setVisibility("never");
            teamPacket.setCollisionRule("never");
            teamPacket.setMode(0x0);
            teamPacket.setFriendlyFire(0x3);
            teamPacket.setPlayers(new ArrayList<String>(Arrays.asList("Dinnerbone")));
            teamPacket.setChatFormat(0);
            PacketUtil.sendPacket(player, teamPacket.getRaw());
            
            
            // Mounting
            /*
            for (Entity e : player.getWorld().getEntities()) {
                if (e instanceof Minecart) {
                    PacketPlayOutMountHandle mount = PacketPlayOutMountHandle.createNew(e.getEntityId(), new int[] {entityId});
                    PacketUtil.sendPacket(player, mount.getRaw());
                }
            }
            */
            
            /*
            Permission.COMMAND_GIVE_EDITOR.handle(sender);
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.getInventory().addItem(TCMapControl.createTCMapItem());
            } else {
                sender.sendMessage("This command is only for players");
            }
            */
            return true;
        }
        return false;
    }

    static boolean aaa = true;
    
    public static void listDestinations(CommandSender sender) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("The following train destinations are available:");
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        for (PathNode node : PathNode.getAll()) {
            if (!node.containsOnlySwitcher()) {
                builder.green(node.getName());
            }
        }
        builder.send(sender);
    }

    public static void listTickets(CommandSender sender) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("The following tickets are available:");
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        for (Ticket ticket : TicketStore.getAll()) {
            builder.green(ticket.getName());
        }
        builder.send(sender);
    }

    public static void listTrains(Player player, String statement) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("You are the proud owner of the following trains:");
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        boolean found = false;
        for (TrainProperties prop : TrainProperties.getAll()) {
            if (!prop.hasOwnership(player)) {
                continue;
            }
            if (prop.hasHolder() && statement.length() > 0) {
                MinecartGroup group = prop.getHolder();
                SignActionEvent event = new SignActionEvent(null, group);
                if (!Statement.has(group, statement, event)) {
                    continue;
                }
            }
            found = true;
            if (prop.isLoaded()) {
                builder.green(prop.getTrainName());
            } else {
                builder.red(prop.getTrainName());
            }
        }
        if (found) {
            builder.send(player);
        } else {
            Localization.EDIT_NONEFOUND.message(player);
        }
    }
}
