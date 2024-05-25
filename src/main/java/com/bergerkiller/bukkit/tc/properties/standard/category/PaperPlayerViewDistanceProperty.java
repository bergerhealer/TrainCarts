package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatChangeEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatEnterEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatExitEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;

/**
 * Paper server only. Applies a new view distance to players that enter a cart
 * with a view distance set, and resets view distance when the player exits the
 * cart again.
 */
public final class PaperPlayerViewDistanceProperty implements ICartProperty<Integer>, Listener {
    public static final PaperPlayerViewDistanceProperty INSTANCE = new PaperPlayerViewDistanceProperty();
    private final Map<Player, PreviousViewSettings> previousViewSettings = new HashMap<>();
    private final FastMethod<Integer> getViewDistance = new FastMethod<>();
    private final FastMethod<Void> setViewDistance = new FastMethod<>();
    private final FastMethod<Integer> getSimulationDistance = new FastMethod<>();
    private final FastMethod<Void> setSimulationDistance = new FastMethod<>();
    private final FastMethod<Integer> getChunkSendDistance = new FastMethod<>();
    private final FastMethod<Void> setChunkSendDistance = new FastMethod<>();

    @CommandTargetTrain
    @PropertyCheckPermission("viewdistance")
    @Command("train viewdistance reset")
    @CommandDescription("Resets the view distance players inside the train have to the defaults")
    private void resetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        setProperty(sender, properties, -1);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("viewdistance")
    @Command("train viewdistance <num_chunks>")
    @CommandDescription("Sets the view distance players inside the train have")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("num_chunks") int distance
    ) {
        properties.set(this, distance);
        getProperty(sender, properties);
    }

    @Command("train viewdistance")
    @CommandDescription("Displays the view distance players inside the train have")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        int distance = properties.get(this);
        if (distance >= 0) {
            sender.sendMessage(ChatColor.YELLOW + "View distance of players in the train: " +
                    ChatColor.WHITE + distance + " chunks");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "View distance of players in the train: " +
                    ChatColor.RED + "Default (not set)");
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("viewdistance")
    @Command("cart viewdistance reset")
    @CommandDescription("Resets the view distance players inside the cart have to the defaults")
    private void resetProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        setProperty(sender, properties, -1);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("viewdistance")
    @Command("cart viewdistance <num_chunks>")
    @CommandDescription("Sets the view distance players inside the cart have")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("num_chunks") int distance
    ) {
        properties.set(this, distance);
        getProperty(sender, properties);
    }

    @Command("cart viewdistance")
    @CommandDescription("Displays the view distance players inside the cart have")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        int distance = properties.get(this);
        if (distance >= 0) {
            sender.sendMessage(ChatColor.YELLOW + "View distance of players in the cart: " +
                    ChatColor.WHITE + distance + " chunks");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "View distance of players in the cart: " +
                    ChatColor.RED + "Default (not set)");
        }
    }

    @PropertyParser("viewdistance|playerviewdistance")
    public int parseViewDistance(PropertyParseContext<Integer> context) {
        return context.inputInteger();
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_VIEW_DISTANCE.has(sender);
    }

    @Override
    public Integer getDefault() {
        return -1;
    }

    @Override
    public Optional<Integer> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "paperPlayerViewDistance", int.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Integer> value) {
        Util.setConfigOptional(config, "paperPlayerViewDistance", value);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(event.getPlayer().getVehicle());
        if (member != null && member.getProperties().get(this) >= 0) {
            restore(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberSeatExit(MemberSeatExitEvent event) {
        if (!event.isPlayer()) {
            return; // Not a player
        }
        if (!event.isMemberVehicleChange()) {
            return; // No change.
        }
        if (event.isSeatChange()) {
            MinecartMember<?> newMember = ((MemberSeatChangeEvent) event).getEnteredMember();
            int newViewDistance = newMember.getProperties().get(this);
            if (newViewDistance >= 0) {
                // Set the new view distance, nothing else is done.
                apply((Player) event.getEntity(), newViewDistance);
            } else if (event.getMember().getProperties().get(this) >= 0) {
                // Restore the original view distance the player had, if any
                restore((Player) event.getEntity());
            }
            return; // Handled.
        }

        // Restore if a view distance was set for the member being exited
        if (event.getMember().getProperties().get(this) >= 0) {
            restore((Player) event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberSeatEnter(MemberSeatEnterEvent event) {
        if (!event.isPlayer()) {
            return; // Not a player
        }
        if (event.wasSeatChange()) {
            return; // Already handled
        }

        // Apply new view distance if property is set
        int viewDistance = event.getMember().getProperties().get(this);
        if (viewDistance >= 0) {
            apply((Player) event.getEntity(), viewDistance);
        }
    }

    @Override
    public void set(CartProperties properties, Integer value) {
        boolean hadViewDistance = get(properties) >= 0;
        ICartProperty.super.set(properties, value);

        MinecartMember<?> member = properties.getHolder();
        if (member != null && !member.isUnloaded()) {
            if (hadViewDistance && value.intValue() < 0) {
                for (Player player : member.getEntity().getPlayerPassengers()) {
                    this.restore(player);
                }
            } else if (value.intValue() >= 0) {
                for (Player player : member.getEntity().getPlayerPassengers()) {
                    this.apply(player, value.intValue());
                }
            }
        }
    }

    public void enable(TrainCarts plugin) throws Throwable {
        getViewDistance.init(Player.class.getMethod("getViewDistance"));
        setViewDistance.init(Player.class.getMethod("setViewDistance", int.class));
        getSimulationDistance.init(Player.class.getMethod("getNoTickViewDistance"));
        setSimulationDistance.init(Player.class.getMethod("setNoTickViewDistance", int.class));
        getChunkSendDistance.init(Player.class.getMethod("getSendViewDistance"));
        setChunkSendDistance.init(Player.class.getMethod("setSendViewDistance", int.class));
        getViewDistance.forceInitialization();
        setViewDistance.forceInitialization();
        getSimulationDistance.forceInitialization();
        setSimulationDistance.forceInitialization();
        getChunkSendDistance.forceInitialization();
        setChunkSendDistance.forceInitialization();

        plugin.register((Listener) this);
    }

    public void disable(TrainCarts plugin) {
        // Restore view distances on shutdown. Dunno if important.
        for (Map.Entry<Player, PreviousViewSettings> e : previousViewSettings.entrySet()) {
            e.getValue().restore(e.getKey());
        }
    }

    private void restore(Player player) {
        PreviousViewSettings prevViewSettings = previousViewSettings.remove(player);
        if (prevViewSettings != null) {
            prevViewSettings.restore(player);
        }
    }

    private void apply(Player player, int viewDistance) {
        // Before applying, if we have no previous view distance for this player, store
        // the original view distance the player had.
        previousViewSettings.computeIfAbsent(player, PreviousViewSettings::new);

        // Set the new view distance. Limit to a min of 3
        viewDistance = MathUtil.clamp(viewDistance, 2, 31);
        setSimulationDistance.invoke(player, viewDistance + 1);
        setViewDistance.invoke(player, viewDistance + 1);
        setChunkSendDistance.invoke(player, viewDistance);
    }

    private class PreviousViewSettings {
        public final int viewDistance;
        public final int simulationDistance;
        public final int chunkSendDistance;

        public PreviousViewSettings(Player player) {
            this.viewDistance = getViewDistance.invoke(player);
            this.simulationDistance = getSimulationDistance.invoke(player);
            this.chunkSendDistance = getChunkSendDistance.invoke(player);
        }

        public void restore(Player player) {
            setSimulationDistance.invoke(player, this.simulationDistance);
            setChunkSendDistance.invoke(player, this.chunkSendDistance);
            setViewDistance.invoke(player, this.viewDistance);
        }

        @Override
        public String toString() {
            return "View{distance=" + this.viewDistance + ", simulation=" + this.simulationDistance +
                    ", chunk=" + this.chunkSendDistance + "}";
        }
    }
}
