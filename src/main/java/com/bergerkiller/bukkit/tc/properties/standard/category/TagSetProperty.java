package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardCartProperty;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Greedy;

/**
 * A simple set of tags that can be used to mark and switch carts or trains
 */
public final class TagSetProperty extends FieldBackedStandardCartProperty<Set<String>> {

    @CommandMethod("train tags")
    @CommandDescription("Displays the tags set for the carts of a train")
    private void getTrainTags(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        if (properties.hasTags()) {
            sender.sendMessage(ChatColor.YELLOW + "Train has tags: " + ChatColor.WHITE
                    + StringUtil.combineNames(properties.getTags()));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Train has tags: "
                    + ChatColor.RED + "None");
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("tags")
    @CommandMethod("train tags clear")
    @CommandDescription("Clears the previous tags for a train")
    private void setCartTags(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        setTrainTags(sender, properties, null);
    }

    @PropertyCheckPermission("tags")
    @CommandMethod("train tags set [tags]")
    @CommandDescription("Clears the previous tags and sets new tags for carts of the train")
    private void setTrainTags(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("tags") @Greedy String[] tags
    ) {
        if (tags != null && tags.length > 0) {
            properties.setTags(tags);
            sender.sendMessage(ChatColor.GREEN + "Train tags set to: "
                    + ChatColor.WHITE + StringUtil.combineNames(properties.getTags()));
        } else {
            properties.setTags(new String[0]);
            sender.sendMessage(ChatColor.GREEN + "Tags of train have been cleared.");
        }
    }

    @CommandMethod("train tags add_many <tags>")
    @CommandDescription("Adds tags to a train")
    private void addTrainTags(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("tags") @Greedy String[] tags
    ) {
        if (tags != null && tags.length > 0) {
            properties.addTags(tags);
            sender.sendMessage(ChatColor.GREEN + "Added tags: "
                    + ChatColor.WHITE + StringUtil.combineNames(tags));
        }

        getTrainTags(sender, properties);
    }

    @PropertyCheckPermission("tags")
    @CommandMethod("train tags remove_many <tags>")
    @CommandDescription("Removes tags from a train")
    private void removeTrainTags(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("tags") @Greedy String[] tags
    ) {
        if (tags != null && tags.length > 0) {
            properties.removeTags(tags);
            sender.sendMessage(ChatColor.GREEN + "Removed tags: "
                    + ChatColor.WHITE + StringUtil.combineNames(tags));
        }

        getTrainTags(sender, properties);
    }

    @CommandMethod("cart tags")
    @CommandDescription("Displays the tags set for a cart")
    private void getCartTags(
            final CommandSender sender,
            final CartProperties properties
    ) {
        if (properties.hasTags()) {
            sender.sendMessage(ChatColor.YELLOW + "Cart has tags: " + ChatColor.WHITE
                    + StringUtil.combineNames(properties.getTags()));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Cart has tags: "
                    + ChatColor.RED + "None");
        }
    }

    @PropertyCheckPermission("tags")
    @CommandMethod("cart tags clear")
    @CommandDescription("Clears the previous tags for a cart")
    private void setCartTags(
            final CommandSender sender,
            final CartProperties properties
    ) {
        setCartTags(sender, properties, null);
    }

    @PropertyCheckPermission("tags")
    @CommandMethod("cart tags set [tags]")
    @CommandDescription("Clears the previous tags and sets new tags for a cart")
    private void setCartTags(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("tags") @Greedy String[] tags
    ) {
        if (tags != null && tags.length > 0) {
            properties.setTags(tags);
            sender.sendMessage(ChatColor.GREEN + "Cart tags set to: "
                    + ChatColor.WHITE + StringUtil.combineNames(properties.getTags()));
        } else {
            properties.setTags(new String[0]);
            sender.sendMessage(ChatColor.GREEN + "Tags of cart have been cleared.");
        }
    }

