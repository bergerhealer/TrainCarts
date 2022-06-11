package com.bergerkiller.bukkit.tc.debug;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import com.bergerkiller.bukkit.common.bases.IntVector2;
import com.bergerkiller.bukkit.common.bases.IntVector3;
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
import com.bergerkiller.bukkit.tc.utils.EventListenerHook;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Quoted;

/**
 * Commands starting with /train debug.
 * Some turn on debug live features, like showing particles,
 * others update an item like a debug stick.
 */
public class DebugCommands {

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug event vehicle_enter [enabled]")
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
    @CommandMethod("train debug event vehicle_exit [enabled]")
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
    @CommandMethod("train debug rails")
    @CommandDescription("Get a debug stick item to visually display what path tracks use")
    private void commandDebugRails(
            final Player player
    ) {
        (new DebugToolTypeRails()).giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug distance")
    @CommandDescription("Get a debug stick item to display the track distance between two points")
    private void commandDebugTrackDistance(
            final Player player
    ) {
        (new DebugToolTypeTrackDistance()).giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug destination")
    @CommandDescription("Get a debug stick item to visually display the possible path finding routes")
    private void commandDebugDestinationAll(
            final Player player
    ) {
        (new DebugToolTypeListDestinations()).giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug destination <destination>")
    @CommandDescription("Get a debug stick item to visually display the route towards a destination")
    private void commandDebugDestinationName(
            final Player player,
            final @Quoted @Argument("destination") String destination
    ) {
        (new DebugToolTypeListDestinations(destination)).giveToPlayer(player);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug mutex")
    @CommandDescription("Displays the area of effect of all nearby mutex signs")
    private void commandDebugMutex(
            final Player player
    ) {
        DebugTool.showMutexZones(player);
        player.sendMessage(ChatColor.GREEN + "Displaying mutex zones near your position");
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug railtracker <enabled>")
    @CommandDescription("Sets whether the rail tracker debugging is currently enabled")
    private void commandDebugSetRailTracker(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        TCConfig.railTrackerDebugEnabled = enabled;
        commandDebugCheckRailTracker(sender);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug railtracker")
    @CommandDescription("Checks whether the rail tracker debugging is currently enabled")
    private void commandDebugCheckRailTracker(
            final CommandSender sender
    ) {
        sender.sendMessage(ChatColor.GREEN + "Displaying tracked rail positions: " +
                (TCConfig.railTrackerDebugEnabled ? "ENABLED" : (ChatColor.RED + "DISABLED")));
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug wheeltracker <enabled>")
    @CommandDescription("Sets whether the rail tracker debugging is currently enabled")
    private void commandDebugSetWheelTracker(
            final CommandSender sender,
            final @Argument("enabled") boolean enabled
    ) {
        TCConfig.wheelTrackerDebugEnabled = enabled;
        commandDebugCheckWheelTracker(sender);
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug wheeltracker")
    @CommandDescription("Checks whether the wheel tracker debugging is currently enabled")
    private void commandDebugCheckWheelTracker(
            final CommandSender sender
    ) {
        sender.sendMessage(ChatColor.GREEN + "Displaying tracked wheel positions: " +
                (TCConfig.wheelTrackerDebugEnabled ? "ENABLED" : (ChatColor.RED + "DISABLED")));
    }

    @CommandRequiresPermission(Permission.DEBUG_COMMAND_DEBUG)
    @CommandMethod("train debug fix signs")
    @CommandDescription("Forcibly recalculates all cached sign information near the player")
    private void commandDebugCheckWheelTracker(
            final Player player,
            final TrainCarts plugin
    ) {
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
    }
}
