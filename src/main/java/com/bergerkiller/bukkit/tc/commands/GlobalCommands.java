package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.debug.DebugTool;
import com.bergerkiller.bukkit.tc.editor.TCMapControl;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import com.bergerkiller.bukkit.tc.utils.StoredTrainItemUtil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

public class GlobalCommands {

    public static boolean execute(CommandSender sender, String[] args) throws NoPermissionException {
        if (args[0].equals("msg") || args[0].equals("message")) {
            Permission.COMMAND_MESSAGE.handle(sender);
            if (args.length == 1) {
                sender.sendMessage(ChatColor.YELLOW + "/train message [name] [text...]");
            } else if (args.length == 2) {
                String value = TCConfig.messageShortcuts.get(args[1]);
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
                TCConfig.messageShortcuts.remove(args[1]);
                TCConfig.messageShortcuts.add(args[1], value);
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
        } else if (args[0].equals("menu")) {
            Permission.COMMAND_GIVE_EDITOR.handle(sender);
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command is only for players");
                return true;
            }
            if (args.length <= 2) {
                sender.sendMessage(ChatColor.YELLOW + "/train menu set [value] - Set value of the current menu element");
                return true;
            }

            // Get editor instance
            AttachmentEditor editor = MapDisplay.getHeldDisplay((Player) sender, AttachmentEditor.class);
            if (editor == null) {
                sender.sendMessage(ChatColor.RED + "You do not have the attachment editor open");
            }

            // Find focused widget
            MapWidget focused = editor.getFocusedWidget();
            if (focused == null) {
                focused = editor.getActivatedWidget();
            }
            if (args[1].equals("set") && focused instanceof SetValueTarget) {
                String fullValue = args[2];
                for (int n = 3; n < args.length; n++) {
                    fullValue += " " + args[n];
                }

                boolean success = ((SetValueTarget) focused).acceptTextValue(fullValue);
                String propname = ((SetValueTarget) focused).getAcceptedPropertyName();
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + propname + " has been updated");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to update " + propname + "!");
                }
                return true;
            }

