package com.bergerkiller.bukkit.tc.debug;

import java.util.Collection;
import java.util.Collections;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ItemDisplayMode;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayItemEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.particle.VirtualBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.surface.CollisionSurface;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld;
import com.bergerkiller.bukkit.tc.debug.types.DebugToolTypeListDestinations;
import com.bergerkiller.bukkit.tc.debug.types.DebugToolTypeRails;
import com.bergerkiller.bukkit.tc.debug.types.DebugToolTypeTrackDistance;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.offline.train.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.EventListenerHook;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;

/**
 * Commands starting with /train debug.
 * Some turn on debug live features, like showing particles,
 * others update an item like a debug stick.
 */
public class DebugCommands {

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug event vehicle_enter [enabled]")
    @CommandDescription("Broadcasts a message when a vehicle enter is cancelled by a plugin")
    private void commandDebugEventVehicleEnter(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        sender.sendMessage(ChatColor.RED + "Vehicle enter debug mode: " + Localization.boolStr(enabled));
        if (enabled) {
            EventListenerHook.hook(VehicleEnterEvent.class, (listener, callEvent, event) -> {
                boolean wasCancelled = event.isCancelled();
                callEvent.accept(event);
                if (
                        !wasCancelled
                        && event.isCancelled()
                        && MinecartMemberStore.getFromEntity(event.getVehicle()) != null
                ) {
                    Bukkit.broadcastMessage("[TrainCarts] Vehicle enter by " +
                            event.getEntered().getName() + " was cancelled by plugin " +
                            listener.getPlugin().getName());
                }
            });
            sender.sendMessage(ChatColor.YELLOW + "A message will be broadcast when entering a traincarts "
                    + "minecart is cancelled by a plugin, with details");
            sender.sendMessage(ChatColor.YELLOW + "Use /train debug vehicle_enter false to turn off again");
        } else {
            EventListenerHook.unhook(VehicleEnterEvent.class);
        }
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug event vehicle_exit [enabled]")
    @CommandDescription("Broadcasts a message when a vehicle exit is cancelled by a plugin")
    private void commandDebugEventVehicleExit(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        sender.sendMessage(ChatColor.RED + "Vehicle exit debug mode: " + Localization.boolStr(enabled));
        if (enabled) {
            EventListenerHook.hook(VehicleExitEvent.class, (listener, callEvent, event) -> {
                boolean wasCancelled = event.isCancelled();
                callEvent.accept(event);
                if (
                        !wasCancelled
                        && event.isCancelled()
                        && MinecartMemberStore.getFromEntity(event.getVehicle()) != null
                ) {
                    Bukkit.broadcastMessage("[TrainCarts] Vehicle exit by " +
                            event.getExited().getName() + " was cancelled by plugin " +
                            listener.getPlugin().getName());
                }
            });
            sender.sendMessage(ChatColor.YELLOW + "A message will be broadcast when exiting a traincarts "
                    + "minecart is cancelled by a plugin, with details");
            sender.sendMessage(ChatColor.YELLOW + "Use /train debug vehicle_exit false to turn off again");
        } else {
            EventListenerHook.unhook(VehicleExitEvent.class);
        }
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug rails")
    @CommandDescription("Get a debug stick item to visually display what path tracks use")
    private void commandDebugRails(
            final Player player
    ) {
        (new DebugToolTypeRails()).giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug distance")
    @CommandDescription("Get a debug stick item to display the track distance between two points")
    private void commandDebugTrackDistance(
            final Player player
    ) {
        (new DebugToolTypeTrackDistance()).giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug destinations")
    @CommandDescription("Get a debug stick item to visually display the possible path finding routes")
    private void commandDebugDestinationAll(
            final Player player,
            final @Flag("max-destinations") Integer maxDestinations
    ) {
        (new DebugToolTypeListDestinations())
                .setMaxDestinations(maxDestinations != null ? maxDestinations : 5)
                .giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug destination")
    @CommandDescription("Get a debug stick item to visually display the possible path finding routes")
    private void commandDebugDestinationAll(
            final Player player
    ) {
        (new DebugToolTypeListDestinations()).giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug destination <destination>")
    @CommandDescription("Get a debug stick item to visually display the route towards a destination")
    private void commandDebugDestinationName(
            final Player player,
            final @Quoted @Argument(value="destination", suggestions="destinations") String destination
    ) {
        (new DebugToolTypeListDestinations(destination)).giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug destination <destination> teleport")
    @CommandDescription("Get a debug stick item to visually display the route towards a destination")
    private void commandDebugTeleportToDestination(
            final Player player,
            final TrainCarts plugin,
            final @Quoted @Argument(value="destination", suggestions="destinations") String destination
    ) {
        // Try to find this PathNode
        PathNode node = plugin.getPathProvider().getWorld(player.getWorld()).getNodeByName(destination);
        if (node == null) {
            // Find on other worlds
            node = plugin.getPathProvider().getWorlds().stream()
                    .map(w -> w.getNodeByName(destination))
                    .findFirst()
                    .orElse(null);
            if (node == null) {
                player.sendMessage(ChatColor.RED + "Destination with name '" + destination + "' not found");
                return;
            }
        }

        // Find the rails block
        RailPiece rail = RailPiece.create(node.location.getBlock());
        if (rail.isNone()) {
            player.sendMessage(ChatColor.RED + "There are no rails at this destination! (No longer exists?)");
            return;
        }
        RailState spawnState = RailState.getSpawnState(rail);
        player.teleport(spawnState.positionLocation());
        player.sendMessage(ChatColor.GREEN + "Teleported to destination '" + ChatColor.YELLOW + destination + ChatColor.GREEN + "'!");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug mutex")
    @CommandDescription("Displays the area of effect of all nearby mutex signs")
    private void commandDebugMutex(
            final Player player,
            final TrainCarts traincarts
    ) {
        DebugTool.showMutexZones(traincarts, player);
        player.sendMessage(ChatColor.GREEN + "Displaying mutex zones near your position");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug railtracker <enabled>")
    @CommandDescription("Sets whether the rail tracker debugging is currently enabled")
    private void commandDebugSetRailTracker(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        TCConfig.railTrackerDebugEnabled = enabled;
        commandDebugCheckRailTracker(sender);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug railtracker")
    @CommandDescription("Checks whether the rail tracker debugging is currently enabled")
    private void commandDebugCheckRailTracker(
            final CommandSender sender
    ) {
        sender.sendMessage(ChatColor.GREEN + "Displaying tracked rail positions: " +
                (TCConfig.railTrackerDebugEnabled ? "ENABLED" : (ChatColor.RED + "DISABLED")));
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug wheeltracker <enabled>")
    @CommandDescription("Sets whether the rail tracker debugging is currently enabled")
    private void commandDebugSetWheelTracker(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        TCConfig.wheelTrackerDebugEnabled = enabled;
        commandDebugCheckWheelTracker(sender);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug wheeltracker")
    @CommandDescription("Checks whether the wheel tracker debugging is currently enabled")
    private void commandDebugCheckWheelTracker(
            final CommandSender sender
    ) {
        sender.sendMessage(ChatColor.GREEN + "Displaying tracked wheel positions: " +
                (TCConfig.wheelTrackerDebugEnabled ? "ENABLED" : (ChatColor.RED + "DISABLED")));
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug splitting <enabled>")
    @CommandDescription("Sets whether messages are logged when trains split apart")
    private void commandDebugSetSplitDebugEnabled(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        TCConfig.logTrainSplitting = enabled;
        commandDebugCheckSplitDebugEnabled(sender);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug splitting")
    @CommandDescription("Checks whether messages are logged when trains split apart")
    private void commandDebugCheckSplitDebugEnabled(
            final CommandSender sender
    ) {
        sender.sendMessage(ChatColor.GREEN + "Logging messages when trains split apart: " +
                (TCConfig.logTrainSplitting ? "ENABLED" : (ChatColor.RED + "DISABLED")));
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug fix signs")
    @CommandDescription("Forcibly recalculates all cached sign information near the player")
    private void commandDebugFixSigns(
            final Player player,
            final TrainCarts plugin,
            final @Flag("redetect_actions") boolean redetectSignActions
    ) {
        if (!TCConfig.enableVanillaActionSigns) {
            player.sendMessage(ChatColor.RED + "Vanilla action signs are disabled in TrainCarts config.yml!");
            return;
        }

        int radius = Bukkit.getViewDistance() - 1; // 1 less, chunks need chunks loaded around it
        IntVector2 mid = IntVector3.blockOf(player.getLocation()).toChunkCoordinates();
        SignControllerWorld controller = plugin.getSignController().forWorld(player.getWorld());
        SignControllerWorld.RefreshResult result = SignControllerWorld.RefreshResult.NONE;
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                Chunk chunk = WorldUtil.getChunk(player.getWorld(), mid.x + cx, mid.z + cz);
                if (chunk != null) {
                    result = result.add(controller.refreshInChunk(chunk));
                }
            }
        }
        if (result.numAdded == 0 && result.numRemoved == 0) {
            player.sendMessage(ChatColor.GREEN + "All signs are correctly cached");
        } else {
            if (result.numRemoved > 0) {
                player.sendMessage(ChatColor.RED.toString() + result.numRemoved + " signs were removed from the cache because they were incorrect!");
            }
            if (result.numAdded > 0) {
                player.sendMessage(ChatColor.YELLOW.toString() + result.numAdded + " signs were missing and have been added to the cache!");
            }
        }

        if (redetectSignActions) {
            plugin.redetectSignActions();
            player.sendMessage(ChatColor.GREEN + "Recalculated the registered sign action for all signs on the server");
        }
    }

    @CommandRequiresPermission(Permission.COMMAND_FIXBUGGED)
    @Command("train debug fix buggedminecarts")
    @CommandDescription("Forcibly removes minecarts and trackers that have glitched out")
    private void commandFixBugged(
            final CommandSender sender
    ) {
        for (World world : WorldUtil.getWorlds()) {
            OfflineGroupManager.removeBuggedMinecarts(world);
        }
        sender.sendMessage(ChatColor.YELLOW + "Bugged minecarts have been forcibly removed.");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug railcache export")
    @CommandDescription("Exports the rail block coordinates inside the current player world's rail cache")
    private void commandDebugRailCacheExport(
            final Player player,
            final TrainCarts plugin
    ) {
        Collection<IntVector3> blocks = RailLookup.forWorld(player.getWorld()).getBlockIndex();
        StringBuffer buffer = new StringBuffer(blocks.size() * 20);
        for (IntVector3 block : blocks) {
            buffer.append(block.x).append(' ').append(block.y).append(' ')
                  .append(block.z).append("\r\n");
        }
        TCConfig.hastebin.upload(buffer.toString()).thenAccept(t -> {
            if (t.success()) {
                player.sendMessage(ChatColor.GREEN + "Rail cache block index exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
            } else {
                player.sendMessage(ChatColor.RED + "Failed to export rail cache block coordinates: " + t.error());
            }
        });
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug loading unload")
    @CommandDescription("Forces the targeted train to unload, even if it otherwise wouldn't")
    private void commandDebugUnloadTrain(
            final CommandSender sender,
            final MinecartGroup group
    ) {
        String name = group.getProperties().getTrainName();
        group.unload();
        sender.sendMessage(ChatColor.YELLOW + "Train '" + ChatColor.WHITE + name +
                ChatColor.YELLOW + "' unloaded!");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug loading refresh")
    @CommandDescription("Forcibly checks all unloaded trains if they can be loaded, and loads them in")
    private void commandDebugForceLoadTrains(
            final CommandSender sender,
            final TrainCarts trainCarts
    ) {
        int loadedBefore = MinecartGroupStore.getGroups().size();
        trainCarts.getOfflineGroups().refresh();
        int loadedAfter = MinecartGroupStore.getGroups().size();
        if (loadedBefore == loadedAfter) {
            sender.sendMessage(ChatColor.YELLOW + "Forcibly refreshed trains on all worlds, no trains loaded");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Forcibly refreshed trains on all worlds, " +
                    ChatColor.WHITE + (loadedAfter - loadedBefore) + ChatColor.YELLOW +
                    " trains loaded");
        }
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug pvc fly")
    private void commandTestFlight(final Player player, final TrainCarts plugin) {
        final Vector center = new Vector(-175.5, 4.1 + 10.0, 354.5);

        new Task(plugin) {
            Quaternion rotation = new Quaternion();
            AttachmentViewer.MovementController controller = plugin.getAttachmentViewer(player).controlMovement();
            Location loc = player.getLocation();
            int ctr = 0;
            final int duration = 200000;
            double rotX = 0.0;
            double rotY = 0.0;
            double radius = 1.0;
            double speed = 0.0;
            final double incr = 0.5;
            Vector lastMotion = new Vector();

            @Override
            public void run() {
                ++ctr;
                if (ctr > (5+duration)) {
                    controller.stop();
                    stop();
                }
                if (ctr > (2+duration)) {
                    return;
                }

                if (player.isSneaking()) {
                    player.teleport(center.toLocation(player.getWorld()));
                    controller.stop();
                    stop();
                    return;
                }
                if (!player.isValid()) {
                    stop();
                    controller.stop();
                    return;
                }

                if (ctr <= 1) {
                    player.setFlying(true);
                    player.teleport(loc);
                    player.setFlying(true);
                }

                controller.update(loc.toVector());

                speed *= 0.9;
                AttachmentViewer.Input input = controller.getInput();
                if (input.hasWalkInput() || input.jumping() || input.sneaking()) {
                    lastMotion = new Vector();
                    speed += 0.2;
                } else if (speed < 0.01) {
                    speed = 0.0;
                }

                Quaternion q = Quaternion.fromLookDirection(player.getEyeLocation().getDirection(), new Vector(0, 1, 0));
                if (input.jumping()) {
                    lastMotion.add(q.upVector());
                }
                if (input.forwards()) {
                    lastMotion.add(q.forwardVector());
                } else if (input.backwards()) {
                    lastMotion.add(q.forwardVector().multiply(-1.0));
                }
                if (input.left()) {
                    lastMotion.add(q.rightVector());
                } else if (input.right()) {
                    lastMotion.add(q.rightVector().multiply(-1.0));
                }

                double fixedSpeed = speed;
                if (input.sprinting()) {
                    fixedSpeed *= 2.0;
                }

                loc.add(lastMotion.clone().multiply(fixedSpeed));
            }
        }.start(5, 1);
        player.sendMessage("Started");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug pvc swing")
    private void commandTestSwing(
            final Player player,
            final TrainCarts plugin,
            final @Flag("no_horizontal") boolean no_horizontal,
            final @Flag("no_vertical") boolean no_vertical
    ) {
        final double radius = 10.0;
        final Vector center = new Vector(-175.5, 4.1 + radius, 354.5);

        new Task(plugin) {
            Quaternion rotation = new Quaternion();
            AttachmentViewer.MovementController controller = plugin.getAttachmentViewer(player).controlMovement();
            int ctr = 0;
            final int duration = 200000;

            @Override
            public void run() {
                ++ctr;
                if (ctr > (5+duration)) {
                    controller.stop();
                    stop();
                }
                if (ctr > (2+duration)) {
                    return;
                }

                if (player.isSneaking()) {
                    player.teleport(center.toLocation(player.getWorld()));
                    controller.stop();
                    stop();
                    return;
                }
                if (!player.isValid()) {
                    stop();
                    controller.stop();
                    return;
                }
                
                Vector pos = center.clone().add(rotation.forwardVector().multiply(radius));
                if (no_horizontal) {
                    pos.setX(center.getX());
                    pos.setZ(center.getZ());
                }
                if (no_vertical) {
                    pos.setY(center.getY());
                }

                if (ctr <= 1) {
                    Location loc = player.getLocation();
                    loc.setX(pos.getX());
                    loc.setY(pos.getY());
                    loc.setZ(pos.getZ());
                    player.setFlying(true);
                    player.teleport(loc);
                    player.setFlying(true);
                }
                controller.update(pos);
                if (ctr > 0) {
                    rotation.rotateX(4.0);
                }
            }
        }.start(5, 1);
        player.sendMessage("Started");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug display")
    private void commandDebugDisplayEntity(final Player player, final TrainCarts plugin) {
        final Location location = player.getEyeLocation();
        final Matrix4x4 transform = Matrix4x4.fromLocation(location);

        final VirtualDisplayItemEntity entity = new VirtualDisplayItemEntity(null);
        entity.setItem(ItemDisplayMode.HEAD,
                CommonItemStack.create(MaterialUtil.getFirst("JACK_O_LANTERN", "LEGACY_JACK_O_LANTERN"), 1)
                        .toBukkit());
        final AttachmentViewer viewer = plugin.getAttachmentViewer(player);

        entity.updatePosition(transform);
        entity.syncPosition(true);
        entity.spawn(viewer, new Vector());

        new Task(plugin) {
            double movement = 0.0;
            double fx = 0.1;

            @Override
            public void run() {
                boolean changed = false;
                double x = DebugUtil.getDoubleValue("x", 0.0);
                double y = DebugUtil.getDoubleValue("y", 0.0);
                double z = DebugUtil.getDoubleValue("z", 0.0);
                if (x != 0.0) {
                    transform.rotateX(x);
                    changed = true;
                }
                if (y != 0.0) {
                    transform.rotateY(y);
                    changed = true;
                }
                if (z != 0.0) {
                    transform.rotateZ(z);
                    changed = true;
                }

                if (changed) {
                    Quaternion rot = transform.getRotation();
                    // player.sendMessage("Quat " + rot.getX() + " / " + rot.getY() + " / " + rot.getZ() + " / " + rot.getW());
                }

                transform.worldTranslate(fx, 0.0, 0.0);
                movement += fx;
                if (Math.abs(movement) > 5.0) {
                    fx = -fx;
                }

                entity.updatePosition(transform);
                entity.syncPosition(true);
            }
        }.start(1, 1);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug sendpos")
    private void commandDebugSendPositionPacket(
            final Player player,
            final TrainCarts plugin,
            final @Flag(value="x") Double x,
            final @Flag(value="y") Double y,
            final @Flag(value="z") Double z,
            final @Flag(value="yaw") Float yaw,
            final @Flag(value="pitch") Float pitch,
            final @Flag(value="dx") Double dx,
            final @Flag(value="dy") Double dy,
            final @Flag(value="dz") Double dz,
            final @Flag(value="relpos") boolean relpos,
            final @Flag(value="reldeltapos") boolean reldeltapos,
            final @Flag(value="relrot") boolean relrot,
            final @Flag(value="look") boolean look
    ) {
        double p_x = LogicUtil.fixNull(x, 0.0);
        double p_y = LogicUtil.fixNull(y, 0.0);
        double p_z = LogicUtil.fixNull(z, 0.0);
        float p_yaw = LogicUtil.fixNull(yaw, 0.0f);
        float p_pitch = LogicUtil.fixNull(pitch, 0.0f);
        double p_dx = LogicUtil.fixNull(dx, 0.0);
        double p_dy = LogicUtil.fixNull(dy, 0.0);
        double p_dz = LogicUtil.fixNull(dz, 0.0);
        RelativeFlags flags = RelativeFlags.fromRawRelativeFlags(Collections.emptySet());
        if (relpos) {
            flags = flags.withRelativeX().withRelativeY().withRelativeZ();
        }
        if (reldeltapos) {
            flags = flags.withRelativeDeltaX().withRelativeDeltaY().withRelativeDeltaZ();
        }
        if (look || relrot) {
            flags = flags.withRelativeRotation();
        }

        PacketUtil.sendPacket(player, PacketPlayOutPositionHandle.createNew(
                p_x, p_y, p_z,
                look ? 0.0f : p_yaw,
                look ? 0.0f : p_pitch,
                p_dx, p_dy, p_dz,
                flags));

        if (look) {
        }
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @Command("train debug surface")
    private void commandDebugShulkerSurface(
            final TrainCarts plugin,
            final Player player,
            final @Flag(value="width") Double widthFlag,
            final @Flag(value="height") Double heightFlag,
            final @Flag(value="behind") boolean behind
    ) {
        AttachmentViewer viewer = plugin.getAttachmentViewer(player);
        Quaternion orientation = Quaternion.fromLookDirection(player.getEyeLocation().getDirection(), new Vector(0, 1, 0));
        OrientedBoundingBox bbox = new OrientedBoundingBox();
        Location eyeLoc = viewer.getPlayer().getEyeLocation();
        bbox.setPosition(eyeLoc.add(eyeLoc.getDirection().setY(0.0).normalize().multiply(behind ? -10.0 : 10.0)).toVector());
        bbox.setSize(LogicUtil.fixNull(widthFlag, 5.0), 0.0, LogicUtil.fixNull(heightFlag, 5.0));
        bbox.setOrientation(orientation);

        // Spawn a particle to display it
        VirtualBoundingBox particle = VirtualBoundingBox.createPlane(null, MaterialUtil.getFirst("ICE", "LEGACY_ICE"));
        particle.update(bbox);
        particle.spawn(viewer, new Vector());

        // Spawn the surface itself (stays until player logs off)
        // Refresh it every tick so that it properly switches between front / back
        final CollisionSurface surface = viewer.createCollisionSurface();
        new Task(plugin) {
            @Override
            public void run() {
                surface.clear();
                if (!viewer.isConnected()) {
                    stop();
                    return;
                }
                surface.addSurface(bbox);
            }
        }.start(1, 1);
    }
}
