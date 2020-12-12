package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionMobCategory;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;

/**
 * Controls the behavior of trains when they collide with other entities or blocks
 */
public final class CollisionProperty extends FieldBackedStandardTrainProperty<CollisionOptions> {

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
                builder.setPlayerMode(collisionConfig.get("players", CollisionOptions.DEFAULT.playerMode()));
            }
            if (collisionConfig.contains("misc")) {
                builder.setMiscMode(collisionConfig.get("misc", CollisionOptions.DEFAULT.miscMode()));
            }
            if (collisionConfig.contains("train")) {
                builder.setTrainMode(collisionConfig.get("train", CollisionOptions.DEFAULT.trainMode()));
            }
            if (collisionConfig.contains("block")) {
                builder.setBlockMode(collisionConfig.get("block", CollisionOptions.DEFAULT.blockMode()));
            }

            // Mob collision modes
            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                if (collisionConfig.contains(category.getMobType())) {
                    CollisionMode mode = collisionConfig.get(category.getMobType(), CollisionMode.class, null);
                    if (mode != null) {
                        builder.setMobMode(category, mode);
                    }
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

            for (CollisionMobCategory category : CollisionMobCategory.values()) {
                CollisionMode mode = data.mobMode(category);
                if (mode != null) {
                    collisionConfig.set(category.getMobType(), mode);
                } else {
                    collisionConfig.remove(category.getMobType());
                }
            }

            collisionConfig.set("players", data.playerMode());
            collisionConfig.set("misc", data.miscMode());
            collisionConfig.set("train", data.trainMode());
            collisionConfig.set("block", data.blockMode());
        } else {
            config.remove("collision");
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
