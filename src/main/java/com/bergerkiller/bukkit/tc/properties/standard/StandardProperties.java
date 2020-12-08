package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.ICartProperty;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.ISyntheticProperty;
import com.bergerkiller.bukkit.tc.properties.api.ITrainProperty;
import com.bergerkiller.bukkit.tc.properties.api.PropertyInvalidInputException;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.type.AttachmentModelBoundToCart;
import com.bergerkiller.bukkit.tc.properties.standard.type.BankingOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionMobCategory;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.ExitOffset;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bergerkiller.bukkit.tc.properties.standard.type.WaitOptions;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

/**
 * All standard TrainCarts built-in train and cart properties
 */
public class StandardProperties {

    /**
     * The full attachment tree that a single cart of a train uses to display itself
     * to players. Is lazily initialized the first time this property is read.
     */
    public static final ICartProperty<AttachmentModel> MODEL = new ICartProperty<AttachmentModel>() {
        @Override
        public AttachmentModel getDefault() {
            return null;
        }

        @Override
        public void onConfigurationChanged(CartProperties properties) {
            FieldBackedStandardCartPropertiesHolder holder = properties.getStandardPropertiesHolder();
            if (holder.model != null) {
                if (properties.getConfig().isNode("model")) {
                    ConfigurationNode modelConfig = properties.getConfig().getNode("model");
                    if (holder.model.isDefault()) {
                        // Model property added, load from new configuration
                        holder.model.update(modelConfig);
                    } else if (modelConfig != holder.model.getConfig()) {
                        // Node was completely swapped out, reload
                        // Configuration has no equals() check we can use
                        holder.model.update(modelConfig);
                    }
                } else if (!holder.model.isDefault()) {
                    // Model property removed, reset to vanilla defaults
                    resetToVanillaDefaults(properties);
                }
            }
        }

        @Override
        public AttachmentModel get(CartProperties properties) {
            FieldBackedStandardCartPropertiesHolder holder = properties.getStandardPropertiesHolder();
            if (holder.model == null) {
                if (properties.getConfig().isNode("model")) {
                    // Decode model and initialize
                    holder.model = new AttachmentModelBoundToCart(properties, properties.getConfig().getNode("model"));
                } else {
                    // No model was set. Create a Vanilla model based on the Minecart information
                    holder.model = new AttachmentModelBoundToCart(properties, new ConfigurationNode());
                    resetToVanillaDefaults(properties);
                    holder.model.setBoundToOwner(false);
                }
            }
            return holder.model;
        }

        @Override
        public void set(CartProperties properties, AttachmentModel value) {
            FieldBackedStandardCartPropertiesHolder holder = properties.getStandardPropertiesHolder();
            if (value == null || value.isDefault()) {
                // Reset model to vanilla defaults and wipe configuration
                properties.getConfig().remove("model");
                if (holder.model != null) {
                    holder.model.setBoundToOwner(false);
                }
                if (holder.model != null && !holder.model.isDefault()) {
                    resetToVanillaDefaults(properties);
                }
            } else {
                // Clone configuration and update/assign model if one was initialized
                // If the model was vanilla defaults, it will set the model during update()
                if (holder.model != null) {
                    holder.model.update(properties.getConfig().getNode("model"));
                } else {
                    properties.getConfig().set("model", value.getConfig().clone());
                }
            }
        }

        @Override
        public Optional<AttachmentModel> readFromConfig(ConfigurationNode config) {
            if (config.isNode("model")) {
                return Optional.of(new AttachmentModel(config.getNode("model").clone()));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<AttachmentModel> value) {
            if (value.isPresent()) {
                config.set("model", value.get().getConfig().clone());
            } else {
                config.remove("model");
            }
        }

        private void resetToVanillaDefaults(CartProperties properties) {
            MinecartMember<?> member = properties.getHolder();
            EntityType entityType = (member == null) ? EntityType.MINECART : member.getEntity().getType();
            properties.getStandardPropertiesHolder().model.resetToDefaults(entityType);
        }
    };

    public static final FieldBackedStandardCartProperty<Boolean> ONLY_OWNERS_CAN_ENTER = new FieldBackedStandardCartProperty<Boolean>() {

        @PropertyParser("onlyownerscanenter")
        public boolean parseCanEnter(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Boolean getHolderValue(FieldBackedStandardCartPropertiesHolder holder) {
            return holder.canOnlyOwnersEnter;
        }

        @Override
        public void setHolderValue(FieldBackedStandardCartPropertiesHolder holder, Boolean value) {
            holder.canOnlyOwnersEnter = value.booleanValue();
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            // Legacy
            if (config.contains("public")) {
                return Optional.of(!config.get("public", true));
            }

            return Util.getConfigOptional(config, "onlyOwnersCanEnter", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            config.remove("public"); // legacy
            Util.setConfigOptional(config, "onlyOwnersCanEnter", value);
        }

        @Override
        public Boolean get(TrainProperties properties) {
            for (CartProperties cProp : properties) {
                if (!get(cProp)) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
    };

    public static final FieldBackedStandardCartProperty<Boolean> PICK_UP_ITEMS = new FieldBackedStandardCartProperty<Boolean>() {

        @PropertyParser("pickup|pickupitems")
        public boolean parsePickupItems(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Boolean getHolderValue(FieldBackedStandardCartPropertiesHolder holder) {
            return holder.pickUpItems;
        }

        @Override
        public void setHolderValue(FieldBackedStandardCartPropertiesHolder holder, Boolean value) {
            holder.pickUpItems = value.booleanValue();
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "pickUp", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "pickUp", value);
        }
    };

    public static final ICartProperty<Boolean> INVINCIBLE = new ICartProperty<Boolean>() {

        @PropertyParser("invincible|godmode")
        public boolean parseInvincible(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "invincible", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "invincible", value);
        }
    };

    public static final ICartProperty<Boolean> SPAWN_ITEM_DROPS = new ICartProperty<Boolean>() {

        @PropertyParser("spawnitemdrops|spawndrops|killdrops")
        public boolean parseSpawnItemDrops(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.TRUE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "spawnItemDrops", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "spawnItemDrops", value);
        }
    };

    public static final ICartProperty<Boolean> ALLOW_PLAYER_ENTER = new ICartProperty<Boolean>() {

        @PropertyParser("playerenter")
        public boolean parsePlayerEnter(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.TRUE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "allowPlayerEnter", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "allowPlayerEnter", value);
        }
    };

    public static final ICartProperty<Boolean> ALLOW_PLAYER_EXIT = new ICartProperty<Boolean>() {

        @PropertyParser("playerexit")
        public boolean parsePlayerExit(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.TRUE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "allowPlayerExit", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "allowPlayerExit", value);
        }
    };

    public static final ICartProperty<String> ENTER_MESSAGE = new ICartProperty<String>() {

        @PropertyParser("entermessage|entermsg")
        public String parseMessage(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "enterMessage", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "enterMessage", value);
        }
    };

