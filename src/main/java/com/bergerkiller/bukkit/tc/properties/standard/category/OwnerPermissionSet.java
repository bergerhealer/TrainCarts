package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardCartProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Set of permission names which players need to modify a cart
 */
public final class OwnerPermissionSet extends FieldBackedStandardCartProperty<Set<String>> {

    /**
     * Appends owner permission details of a cart or train to
     * a message builder for display.
     * 
     * @param message
     * @param properties
     */
    public void addOwnerPermInfo(MessageBuilder message, IProperties properties) {
        if (properties.hasOwnerPermissions()) {
            message.yellow("Owned by players with the permissions:");
            for (String ownerPerm : properties.getOwnerPermissions()) {
                message.newLine().yellow("  - ").white(ownerPerm);
            }
        } else {
            message.yellow("No owner permission rules are set.");
        }
    }

    @CommandMethod("cart owners permission")
    @CommandDescription("Display the owner permissions set for a cart")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        MessageBuilder message = new MessageBuilder();
        addOwnerPermInfo(message, properties);
        message.send(sender);
    }

    @PropertyCheckPermission("ownerperms")
    @CommandMethod("cart owners permission add <permissions>")
    @CommandDescription("Adds permissions players need to access a cart")
    private void setPropertyAdd(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("permissions") String[] permissions
    ) {
        if (permissions != null && permissions.length > 0) {
            sender.sendMessage(ChatColor.GREEN + "Adding permission rules to cart: "
                    + StringUtil.combineNames(permissions));
            for (String permission : permissions) {
                properties.addOwnerPermission(permission);
            }
        }
        getProperty(sender, properties);
    }

    @PropertyCheckPermission("ownerperms")
    @CommandMethod("cart owners permission remove <permissions>")
    @CommandDescription("Removes permissions players need to access a cart")
    private void setPropertyRemove(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("permissions") String[] permissions
    ) {
        if (permissions != null && permissions.length > 0) {
            sender.sendMessage(ChatColor.GREEN + "Removing permission rules from cart: "
                    + StringUtil.combineNames(permissions));
            for (String permission : permissions) {
                properties.removeOwnerPermission(permission);
            }
        }
        getProperty(sender, properties);
    }

    @PropertyCheckPermission("ownerperms")
    @CommandMethod("cart owners permission set <permissions>")
    @CommandDescription("Discards previous owner permissions and sets new permissions players need to access a cart")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("permissions") String[] permissions
    ) {
        if (permissions != null && permissions.length > 0) {
            properties.clearOwnerPermissions();
            sender.sendMessage(ChatColor.GREEN + "Set new permission rules for cart: "
                    + StringUtil.combineNames(permissions));
            for (String permission : permissions) {
                properties.addOwnerPermission(permission);
            }
        } else {
            setPropertyClear(sender, properties);
        }
    }

    @PropertyCheckPermission("ownerperms")
    @CommandMethod("cart owners permission clear")
    @CommandDescription("Clears all owner permissions set for a cart")
    private void setPropertyClear(
            final CommandSender sender,
            final CartProperties properties
    ) {
        properties.clearOwnerPermissions();
        sender.sendMessage(ChatColor.GREEN + "Permission rules cleared.");
    }

    @CommandMethod("train owners permission")
    @CommandDescription("Display the owner permissions set for a train")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MessageBuilder message = new MessageBuilder();
        addOwnerPermInfo(message, properties);
        message.send(sender);
    }

    @PropertyCheckPermission("ownerperms")
    @CommandMethod("train owners permission add <permissions>")
    @CommandDescription("Adds permissions players need to access a cart")
    private void setPropertyAdd(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("permissions") String[] permissions
    ) {
        if (permissions != null && permissions.length > 0) {
            sender.sendMessage(ChatColor.GREEN + "Adding permission rules to train: "
                    + StringUtil.combineNames(permissions));
            for (String permission : permissions) {
                properties.addOwnerPermission(permission);
            }
        }
        getProperty(sender, properties);
    }

    @PropertyCheckPermission("ownerperms")
    @CommandMethod("train owners permission remove <permissions>")
    @CommandDescription("Removes permissions players need to access a cart")
    private void setPropertyRemove(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("permissions") String[] permissions
    ) {
        if (permissions != null && permissions.length > 0) {
            sender.sendMessage(ChatColor.GREEN + "Removing permission rules from train: "
                    + StringUtil.combineNames(permissions));
            for (String permission : permissions) {
                properties.removeOwnerPermission(permission);
            }
        }
        getProperty(sender, properties);
    }

    @PropertyCheckPermission("ownerperms")
    @CommandMethod("train owners permission set <permissions>")
    @CommandDescription("Discards previous owner permissions and sets new permissions players need to access a train")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("permissions") String[] permissions
    ) {
        if (permissions != null && permissions.length > 0) {
            properties.clearOwnerPermissions();
            sender.sendMessage(ChatColor.GREEN + "Set new permission rules for train: "
                    + StringUtil.combineNames(permissions));
            for (String permission : permissions) {
                properties.addOwnerPermission(permission);
            }
        } else {
            setPropertyClear(sender, properties);
        }
    }

    @PropertyCheckPermission("ownerperms")
    @CommandMethod("train owners permission clear")
    @CommandDescription("Clears all owner permissions set for a train")
    private void setPropertyClear(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.clearOwnerPermissions();
        sender.sendMessage(ChatColor.GREEN + "Permission rules cleared.");
    }

    @PropertyParser("setownerperm|ownerperms set")
    public Set<String> parseSet(String input) {
        return input.isEmpty() ? Collections.emptySet() : Collections.singleton(input);
    }

    @PropertyParser("clearownerperm|ownerperms clear")
    public Set<String> parseClear(String input) {
        return Collections.emptySet();
    }

    @PropertyParser(value = "addownerperm|ownerperms add", processPerCart = true)
    public Set<String> parseAdd(PropertyParseContext<Set<String>> context) {
        if (context.input().isEmpty() || context.current().contains(context.input())) {
            return context.current();
        } else {
            HashSet<String> newPerms = new HashSet<String>(context.current());
            newPerms.add(context.input());
            return Collections.unmodifiableSet(newPerms);
        }
    }

    @PropertyParser(value = "remownerperm|ownerperm rem|ownerperms remove", processPerCart = true)
    public Set<String> parseRemove(PropertyParseContext<Set<String>> context) {
        if (context.input().isEmpty() || !context.current().contains(context.input())) {
            return context.current();
        } else {
            HashSet<String> newPerms = new HashSet<String>(context.current());
            newPerms.remove(context.input());
            return Collections.unmodifiableSet(newPerms);
        }
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_OWNERS.has(sender);
    }

    @Override
    public Set<String> getDefault() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getData(CartInternalData data) {
        return data.ownerPermissions;
    }

    @Override
    public void setData(CartInternalData data, Set<String> value) {
        data.ownerPermissions = value;
    }

    @Override
    public Optional<Set<String>> readFromConfig(ConfigurationNode config) {
        return Util.getConfigStringSetOptional(config, "ownerPermissions");
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Set<String>> value) {
        Util.setConfigStringCollectionOptional(config, "ownerPermissions", value);
    }

    @Override
    public Set<String> get(TrainProperties properties) {
        return FieldBackedStandardCartProperty.combineCartValues(properties, this);
    }
}
