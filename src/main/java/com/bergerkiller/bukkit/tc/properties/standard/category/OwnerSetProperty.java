package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardCartProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Set of usernames of owners of a cart who have permission to edit it
 */
public final class OwnerSetProperty extends FieldBackedStandardCartProperty<Set<String>> {

    /**
     * Appends owner and owner permission details of a cart or train to
     * a message builder for display.
     * 
     * @param message
     * @param properties
     */
    public void addOwnerInfo(MessageBuilder message, IProperties properties) {
        if (!properties.hasOwners() && !properties.hasOwnerPermissions()) {
            message.yellow("Owned by: ").green("Everyone").white(" (use /train claim)");
        } else {
            if (properties.hasOwners()) {
                message.yellow("Owned by: ");
                message.setSeparator(ChatColor.YELLOW, " / ").setIndent(4);
                for (String owner : properties.getOwners()) {
                    message.white(owner);
                }
                message.clearSeparator().setIndent(0);
            }
            if (properties.hasOwnerPermissions()) {
                StandardProperties.OWNER_PERMISSIONS.addOwnerPermInfo(message, properties);
            }
        }
    }

    @CommandMethod("cart owners")
    @CommandDescription("Display the owners set for the cart")
    private void getProperty(
            final CommandSender sender,
            final CartProperties properties
    ) {
        MessageBuilder message = new MessageBuilder();
        addOwnerInfo(message, properties);
        message.send(sender);
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("cart claim")
    @CommandDescription("Sets the caller as the sole owner of a cart")
    private void setPropertyClaim(
            final Player sender,
            final CartProperties properties
    ) {
        properties.clearOwners();
        properties.setOwner(sender.getName(), true);
        sender.sendMessage(ChatColor.GREEN + "You are now the only owner of this cart!");
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("cart owners add <player_names>")
    @CommandDescription("Adds players as owners of a cart")
    private void setPropertyAdd(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("player_names") String[] playerNames
    ) {
        if (playerNames != null && playerNames.length > 0) {
            sender.sendMessage(ChatColor.GREEN + "Adding owners to cart: "
                    + StringUtil.combineNames(playerNames));
            for (String playerName : playerNames) {
                properties.setOwner(playerName, true);
            }
        }
        getProperty(sender, properties);
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("cart owners remove <player_names>")
    @CommandDescription("Removes players as owners of a cart")
    private void setPropertyRemove(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("player_names") String[] playerNames
    ) {
        if (playerNames != null && playerNames.length > 0) {
            sender.sendMessage(ChatColor.GREEN + "Removing owners from cart: "
                    + StringUtil.combineNames(playerNames));
            for (String playerName : playerNames) {
                properties.setOwner(playerName, false);
            }
        }
        getProperty(sender, properties);
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("cart owners set <player_names>")
    @CommandDescription("Discards previous owners and sets players as owners of a cart")
    private void setProperty(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("player_names") String[] playerNames
    ) {
        if (playerNames != null && playerNames.length > 0) {
            properties.clearOwners();
            sender.sendMessage(ChatColor.GREEN + "Set new owners of cart: "
                    + StringUtil.combineNames(playerNames));
            for (String playerName : playerNames) {
                properties.setOwner(playerName, true);
            }
        } else {
            setPropertyClear(sender, properties);
        }
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("cart owners clear")
    @CommandDescription("Clears all owners set for a cart, allowing everyone access")
    private void setPropertyClear(
            final CommandSender sender,
            final CartProperties properties
    ) {
        properties.clearOwners();
        sender.sendMessage(ChatColor.GREEN + "Owners cleared! Everyone can now modify the cart.");
        if (properties.hasOwnerPermissions()) {
            getProperty(sender, properties);
        }
    }

    @CommandMethod("train owners")
    @CommandDescription("Display the owners set for carts of the train")
    private void getProperty(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MessageBuilder message = new MessageBuilder();
        addOwnerInfo(message, properties);
        message.send(sender);
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("train claim")
    @CommandDescription("Sets the caller as the sole owner of a train")
    private void setPropertyClaim(
            final Player sender,
            final TrainProperties properties
    ) {
        properties.clearOwners();
        properties.setOwner(sender.getName(), true);
        sender.sendMessage(ChatColor.GREEN + "You are now the only owner of this train!");
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("train owners add <player_names>")
    @CommandDescription("Adds players as owners of all carts of a train")
    private void setPropertyAdd(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("player_names") String[] playerNames
    ) {
        if (playerNames != null && playerNames.length > 0) {
            sender.sendMessage(ChatColor.GREEN + "Adding owners to train: "
                    + StringUtil.combineNames(playerNames));
            for (String playerName : playerNames) {
                properties.setOwner(playerName, true);
            }
        }
        getProperty(sender, properties);
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("train owners remove <player_names>")
    @CommandDescription("Removes players as owners of all carts of a train")
    private void setPropertyRemove(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("player_names") String[] playerNames
    ) {
        if (playerNames != null && playerNames.length > 0) {
            sender.sendMessage(ChatColor.GREEN + "Removing owners from train: "
                    + StringUtil.combineNames(playerNames));
            for (String playerName : playerNames) {
                properties.setOwner(playerName, false);
            }
        }
        getProperty(sender, properties);
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("train owners set <player_names>")
    @CommandDescription("Discards previous owners and sets players as owners of all carts of a train")
    private void setProperty(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("player_names") String[] playerNames
    ) {
        if (playerNames != null && playerNames.length > 0) {
            properties.clearOwners();
            sender.sendMessage(ChatColor.GREEN + "Set new owners of train: "
                    + StringUtil.combineNames(playerNames));
            for (String playerName : playerNames) {
                properties.setOwner(playerName, true);
            }
        } else {
            setPropertyClear(sender, properties);
        }
    }

    @PropertyCheckPermission("owners")
    @CommandMethod("train owners clear")
    @CommandDescription("Clears all owners set for a train, allowing everyone access")
    private void setPropertyClear(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.clearOwners();
        sender.sendMessage(ChatColor.GREEN + "Owners cleared! Everyone can now modify the train.");
        if (properties.hasOwnerPermissions()) {
            getProperty(sender, properties);
        }
    }

    @PropertyParser("setowner|owners set")
    public Set<String> parseSet(String input) {
        return input.isEmpty() ? Collections.emptySet() : Collections.singleton(input.toLowerCase());
    }

    @PropertyParser("clearowner|clearowners|owners clear")
    public Set<String> parseClear(String input) {
        return Collections.emptySet();
    }

    @PropertyParser(value = "addowner|owners add", processPerCart = true)
    public Set<String> parseAdd(PropertyParseContext<Set<String>> context) {
        String name_lc = context.input().toLowerCase();
        if (name_lc.isEmpty() || context.current().contains(name_lc)) {
            return context.current();
        } else {
            HashSet<String> newPerms = new HashSet<String>(context.current());
            newPerms.add(name_lc);
            return Collections.unmodifiableSet(newPerms);
        }
    }

    @PropertyParser(value = "remowner|owners rem|owners remove", processPerCart = true)
    public Set<String> parseRemove(PropertyParseContext<Set<String>> context) {
        String name_lc = context.input().toLowerCase();
        if (name_lc.isEmpty() || !context.current().contains(name_lc)) {
            return context.current();
        } else {
            HashSet<String> newPerms = new HashSet<String>(context.current());
            newPerms.remove(name_lc);
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
        return data.owners;
    }

    @Override
    public void setData(CartInternalData data, Set<String> value) {
        data.owners = value;
    }

    @Override
    public Optional<Set<String>> readFromConfig(ConfigurationNode config) {
        return Util.getConfigStringSetOptional(config, "owners");
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Set<String>> value) {
        Util.setConfigStringCollectionOptional(config, "owners", value);
    }

    @Override
    public Set<String> get(TrainProperties properties) {
        return FieldBackedStandardCartProperty.combineCartValues(properties, this);
    }
}
