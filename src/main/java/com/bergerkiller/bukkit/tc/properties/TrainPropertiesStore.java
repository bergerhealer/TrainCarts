package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.standard.type.CollisionMobCategory;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;

/**
 * Stores all the Train Properties available by name
 */
public class TrainPropertiesStore extends LinkedHashSet<CartProperties> {
    private static boolean hasChanges = false;
    private static final long serialVersionUID = 1L;
    private static final String propertiesFile = "TrainProperties.yml";
    private static final String defaultPropertiesFile = "DefaultTrainProperties.yml";
    private static FileConfiguration config = null;
    private static FileConfiguration defconfig = null;
    private static Map<String, TrainProperties> trainProperties = new TreeMap<>();

    /**
     * Gets all the TrainProperties available
     *
     * @return a Collection of available Train Properties
     */
    public static Collection<TrainProperties> getAll() {
        return trainProperties.values();
    }

    /**
     * Matches all train properties that have a train name matching the expression.
     * The expression can use *-characters to denote portions of 'any' contents.
     *
     * @param expression
     * @return matching train properties (unmodifiable)
     */
    public static Collection<TrainProperties> matchAll(String expression) {
        if (expression != null && !expression.isEmpty()) {
            final String[] elements = expression.split("\\*");
            final boolean first = expression.startsWith("*");
            final boolean last = expression.endsWith("*");
            return trainProperties.values().stream()
                    .filter(p -> p.matchName(elements, first, last))
                    .collect(StreamUtil.toUnmodifiableList());
        }
        return Collections.emptySet();
    }

    /**
     * Renames a TrainProperties instance
     *
     * @param properties   to rename
     * @param newTrainName to set to
     * @throws IllegalArgumentException if another train by this name already {@link #exists(String)}
     */
    public static void rename(TrainProperties properties, String newTrainName) {
        // If unchanged, skip
        if (properties.getTrainName().equals(newTrainName)) {
            return;
        }

        // Check
        if (exists(newTrainName)) {
            throw new IllegalArgumentException("Another train with name '" + newTrainName + "' already exists");
        }

        // Rename the offline group
        OfflineGroupManager.rename(properties.getTrainName(), newTrainName);

        // Keep for later
        ConfigurationNode oldConfig = properties.getConfig();

        // Delete previous registration
        trainProperties.remove(properties.getTrainName());
        config.remove(properties.getTrainName());

        // Store under a new name
        properties.trainname = newTrainName;
        trainProperties.put(newTrainName, properties);
        config.set(newTrainName, oldConfig);
        hasChanges = true;
    }

    /**
     * Removes a TrainProperties instance
     *
     * @param trainName of the properties to remove
     */
    public static void remove(String trainName) {
        TrainProperties prop = trainProperties.remove(trainName);
        if (prop == null) {
            return;
        }
        hasChanges = true;
        config.remove(trainName);

        // Make sure to break any reference with existing carts
        // If still assigned to a cart, remove from properties store also
        if (!prop.isEmpty()) {
            for (CartProperties cProp : new ArrayList<CartProperties>(prop)) {
                // Remove from properties and removed train configuration
                prop.remove(cProp);

                // If not a loaded minecart, erase it from the by-uuid store also
                // If part of the train properties, it means no other train properties
                // have assigned the cart properties to itself. Assigning cart properties
                // un-assigns it from the previous train properties!
                if (cProp.getHolder() == null
                        || cProp.getHolder().getEntity() == null
                        || cProp.getHolder().getEntity().isDead())
                {
                    CartPropertiesStore.remove(cProp.getUUID());
                }
            }
        }
    }

    /**
     * Gets a TrainProperties instance by name<br>
     * Creates a new instance if none is contained
     *
     * @param trainname to get the properties of
     * @return TrainProperties instance of the train name
     */
    public static TrainProperties get(String trainname) {
        if (trainname == null) return null;

        TrainProperties prop = trainProperties.get(trainname);
        return (prop != null) ? prop : createDefaultWithName(trainname);
    }
    
