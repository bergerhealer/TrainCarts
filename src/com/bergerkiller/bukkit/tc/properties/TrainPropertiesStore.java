package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.CollisionMode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;

/**
 * Stores all the Train Properties available by name
 */
public class TrainPropertiesStore extends HashSet<CartProperties> {
	private static final long serialVersionUID = 1L;
	private static final String propertiesFile = "TrainProperties.yml";
	private static final String defaultPropertiesFile = "DefaultTrainProperties.yml";
	private static FileConfiguration defconfig = null;
	private static HashMap<String, TrainProperties> trainProperties = new HashMap<String, TrainProperties>();

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
		List<TrainProperties> rval = new ArrayList<TrainProperties>();
		if (expression != null && !expression.isEmpty()) {
			String[] elements = expression.split("\\*");
			boolean first = expression.startsWith("*");
			boolean last = expression.startsWith("*");
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
	 * @param properties to rename
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
	}

	/**
	 * Removes a TrainProperties instance
	 * 
	 * @param trainName of the properties to remove
	 */
	public static void remove(String trainName) {
		trainProperties.remove(trainName);
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
			trainProperties.put(trainname, prop);
		}
		return prop;
	}

	/**
	 * Creates a new TrainProperties value using a random name
	 * 
	 * @return new Train Properties
	 */
	public static TrainProperties create() {
		String name;
		for (int i = trainProperties.size(); i < Integer.MAX_VALUE; i++) {
			name = "train" + i;
			if (!exists(name)) {
				TrainProperties prop = new TrainProperties(name);
				trainProperties.put(name, prop);
				return prop;
			}
		}
		// this should never fire...
		return new TrainProperties("randname" + (int) (Math.random() * 100000));
	}

	/**
	 * Tests whether TrainProperties exist for the train name specified
	 * 
	 * @param trainname of the Properties
	 * @return True if TrainProperties exist, False if not
	 */
	public static boolean exists(String trainname) {
		if (trainProperties == null) return false;
		if (trainProperties.containsKey(trainname)) {
			if (OfflineGroupManager.contains(trainname)) {
				return true;
			} else {
				//doesn't link to a train!
				trainProperties.remove(trainname);
			}
		}
		return false;
	}

	/**
	 * Erases all known Train Properties information from the mapping<br>
	 * Note that Groups may still reference certain Properties!
	 */
	public static void clearAll() {
		trainProperties.clear();
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
			trainProperties.put(prop.trainname, prop);
		}
	}

	/**
	 * Fixes deprecated properties from all nodes in a File configuration
	 * 
	 * @param node to fix
	 * @return True if changes occurred, False if not
	 */
	private static boolean fixDeprecation(FileConfiguration config) {
		boolean changed = false;
		for (ConfigurationNode node : config.getNodes()) {
			if (node.contains("pushAway")) {
				node.set("collision.mobs", CollisionMode.fromPushing(node.get("pushAway.mobs", false)).toString());
				node.set("collision.players", CollisionMode.fromPushing(node.get("pushAway.players", false)).toString());
				node.set("collision.misc", CollisionMode.fromPushing(node.get("pushAway.misc", true)).toString());
				node.remove("pushAway");
				changed = true;
			}
			if (node.contains("allowMobsEnter")) {
				if (node.get("allowMobsEnter", false)) {
					node.set("collision.mobs", CollisionMode.ENTER.toString());
				}
				node.remove("allowMobsEnter");
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
			TrainProperties.EMPTY.saveAsDefault(node);
			changed = true;
		}
		if (fixDeprecation(defconfig)) {
			changed = true;
		}
		if (changed) defconfig.save();
	}

	/**
	 * Saves all Train Properties to disk
	 */
	public static void save() {
		FileConfiguration config = new FileConfiguration(TrainCarts.plugin, propertiesFile);
		for (TrainProperties prop : trainProperties.values()) {
			//does this train even exist?!
			if (OfflineGroupManager.contains(prop.getTrainName())) {
				ConfigurationNode train = config.getNode(prop.getTrainName());
				prop.save(train);
				if (train.isEmpty()) {
					config.remove(prop.getTrainName());
				}
			} else {
				config.remove(prop.getTrainName());
			}
		}
		config.save();
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
		for (ConfigurationNode node : defconfig.getNodes()) {
			if (player.hasPermission("train.properties." + node.getName())) {
				return node;
			}
		}
		return null;
	}
}
