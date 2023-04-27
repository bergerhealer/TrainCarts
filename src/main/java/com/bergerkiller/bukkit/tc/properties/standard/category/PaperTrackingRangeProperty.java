package com.bergerkiller.bukkit.tc.properties.standard.category;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Paper server only. Sets the tracking range for the cart/carts of the train.
 * This is from how far the entity is visible to players.
 */
public final class PaperTrackingRangeProperty implements ICartProperty<Integer> {
    public static final PaperTrackingRangeProperty INSTANCE = new PaperTrackingRangeProperty();
    private final FastMethod<Void> setCustomTrackingRange = new FastMethod<>();

    @CommandTargetTrain
    @PropertyCheckPermission("trackingrange")
    @CommandMethod("train trackingrange reset")
    @CommandDescription("Resets the view distance players inside the train have to the defaults")
    private void resetProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        setProperty(sender, properties, -1);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("trackingrange")
    @CommandMethod("train trackingrange <num_blocks>")
    @CommandDescription("Sets the view distance players inside the train have")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("num_blocks") int distance
    ) {
        properties.set(this, distance);
        getProperty(sender, properties);
    }

    @CommandMethod("train trackingrange")
    @CommandDescription("Displays the view distance players inside the train have")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        int distance = properties.get(this);
        if (distance >= 0) {
            sender.sendMessage(ChatColor.YELLOW + "Train is visible from: " +
                    ChatColor.WHITE + distance + " blocks");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Train is visible from: " +
                    ChatColor.RED + "Default (not set)");
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("trackingrange")
    @CommandMethod("cart trackingrange reset")
    @CommandDescription("Resets the view distance players inside the cart have to the defaults")
    private void resetProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        setProperty(sender, properties, -1);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("trackingrange")
    @CommandMethod("cart trackingrange <num_blocks>")
    @CommandDescription("Sets the view distance players inside the cart have")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("num_blocks") int distance
    ) {
        properties.set(this, distance);
        getProperty(sender, properties);
    }

    @CommandMethod("cart trackingrange")
    @CommandDescription("Displays the view distance players inside the cart have")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        int distance = properties.get(this);
        if (distance >= 0) {
            sender.sendMessage(ChatColor.YELLOW + "Cart is visible from: " +
                    ChatColor.WHITE + distance + " blocks");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Cart is visible from: " +
                    ChatColor.RED + "Default (not set)");
        }
    }

    @PropertyParser("trackingrange")
    public int parseTrackingRange(PropertyParseContext<Integer> context) {
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
        return Util.getConfigOptional(config, "paperTrackingRange", int.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Integer> value) {
        Util.setConfigOptional(config, "paperTrackingRange", value);
    }

    @Override
    public void set(CartProperties properties, Integer value) {
        ICartProperty.super.set(properties, value);

        MinecartMember<?> member = properties.getHolder();
        if (member != null && !member.isUnloaded()) {
            setCustomTrackingRange.invoke(member.getEntity().getEntity(), value);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                Method m = Player.class.getMethod("setSendViewDistance", int.class);
                //m.invoke(player, (value.intValue() + 16) >> 4);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public void enable(TrainCarts plugin) throws Throwable {
        setCustomTrackingRange.init(Entity.class.getMethod("setCustomTrackingRange", int.class));
        setCustomTrackingRange.forceInitialization();
    }

    public void disable(TrainCarts plugin) {
    }
}