    /**
     * Generates a new train name using the default format.
     *
     * @return generated (unused) train name
     */
    public static String generateTrainName() {
        return generateTrainName("train#");
    }

    /**
     * Generates a new train name using the format specified.
     * The location for the generated number is denoted using a '#'-character.
     * If none is set and the name is taken, a number is appended at the end of the name.
     *
     * @param format to use for the name
     * @return generated (unused) train name
     */
    public static String generateTrainName(String format) {
        // No constant: append a number at the end or use full name
        if (!format.contains("#")) {
            // If possible, set the name as it is
            if (exists(format)) {
                // Already exists, append number
                format = format + "#";
            } else {
                // Doesn't exist, use it directly
                return format;
            }
        }
        // Replace the numeric constant
        String trainName = format;
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            trainName = format.replace("#", Integer.toString(i));
            if (!exists(trainName)) {
                break;
            }
        }
        return trainName;
    }

    /**
     * Generates a new unique train name for a train that is split in two.
     * This uses the format:
     * <pre>
     * train12 -> train12~a
     * train12~a -> train12~b
     * </pre>
     * 
     * @param trainName The original name of the train that was split
     * @return new train name for the split-off part
     */
    public static String generateSplitTrainName(String trainName) {
        int split_idx = trainName.indexOf('~');
        int index = -1;
        if (split_idx != -1 && (index = fromAlphabeticRadix(trainName.substring(split_idx+1))) != -1) {
            trainName = trainName.substring(0, split_idx);
        }

        trainName += "~";

        String splitName;
        do {
            splitName = trainName + toAlphabeticRadix(++index);
        } while (exists(splitName));
        return splitName;
    }

    // https://stackoverflow.com/a/41733499
    private static String toAlphabeticRadix(int num) {
        char[] str = Integer.toString(num, 26).toCharArray();
        for (int i = 0; i < str.length; i++) {
            str[i] += str[i] > '9' ? 10 : 49;
        }
        return new String(str);
    }

    // returns -1 if invalid string
    private static int fromAlphabeticRadix(String radixStr) {
        if (radixStr.isEmpty()) {
            return -1;
        }
        char[] str = radixStr.toCharArray();
        for (int i = 0; i < str.length; i++) {
            char c = str[i];
            if (c >= 'a' && c <= 'z') {
                str[i] -= c > 'j' ? 10 : 49;
            } else {
                return -1;
            }
        }
        try {
            return Integer.parseInt(new String(str), 26);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * Creates a new TrainProperties value using a random name
     *
     * @return new Train Properties
     */
    public static TrainProperties create() {
        return createDefaultWithName(generateTrainName());
    }

    private static TrainProperties createDefaultWithName(String newTrainName) {
        ConfigurationNode newTrainConfig = config.getNode(newTrainName);
        TrainProperties prop = new TrainProperties(newTrainName, newTrainConfig);
        trainProperties.put(newTrainName, prop);
        prop.onConfigurationChanged(true);
        prop.setDefault();
        hasChanges = true;
        return prop;
    }

    /**
     * Creates a new TrainProperties value based off of another train's properties,
     * for when a train is split in two. Cart properties are not copied.
     * 
     * @param fromTrainProperties The properties of the train from which is split
     * @return new Train Properties
     */
    public static TrainProperties createSplitFrom(TrainProperties fromTrainProperties) {
        String name = generateSplitTrainName(fromTrainProperties.getTrainName());
        ConfigurationNode newTrainConfig = config.getNode(name);

        // Deep-copy old train configuration to the new one, skip 'carts'
        fromTrainProperties.saveToConfig().cloneIntoExcept(newTrainConfig, Collections.singleton("carts"));

        // Create new properties with this configuration
        TrainProperties prop = new TrainProperties(name, newTrainConfig);
        trainProperties.put(name, prop);
        prop.onConfigurationChanged(true);

        hasChanges = true;
        return prop;
    }

    /**
     * Tests whether TrainProperties exist for the train name specified
     *
     * @param trainname of the Properties
     * @return True if TrainProperties exist, False if not
     */
    public static boolean exists(String trainname) {
        return trainProperties != null && trainProperties.containsKey(trainname);
    }

    /**
     * Erases all known Train Properties information from the mapping<br>
     * Note that Groups may still reference certain Properties!
     */
    public static void clearAll() {
        trainProperties.clear();
        config.clear();
        CartPropertiesStore.clearAllCarts();
        hasChanges = true;
    }

    /**
     * Loads all Train Properties and defaults from disk
     */
    public static void load() {
        loadDefaults();
        config = new FileConfiguration(TrainCarts.plugin, propertiesFile);
        config.load();
        if (fixDeprecation(config)) {
            config.save();
        }
        for (ConfigurationNode node : config.getNodes()) {
            TrainProperties prop = new TrainProperties(node.getName(), node);
            if (prop.isEmpty()) {
                // Carts could not be decoded, invalid properties
                // Get rid of it
                config.remove(node.getName());
                TrainCarts.plugin.log(Level.WARNING, "Train properties with name " + prop.getTrainName() + " has no carts!");
                continue;
            }

            // Store in by-name mapping
            trainProperties.put(prop.getTrainName(), prop);

            // Initialize properties by reading the YAML
            prop.onConfigurationChanged(true);
        }
        hasChanges = false;

        // Add a change listener which will set hasChanges to true
        config.addChangeListener((path) -> hasChanges = true);
    }

    /**
     * Fixes deprecated properties from all nodes in a File configuration
     *
     * @param config to fix
     * @return True if changes occurred, False if not
     */
    private static boolean fixDeprecation(FileConfiguration config) {
        boolean changed = false;
        for (ConfigurationNode node : config.getNodes()) {
            if (node.contains("allowLinking")) {
                node.set("collision.train", CollisionMode.fromLinking(node.get("allowLinking", true)));
                node.remove("allowLinking");
                changed = true;
            }
            if (node.contains("collision.mobs")) {
                for (CollisionMobCategory collisionConfigObject : CollisionMobCategory.values()) {
                    if (collisionConfigObject.isMobCategory()) {
                        node.set("collision." + collisionConfigObject.getMobType(), node.get("collision.mobs", CollisionMode.DEFAULT).toString());
                    }
                }
                node.remove("collision.mobs");
                changed = true;
            }
            if (node.contains("pushAway")) {
                for (CollisionMobCategory collisionConfigObject : CollisionMobCategory.values()) {
                    if (collisionConfigObject.isMobCategory()) {
                        String mobType = collisionConfigObject.getMobType();
                        node.set("collision." + mobType, CollisionMode.fromPushing(node.get("pushAway." + mobType, false)).toString());
                    }
                }
                node.set("collision.players", CollisionMode.fromPushing(node.get("pushAway.players", false)).toString());
                node.set("collision.misc", CollisionMode.fromPushing(node.get("pushAway.misc", true)).toString());
                node.remove("pushAway");
                changed = true;
            }
            if (node.contains("allowMobsEnter")) {
                if (node.get("allowMobsEnter", false)) {
                    for (CollisionMobCategory collisionConfigObject : CollisionMobCategory.values()) {
                        if (collisionConfigObject.isMobCategory()) {
                            String mobType = collisionConfigObject.getMobType();
                            node.set("collision." + mobType, CollisionMode.ENTER.toString());
                        }
                    }
                }
                node.remove("allowMobsEnter");
                changed = true;
            }
            if (node.contains("mobenter") || node.contains("mobsenter")) {
                if (node.get("mobenter", false) || node.get("mobsenter", false)) {
                    if (node.get("allowMobsEnter", false)) {
                        for (CollisionMobCategory collisionConfigObject : CollisionMobCategory.values()) {
                            if (collisionConfigObject.isMobCategory()) {
                                String mobType = collisionConfigObject.getMobType();
                                node.set("collision." + mobType, CollisionMode.ENTER.toString());
                            }
                        }
                    }
                }
                node.remove("mobenter");
                node.remove("mobenters");
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Loads the default Train Properties from file
     */
    public static void loadDefaults() {
        defconfig = new FileConfiguration(TrainCarts.plugin, defaultPropertiesFile);
        defconfig.load();
        boolean changed = false;
        if (!defconfig.contains("default")) {
            ConfigurationNode node = defconfig.getNode("default");

            // Store all default properties, if they exist
            for (IProperty<Object> property : IPropertyRegistry.instance().all()) {
                Object value = property.getDefault();
                if (value != null) {
                    property.writeToConfig(node, Optional.of(value));
                }
            }

            // These defaults are only read, never written
            node.set("blockTypes", "");
            node.set("blockOffset", "unset");

            changed = true;
        }
        if (!defconfig.contains("admin")) {
            ConfigurationNode node = defconfig.getNode("admin");
            for (Entry<String, Object> entry : defconfig.getNode("default").getValues().entrySet()) {
                node.set(entry.getKey(), entry.getValue());
            }
            changed = true;
        }
        if (!defconfig.contains("spawner")) {
            ConfigurationNode node = defconfig.getNode("spawner");
            for (Entry<String, Object> entry : defconfig.getNode("default").getValues().entrySet()) {
                node.set(entry.getKey(), entry.getValue());
            }
            changed = true;
        }
        if (fixDeprecation(defconfig)) {
            changed = true;
        }
        if (changed) {
            defconfig.save();
        }
    }

    /**
     * Saves all Train Properties to disk
     */
    public static void save(boolean autosave) {
        if (autosave && !hasChanges) {
            return;
        }
        for (TrainProperties prop : trainProperties.values()) {
            // Does this train even exist?!
            if (!prop.hasHolder() && !OfflineGroupManager.contains(prop.getTrainName())) {
                config.remove(prop.getTrainName());
                continue;
            }

            // Do .saveToConfig() to refresh everything
            // Becomes obsolete once all properties are IProperties
            prop.saveToConfig();
        }
        config.save();
        hasChanges = false;
    }

    /**
     * Gets the Configuration Node containing the defaults of the name specified
     *
     * @param name of the properties Default
     * @return Default properties configuration node, or null if it doesn't exist
     */
    public static ConfigurationNode getDefaultsByName(String name) {
        return defconfig.isNode(name) ? defconfig.getNode(name) : null;
    }

    /**
     * Gets the Configuration Node containing the defaults for the player specified
     *
     * @param player the defaults apply to
     * @return Default properties configuration node, or null if not found
     */
    public static ConfigurationNode getDefaultsByPlayer(Player player) {
        Set<ConfigurationNode> specialNodes = new TreeSet<>(new Comparator<ConfigurationNode>() {
            @Override
            public int compare(ConfigurationNode o1, ConfigurationNode o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (ConfigurationNode node : defconfig.getNodes()) {
            if (LogicUtil.contains(node.getName(), "default", "spawner")) {
                continue;
            }
            if (CommonUtil.hasPermission(player, "train.properties." + node.getName())) {
                specialNodes.add(node);
            }
        }
        if (specialNodes.isEmpty()) {
            return defconfig.getNode("default");
        } else {
            return specialNodes.iterator().next();
        }
    }

    /**
     * Used internally to bind the MinecartGroup to properties, please don't use
     * 
     * @param properties
     * @param group
     */
    public static void bindGroupToProperties(TrainProperties properties, MinecartGroup group) {
        properties.updateHolder(group, true);
    }

    /**
     * Used internally to unbind the MinecartGroup from properties, please don't use
     * 
     * @param properties
     * @param group
     */
    public static void unbindGroupFromProperties(TrainProperties properties, MinecartGroup group) {
        properties.updateHolder(group, false);
    }
}
