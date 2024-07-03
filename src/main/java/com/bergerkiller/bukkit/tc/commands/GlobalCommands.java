package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.cloud.parsers.SoundEffectParser;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.resources.ResourceKey;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorException;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.editor.TCMapControl;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.pathfinding.PathWorld;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.statements.Statement;

import com.bergerkiller.bukkit.tc.tickets.TicketStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotation.specifier.Range;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.description.Description;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class GlobalCommands {

    @Command("train version")
    @CommandDescription("Shows installed version of TrainCarts and BKCommonLib")
    private void commandShowVersion(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        plugin.onVersionCommand("version", sender);
    }

    @Command("train startuplog")
    @CommandDescription("Views everything logged during startup of TrainCarts")
    @org.incendo.cloud.annotations.Permission("bkcommonlib.command.startuplog")
    private void commandShowStartupLog(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        plugin.onStartupLogCommand(sender, "startuplog", new String[0]);
    }

    @Command("train list destinations")
    @CommandDescription("Lists all the destination names that exist on the server")
    private void commandListDestinations(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("The following train destinations are available:");
        builder.newLine().setSeparator(ChatColor.WHITE, " / ");
        Collection<PathWorld> worlds;
        if (sender instanceof Player) {
            World playerWorld = ((Player) sender).getWorld();
            worlds = Collections.singleton(plugin.getPathProvider().getWorld(playerWorld));
        } else {
            worlds = plugin.getPathProvider().getWorlds();
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

    @Command("train list [filter]")
    @CommandDescription("Lists all the trains on the server that match the specified statement")
    private void commandListTrains(
            final TrainCarts plugin,
            final CommandSender sender,
            final @Argument(value="filter", suggestions="trainlistfilter") @Greedy String filter
    ) {
        // Arg-less list command also shows global stats
        if (filter == null || filter.isEmpty()) {
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
            count += plugin.getOfflineGroups().getStoredCountInLoadedWorlds();
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
        }

        // Show additional information about owned trains to players
        listTrains(plugin, sender, filter == null ? "" : filter);
    }

    @CommandRequiresPermission(Permission.COMMAND_MESSAGE)
    @Command("train message <key>")
    @CommandDescription("Checks what value is assigned to a given message key")
    private void commandGetMessage(
            final CommandSender sender,
            final @Argument("key") String key
    ) {
        String value = TCConfig.messageShortcuts.get(key);
        if (value == null) {
            sender.sendMessage(ChatColor.RED + "No shortcut is set for key '" + key + "'");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Shortcut value of '" + key + "' = " + ChatColor.WHITE + value);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_MESSAGE)
    @Command("train message <key> <value>")
    @CommandDescription("Checks what value is assigned to a given message key")
    private void commandSetMessage(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Argument("key") String key,
            final @Argument("value") @Greedy String value
    ) {
        String conv_value = StringUtil.ampToColor(value);
        TCConfig.messageShortcuts.remove(key);
        TCConfig.messageShortcuts.add(key, conv_value);
        plugin.saveShortcuts();
        sender.sendMessage(ChatColor.GREEN + "Shortcut '" + key + "' set to: " + ChatColor.WHITE + conv_value);
    }

    @CommandRequiresPermission(Permission.COMMAND_DESTROYALL)
    @Command("train destroyall|removeall")
    @CommandDescription("Destroys all trains on the server or world")
    private void commandDestroyAll(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Flag("world") World world,
            final @Flag(value="vanilla",
                        description="Whether to destroy non-Traincarts vanilla Minecarts too") boolean destroyVanilla
    ) {
        // Destroy all trains on the entire server (or on one world)
        CompletableFuture<Integer> future = (world == null)
                ? plugin.getOfflineGroups().destroyAllAsync(destroyVanilla)
                : plugin.getOfflineGroups().destroyAllAsync(world, destroyVanilla);
        future.thenAccept(count -> {
            sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");  
        });
    }

    public void init(CommandManager<CommandSender> manager) {
        initTrainMenuSetSoundCommand(manager);
    }

    private void initTrainMenuSetSoundCommand(CommandManager<CommandSender> manager) {
        if (!Common.hasCapability("Common:Sound:CloudParser")) {
            return;
        }

        manager.command(manager.commandBuilder("train").literal("menu")
                .literal("sound", Description.of("Sets a sound effect in the TrainCarts editor map"))
                .required("path", SoundEffectParser.soundEffectParser())
                .permission(Permission.COMMAND_GIVE_EDITOR.cloudPermission())
                .senderType(Player.class)
                .handler(context -> {
                    Player sender = context.sender();
                    ResourceKey<SoundEffect> effect = context.get("path");
                    commandMenuSet(sender, SetValueTarget.Operation.SET, effect.getPath());
                }));
    }

    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("train menu <operation> <value>")
    @CommandDescription("Updates a menu item in a TrainCarts editor map using commands")
    private void commandMenuSet(
            final Player sender,
            final @Argument("operation") SetValueTarget.Operation operation,
            final @Argument("value") @Greedy String value
    ) {
        // Get editor instance
        MapDisplay display = MapDisplay.getHeldDisplay((Player) sender, AttachmentEditor.class);
        if (display == null) {
            display = MapDisplay.getHeldDisplay((Player) sender);
            if (display == null) {
                sender.sendMessage(ChatColor.RED + "You do not have an editor menu open");
                return;
            }
        }

        // Find focused widget
        MapWidget focused = display.getFocusedWidget();
        if (!(focused instanceof SetValueTarget)) {
            focused = display.getActivatedWidget();
        }
        if (!(focused instanceof SetValueTarget)) {
            sender.sendMessage(ChatColor.RED + "No suitable menu item is active!");
            return;
        }

        // Got a target, input the value into it
        SetValueTarget target = (SetValueTarget) focused;
        boolean success = target.acceptTextValue(operation, value);
        String propname = target.getAcceptedPropertyName();
        if (success) {
            sender.sendMessage(ChatColor.GREEN + propname + " has been updated");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to update " + propname + "!");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_REROUTE)
    @Command("train reroute")
    @CommandDescription("Recalculates all path finding information on the server")
    private void commandReroute(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Flag(value="lazy", description="Delays recalculating routes until a train needs it") boolean lazy,
            final @Flag(value="stop", description="Stops all ongoing path route discovery operations") boolean stop,
            final @Flag(value="status", description="Displays what the routing manager is currently doing") boolean status
    ) {
        if (status) {
            if (!plugin.getPathProvider().isProcessing()) {
                sender.sendMessage(ChatColor.GREEN + "No train routings are being calculated right now");
            } else {
                int numNodes = plugin.getPathProvider().getNumPendingNodes();
                int numTasks = plugin.getPathProvider().getNumPendingOperations();
                sender.sendMessage(ChatColor.YELLOW + "Train routings are being calculated right now:");
                sender.sendMessage(ChatColor.YELLOW + "Number of switchers/destinations remaining: " + ChatColor.RED + numNodes);
                sender.sendMessage(ChatColor.YELLOW + "Number of paths remaining: " + ChatColor.RED + numTasks);
            }
        } else if (stop) {
            plugin.getPathProvider().stopRouting();
            sender.sendMessage(ChatColor.YELLOW + "Cancelled all ongoing train route discovery operations");
        } else if (lazy) {
            PathNode.clearAll();
            sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated when needed");
        } else {
            PathNode.reroute();
            plugin.getPathProvider().notifyOfCompletion(sender);
            sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_RELOAD)
    @Command("train globalconfig reload|load")
    @CommandDescription("Reloads one or more global TrainCarts configuration files from disk")
    private void commandReloadConfig(
            final CommandSender sender,
            final TrainCarts traincarts,
            final @Flag(value="config", description="Reload config.yml") boolean config,
            final @Flag(value="routes", description="Reload routes.yml") boolean routes,
            final @Flag(value="defaulttrainproperties", description="Reload DefaultTrainProperties.yml") boolean defaultTrainproperties,
            final @Flag(value="savedtrainproperties", description="Reload SavedTrainProperties.yml and modules") boolean savedTrainproperties,
            final @Flag(value="modelstore", description="Reload SavedModels.yml and modules") boolean modelStore,
            final @Flag(value="tickets", description="Reload tickets.yml") boolean tickets
    ) {
        if (!config &&
            !routes &&
            !defaultTrainproperties &&
            !savedTrainproperties &&
            !modelStore &&
            !tickets
        ) {
            sender.sendMessage(ChatColor.RED + "Please specify one or more configuration files to reload:");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --config");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --routes");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --defaulttrainproperties");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --savedtrainproperties");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --modelstore");
            sender.sendMessage(ChatColor.RED + "/train globalconfig reload --tickets");
            return;
        }

        if (config) {
            traincarts.loadConfig();
        }
        if (routes) {
            traincarts.getRouteManager().load();
        }
        if (defaultTrainproperties) {
            TrainProperties.loadDefaults(traincarts);
        }
        if (savedTrainproperties) {
            traincarts.getSavedTrains().reload();
        }
        if (modelStore) {
            traincarts.getSavedAttachmentModels().reload();
        }
        if (tickets) {
            TicketStore.load(traincarts);
        }
        sender.sendMessage(ChatColor.YELLOW + "Configuration has been reloaded!");
    }

    @CommandRequiresPermission(Permission.COMMAND_SAVEALL)
    @Command("train globalconfig save")
    @CommandDescription("Forces a save of all configuration to disk")
    private void commandReloadConfig(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        plugin.save(TrainCarts.SaveMode.COMMAND);
        sender.sendMessage(ChatColor.YELLOW + "TrainCarts' information has been saved to file.");
    }

    @Command("train edit")
    @CommandDescription("Selects a train the player is looking at for editing")
    private void commandEditLookingAt(
            final TrainCarts plugin,
            final Player player
    ) {
        // Create an inverted camera transformation of the player's view direction
        World playerWorld = player.getWorld();
        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(Util.getRealEyeLocation(player));
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

                // Too far away
                if (pos.getZ() > TCConfig.maxTrainEditdistance) {
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
            Localization.EDIT_NOTOWNED.message(player);
        } else if (bestMember != null) {
            // Play a particle effect shooting upwards from the Minecart
            final Entity memberEntity = bestMember.getEntity().getEntity();
            new Task(plugin) {
                final int batch_ctr = 5;
                double dy = 0.0;

                @Override
                public void run() {
                    for (int i = 0; i < batch_ctr; i++) {
                        if (dy > 50.0 || !player.isValid() || memberEntity.isDead()) {
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
            plugin.getPlayer(player).editMember(bestMember);
            Localization.EDIT_SUCCESS.message(player, bestMember.getGroup().getProperties().getTrainName());
        } else {
            player.sendMessage(ChatColor.RED + "You are not looking at any Minecart right now");
            player.sendMessage(ChatColor.RED + "Please enter the exact name of the train to edit");
            commandListTrains(plugin, player, null);
        }
    }

    @Command("train edit <trainname>")
    @CommandDescription("Forcibly removes minecarts and trackers that have glitched out")
    private void commandEditByName(
            final TrainCarts plugin,
            final Player sender,
            final @Quoted @Argument(value="trainname", suggestions="quoted_trainnames") String trainName
    ) {
        TrainProperties prop = TrainProperties.get(trainName);
        if (prop == null) {
            prop = TrainProperties.getRelaxed(trainName);
        }
        if (prop != null && !prop.isEmpty()) {
            if (prop.hasOwnership((Player) sender)) {
                plugin.getPlayer((Player) sender).editCart(prop.get(0));
                Localization.EDIT_SUCCESS.message(sender, prop.getTrainName());
            } else {
                Localization.EDIT_NOTOWNED.message(sender);
            }
        } else {
            Localization.EDIT_NOTFOUND.message(sender, trainName);
            commandListTrains(plugin, sender, null);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @Command("train tick disable")
    @CommandDescription("Disables ticking of all trains, causing all physics to pause")
    private void commandTickDisable(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        commandSetTickEnabled(sender, plugin, false);
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @Command("train tick enable")
    @CommandDescription("Enables ticking of all trains, causing all physics to resume")
    private void commandTickEnable(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        commandSetTickEnabled(sender, plugin, true);
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @Command("train tick toggle")
    @CommandDescription("Toggles ticking of all trains, causing all physics to pause or resume")
    private void commandTickToggle(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        commandSetTickEnabled(sender, plugin, plugin.getTrainUpdateController().getTickDivider() == Integer.MAX_VALUE);
    }

    private void commandSetTickEnabled(CommandSender sender, TrainCarts plugin, boolean enabled) {
        plugin.getTrainUpdateController().setTickDivider(enabled ? 1 : Integer.MAX_VALUE);
        sender.sendMessage(ChatColor.YELLOW + "Train tick updates have been globally " +
                (enabled ? (ChatColor.GREEN + "enabled") : (ChatColor.RED + "disabled")));
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @Command("train tick div")
    @CommandDescription("Checks what kind of tick divider configuration is configured")
    private void commandGetTickDivider(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        int divider = plugin.getTrainUpdateController().getTickDivider();
        if (divider == Integer.MAX_VALUE) {
            sender.sendMessage(ChatColor.YELLOW + "Automatic train tick updates are globally disabled");
        } else {
            sender.sendMessage(ChatColor.GREEN + "The tick rate divider is currently set to " + ChatColor.YELLOW + divider);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @Command("train tick div reset")
    @CommandDescription("Resets any previous global tick divider, resuming physics as normal")
    private void commandResetTickDivider(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        commandSetTickDivider(sender, plugin, 1);
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @Command("train tick div <divider>")
    @CommandDescription("Configures a global tick divider, causing all physics to run more slowly")
    private void commandSetTickDivider(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Argument("divider") int divider
    ) {
        if (divider > 1) {
            plugin.getTrainUpdateController().setTickDivider(divider);
            sender.sendMessage(ChatColor.GREEN + "The tick rate divider has been set to " + ChatColor.YELLOW + divider);
        } else {
            plugin.getTrainUpdateController().setTickDivider(1);
            sender.sendMessage(ChatColor.GREEN + "The tick rate divider has been reset to the default");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @Command("train tick")
    @CommandDescription("Performs a single update tick. Useful when automatic ticking is disabled or slowed down.")
    private void commandPerformTick(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        commandPerformTick(sender, plugin, 1);
    }

    @CommandRequiresPermission(Permission.COMMAND_CHANGETICK)
    @Command("train tick <times>")
    @CommandDescription("Performs a burst of update ticks. Useful when automatic ticking is disabled or slowed down.")
    private void commandPerformTick(
            final CommandSender sender,
            final TrainCarts plugin,
            final @Argument("times") @Range(min="1") int number
    ) {
        plugin.getTrainUpdateController().step(number);
        if (number <= 1) {
            sender.sendMessage(ChatColor.GREEN + "Trains ticked once");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Trains ticked " + number + " times");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_ISSUE)
    @Command("train issue")
    @CommandDescription("Shows helpful information for posting an issue ticket on our Github")
    private void commandIssueTicket(
            final CommandSender sender,
            final TrainCarts plugin
    ) {
        if(sender instanceof Player){
            Player player = (Player)sender;

            ChatText chatText = ChatText.fromMessage(ChatColor.YELLOW.toString() + "Click one of the below options to open an issue on GitHub:");
            chatText.sendTo(player);
            try{
                String bugReport = "## Info" +
                        "\nPlease provide the following information:" +
                        "\n" +
                        "\n- BKCommonLib Version: " + CommonPlugin.getInstance().getDebugVersion() +
                        "\n- TrainCarts Version: " + plugin.getDebugVersion() +
                        "\n- Server Type and Version: " + Bukkit.getVersion() +
                        "\n" +
                        "\n----" +
                        "\n## Bug" +
                        "\n" +
                        "\n### Description" +
                        "\n" +
                        "\n### Expected Behaviour" +
                        "\n" +
                        "\n### Actual Behaviour" +
                        "\n" +
                        "\n### Steps to reproduce" +
                        "\n" +
                        "\n### Additional Information" +
                        "\n*This issue was created using the `/train issue` command!*";
                
                String featureRequest = "## Feature Request" +
                        "\n" +
                        "\n### Description" +
                        "\n" +
                        "\n### Examples";
                
                chatText = ChatText.empty().appendClickableURL(ChatColor.RED.toString() + ChatColor.UNDERLINE.toString() + "Bug Report", 
                        "https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(bugReport, "UTF-8"),
                        "Click to open a Bug Report");
                chatText.sendTo(player);
                
                chatText = ChatText.empty().appendClickableURL(ChatColor.GREEN.toString() + ChatColor.UNDERLINE.toString() + "Feature Request",
                        "https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(featureRequest, "UTF-8"),
                        "Click to open a Feature Request");
                chatText.sendTo(player);
            }catch(UnsupportedEncodingException ex){
                chatText = ChatText.empty().appendClickableURL(ChatColor.RED.toString() + ChatColor.UNDERLINE.toString() + "Bug Report",
                        "https://github.com/bergerhealer/TrainCarts/issues/new?template=bug_report.md",
                        "Click to open a Bug Report");
                chatText.sendTo(player);
                
                chatText = ChatText.empty().appendClickableURL(ChatColor.GREEN.toString() + ChatColor.UNDERLINE.toString() + "Feature Request",
                        "https://github.com/bergerhealer/TrainCarts/issues/new?template=feature_request.md",
                        "Click to open a Feature Request");
                chatText.sendTo(player);
            }
        }else{
            MessageBuilder builder = new MessageBuilder();
            builder.white("Click one of the below URLs to open an issue on GitHub:");
            
            try{
                String bugReport = "## Info" +
                        "\nPlease provide the following information:" +
                        "\n" +
                        "\n- BKCommonLib Version: " + CommonPlugin.getInstance().getDebugVersion() +
                        "\n- TrainCarts Version: " + plugin.getDebugVersion() +
                        "\n- Server Type and Version: " + Bukkit.getVersion() +
                        "\n" +
                        "\n----" +
                        "\n## Bug" +
                        "\n" +
                        "\n### Description" +
                        "\n" +
                        "\n### Expected Behaviour" +
                        "\n" +
                        "\n### Actual Behaviour" +
                        "\n" +
                        "\n### Steps to reproduce" +
                        "\n" +
                        "\n### Additional Information" +
                        "\n*This issue was created using the `/train issue` command!*";

                String featureRequest = "## Feature Request" +
                        "\n" +
                        "\n### Description" +
                        "\n" +
                        "\n### Examples";
                
                builder.white("Bug Report: https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(bugReport, "UTF-8"))
                       .append("Feature Request: https://github.com/bergerhealer/TrainCarts/issues/new?body=" + URLEncoder.encode(featureRequest, "UTF-8"));
            }catch(UnsupportedEncodingException ex){
                builder.white("Bug Report: https://github.com/bergerhealer/TrainCarts/issues/new?template=bug_report.md")
                       .append("Feature Request: https://github.com/bergerhealer/TrainCarts/issues/new?template=feature_request.md");
            }
            builder.send(sender);
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("train debug editor")
    @CommandDescription("Gives a legacy editor map item (broken)")
    private void commandGiveEditor(
            final Player sender
    ) {
        sender.getInventory().addItem(TCMapControl.createTCMapItem());
        sender.sendMessage("Given editor map item (note: broken)");
    }

    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("train attachments")
    @CommandDescription("Gives an attachment editor map item to the player")
    private void commandGiveAttachmentEditor(
            final Player sender
    ) {
        CommonItemStack item = CommonItemStack.of(MapDisplay.createMapItem(AttachmentEditor.class))
                .setCustomNameMessage("Traincarts Attachments Editor")
                .setFilledMapColor(0xFF0000);
        sender.getInventory().addItem(item.toBukkit());
        sender.sendMessage(ChatColor.GREEN + "Given a Traincarts attachments editor");
    }

    public static void listTrains(TrainCarts plugin, CommandSender sender, String filter) {
        MessageBuilder builder = new MessageBuilder();
        builder.setSeparator(" / ");

        if (filter.startsWith("@train[")) {
            // Trim trailing spaces
            while (filter.endsWith(" ")) {
                filter = filter.substring(0, filter.length() - 1);
            }

            // Check closed
            if (!filter.endsWith("]")) {
                Localization.COMMAND_INPUT_SELECTOR_INVALID.message(sender, filter.substring(7));
                return;
            }

            // Fully parse out the selector conditions specified
            String conditionsString = filter.substring(7, filter.length() - 1);
            List<SelectorCondition> conditions = SelectorCondition.parseAll(conditionsString);
            if (conditions == null) {
                Localization.COMMAND_INPUT_SELECTOR_INVALID.message(sender, conditionsString);
                return;
            }

            // Let the train name handler handle this one
            try {
                plugin.getSelectorHandlerRegistry().find("train")
                                                   .handle(sender, "train", conditions)
                                                   .forEach(builder::append);
            } catch (SelectorException ex) {
                sender.sendMessage(ChatColor.RED + "[TrainCarts] " + ex.getMessage());
                return;
            }

            ChatText.fromMessage(ChatColor.YELLOW + "The ")
                .append(ChatText.fromClickableContent(ChatColor.BLUE.toString() + ChatColor.UNDERLINE + "selector", filter)
                                .setHoverText("Click to copy selector to Clipboard"))
                .append(ChatColor.YELLOW + " matches the following trains:")
                .sendTo(sender);
        } else {
            // Default list command / uses statements
            if (sender instanceof Player) {
                sender.sendMessage(ChatColor.YELLOW + "You are the proud owner of the following trains:");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "The following trains exist on this server:");
            }

            boolean found = false;
            for (TrainProperties prop : TrainProperties.getAll()) {
                if (sender instanceof Player && !prop.hasOwnership((Player) sender)) {
                    continue;
                }

                // Check if train is loaded, or stored in a loaded world
                if (!prop.hasHolder() && !prop.getTrainCarts().getOfflineGroups().containsInLoadedWorld(prop.getTrainName())) {
                    continue;
                }

                if (prop.hasHolder() && !filter.isEmpty()) {
                    MinecartGroup group = prop.getHolder();
                    if (!Statement.has(group, filter, null)) {
                        continue;
                    }
                }
                found = true;
                builder.append(prop.getTrainName());
            }
            if (!found) {
                Localization.EDIT_NONEFOUND.message(sender);
                return;
            }
        }

        //builder.send(sender);

        // Turn the train names into clickable items, which when clicked, run /train edit [trainname]
        for (String line : builder.lines()) {
            String[] trainNames = line.split(Pattern.quote(" / "));

            // Rebuild a new line with clickable items and hover display details
            ChatText combined = ChatText.empty();
            for (int i = 0; i < trainNames.length; i++) {
                if (i > 0) {
                    combined.append(ChatColor.WHITE + " / ");
                }
                combined.append(listFormatTrainName(trainNames[i]));
            }
            combined.sendTo(sender);
        }
    }

    private static ChatText listFormatTrainName(String name) {
        TrainProperties properties = TrainProperties.get(name);
        if (properties == null) {
            return ChatText.fromMessage(ChatColor.RED + name);
        }

        ChatText text;
        if (properties.isLoaded() && !properties.isEmpty()) {
            CommonEntity<?> head = properties.getHolder().head().getEntity();
            String worldName = head.getWorld().getName();
            IntVector3 block = head.loc.block();
            text = ChatText.fromMessage(ChatColor.GREEN.toString() + ChatColor.UNDERLINE + name);
            text.setHoverText(ChatColor.GREEN + "Loaded in world " + ChatColor.YELLOW + worldName +
                              ChatColor.GREEN + " at " +
                              ChatColor.WHITE + block.x + "/" + block.y + "/" + block.z);
        } else {
            text = ChatText.fromMessage(ChatColor.RED.toString() + ChatColor.UNDERLINE + name);
            text.setHoverText(ChatColor.RED + "Not loaded");
        }

        String safeEditName = StringUtil.stripChatStyle(name);
        text.setClickableRunCommand("/train edit " + Util.escapeQuotedArgument(safeEditName));
        return text;
    }
}