    @PropertyCheckPermission("tags")
    @CommandMethod("cart tags add_many <tags>")
    @CommandDescription("Adds tags to a cart")
    private void addCartTags(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("tags") @Greedy String[] tags
    ) {
        if (tags != null && tags.length > 0) {
            properties.addTags(tags);
            sender.sendMessage(ChatColor.GREEN + "Added tags: "
                    + ChatColor.WHITE + StringUtil.combineNames(tags));
        }

        getCartTags(sender, properties);
    }

    @PropertyCheckPermission("tags")
    @CommandMethod("cart tags remove_many <tags>")
    @CommandDescription("Removes tags from a cart")
    private void removeCartTags(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("tags") @Greedy String[] tags
    ) {
        if (tags != null && tags.length > 0) {
            properties.removeTags(tags);
            sender.sendMessage(ChatColor.GREEN + "Removed tags: "
                    + ChatColor.WHITE + StringUtil.combineNames(tags));
        }

        getCartTags(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("tags")
    @CommandMethod("train tags add <tag>")
    @CommandDescription("Adds a single tag to the train, supports targeting")
    private void addTrainSingleTag(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("tag") String tag
    ) {
        addTrainTags(sender, properties, new String[] {tag});
    }

    @CommandTargetTrain
    @PropertyCheckPermission("tags")
    @CommandMethod("train tags remove <tag>")
    @CommandDescription("Removes a single tag from the train, supports targeting")
    private void removeTrainSingleTag(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("tag") String tag
    ) {
        removeTrainTags(sender, properties, new String[] {tag});
    }

    @CommandTargetTrain
    @PropertyCheckPermission("tags")
    @CommandMethod("cart tags add <tag>")
    @CommandDescription("Adds a single tag to the cart, supports targeting")
    private void addCartSingleTag(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("tag") String tag
    ) {
        addCartTags(sender, properties, new String[] {tag});
    }

    @CommandTargetTrain
    @PropertyCheckPermission("tags")
    @CommandMethod("cart tags remove <tag>")
    @CommandDescription("Removes a single tag from the cart, supports targeting")
    private void removeCartSingleTag(
            final CommandSender sender,
            final CartProperties properties,
            final @Argument("tag") String tag
    ) {
        removeCartTags(sender, properties, new String[] {tag});
    }

    @PropertyParser("settag|tags set")
    public Set<String> parse(String input) {
        return Collections.singleton(input);
    }

    @PropertyParser(value="addtag|tags add", processPerCart=true)
    public Set<String> parseAddTag(PropertyParseContext<Set<String>> context) {
        // If empty, do nothing
        if (context.input().isEmpty()) {
            return context.current();
        }

        // When old set of tags is empty, return singleton set of new tag
        if (context.current().isEmpty()) {
            return Collections.singleton(context.input());
        }

        // If already contained, return the same set of tags
        if (context.current().contains(context.input())) {
            return context.current();
        }

        // Combine old and new into a new set
        HashSet<String> newTags = new HashSet<String>(context.current());
        newTags.add(context.input());
        return Collections.unmodifiableSet(newTags);
    }

    @PropertyParser(value="remtag|removetag|tags remove", processPerCart=true)
    public Set<String> parseRemoveTag(PropertyParseContext<Set<String>> context) {
        // If empty or not contained, do nothing
        if (context.input().isEmpty() || !context.current().contains(context.input())) {
            return context.current();
        }

        // If size=1 then no more tags remain, return empty set
        if (context.current().size() == 1) {
            return Collections.emptySet();
        }

        // Remove from set
        HashSet<String> newTags = new HashSet<String>(context.current());
        newTags.remove(context.input());
        return Collections.unmodifiableSet(newTags);
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_TAGS.has(sender);
    }

    @Override
    public Set<String> getDefault() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getData(CartInternalData data) {
        return data.tags;
    }

    @Override
    public void setData(CartInternalData data, Set<String> value) {
        data.tags = value;
    }

    @Override
    public Optional<Set<String>> readFromConfig(ConfigurationNode config) {
        return Util.getConfigStringSetOptional(config, "tags");
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Set<String>> value) {
        Util.setConfigStringCollectionOptional(config, "tags", value);
    }

    @Override
    public Set<String> get(TrainProperties properties) {
        return FieldBackedStandardCartProperty.combineCartValues(properties, this);
    }
}
