package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.Map.Entry;

/**
 * Stores all the Train Properties available by name
 */
public class TrainPropertiesStore extends LinkedHashSet<CartProperties> {
    private static boolean hasChanges = false;
    private static final long serialVersionUID = 1L;
    private static final String propertiesFile = "TrainProperties.yml";
    private static final String defaultPropertiesFile = "DefaultTrainProperties.yml";
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
     * Finds all the Train Properties that match the name with the expression given
     *
     * @param expression to match to
     * @return a Collection of TrainProperties that match
     */
    public static Collection<TrainProperties> matchAll(String expression) {
        List<TrainProperties> rval = new ArrayList<>();
        if (expression != null && !expression.isEmpty()) {
            String[] elements = expression.split("\\*");
            boolean first = expression.startsWith("*");
            boolean last = expression.endsWith("*");
            for (TrainProperties prop : getAll()) {
                if (prop.matchName(elements, first, last)) {
                    rval.add(prop);
                }
            }
        }
        return rval;
    }

    /**
     * Renames a TrainProperties instance
     *
     * @param properties   to rename
     * @param newTrainName to set to
     */
    public static void rename(TrainProperties properties, String newTrainName) {
        // Rename the offline group
        OfflineGroupManager.rename(properties.getTrainName(), newTrainName);
        // Rename the properties
        trainProperties.remove(properties.getTrainName());
        properties.setDisplayName(newTrainName);
        properties.trainname = newTrainName;
        trainProperties.put(newTrainName, properties);
        hasChanges = true;
    }

    /**
     * Removes a TrainProperties instance
     *
     * @param trainName of the properties to remove
     */
    public static void remove(String trainName) {
        TrainProperties prop = trainProperties.remove(trainName);
        if (prop != null && !prop.isEmpty()) {
            hasChanges = true;
            Iterator<CartProperties> iter = prop.iterator();
            while (iter.hasNext()) {
                CartProperties cprop = iter.next();
                iter.remove();
                CartPropertiesStore.remove(cprop.getUUID());
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
        if (prop == null) {
            prop = new TrainProperties(trainname);
            prop.setDefault();
            trainProperties.put(trainname, prop);
            hasChanges = true;
        }
        return prop;
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
     * Creates a new TrainProperties value using a random name
     *
     * @return new Train Properties
     */
    public static TrainProperties create() {
        String name = generateTrainName();
        TrainProperties prop = new TrainProperties(name);
        prop.setDefault();
        trainProperties.put(name, prop);
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
        CartPropertiesStore.clearAllCarts();
        hasChanges = true;
    }

    /**
     * Loads all Train Properties and defaults from disk
     */
    public static void load() {
        loadDefaults();
        FileConfiguration config = new FileConfiguration(TrainCarts.plugin, propertiesFile);
        config.load();
        if (fixDeprecation(config)) {
            config.save();
        }
        for (ConfigurationNode node : config.getNodes()) {
            TrainProperties prop = new TrainProperties(node.getName());
            prop.load(node);
            trainProperties.put(prop.getTrainName(), prop);
        }
        hasChanges = false;
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
                for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
                    if (collisionConfigObject.isAddToConfigFile()) {
                        node.set("collision." + collisionConfigObject.getMobType(), node.get("collision.mobs", CollisionMode.DEFAULT).toString());
                    }
                }
                node.remove("collision.mobs");
                changed = true;
            }
            if (node.contains("pushAway")) {
                for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
                    if (collisionConfigObject.isAddToConfigFile()) {
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
                    for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
                        if (collisionConfigObject.isAddToConfigFile()) {
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
                        for (CollisionConfig collisionConfigObject : CollisionConfig.values()) {
                            if (collisionConfigObject.isAddToConfigFile()) {
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
            TrainProperties.EMPTY.saveAsDefault(node);
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
     * Informs TrainCarts that (some) Train Properties have changed, and will need to be synchronized to disk
     */
    public static void markForAutosave() {
        hasChanges = true;
    }

    /**
     * Saves all Train Properties to disk
     */
    public static void save(boolean autosave) {
        if (autosave && !hasChanges) {
            return;
        }
        FileConfiguration config = new FileConfiguration(TrainCarts.plugin, propertiesFile);
        for (TrainProperties prop : trainProperties.values()) {
            //does this train even exist?!
            if (prop.hasHolder() || OfflineGroupManager.contains(prop.getTrainName())) {
                prop.save(config.getNode(prop.getTrainName()));
            } else {
                config.remove(prop.getTrainName());
            }
        }
        config.save();
        hasChanges = false;
    }

    /**
     * Gets the Configuration Node containing the defaults of the name specified
     *
     * @param name of the properties Default
     * @return Default properties configuration node
     */
    public static ConfigurationNode getDefaultsByName(String name) {
        return defconfig.getNode(name);
    }

    /**
     * Gets the Configuration Node containing the defaults for the player specified
     *
     * @param player the defaults apply to
     * @return Default properties configuration node, or null if not found
     */
    public static ConfigurationNode getDefaultsByPlayer(Player player) {
        Set<ConfigurationNode> specialNodes = new TreeSet<>((o1, o2) -> o1.getName().compareTo(o2.getName()));
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
}
