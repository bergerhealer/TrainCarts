package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.tc.TCConfig;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyCheckPermission;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.api.context.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionMobCategory;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;

/**
 * Controls the behavior of trains when they collide with other entities or blocks
 */
public final class CollisionProperty extends FieldBackedStandardTrainProperty<CollisionOptions> {

    public void appendCollisionInfo(MessageBuilder builder, TrainProperties properties) {
        CollisionOptions opt = properties.getCollision();

        builder.yellow("Collision rules for the train:");

        // Blocks
        appendCollisionMode(builder, opt.blockMode(), "blocks");

        // Administrators forced to default using config.yml
        if (TCConfig.collisionIgnoreGlobalOwners) {
            appendCollisionMode(builder, CollisionMode.DEFAULT, "administrators");
            builder.newLine().white("      collision.ignoreGlobalOwners = true ").blue("[config.yml]");
        }

        // Administrators forced to default using config.yml
        if (TCConfig.collisionIgnoreOwners) {
            appendCollisionMode(builder, CollisionMode.DEFAULT, "owners of this train");
            builder.newLine().white("      collision.ignoreOwners = true ").blue("[config.yml]");
        }

        // Players
        appendCollisionMode(builder, opt.playerMode(),
                (TCConfig.collisionIgnoreGlobalOwners || TCConfig.collisionIgnoreOwners)
                        ? "other players" : "players");

        // Trains
        appendCollisionMode(builder, opt.trainMode(), "other trains");

        // Mob categories
        for (Map.Entry<CollisionMobCategory, CollisionMode> entry : opt.mobModes().entrySet()) {
            appendCollisionMode(builder, entry.getValue(), entry.getKey().getPluralMobType());
        }

        // Misc
        appendCollisionMode(builder, opt.miscMode(), "miscellaneous entities");
    }