            sender.sendMessage(ChatColor.RED + "Unknown command or no suitable menu item is active!");
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
        } else if (args[0].equals("reloadsavedtrains")) {
            Permission.COMMAND_RELOAD.handle(sender);
            TrainCarts.plugin.save(false);
            TrainCarts.plugin.loadSavedTrains();
            sender.sendMessage(ChatColor.YELLOW + "Reloaded saved trains and modules");
            return true;
        } else if (args[0].equals("reloadroutes")) {
            Permission.COMMAND_RELOAD.handle(sender);
            TrainCarts.plugin.getRouteManager().load();
            sender.sendMessage(ChatColor.YELLOW + "Reloaded saved routes");
            return true;
        } else if (args[0].equals("saveall")) {
            Permission.COMMAND_SAVEALL.handle(sender);
            TrainCarts.plugin.save(false);
            sender.sendMessage(ChatColor.YELLOW + "TrainCarts' information has been saved to file.");
            return true;
        } else if (args[0].equals("upgradesavedtrains")) {
            Permission.COMMAND_UPGRADESAVED.handle(sender);
            boolean undo = (args.length >= 2 && args[1].equalsIgnoreCase("undo"));
            TrainCarts.plugin.getSavedTrains().upgradeSavedTrains(undo);
            if (undo) {
                sender.sendMessage(ChatColor.YELLOW + "All saved trains have been restored to use the old position calibration of Traincarts v1.12.2-v2 (UNDO)");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "All saved trains have been upgraded to use the new position calibration of Traincarts v1.12.2-v3");
            }
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
                count += OfflineGroupManager.getStoredCountInLoadedWorlds();
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
                StringBuilder statement = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    if (i > 1) {
                        statement.append(' ');
                    }
                    statement.append(args[i]);
                }
                listTrains(sender, statement.toString());
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
                // Create an inverted camera transformation of the player's view direction
                final Player player = (Player) sender;
                World playerWorld = player.getWorld();
                Matrix4x4 cameraTransform = new Matrix4x4();
                cameraTransform.translateRotate(player.getEyeLocation());
                cameraTransform.invert();

                // Go by all minecarts on the server, and pick those close in view on the same world
                // The transformed point is a projective view of the Minecart in the player's vision
                // X/Y is left-right/up-down and Z is depth after the transformation is applied
                MinecartMember<?> bestMember = null;
                Vector bestPos = null;
                double bestDistance = Double.MAX_VALUE;
                for (MinecartGroup group : MinecartGroup.getGroups().cloneAsIterable()) {
                    if (group.getWorld() != playerWorld) continue;
                    for (MinecartMember<?> member : group) {
                        Vector pos = member.getEntity().loc.vector();
                        cameraTransform.transformPoint(pos);

                        // Behind the player
                        if (pos.getZ() < 0.0) {
                            continue;
                        }

                        // Check if position is allowed
                        double lim = Math.max(1.0, MathUtil.HALFROOTOFTWO * pos.getZ());
                        if (Math.abs(pos.getX()) > lim || Math.abs(pos.getY()) > lim) {
                            continue;
                        }

                        // Pick lowest distance
                        double distance = Math.sqrt(pos.getX() * pos.getX() + pos.getY() * pos.getY()) / lim;
                        if (bestPos == null || distance < bestDistance) {
                            bestPos = pos;
                            bestDistance = distance;
                            bestMember = member;
                        }
                    }
                }

                if (bestMember != null && !bestMember.getProperties().hasOwnership(player)) {
                    sender.sendMessage(ChatColor.RED + "You do not own this train and can not edit it!");
                } else if (bestMember != null) {
                    // Play a particle effect shooting upwards from the Minecart
                    final Entity memberEntity = bestMember.getEntity().getEntity();
                    new Task(TrainCarts.plugin) {
                        final int batch_ctr = 5;
                        double dy = 0.0;

                        @Override
                        public void run() {
                            for (int i = 0; i < batch_ctr; i++) {
                                if (dy > 50.0 || !player.isOnline() || memberEntity.isDead()) {
                                    stop();
                                    return;
                                }
                                Location loc = memberEntity.getLocation();
                                loc.add(0.0, dy, 0.0);
                                player.playEffect(loc, Effect.SMOKE, 4);
                                dy += 1.0;
                            }
                        }
                    }.start(1, 1);

                    // Mark minecart as editing
                    CartProperties.setEditing(player, bestMember.getProperties());
                    sender.sendMessage(ChatColor.GREEN + "You are now editing train '" + bestMember.getGroup().getProperties().getTrainName() + "'!");
                    return true;
                } else {
                    sender.sendMessage(ChatColor.RED + "You are not looking at any Minecart right now");
                    sender.sendMessage(ChatColor.RED + "Please enter the exact name of the train to edit");
                }
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
                TCConfig.tickUpdateDivider = Integer.MAX_VALUE;
                sender.sendMessage(ChatColor.YELLOW + "Train tick updates have been globally " + ChatColor.RED + "disabled");
                return true;
            }
            if (enableTicks) {
                TCConfig.tickUpdateDivider = 1;
                sender.sendMessage(ChatColor.YELLOW + "Train tick updates have been globally " + ChatColor.GREEN + "enabled");
                return true;
            }

            if (args.length >= 2 && args[1].equals("div")) {
                // Set a tick divider value
                if (args.length == 3) {
                    try {
                        TCConfig.tickUpdateDivider = Integer.parseInt(args[2]);
                        sender.sendMessage(ChatColor.GREEN + "The tick rate divider has been set to " + ChatColor.YELLOW + TCConfig.tickUpdateDivider);
                    } catch (NumberFormatException ex) {
                        TCConfig.tickUpdateDivider = 1;
                        sender.sendMessage(ChatColor.GREEN + "The tick rate divider has been reset to the default");
                    }
                } else {
                    if (TCConfig.tickUpdateDivider == Integer.MAX_VALUE) {
                        sender.sendMessage(ChatColor.YELLOW + "Automatic train tick updates are globally disabled");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "The tick rate divider is currently set to " + ChatColor.YELLOW + TCConfig.tickUpdateDivider);
                    }
                }
            } else {
                TCConfig.tickUpdateNow = 1;
                try {
                    if (args.length >= 2) {
                        TCConfig.tickUpdateNow = Integer.parseInt(args[1]);
                    }
                } catch (NumberFormatException ex) {}

                if (TCConfig.tickUpdateNow <= 1) {
                    sender.sendMessage(ChatColor.GREEN + "Trains ticked once");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Trains ticked " + TCConfig.tickUpdateNow + " times");
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
            Permission.COMMAND_GIVE_EDITOR.handle(sender);
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.getInventory().addItem(TCMapControl.createTCMapItem());
            } else {
                throw new NoPermissionException();
            }
            return true;
        } else if (args[0].equals("attachments")) {
            if (sender instanceof Player) {
                Permission.COMMAND_GIVE_EDITOR.handle(sender);
                ItemStack item = MapDisplay.createMapItem(AttachmentEditor.class);
                ItemUtil.setDisplayName(item, "Traincarts Attachments Editor");
                CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
                CommonTagCompound display = tag.createCompound("display");
                display.putValue("MapColor", 0xFF0000);
                ((Player) sender).getInventory().addItem(item);
                sender.sendMessage(ChatColor.GREEN + "Given a Traincarts attachments editor");
            } else {
                throw new NoPermissionException();
            }
            return true;
        } else if (args[0].equals("chest")) {
            Permission.COMMAND_USE_STORAGE_CHEST.handle(sender);

            Player player = (Player) sender;

            ItemStack item = null;

            String instruction = (args.length > 1) ? args[1].toLowerCase(Locale.ENGLISH) : "";
            String parameters = "";
            if (args.length > 2) {
                parameters = StringUtil.join(" ", Arrays.asList(args).subList(2, args.length));
            }
            if (!instruction.isEmpty()) {
                item = HumanHand.getItemInMainHand(player);
                if (StoredTrainItemUtil.isItem(item)) {
                    item = ItemUtil.cloneItem(item);
                } else {
                    item = null;
                    instruction = "";
                }
            }

            if (instruction.equals("set")) {
                StoredTrainItemUtil.store(item, parameters);
            } else if (instruction.equals("clear")) {
                StoredTrainItemUtil.clear(item);
            } else if (instruction.equals("lock")) {
                StoredTrainItemUtil.setLocked(item, true);
            } else if (instruction.equals("unlock")) {
                StoredTrainItemUtil.setLocked(item, false);
            } else if (instruction.equals("name")) {
                StoredTrainItemUtil.setName(item, parameters);
            } else {
                // Invalid
                instruction = "";
                item = null;
            }

            if (item == null) {
                // No item, create a new one and give it to the player
                item = StoredTrainItemUtil.createItem();
                if (args.length > 1) {
                    String typesStr = StringUtil.join(" ", Arrays.asList(args).subList(1, args.length));
                    StoredTrainItemUtil.store(item, typesStr);
                }
                player.getInventory().addItem(item);
                Localization.CHEST_GIVE.message(sender);
            } else {
                // Existing item. Update it in the player's currently selected slot
                HumanHand.setItemInMainHand(player, item);
                Localization.CHEST_UPDATE.message(sender);
            }

            return true;
        } else if (args[0].equals("debug")) {
            Permission.DEBUG_COMMAND_DEBUG.handle(sender);
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command is only for players");
                return true;
            }
            Player player = (Player) sender;
            String cmd = (args.length >= 2) ? args[1] : "";
            if (cmd.equalsIgnoreCase("rails")) {
                giveDebugItem(player, "Rails", "TrainCarts Rails Debugger");
                player.sendMessage(ChatColor.GREEN + "Given a rails debug item. Right-click rails and see where a train would go.");
            } else if (cmd.equalsIgnoreCase("destination")) {
                if (args.length >= 3) {
                    giveDebugItem(player, "Destination " + args[2], "TrainCarts Destination Debugger [" + args[2] + "]");
                    player.sendMessage(ChatColor.GREEN + "Given a destination debug item. " +
                            "Right-click rails to see whether and how a train would travel to " + args[2] + ".");
                } else {
                    giveDebugItem(player, "Destinations", "TrainCarts Destination Debugger");
                    player.sendMessage(ChatColor.GREEN + "Given a destination debug item. " +
                            "Right-click rails to see what destinations can be reached from there.");
                }
            } else if (cmd.equalsIgnoreCase("mutex")) {
                DebugTool.showMutexZones(player);
                player.sendMessage(ChatColor.GREEN + "Displaying mutex zones near your position");
            } else if (cmd.equals("railtracker")) {
                if (args.length >= 3) {
                    TCConfig.railTrackerDebugEnabled = ParseUtil.parseBool(args[2]);
                }
                player.sendMessage(ChatColor.GREEN + "Displaying tracked rail positions: " +
                        (TCConfig.railTrackerDebugEnabled ? "ENABLED" : (ChatColor.RED + "DISABLED")));
            } else if (cmd.equals("wheeltracker")) {
                if (args.length >= 3) {
                    TCConfig.wheelTrackerDebugEnabled = ParseUtil.parseBool(args[2]);
                }
                player.sendMessage(ChatColor.GREEN + "Displaying tracked wheel positions: " +
                        (TCConfig.wheelTrackerDebugEnabled ? "ENABLED" : (ChatColor.RED + "DISABLED")));
            } else {
                player.sendMessage(ChatColor.RED + "Specify the type of debug to perform!");
                player.sendMessage(ChatColor.RED + "/train debug rails - debug rails");
            }
            return true;
        }
        return false;
    }

    public static void giveDebugItem(Player player, String debugMode, String debugTitle) {
        ItemStack item = ItemUtil.createItem(Material.STICK, 1);
        ItemUtil.getMetaTag(item, true).putValue("TrainCartsDebug", debugMode);
        ItemUtil.setDisplayName(item, debugTitle);

        // Update item in main hand, if it is a debug item
        ItemStack inMainHand = HumanHand.getItemInMainHand(player);
        if (inMainHand != null) {
            CommonTagCompound tag = ItemUtil.getMetaTag(inMainHand, false);
            if (tag != null && tag.containsKey("TrainCartsDebug")) {
                HumanHand.setItemInMainHand(player, item);
                return;
            }
        }

        // Give new item
        player.getInventory().addItem(item);
    }

    public static void listDestinations(CommandSender sender) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("The following train destinations are available:");
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        Collection<PathWorld> worlds;
        if (sender instanceof Player) {
            World playerWorld = ((Player) sender).getWorld();
            worlds = Collections.singleton(TrainCarts.plugin.getPathProvider().getWorld(playerWorld));
        } else {
            worlds = TrainCarts.plugin.getPathProvider().getWorlds();
        }
        for (PathWorld world : worlds) {
            for (PathNode node : world.getNodes()) {
                if (!node.containsOnlySwitcher()) {
                    builder.green(node.getName());
                }
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

    public static void listTrains(CommandSender sender, String statement) {
        MessageBuilder builder = new MessageBuilder();
        if (sender instanceof Player) {
            builder.yellow("You are the proud owner of the following trains:");
        } else {
            builder.yellow("The following trains exist on this server:");
        }
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        boolean found = false;
        for (TrainProperties prop : TrainProperties.getAll()) {
            if (sender instanceof Player && !prop.hasOwnership((Player) sender)) {
                continue;
            }

            // Check if train is loaded, or stored in a loaded world
            if (!prop.hasHolder() && !OfflineGroupManager.containsInLoadedWorld(prop.getTrainName())) {
                continue;
            }

            if (prop.hasHolder() && statement.length() > 0) {
                MinecartGroup group = prop.getHolder();
                SignActionEvent event = new SignActionEvent((Block) null, group);
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
            builder.send(sender);
        } else {
            Localization.EDIT_NONEFOUND.message(sender);
        }
    }
}