    public static final ICartProperty<String> DRIVE_SOUND = new ICartProperty<String>() {

        @PropertyParser("drivesound|driveeffect")
        public String parseSound(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "driveSound", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "driveSound", value);
        }
    };

    public static final FieldBackedStandardCartProperty<Set<Material>> BLOCK_BREAK_TYPES = new FieldBackedStandardCartProperty<Set<Material>>() {
        @Override
        public Set<Material> getDefault() {
            return Collections.emptySet();
        }

        @Override
        public Set<Material> getHolderValue(FieldBackedStandardCartPropertiesHolder holder) {
            return holder.blockBreakTypes;
        }

        @Override
        public void setHolderValue(FieldBackedStandardCartPropertiesHolder holder, Set<Material> value) {
            holder.blockBreakTypes = value;
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
    };

    public static final ICartProperty<String> DESTINATION_LAST_PATH_NODE = new ICartProperty<String>() {
        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "lastPathNode", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "lastPathNode", value);
        }
    };

    public static final ICartProperty<Integer> DESTINATION_ROUTE_INDEX = new ICartProperty<Integer>() {
        private final Integer DEFAULT = 0;

        @Override
        public Integer getDefault() {
            return DEFAULT;
        }

        @Override
        public Optional<Integer> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "routeIndex", int.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Integer> value) {
            Util.setConfigOptional(config, "routeIndex", value);
        }
    };

    public static final ICartProperty<List<String>> DESTINATION_ROUTE = new ICartProperty<List<String>>() {

        @PropertyParser("clearroute|route clear")
        public List<String> parseClear(String input) {
            return Collections.emptyList();
        }

        @PropertyParser("setroute|route set")
        public List<String> parseSet(String input) {
            return input.isEmpty() ? Collections.emptyList() : Collections.singletonList(input);
        }

        @PropertyParser("loadroute|route load")
        public List<String> parseLoad(String input) {
            return TrainCarts.plugin.getRouteManager().findRoute(input);
        }

        @PropertyParser(value="addroute|route add", processPerCart = true)
        public List<String> parseAdd(PropertyParseContext<List<String>> context) {
            if (context.input().isEmpty()) {
                return context.current();
            } else if (context.current().isEmpty()) {
                return Collections.singletonList(context.input());
            } else {
                ArrayList<String> newRoute = new ArrayList<String>(context.current());
                newRoute.add(context.input());
                return Collections.unmodifiableList(newRoute);
            }
        }

        @PropertyParser(value="remroute|removeroute|route rem|route remove", processPerCart = true)
        public List<String> parseRemove(PropertyParseContext<List<String>> context) {
            if (context.input().isEmpty() || !context.current().contains(context.input())) {
                return context.current();
            } else {
                ArrayList<String> newRoute = new ArrayList<String>(context.current());
                while (newRoute.remove(context.input())); // remove all instances
                return Collections.unmodifiableList(newRoute);
            }
        }

        @Override
        public List<String> getDefault() {
            return Collections.emptyList();
        }

        @Override
        public Optional<List<String>> readFromConfig(ConfigurationNode config) {
            if (config.contains("route")) {
                return Optional.of(Collections.unmodifiableList(new ArrayList<String>(
                        config.getList("route", String.class)
                )));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<List<String>> value) {
            if (value.isPresent()) {
                config.set("route", value.get());
            } else {
                config.remove("route");
            }
        }

        @Override
        public void set(CartProperties properties, List<String> value) {
            // Update route itself
            ICartProperty.super.set(properties, value);

            // Keep routing towards the same destination
            // This allows for a seamless transition between routes
            if (!value.isEmpty() && properties.hasDestination()) {
                int new_index = value.indexOf(properties.getDestination());
                if (new_index == -1) {
                    new_index = 0;
                }
                properties.set(DESTINATION_ROUTE_INDEX, new_index);
            } else {
                properties.set(DESTINATION_ROUTE_INDEX, 0);
            }
        }

        @Override
        public List<String> get(TrainProperties properties) {
            for (CartProperties cprop : properties) {
                List<String> route = get(cprop);
                if (!route.isEmpty()) {
                    return route;
                }
            }
            return Collections.emptyList();
        }
    };

    public static final ICartProperty<String> DESTINATION = new ICartProperty<String>() {

        @PropertyParser("destination")
        public String parseDestination(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "destination", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "destination", value);
        }

        @Override
        public void set(CartProperties properties, String value) {
            // Save current index before the destination was changed
            int prior_route_index = properties.getCurrentRouteDestinationIndex();

            // Update destination
            ICartProperty.super.set(properties, value);

            // If a destination is now set, increment the route index if it matches the next one in the list
            if (!value.isEmpty() && prior_route_index != -1) {
                List<String> route = StandardProperties.DESTINATION_ROUTE.get(properties);
                int nextIndex = (prior_route_index + 1) % route.size();
                if (value.equals(route.get(nextIndex))) {
                    StandardProperties.DESTINATION_ROUTE_INDEX.set(properties, nextIndex);
                }
            }
        }

        @Override
        public String get(TrainProperties properties) {
            // Return first cart from index=0 that has a destination
            for (CartProperties cprop : properties) {
                String destination = get(cprop);
                if (!destination.isEmpty()) {
                    return destination;
                }
            }
            return "";
        }
    };

    public static final FieldBackedStandardCartProperty<Set<String>> OWNER_PERMISSIONS = new FieldBackedStandardCartProperty<Set<String>>() {
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
        public Set<String> getDefault() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getHolderValue(FieldBackedStandardCartPropertiesHolder holder) {
            return holder.ownerPermissions;
        }

        @Override
        public void setHolderValue(FieldBackedStandardCartPropertiesHolder holder, Set<String> value) {
            holder.ownerPermissions = value;
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
    };

    public static final FieldBackedStandardCartProperty<Set<String>> OWNERS = new FieldBackedStandardCartProperty<Set<String>>() {
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
        public Set<String> getDefault() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getHolderValue(FieldBackedStandardCartPropertiesHolder holder) {
            return holder.owners;
        }

        @Override
        public void setHolderValue(FieldBackedStandardCartPropertiesHolder holder, Set<String> value) {
            holder.owners = value;
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
    };

    public static final FieldBackedStandardCartProperty<Set<String>> TAGS = new FieldBackedStandardCartProperty<Set<String>>() {
        @PropertyParser("settag")
        public Set<String> parse(String input) {
            return Collections.singleton(input);
        }

        @PropertyParser(value="addtag", processPerCart=true)
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

        @PropertyParser(value="remtag|removetag", processPerCart=true)
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
        public Set<String> getDefault() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getHolderValue(FieldBackedStandardCartPropertiesHolder holder) {
            return holder.tags;
        }

        @Override
        public void setHolderValue(FieldBackedStandardCartPropertiesHolder holder, Set<String> value) {
            holder.tags = value;
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
    };

    public static final ICartProperty<ExitOffset> EXIT_OFFSET = new ICartProperty<ExitOffset>() {

        @PropertyParser(value="exitoffset", processPerCart = true)
        public ExitOffset parseOffset(PropertyParseContext<ExitOffset> context) {
            final Vector vec = Util.parseVector(context.input(), null);
            if (vec == null) {
                throw new PropertyInvalidInputException("Not a vector");
            }

            if (vec.length() > TCConfig.maxEjectDistance) {
                vec.normalize().multiply(TCConfig.maxEjectDistance);
            }
            return ExitOffset.create(vec,
                    context.current().getYaw(),
                    context.current().getPitch());
        }

        @PropertyParser(value="exityaw", processPerCart = true)
        public ExitOffset parseYaw(PropertyParseContext<ExitOffset> context) {
            return ExitOffset.create(context.current().getRelativePosition(),
                    context.inputFloat(),
                    context.current().getPitch());
        }

        @PropertyParser(value="exitpitch", processPerCart = true)
        public ExitOffset parsePitch(PropertyParseContext<ExitOffset> context) {
            return ExitOffset.create(context.current().getRelativePosition(),
                    context.current().getYaw(),
                    context.inputFloat());
        }

        @PropertyParser(value="exitrot|exitrotation", processPerCart = true)
        public ExitOffset parseRotation(PropertyParseContext<ExitOffset> context) {
            String[] angletext = Util.splitBySeparator(context.input());
            final float new_yaw;
            final float new_pitch;
            if (angletext.length == 2) {
                new_yaw = ParseUtil.parseFloat(angletext[0], Float.NaN);
                new_pitch = ParseUtil.parseFloat(angletext[1], Float.NaN);
            } else if (angletext.length == 1) {
                new_yaw = ParseUtil.parseFloat(angletext[0], Float.NaN);
                new_pitch = 0.0f;
            } else {
                new_yaw = 0.0f;
                new_pitch = 0.0f;
            }
            if (Float.isNaN(new_yaw)) {
                throw new PropertyInvalidInputException("Rotation yaw is not a number");
            }
            if (Float.isNaN(new_pitch)) {
                throw new PropertyInvalidInputException("Rotation pitch is not a number");
            }
            return ExitOffset.create(context.current().getRelativePosition(),
                    new_yaw, new_pitch);
        }

        @Override
        public ExitOffset getDefault() {
            return ExitOffset.DEFAULT;
        }

        @Override
        public Optional<ExitOffset> readFromConfig(ConfigurationNode config) {
            if (config.contains("exitOffset") || config.contains("exitYaw") || config.contains("exitPitch")) {
                Vector offset = config.get("exitOffset", new Vector());
                float yaw = config.get("exitYaw", 0.0f);
                float pitch = config.get("exitPitch", 0.0f);
                return Optional.of(ExitOffset.create(offset, yaw, pitch));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<ExitOffset> value) {
            if (value.isPresent()) {
                ExitOffset data = value.get();
                config.set("exitOffset", data.getRelativePosition());
                config.set("exitYaw", data.getYaw());
                config.set("exitPitch", data.getPitch());
            } else {
                config.remove("exitOffset");
                config.remove("exitYaw");
                config.remove("exitPitch");
            }
        }
    };

    public static final ITrainProperty<Set<String>> TICKETS = new ITrainProperty<Set<String>>() {
        @PropertyParser("setticket|tickets set")
        public Set<String> parseSet(String input) {
            return input.isEmpty() ? Collections.emptySet() : Collections.singleton(input);
        }

        @PropertyParser("clrticket|cleartickets|tickets clear")
        public Set<String> parseClear(String input) {
            return Collections.emptySet();
        }

        @PropertyParser(value = "addticket|tickets add", processPerCart = true)
        public Set<String> parseAdd(PropertyParseContext<Set<String>> context) {
            if (context.input().isEmpty() || context.current().contains(context.input())) {
                return context.current();
            } else {
                HashSet<String> newPerms = new HashSet<String>(context.current());
                newPerms.add(context.input());
                return Collections.unmodifiableSet(newPerms);
            }
        }

        @PropertyParser(value = "remticket|tickets rem|tickets remove", processPerCart = true)
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
        public Set<String> getDefault() {
            return Collections.emptySet();
        }

        @Override
        public Optional<Set<String>> readFromConfig(ConfigurationNode config) {
            return Util.getConfigStringSetOptional(config, "tickets");
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Set<String>> value) {
            Util.setConfigStringCollectionOptional(config, "tickets", value);
        }
    };

    public static final ITrainProperty<String> KILL_MESSAGE = new ITrainProperty<String>() {

        @PropertyParser("killmessage")
        public String parseMessage(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "killMessage", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "killMessage", value);
        }
    };

    public static final ITrainProperty<Boolean> SUFFOCATION = new ITrainProperty<Boolean>() {

        @PropertyParser("suffocation")
        public boolean parseSuffocate(PropertyParseContext<Boolean> parser) {
            return parser.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.TRUE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "suffocation", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "suffocation", value);
        }
    };

    public static final ITrainProperty<Boolean> REQUIRE_POWERED_MINECART = new ITrainProperty<Boolean>() {
        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "requirePoweredMinecart", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "requirePoweredMinecart", value);
        }
    };

    public static final FieldBackedStandardTrainProperty<Boolean> ALLOW_MOB_MANUAL_MOVEMENT = new FieldBackedStandardTrainProperty<Boolean>() {

        @PropertyParser("allowmobmanual|mobmanualmove|mobmanual")
        public boolean parseAllowMovement(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Boolean getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.allowMobManualMovement;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, Boolean value) {
            holder.allowMobManualMovement = value.booleanValue();
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "allowMobManualMovement", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "allowMobManualMovement", value);
        }
    };

    public static final FieldBackedStandardTrainProperty<Boolean> ALLOW_PLAYER_MANUAL_MOVEMENT = new FieldBackedStandardTrainProperty<Boolean>() {

        @PropertyParser("allowmanual|manualmove|manual")
        public boolean parseAllowMovement(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Boolean getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.allowPlayerManualMovement;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, Boolean value) {
            holder.allowPlayerManualMovement = value.booleanValue();
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "allowManualMovement", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "allowManualMovement", value);
        }
    };

    public static final FieldBackedStandardTrainProperty<Boolean> KEEP_CHUNKS_LOADED = new FieldBackedStandardTrainProperty<Boolean>() {

        @PropertyParser("keeploaded|keepcloaded|loadchunks")
        public boolean parseKeepChunksLoaded(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Boolean getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.keepChunksLoaded;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, Boolean value) {
            holder.keepChunksLoaded = value.booleanValue();
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "keepChunksLoaded", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "keepChunksLoaded", value);
        }

        @Override
        public void onConfigurationChanged(TrainProperties properties) {
            FieldBackedStandardTrainProperty.super.onConfigurationChanged(properties);
            updateState(properties, properties.getStandardPropertiesHolder().keepChunksLoaded);
        }

        @Override
        public void set(TrainProperties properties, Boolean value) {
            FieldBackedStandardTrainProperty.super.set(properties, value);
            updateState(properties, value.booleanValue());
        }

        private void updateState(TrainProperties properties, boolean keepLoaded) {
            // When turning keep chunks loaded on, load the train if presently unloaded
            if (keepLoaded) {
                properties.restore();
            }

            MinecartGroup group = properties.getHolder();
            if (group != null) {
                group.keepChunksLoaded(keepLoaded);
            }
        }
    };

    public static final ITrainProperty<Double> COLLISION_DAMAGE = new ITrainProperty<Double>() {
        private final Double DEFAULT = 1.0;

        @PropertyParser("collisiondamage")
        public double parseDamage(PropertyParseContext<Double> context) {
            return context.inputDouble();
        }

        @Override
        public Double getDefault() {
            return DEFAULT;
        }

        @Override
        public Optional<Double> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "collisionDamage", double.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
            Util.setConfigOptional(config, "collisionDamage", value);
        }
    };

    public static final ITrainProperty<Boolean> ALLOW_PLAYER_TAKE = new ITrainProperty<Boolean>() {
        @Override
        public Boolean getDefault() {
            return Boolean.FALSE;
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "allowPlayerTake", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "allowPlayerTake", value);
        }
    };

    public static final FieldBackedStandardTrainProperty<Boolean> SOUND_ENABLED = new FieldBackedStandardTrainProperty<Boolean>() {

        @PropertyParser("sound|minecartsound")
        public boolean parseSoundEnabled(PropertyParseContext<Boolean> context) {
            return context.inputBoolean();
        }

        @Override
        public Boolean getDefault() {
            return Boolean.TRUE;
        }

        @Override
        public Boolean getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.soundEnabled;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, Boolean value) {
            holder.soundEnabled = value.booleanValue();
        }

        @Override
        public Optional<Boolean> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "soundEnabled", boolean.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
            Util.setConfigOptional(config, "soundEnabled", value);
        }
    };

    /**
     * Configures how trains roll inwards when turning
     */
    public static final FieldBackedStandardTrainProperty<BankingOptions> BANKING = new FieldBackedStandardTrainProperty<BankingOptions>() {

        @PropertyParser("banking")
        public BankingOptions parseBanking(PropertyParseContext<BankingOptions> context) {
            String[] args = context.input().trim().split(" ");
            double newStrength, newSmoothness;
            if (args.length >= 2) {
                newStrength = ParseUtil.parseDouble(args[0], Double.NaN);
                newSmoothness = ParseUtil.parseDouble(args[1], Double.NaN);
            } else {
                newStrength = ParseUtil.parseDouble(context.input(), Double.NaN);
                newSmoothness = context.current().smoothness();
            }
            if (Double.isNaN(newStrength)) {
                throw new PropertyInvalidInputException("Banking strength is not a number");
            }
            if (Double.isNaN(newSmoothness)) {
                throw new PropertyInvalidInputException("Banking smoothness is not a number");
            }
            return BankingOptions.create(newStrength, newSmoothness);
        }

        @Override
        public BankingOptions getDefault() {
            return BankingOptions.DEFAULT;
        }

        @Override
        public BankingOptions getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.bankingOptionsData;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, BankingOptions value) {
            holder.bankingOptionsData = value;
        }

        @Override
        public Optional<BankingOptions> readFromConfig(ConfigurationNode config) {
            if (!config.isNode("banking")) {
                return Optional.empty();
            }

            ConfigurationNode banking = config.getNode("banking");
            return Optional.of(BankingOptions.create(
                    banking.get("strength", 0.0),
                    banking.get("smoothness", 0.0)
            ));
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<BankingOptions> value) {
            if (value.isPresent()) {
                BankingOptions data = value.get();
                ConfigurationNode banking = config.getNode("banking");
                banking.set("strength", data.strength());
                banking.set("smoothness", data.smoothness());
            } else {
                config.remove("banking");
            }
        }
    };

    /**
     * Configures train behavior for waiting on obstacles on the track ahead
     */
    public static final FieldBackedStandardTrainProperty<WaitOptions> WAIT = new FieldBackedStandardTrainProperty<WaitOptions>() {

        @PropertyParser("waitdistance")
        public WaitOptions parseWaitDistance(PropertyParseContext<WaitOptions> context) {
            return WaitOptions.create(context.inputDouble(),
                    context.current().delay(),
                    context.current().acceleration(),
                    context.current().deceleration());
        }

        @PropertyParser("waitdelay")
        public WaitOptions parseWaitDelay(PropertyParseContext<WaitOptions> context) {
            return WaitOptions.create(context.current().distance(),
                    context.inputDouble(),
                    context.current().acceleration(),
                    context.current().deceleration());
        }

        @PropertyParser("waitacceleration")
        public WaitOptions parseWaitAcceleration(PropertyParseContext<WaitOptions> context) {
            String[] args = context.input().trim().split(" ");
            double newAcceleration;
            double newDeceleration;
            if (args.length >= 2) {
                newAcceleration = Util.parseAcceleration(args[0], Double.NaN);
                newDeceleration = Util.parseAcceleration(args[1], Double.NaN);
            } else {
                newAcceleration = newDeceleration = Util.parseAcceleration(context.input(), Double.NaN);
            }
            if (Double.isNaN(newAcceleration)) {
                throw new PropertyInvalidInputException("Acceleration is not a number or acceleration expression");
            }
            if (Double.isNaN(newDeceleration)) {
                throw new PropertyInvalidInputException("Deceleration is not a number or acceleration expression");
            }
            return WaitOptions.create(context.current().distance(),
                    context.inputDouble(),
                    newAcceleration,
                    newDeceleration);
        }

        @Override
        public WaitOptions getDefault() {
            return WaitOptions.DEFAULT;
        }

        @Override
        public WaitOptions getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.waitOptionsData;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, WaitOptions value) {
            holder.waitOptionsData = value;
        }

        @Override
        public Optional<WaitOptions> readFromConfig(ConfigurationNode config) {
            if (!config.isNode("wait")) {
                if (config.contains("waitDistance")) {
                    return Optional.of(WaitOptions.create(config.get("waitDistance", 0.0)));
                } else {
                    return Optional.empty();
                }
            }

            ConfigurationNode waitConfig = config.getNode("wait");
            double distance = waitConfig.get("distance", 0.0);
            double delay = waitConfig.get("delay", 0.0);
            double accel = waitConfig.get("acceleration", 0.0);
            double decel = waitConfig.get("deceleration", 0.0);
            return Optional.of(WaitOptions.create(distance, delay, accel, decel));
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<WaitOptions> value) {
            config.remove("waitDistance");
            if (value.isPresent()) {
                WaitOptions data = value.get();
                ConfigurationNode node = config.getNode("wait");
                node.set("distance", data.distance());
                node.set("delay", data.delay());
                node.set("acceleration", data.acceleration());
                node.set("deceleration", data.deceleration());
            } else {
                config.remove("wait");
            }
        }
    };

    /**
     * The persistent data stored for the sign skip feature of carts and trains.
     * This property is used internally by the SignSkipOptions class.
     */
    public static final IProperty<SignSkipOptions> SIGN_SKIP = new IProperty<SignSkipOptions>() {
        @Override
        public SignSkipOptions getDefault() {
            return SignSkipOptions.NONE;
        }

        @Override
        public Optional<SignSkipOptions> readFromConfig(ConfigurationNode config) {
            if (!config.isNode("skipOptions")) {
                return Optional.empty();
            }

            ConfigurationNode skipOptions = config.getNode("skipOptions");
            int ignoreCtr = skipOptions.get("ignoreCtr", 0);
            int skipCtr = skipOptions.get("skipCtr", 0);
            String filter = skipOptions.get("filter", "");
            Set<BlockLocation> signs = Collections.emptySet();
            if (skipOptions.contains("signs")) {
                List<String> signLocationNames = skipOptions.getList("signs", String.class);
                if (!signLocationNames.isEmpty()) {
                    signs = new LinkedHashSet<BlockLocation>(signLocationNames.size());
                    for (String signLocationName : signLocationNames) {
                        BlockLocation loc = BlockLocation.parseLocation(signLocationName);
                        if (loc != null) {
                            signs.add(loc);
                        }
                    }
                    signs = Collections.unmodifiableSet(signs);
                }
            }

            return Optional.of(SignSkipOptions.create(ignoreCtr, skipCtr, filter, signs));
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<SignSkipOptions> value) {
            if (value.isPresent()) {
                SignSkipOptions data = value.get();
                ConfigurationNode skipOptions = config.getNode("skipOptions");
                skipOptions.set("ignoreCtr", data.ignoreCounter());
                skipOptions.set("skipCtr", data.skipCounter());
                skipOptions.set("filter", data.filter());

                // Save signs in an offline friendly format
                if (data.hasSkippedSigns()) {
                    List<String> signs = skipOptions.getList("signs", String.class);
                    Iterator<BlockLocation> signBlockIter = data.skippedSigns().iterator();

                    int num_signs = 0;
                    while (signBlockIter.hasNext()) {
                        if (num_signs >= signs.size()) {
                            signs.add(signBlockIter.next().toString());
                        } else {
                            signs.set(num_signs, signBlockIter.next().toString());
                        }
                        ++num_signs;
                    }
                    while (signs.size() > num_signs) {
                        signs.remove(signs.size()-1);
                    }
                } else {
                    skipOptions.remove("signs");
                }
            } else {
                config.remove("skipOptions");
            }
        }

        @Override
        public SignSkipOptions get(CartProperties properties) {
            return properties.getStandardPropertiesHolder().signSkipOptionsData;
        }

        @Override
        public void set(CartProperties properties, SignSkipOptions value) {
            if (value.equals(SignSkipOptions.NONE)) {
                properties.getStandardPropertiesHolder().signSkipOptionsData = SignSkipOptions.NONE;
                this.writeToConfig(properties.getConfig(), Optional.empty());
            } else {
                properties.getStandardPropertiesHolder().signSkipOptionsData = value;
                this.writeToConfig(properties.getConfig(), Optional.of(value));
            }
        }

        @Override
        public SignSkipOptions get(TrainProperties properties) {
            return properties.getStandardPropertiesHolder().signSkipOptionsData;
        }

        @Override
        public void set(TrainProperties properties, SignSkipOptions value) {
            if (value.equals(SignSkipOptions.NONE)) {
                properties.getStandardPropertiesHolder().signSkipOptionsData = SignSkipOptions.NONE;
                this.writeToConfig(properties.getConfig(), Optional.empty());
            } else {
                properties.getStandardPropertiesHolder().signSkipOptionsData = value;
                this.writeToConfig(properties.getConfig(), Optional.of(value));
            }
        }

        @Override
        public void onConfigurationChanged(CartProperties properties) {
            Optional<SignSkipOptions> opt = this.readFromConfig(properties.getConfig());
            properties.getStandardPropertiesHolder().signSkipOptionsData = opt.isPresent() ? opt.get() : SignSkipOptions.NONE;
        }

        @Override
        public void onConfigurationChanged(TrainProperties properties) {
            Optional<SignSkipOptions> opt = this.readFromConfig(properties.getConfig());
            properties.getStandardPropertiesHolder().signSkipOptionsData = opt.isPresent() ? opt.get() : SignSkipOptions.NONE;
        }
    };

    public static final FieldBackedStandardTrainProperty<Set<SlowdownMode>> SLOWDOWN = new FieldBackedStandardTrainProperty<Set<SlowdownMode>>() {
        private final Set<SlowdownMode> ALL = Collections.unmodifiableSet(EnumSet.allOf(SlowdownMode.class));
        private final Set<SlowdownMode> NONE = Collections.unmodifiableSet(EnumSet.noneOf(SlowdownMode.class));

        // Uses constants if possible, and otherwise makes the set unmodifiable
        private Set<SlowdownMode> wrapAndOptimize(Set<SlowdownMode> result) {
            if (result.isEmpty()) {
                return NONE;
            } else if (result.size() == ALL.size()) {
                return ALL;
            } else {
                return Collections.unmodifiableSet(result);
            }
        }

        @PropertyParser("slowdown")
        public Set<SlowdownMode> parseSlowdownAll(PropertyParseContext<Set<SlowdownMode>> context) {
            return context.inputBoolean() ? ALL : NONE;
        }

        @PropertyParser("slowfriction")
        public Set<SlowdownMode> parseSlowdownFriction(PropertyParseContext<Set<SlowdownMode>> context) {
            EnumSet<SlowdownMode> values = EnumSet.noneOf(SlowdownMode.class);
            values.addAll(context.current());
            LogicUtil.addOrRemove(values, SlowdownMode.FRICTION, context.inputBoolean());
            return wrapAndOptimize(values);
        }

        @PropertyParser("slowgravity")
        public Set<SlowdownMode> parseSlowdownGravity(PropertyParseContext<Set<SlowdownMode>> context) {
            EnumSet<SlowdownMode> values = EnumSet.noneOf(SlowdownMode.class);
            values.addAll(context.current());
            LogicUtil.addOrRemove(values, SlowdownMode.GRAVITY, context.inputBoolean());
            return wrapAndOptimize(values);
        }

        @Override
        public Set<SlowdownMode> getDefault() {
            return ALL;
        }

        @Override
        public Set<SlowdownMode> getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.slowdown;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, Set<SlowdownMode> value) {
            holder.slowdown = value;
        }

        @Override
        public Optional<Set<SlowdownMode>> readFromConfig(ConfigurationNode config) {
            if (!config.contains("slowDown")) {
                // Not set
                return Optional.empty();
            } else if (!config.isNode("slowDown")) {
                // Boolean all defaults / none
                return Optional.of(config.get("slowDown", true) ? ALL : NONE);
            } else {
                // Node with [name]: true/false options
                final EnumSet<SlowdownMode> modes = EnumSet.noneOf(SlowdownMode.class);
                ConfigurationNode slowDownNode = config.getNode("slowDown");
                for (SlowdownMode mode : SlowdownMode.values()) {
                    if (slowDownNode.contains(mode.getKey()) &&
                        slowDownNode.get(mode.getKey(), true))
                    {
                        modes.add(mode);
                    }
                }
                return Optional.of(wrapAndOptimize(modes));
            }
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Set<SlowdownMode>> value) {
            if (value.isPresent()) {
                Set<SlowdownMode> modes = value.get();
                if (modes.isEmpty()) {
                    config.set("slowDown", false);
                } else if (modes.equals(ALL)) {
                    config.set("slowDown", true);
                } else {
                    ConfigurationNode slowDownNode = config.getNode("slowDown");
                    slowDownNode.clear();
                    for (SlowdownMode mode : SlowdownMode.values()) {
                        slowDownNode.set(mode.getKey(), modes.contains(mode));
                    }
                }
            } else {
                config.remove("slowDown");
            }
        }
    };

    /**
     * Applies default train properties from configuration by name to the train.
     * Only used to make this available as a property.
     */
    public static final ISyntheticProperty<ConfigurationNode> DEFAULT_CONFIG = new ISyntheticProperty<ConfigurationNode>() {

        @PropertyParser("setdefault|default")
        public ConfigurationNode parseDefaultConfig(String defaultName) {
            ConfigurationNode defaults = TrainPropertiesStore.getDefaultsByName(defaultName);
            if (defaults == null) {
                throw new PropertyInvalidInputException("Train Property Defaults by key '" + defaultName + "' does not exist");
            }
            return defaults;
        }

        @Override
        public ConfigurationNode getDefault() {
            return TrainPropertiesStore.getDefaultsByName("default");
        }

        @Override
        public ConfigurationNode get(CartProperties properties) {
            return getDefault();
        }

        @Override
        public ConfigurationNode get(TrainProperties properties) {
            return getDefault();
        }

        @Override
        public void set(CartProperties properties, ConfigurationNode config) {
            // Go by all properties and apply them to the cart
            // Do note: if properties are for trains, they are applied too!
            for (IProperty<Object> property : IPropertyRegistry.instance().all()) {
                Optional<Object> value = property.readFromConfig(config);
                if (value.isPresent()) {
                    properties.set(property, value.get());
                }
            }
        }

        @Override
        public void set(TrainProperties properties, ConfigurationNode config) {
            properties.apply(config);
        }
    };

    /**
     * Accesses {@link TrainProperties#setTrainName(String)} as if it were a property.
     * Adds a parser which allows the train name to be changed using property signs.
     */
    public static final ISyntheticProperty<String> TRAIN_NAME = new ISyntheticProperty<String>() {

        @PropertyParser("name|rename|setname|settrainname")
        public String parseRename(String nameFormat) {
            return TrainPropertiesStore.generateTrainName(nameFormat);
        }

        @Override
        public String getDefault() {
            return "train";
        }

        @Override
        public String get(CartProperties properties) {
            return get(properties.getTrainProperties());
        }

        @Override
        public void set(CartProperties properties, String value) {
            set(properties.getTrainProperties(), value);
        }

        @Override
        public String get(TrainProperties properties) {
            return properties.getTrainName();
        }

        @Override
        public void set(TrainProperties properties, String value) {
            properties.setTrainName(value);
        }
    };

    public static final ITrainProperty<String> DISPLAY_NAME = new ITrainProperty<String>() {

        @PropertyParser("dname|displayname|setdisplayname|setdname")
        public String parseName(String input) {
            return input;
        }

        @Override
        public String getDefault() {
            return "";
        }

        @Override
        public Optional<String> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "displayName", String.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<String> value) {
            Util.setConfigOptional(config, "displayName", value);
        }
    };

    public static final FieldBackedStandardTrainProperty<CollisionOptions> COLLISION = new FieldBackedStandardTrainProperty<CollisionOptions>() {

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
        public CollisionOptions getHolderValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.collision;
        }

        @Override
        public void setHolderValue(FieldBackedStandardTrainPropertiesHolder holder, CollisionOptions value) {
            holder.collision = value;
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
    };

    public static final FieldBackedStandardTrainProperty.StandardDouble GRAVITY = new FieldBackedStandardTrainProperty.StandardDouble() {

        @PropertyParser("gravity")
        public double parseGravity(PropertyParseContext<Double> context) {
            return context.inputDouble();
        }

        @Override
        public double getDoubleDefault() {
            return 1.0;
        }

        @Override
        public double getHolderDoubleValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.gravity;
        }

        @Override
        public void setHolderDoubleValue(FieldBackedStandardTrainPropertiesHolder holder, double value) {
            holder.gravity = value;
        }

        @Override
        public Optional<Double> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "gravity", double.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
            Util.setConfigOptional(config, "gravity", value);
        }
    };

    public static final FieldBackedStandardTrainProperty.StandardDouble SPEEDLIMIT = new FieldBackedStandardTrainProperty.StandardDouble() {

        @Override
        public double getDoubleDefault() {
            return 0.4;
        }

        @PropertyParser("maxspeed|speedlimit")
        public double parse(String input) {
            double result = Util.parseVelocity(input, Double.NaN);
            if (Double.isNaN(result)) {
                throw new PropertyInvalidInputException("Not a valid number or speed expression");
            }

            return result;
        }

        @Override
        public double getHolderDoubleValue(FieldBackedStandardTrainPropertiesHolder holder) {
            return holder.speedLimit;
        }

        @Override
        public void setHolderDoubleValue(FieldBackedStandardTrainPropertiesHolder holder, double value) {
            holder.speedLimit = value;
        }

        @Override
        public Optional<Double> readFromConfig(ConfigurationNode config) {
            return Util.getConfigOptional(config, "speedLimit", double.class);
        }

        @Override
        public void writeToConfig(ConfigurationNode config, Optional<Double> value) {
            Util.setConfigOptional(config, "speedLimit", value);
        }

        @Override
        public void set(TrainProperties properties, Double value) {
            // Limit the value between 0.0 and the maximum allowed speed
            double valuePrim = value.doubleValue();
            if (valuePrim < 0.0) {
                value = Double.valueOf(0.0);
            } else if (valuePrim > TCConfig.maxVelocity) {
                value = Double.valueOf(TCConfig.maxVelocity);
            }

            // Standard set
            StandardDouble.super.set(properties, value);
        }
    };
}