    private void appendCollisionMode(MessageBuilder builder, CollisionMode mode, String who) {
        builder.newLine().yellow(" - ").red(mode.getOperationName())
                .yellow(" ").blue(who);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("collision")
    @Command("train collision default|true")
    @CommandDescription("Configures the default collision settings")
    private void trainSetCollisionDefault(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.setCollision(CollisionOptions.DEFAULT);
        trainGetCollisionInfo(sender, properties);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("collision")
    @Command("train collision none|false")
    @CommandDescription("Disables collision with all entities and blocks")
    private void trainSetCollisionNone(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.setCollision(CollisionOptions.CANCEL);
        trainGetCollisionInfo(sender, properties);
    }

    @Command("train collision")
    @CommandDescription("Gets all collision rules configured for a train")
    private void trainGetCollisionInfo(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        MessageBuilder builder = new MessageBuilder();
        appendCollisionInfo(builder, properties);
        builder.send(sender);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("mobcollision")
    @Command("train collision <mobcategory> <mode>")
    @CommandDescription("Sets new behavior when colliding with a given mob category")
    private void trainSetMobCollision(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mobcategory") CollisionMobCategory category,
            final @Argument("mode") CollisionMode mode
    ) {
        properties.setCollisionMode(category, mode);
        trainGetMobCollision(sender, properties, category);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("mobcollision")
    @Command("train collision mobs|mob <mode>")
    @CommandDescription("Sets new behavior when colliding with all types of mob")
    private void trainSetAllMobCollision(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mode") CollisionMode mode
    ) {
        properties.setCollisionModeForMobs(mode);
        showMode(sender, "mobs", mode);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("mobcollision")
    @Command("train collision <mobcategory> none")
    @CommandDescription("Resets behavior when colliding with a given mob category")
    private void trainResetMobCollision(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mobcategory") CollisionMobCategory category
    ) {
        properties.setCollisionMode(category, null);
        trainGetMobCollision(sender, properties, category);
    }

    @CommandTargetTrain
    @PropertyCheckPermission("mobcollision")
    @Command("train collision mobs|mob none")
    @CommandDescription("Resets behavior when colliding with any type of mob")
    private void trainResetAllMobsCollision(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        properties.setCollisionModeForMobs(null);
        sender.sendMessage(ChatColor.YELLOW + "Reset collision rules for all mob types. Will default to misc.");
    }

    @Command("train collision <mobcategory>")
    @CommandDescription("Gets the current behavior when colliding with a given mob category")
    private void trainGetMobCollision(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mobcategory") CollisionMobCategory category
    ) {
        CollisionMode mode = properties.getCollision().mobMode(category);
        if (mode == null) {
            sender.sendMessage(ChatColor.YELLOW + "The train has no specific mob collision mode set");
            sender.sendMessage(ChatColor.YELLOW + "Other mob collision rules might be set. If none are, "+
                    "behavior defaults to what is set for misc: ");
            showMode(sender, category.getPluralMobType(), properties.getCollision().miscMode());
        } else {
            showMode(sender, category.getPluralMobType(), mode);
        }
    }

    @Command("train collision mobs|mob")
    @CommandDescription("Gets the current behavior when colliding with a given mob category")
    private void trainGetAllMobCollision(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        CollisionOptions options = properties.getCollision();

        // Check if nothing is configured at all, for any type of mob
        if (options.mobModes().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "The train has no specific mob collision modes set");
            sender.sendMessage(ChatColor.YELLOW + "Behavior will default to what is set for misc: ");
            showMode(sender, "mobs", options.miscMode());
            return;
        }

        // Check if all mobs have the same mode. If not, list all the modes that are set for mobs.
        // This is so that if someone configures a mode for all mobs, it shows a clean short message here
        {
            CollisionMode foundMode = null;
            boolean hasNonMobModes = false;
            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                CollisionMode modeForMob = options.mobMode(category);
                if (category.isMobCategory()) {
                    if (modeForMob == null || (foundMode != null && foundMode != modeForMob)) {
                        foundMode = null; // Not all the same option
                        break;
                    } else if (foundMode == null) {
                        foundMode = modeForMob;
                    }
                } else if (modeForMob != null) {
                    hasNonMobModes = true;
                    showMode(sender, category.getPluralMobType(), modeForMob);
                }
            }
            if (foundMode != null) {
                showMode(sender, hasNonMobModes ? "other mobs" : "mobs", foundMode);
                return;
            }
        }

        // List each configured mob separately
        for (Map.Entry<CollisionMobCategory, CollisionMode> mode : options.mobModes().entrySet()) {
            if (mode.getKey().isMobCategory()) { // skip non-mobs, already displayed earlier
                showMode(sender, mode.getKey().getPluralMobType(), mode.getValue());
            }
        }
    }

    @CommandTargetTrain
    @PropertyCheckPermission("blockcollision")
    @Command("train collision block <mode>")
    @CommandDescription("Sets the behavior of the train when colliding with blocks")
    private void trainSetBlockCollision(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mode") CollisionMode mode
    ) {
        properties.setCollision(properties.getCollision().cloneAndSetBlockMode(mode));
        trainGetBlockCollision(sender, properties);
    }

    @Command("train collision block")
    @CommandDescription("Gets the behavior of the train when colliding with blocks")
    private void trainGetBlockCollision(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        showMode(sender, "blocks", properties.getCollision().blockMode());
    }

    @CommandTargetTrain
    @PropertyCheckPermission("playercollision")
    @Command("train collision player <mode>")
    @CommandDescription("Sets the behavior of the train when colliding with players")
    private void trainSetPlayerCollision(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mode") CollisionMode mode
    ) {
        properties.setCollision(properties.getCollision().cloneAndSetPlayerMode(mode));
        trainGetPlayerCollision(sender, properties);
    }

    @Command("train collision player")
    @CommandDescription("Gets the behavior of the train when colliding with players")
    private void trainGetPlayerCollision(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        showMode(sender, "players", properties.getCollision().playerMode());
    }

    @CommandTargetTrain
    @PropertyCheckPermission("traincollision")
    @Command("train collision train <mode>")
    @CommandDescription("Sets the behavior of the train when colliding with other trains")
    private void trainSetTrainCollision(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mode") CollisionMode mode
    ) {
        properties.setCollision(properties.getCollision().cloneAndSetTrainMode(mode));
        trainGetTrainCollision(sender, properties);
    }

    @Command("train collision train")
    @CommandDescription("Gets the behavior of the train when colliding with other trains")
    private void trainGetTrainCollision(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        showMode(sender, "other trains", properties.getCollision().trainMode());
    }

    @CommandTargetTrain
    @PropertyCheckPermission("misccollision")
    @Command("train collision misc <mode>")
    @CommandDescription("Sets the behavior of the train when colliding with miscellaneous mobs and entities")
    private void trainSetMiscCollision(
            final CommandSender sender,
            final TrainProperties properties,
            final @Argument("mode") CollisionMode mode
    ) {
        properties.setCollision(properties.getCollision().cloneAndSetMiscMode(mode));
        trainGetMiscCollision(sender, properties);
    }

    @Command("train collision misc")
    @CommandDescription("Gets the behavior of the train when colliding with miscellaneous mobs and entities")
    private void trainGetMiscCollision(
            final CommandSender sender,
            final TrainProperties properties
    ) {
        showMode(sender, "miscellaneous mobs and entities", properties.getCollision().miscMode());
    }

    private static void showMode(CommandSender sender, String category, CollisionMode mode) {
        MessageBuilder builder = new MessageBuilder();
        builder.yellow("The train ").red(mode.getOperationName());
        builder.yellow(" ").blue(category).yellow(" when colliding");
        builder.send(sender);
    }

    @PropertyParser("playercollision")
    public CollisionOptions parsePlayerCollisionMode(PropertyParseContext<CollisionOptions> context) {
        return context.current().cloneAndSetPlayerMode(parseMode(context));
    }

    @PropertyParser("misccollision")
    public CollisionOptions parseMiscCollisionMode(PropertyParseContext<CollisionOptions> context) {
        return context.current().cloneAndSetMiscMode(parseMode(context));
    }

    @PropertyParser("traincollision")
    public CollisionOptions parseTrainCollisionMode(PropertyParseContext<CollisionOptions> context) {
        return context.current().cloneAndSetTrainMode(parseMode(context));
    }

    @PropertyParser("blockcollision")
    public CollisionOptions parseBlockCollisionMode(PropertyParseContext<CollisionOptions> context) {
        return context.current().cloneAndSetBlockMode(parseMode(context));
    }

    // For mobs, only used if it doesn't match player/misc/train/block
    @PropertyParser("([a-z]+)collision")
    public CollisionOptions parseCollisionMobsType(PropertyParseContext<CollisionOptions> context) {
        return parseUpdateForMobs(context, context.nameGroup(1), parseModeOrReset(context));
    }

    @PropertyParser("linking|link")
    public CollisionOptions parseLinkingMode(PropertyParseContext<CollisionOptions> context) {
        if (context.inputBoolean()) {
            return context.current().cloneAndSetTrainMode(CollisionMode.LINK);
        } else if (context.current().trainMode() == CollisionMode.LINK) {
            return context.current().cloneAndSetTrainMode(CollisionMode.DEFAULT);
        } else {
            return context.current();
        }
    }

    @PropertyParser("pushplayers")
    public CollisionOptions parsePushPlayers(PropertyParseContext<CollisionOptions> context) {
        return context.current().cloneAndSetPlayerMode(CollisionMode.fromPushing(context.inputBoolean()));
    }

    @PropertyParser("pushmisc")
    public CollisionOptions parsePushMisc(PropertyParseContext<CollisionOptions> context) {
        return context.current().cloneAndSetMiscMode(CollisionMode.fromPushing(context.inputBoolean()));
    }

    // For mobs
    @PropertyParser("push([a-z]+)")
    public CollisionOptions parsePushingMobsType(PropertyParseContext<CollisionOptions> context) {
        CollisionMode mode;
        if (ParseUtil.isBool(context.input())) {
            mode = CollisionMode.fromPushing(context.inputBoolean());
        } else {
            mode = parseModeOrReset(context);
        }
        return parseUpdateForMobs(context, context.nameGroup(1), mode);
    }

    @PropertyParser("push|pushing")
    public CollisionOptions parsePushing(PropertyParseContext<CollisionOptions> context) {
        // Legacy, ew.
        CollisionMode mode = CollisionMode.fromPushing(context.inputBoolean());
        return context.current()
                .cloneAndSetPlayerMode(mode)
                .cloneAndSetMiscMode(mode)
                .cloneAndSetForAllMobs(mode);
    }

    @PropertyParser("mobenter|mobsenter")
    public CollisionOptions parseMobsEnter(PropertyParseContext<CollisionOptions> context) {
        if (context.inputBoolean()) {
            return context.current().cloneAndSetForAllMobs(CollisionMode.ENTER);
        } else {
            return context.current().cloneCompareAndSetForAllMobs(CollisionMode.ENTER, CollisionMode.DEFAULT);
        }
    }

    @PropertyParser("collision|collide")
    public CollisionOptions parseDefaultOrNoCollision(PropertyParseContext<CollisionOptions> context) {
        return context.inputBoolean() ? CollisionOptions.DEFAULT : CollisionOptions.CANCEL;
    }

    @Override
    public boolean hasPermission(CommandSender sender, String name) {
        return Permission.PROPERTY_COLLISION.has(sender);
    }

    @Override
    public CollisionOptions getDefault() {
        return CollisionOptions.DEFAULT;
    }

    @Override
    public CollisionOptions getData(TrainInternalData data) {
        return data.collision;
    }

    @Override
    public void setData(TrainInternalData data, CollisionOptions value) {
        data.collision = value;
    }

    @Override
    public Optional<CollisionOptions> readFromConfig(ConfigurationNode config) {
        if (config.contains("trainCollision") && !config.get("trainCollision", true)) {
            // Collision is completely disabled, except for blocks
            // This is a legacy property, we no longer save it.
            CollisionOptions collision = CollisionOptions.CANCEL;
            if (config.contains("collision.block")) {
                collision = collision.cloneAndSetBlockMode(config.get("collision.block", CollisionMode.DEFAULT));
            }
            return Optional.of(collision);
        } else if (config.isNode("collision")) {
            ConfigurationNode collisionConfig = config.getNode("collision");
            CollisionOptions.Builder builder = CollisionOptions.builder();

            // Standard modes
            if (collisionConfig.contains("players")) {
                builder.setPlayerMode(readMode(collisionConfig, "players", CollisionOptions.DEFAULT.playerMode()));
            }
            if (collisionConfig.contains("misc")) {
                builder.setMiscMode(readMode(collisionConfig,"misc", CollisionOptions.DEFAULT.miscMode()));
            }
            if (collisionConfig.contains("train")) {
                builder.setTrainMode(readMode(collisionConfig,"train", CollisionOptions.DEFAULT.trainMode()));
            }
            if (collisionConfig.contains("block")) {
                builder.setBlockMode(readMode(collisionConfig,"block", CollisionOptions.DEFAULT.blockMode()));
            }
            if (collisionConfig.contains("mobs")) {
                builder.setModeForAllMobs(readMode(collisionConfig,"mobs", null));
            } else if (collisionConfig.contains("mob")) {
                builder.setModeForAllMobs(readMode(collisionConfig,"mob", null));
            }

            // Specialized mob collision modes
            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                CollisionMode mode;
                if (collisionConfig.contains(category.getMobType())) {
                    // Singular
                    mode = readMode(collisionConfig, category.getMobType(), null);
                } else if (collisionConfig.contains(category.getPluralMobType())) {
                    // Plural
                    mode = readMode(collisionConfig, category.getPluralMobType(), null);
                } else {
                    continue; // Not found
                }

                if (mode != null) {
                    builder.setMobMode(category, mode);
                }
            }

            return Optional.of(builder.build());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<CollisionOptions> value) {
        // Get rid of legacy trainCollision property (legacy)
        config.remove("trainCollision");

        if (value.isPresent()) {
            ConfigurationNode collisionConfig = config.getNode("collision");
            CollisionOptions data = value.get();

            // If all mob categories are set to the same value, then we write mobs: with the value,
            // instead of bothering to write out each individual category
            final boolean hasMobsMode;
            {
                List<CollisionMode> mobCollisionModes = Stream.of(CollisionMobCategory.values())
                        .filter(CollisionMobCategory::isMobCategory)
                        .map(data::mobMode)
                        .distinct()
                        .collect(Collectors.toList());
                hasMobsMode = (mobCollisionModes.size() == 1 && mobCollisionModes.get(0) != null);
                if (hasMobsMode) {
                    collisionConfig.set("mobs", mobCollisionModes.get(0));
                } else {
                    collisionConfig.remove("mobs");
                }
                collisionConfig.remove("mob");
            }

            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                CollisionMode mode;
                if (hasMobsMode && category.isMobCategory()) {
                    mode = null; // Omit. Already written as 'mobs'
                } else {
                    mode = data.mobMode(category);
                }

                // Always save as singular mob type. So delete the plural one to avoid trouble.
                if (mode != null) {
                    collisionConfig.set(category.getMobType(), mode);
                } else {
                    collisionConfig.remove(category.getMobType());
                }
                collisionConfig.remove(category.getPluralMobType());
            }

            collisionConfig.set("players", data.playerMode());
            collisionConfig.set("misc", data.miscMode());
            collisionConfig.set("train", data.trainMode());
            collisionConfig.set("block", data.blockMode());
        } else {
            config.remove("collision");
        }
    }

    private CollisionMode readMode(ConfigurationNode config, String key, CollisionMode defValue) {
        String name = config.get(key, String.class, null);
        CollisionMode parsed;
        if (name != null && (parsed = CollisionMode.parse(name)) != null) {
            return parsed;
        } else {
            return null;
        }
    }

    private CollisionMode parseMode(PropertyParseContext<CollisionOptions> context) {
        CollisionMode mode = CollisionMode.parse(context.input());
        if (mode == null)
            throw new PropertyInvalidInputException("Not a valid collision mode");

        return mode;
    }

    private CollisionMode parseModeOrReset(PropertyParseContext<CollisionOptions> context) {
        if (context.input().equalsIgnoreCase("reset")) {
            return null;
        } else {
            return parseMode(context);
        }
    }

    private CollisionOptions parseUpdateForMobs(PropertyParseContext<CollisionOptions> context, String mobType, CollisionMode newMode) {
        if (mobType.equals("mob") || mobType.equals("mobs")) {
            return context.current().cloneAndSetForAllMobs(newMode);
        }

        boolean matchedMode = false;
        CollisionOptions newCollision = context.current();
        for (CollisionMobCategory mobCategory : CollisionMobCategory.values()) {
            if (mobType.equals(mobCategory.getMobType()) || mobType.equals(mobCategory.getPluralMobType())) {
                newCollision = newCollision.cloneAndSetMobMode(mobCategory, newMode);
                matchedMode = true;
            }
        }
        if (!matchedMode) {
            throw new PropertyInvalidInputException("Invalid collision category: " + mobType);
        }
        return newCollision;
    }
}
