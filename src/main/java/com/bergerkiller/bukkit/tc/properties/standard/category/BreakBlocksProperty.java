package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardCartProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Configures blocks automatically broken by carts
 */
public final class BreakBlocksProperty extends FieldBackedStandardCartProperty<Set<Material>> {

    @CommandMethod("cart breakblocks|break")
    @CommandDescription("Displays what block types are broken by the cart")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        Collection<Material> types = properties.getBlockBreakTypes();
        sender.sendMessage(ChatColor.YELLOW + "This cart breaks: " + ChatColor.WHITE + StringUtil.combineNames(types));
    }

    @CommandMethod("train breakblocks|break")
    @CommandDescription("Displays what block types are broken by the train")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        Set<Material> types = new HashSet<>();
        for (CartProperties cprop : properties) {
            types.addAll(cprop.getBlockBreakTypes());
        }
        sender.sendMessage(ChatColor.YELLOW + "This train breaks: " + ChatColor.WHITE + StringUtil.combineNames(types));
    }

    @CommandTargetTrain
    @PropertyCheckPermission("breakblocks")
    @CommandMethod("cart breakblocks|break clear")
    @CommandDescription("Clears the list of blocks broken by the cart, disabling it")
    private void setPropertyClear(
            final CommandSender sender,
            final CartProperties properties
    ) {
        properties.clearBlockBreakTypes();
        sender.sendMessage(ChatColor.YELLOW + "Block break types have been cleared!");
    }

    @CommandTargetTrain
    @PropertyCheckPermission("breakblocks")
    @CommandMethod("train breakblocks|break clear")
    @CommandDescription("Clears the list of blocks broken by the train, disabling it")
    private void setPropertyClear(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        for (CartProperties cProp : properties) {
            cProp.clearBlockBreakTypes();
        }
        sender.sendMessage(ChatColor.YELLOW + "Train block break types have been cleared!");
    }

    @PropertyCheckPermission("breakblocks")
    @CommandMethod("cart breakblocks|break <block_types>")
    @CommandDescription("Sets the list of blocks broken by the cart")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("block_types") String[] args
    ) {
        boolean anyblock = Permission.PROPERTY_BREAKBLOCKS_ADMIN.has(sender);
        boolean asBreak = true;
        boolean lastIsBool = ParseUtil.isBool(args[args.length - 1]);
        if (lastIsBool) asBreak = ParseUtil.parseBool(args[args.length - 1]);
        int count = lastIsBool ? args.length - 1 : args.length;
        Set<Material> mats = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Material mat = ParseUtil.parseMaterial(args[i], null);
            if (mat != null) {
                if (anyblock || TrainCarts.canBreak(mat)) {
                    mats.add(mat);
                } else {
                    sender.sendMessage(ChatColor.RED + "You are not allowed to make this cart break '" + mat.toString() + "'!");
                }
            }
        }
        if (mats.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Failed to find possible and allowed block types in the list given.");
            return;
        }
        if (asBreak) {
            properties.update(this, blocks -> {
                HashSet<Material> new_blocks = new HashSet<Material>(blocks);
                new_blocks.addAll(mats);
                return new_blocks;
            });
            sender.sendMessage(ChatColor.YELLOW + "This cart can now (also) break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
        } else {
            properties.update(this, blocks -> {
                HashSet<Material> new_blocks = new HashSet<Material>(blocks);
                new_blocks.removeAll(mats);
                return new_blocks;
            });
            sender.sendMessage(ChatColor.YELLOW + "This cart can no longer break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
        }
    }

    @PropertyCheckPermission("breakblocks")
    @CommandMethod("train breakblocks|break <block_types>")
    @CommandDescription("Sets the list of blocks broken by the train")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("block_types") String[] args
    ) {
        boolean anyblock = Permission.PROPERTY_BREAKBLOCKS_ADMIN.has(sender);
        boolean asBreak = true;
        boolean lastIsBool = ParseUtil.isBool(args[args.length - 1]);
        if (lastIsBool) asBreak = ParseUtil.parseBool(args[args.length - 1]);
        int count = lastIsBool ? args.length - 1 : args.length;
        Set<Material> mats = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Material mat = ParseUtil.parseMaterial(args[i], null);
            if (mat != null) {
                if (anyblock || TrainCarts.canBreak(mat)) {
                    mats.add(mat);
                } else {
                    sender.sendMessage(ChatColor.RED + "You are not allowed to make this train break '" + mat.toString() + "'!");
                }
            }
        }
        if (mats.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Failed to find possible and allowed block types in the list given.");
            return;
        }
        if (asBreak) {
            for (CartProperties cprop : properties) {
                cprop.update(this, blocks -> {
                    HashSet<Material> new_blocks = new HashSet<Material>(blocks);
                    new_blocks.addAll(mats);
                    return new_blocks;
                });
            }
            sender.sendMessage(ChatColor.YELLOW + "This cart can now (also) break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
        } else {
            for (CartProperties cprop : properties) {
                cprop.update(this, blocks -> {
                    HashSet<Material> new_blocks = new HashSet<Material>(blocks);
                    new_blocks.removeAll(mats);
                    return new_blocks;
                });
            }
            sender.sendMessage(ChatColor.YELLOW + "This cart can no longer break: " + ChatColor.WHITE + StringUtil.combineNames(mats));
        }
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_BREAKBLOCKS_NORMAL.has(sender)
                || Permission.PROPERTY_BREAKBLOCKS_ADMIN.has(sender);
    }

    @Override
    public Set<Material> getDefault() {
        return Collections.emptySet();
    }

    @Override
    public Set<Material> getData(CartInternalData data) {
        return data.blockBreakTypes;
    }

    @Override
    public void setData(CartInternalData data, Set<Material> value) {
        data.blockBreakTypes = value;
    }

    @Override
    public Optional<Set<Material>> readFromConfig(ConfigurationNode config) {
        if (config.contains("blockBreakTypes")) {
            return Optional.of(Collections.unmodifiableSet(
                    config.getList("blockBreakTypes", String.class).stream()
                        .map(name -> ParseUtil.parseMaterial(name, null))
                        .filter(m -> m != null)
                        .collect(Collectors.toSet())));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Set<Material>> value) {
        if (value.isPresent()) {
            config.set("blockBreakTypes", value.get().stream().map(Material::toString)
                    .collect(Collectors.toList()));
        } else {
            config.remove("blockBreakTypes");
        }
    }
}
