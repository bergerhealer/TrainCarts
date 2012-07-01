package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.utils.SoftReference;

public class TrainProperties extends HashSet<CartProperties> {
	private static final long serialVersionUID = 1L;
	public static final TrainProperties EMPTY = new TrainProperties();
	private static final String defaultPropertiesFile = "DefaultTrainProperties.yml";
	private static final String propertiesFile = "TrainProperties.yml";
	
	private static HashMap<String, TrainProperties> properties = new HashMap<String, TrainProperties>();
	public static Collection<TrainProperties> getAll(String expression) {
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
	public static Collection<TrainProperties> getAll() {
		return properties.values();
	}
	public static TrainProperties get(String trainname) {
		if (trainname == null) return null;
		TrainProperties prop = properties.get(trainname);
		if (prop == null) {
			return new TrainProperties(trainname);
		}
		return prop;
	}
	public static boolean exists(String trainname) {
		if (properties == null) return false;
		if (properties.containsKey(trainname)) {
			if (OfflineGroupManager.contains(trainname)) {
				return true;
			} else if (MinecartGroup.get(trainname) != null) {
				return true;
			} else {
				//doesn't link to a train!
				properties.remove(trainname);
			}
		}
		return false;
	}
	public static void clearAll() {
		properties.clear();
	}
	
	private String trainname;	
	public boolean allowLinking = true;
	public boolean trainCollision = true;
	public boolean slowDown = true;
	public boolean pushMobs = false;
	public boolean pushPlayers = false;
	public boolean pushMisc = true;
	public double speedLimit = 0.4;
	public boolean requirePoweredMinecart = false;
	public boolean keepChunksLoaded = false;
	public boolean ignoreStations = false;
	private SoftReference<MinecartGroup> group = new SoftReference<MinecartGroup>();
	
	@Deprecated
	public Set<CartProperties> getCarts() {
		return this;
	}
	public MinecartGroup getGroup() {
		MinecartGroup group = this.group.get();
		if (group == null || group.isRemoved()) {
			return this.group.set(MinecartGroup.get(this.trainname));
		} else {
			return group;
		}
	}
	
	/*
	 * Carts
	 */
	public void add(MinecartMember member) {
		this.add(member.getProperties());
	}
	public void remove(MinecartMember member) {
		this.remove(member.getProperties());
	}
	public boolean add(CartProperties properties) {
		properties.group = this;
		return super.add(properties);
	}
	public CartProperties get(int index) {
		for (CartProperties prop : this) {
			if (index-- == 0) {
				return prop;
			}
		}
		throw new IndexOutOfBoundsException("No cart properties found at index " + index);
	}
	
	/*
	 * Pick up items
	 */
	public void setPickup(boolean pickup) {
		for (CartProperties prop : this) prop.pickUp = pickup;
	}
	
	/*
	 * Owners
	 */
	public boolean hasOwners() {
		for (CartProperties prop : this) {
			if (prop.hasOwners()) return true;
		}
		return false;
	}
	public boolean hasOwnership(Player player) {
        if (!CartProperties.canHaveOwnership(player)) return false;
        if (CartProperties.hasGlobalOwnership(player)) return true;
        if (!this.hasOwners()) return true;
        return this.isOwner(player);
	}
	public boolean isOwner(Player player) {
		boolean hasowner = false;
		for (CartProperties prop : this) {
			if (prop.isOwner(player)) return true;
			if (prop.hasOwners()) hasowner = true;
		}
		return !hasowner;
	}	
	public boolean isDirectOwner(Player player) {
		return this.isDirectOwner(player.getName().toLowerCase());
	}
	public boolean isDirectOwner(String player) {
		for (CartProperties prop : this) {
			if (prop.isOwner(player)) return true;
		}
		return false;
	}
	public Set<String> getOwners() {
		Set<String> rval = new HashSet<String>();
		for (CartProperties cprop : this) {
			rval.addAll(cprop.getOwners());
		}
		return rval;
	}
	
	/*
	 * Tags
	 */
	public boolean hasTag(String tag) {
		for (CartProperties prop : this) {
			if (prop.hasTag(tag)) return true;
		}
		return false;
	}
	public void clearTags() {
		for (CartProperties prop : this) {
			prop.clearTags();
		}
	}
	public void setTags(String... tags) {
		for (CartProperties prop : this) {
			prop.setTags(tags);
		}
	}
	public void addTags(String... tags) {
		for (CartProperties prop : this) {
			prop.addTags(tags);
		}
	}
	public void removeTags(String... tags) {
		for (CartProperties prop : this) {
			prop.removeTags(tags);
		}
	}
	
	/*
	 * Other batch cart property changing
	 */
	public void setAllowPlayerEnter(boolean state) {
		for (CartProperties prop : this) {
		    prop.allowPlayerEnter = state;
		}
	}
	public void setAllowPlayerExit(boolean state) {
		for (CartProperties prop : this) {
		    prop.allowPlayerExit = state;
		}
	}
	public void setAllowMobsEnter(boolean state) {
		for (CartProperties prop : this) {
		    prop.allowMobsEnter = state;
		}
	}
	
	/*
	 * Destination
	 */
	public boolean hasDestination() {
		for (CartProperties prop : this) {
			if (prop.hasDestination()) return true;
		}
		return false;
	}
	public String getDestination() {
		for (CartProperties prop : this) {
			if (prop.hasDestination()) return prop.destination;
		}
		return "";
	}
	public void setDestination(String destination) {
		for (CartProperties prop : this) {
			prop.destination = destination;
		}
	}
	public void clearDestination() {
		this.setDestination("");
	}

	/*
	 * Push away settings
	 */
	public boolean canPushAway(Entity entity) {
		if (entity instanceof Player) {
			if (this.pushPlayers) {
				if (!TrainCarts.pushAwayIgnoreOwners) return true;
				if (TrainCarts.pushAwayIgnoreGlobalOwners) {
					if (CartProperties.hasGlobalOwnership((Player) entity)) return false;
				}
				return !this.isOwner((Player) entity);
			}
		} else if (entity instanceof Creature || entity instanceof Slime || entity instanceof Ghast) {
			if (this.pushMobs) return true;
		} else {
			if (this.pushMisc) return true;
		}
		return false;
	}

	/*
	 * Collision settings
	 */
	public boolean canCollide(MinecartMember with) {
		return this.canCollide(with.getGroup());
	}
	public boolean canCollide(MinecartGroup with) {
		return this.trainCollision && with.getProperties().trainCollision;
	}
	public boolean canCollide(Entity with) {
		MinecartMember mm = MinecartMember.get(with);
		if (mm == null) {
			if (this.trainCollision) return true;
			if (with instanceof Player) {
				return this.isOwner((Player) with);
			} else {
				return false;
			}
		} else {
			return this.canCollide(mm);
		}
	}
		
	/*
	 * General coding not related to properties themselves
	 */
	private TrainProperties() {};
	private TrainProperties(String trainname) {
		this.trainname = trainname;
		properties.put(trainname, this);
		this.setDefault();
	}	
	public String getTrainName() {
		return this.trainname;
	}	
	public void remove() {
		properties.remove(this.trainname);
	}
	public void add() {
		properties.put(this.trainname, this);
	}
	public TrainProperties rename(String newtrainname) {
		properties.remove(this.trainname);
		this.trainname = newtrainname;
		properties.put(newtrainname, this);
		return this;
	}
	public boolean isTrainRenamed() {
		if (this.trainname.startsWith("train")) {
			try {
				Integer.parseInt(this.trainname.substring(5));
			} catch (NumberFormatException ex) {return true;}
		}
		return false;
	}
	public boolean isLoaded() {
		return this.getGroup() != null;
	}
	public boolean matchName(String expression) {
		return Util.matchText(this.getTrainName(), expression);
	}
	public boolean matchName(String[] expressionElements, boolean firstAny, boolean lastAny) {
		return Util.matchText(this.getTrainName(), expressionElements, firstAny, lastAny);
	}

	public BlockLocation getLocation() {
		for (CartProperties prop : this) {
			return prop.getLocation();
		}
		return null;
	}

	public static void init() {
		load();
	}
	public static void deinit() {
		save();
		defconfig = null;
	}
    		
	/*
	 * Train properties defaults
	 */
	private static FileConfiguration defconfig = null;
	public static void reloadDefaults() {
		defconfig = new FileConfiguration(TrainCarts.plugin, defaultPropertiesFile);
		defconfig.load();
		boolean changed = false;
		if (!defconfig.contains("default")) {
			ConfigurationNode node = defconfig.getNode("default");
			TrainProperties.EMPTY.save(node, false, false);
			CartProperties.EMPTY.save(node, false);
			changed = true;
		}
		if (!defconfig.contains("admin")) {
			ConfigurationNode node = defconfig.getNode("admin");
			TrainProperties.EMPTY.save(node, false, false);
			CartProperties.EMPTY.save(node, false);
			changed = true;
		}
		if (changed) defconfig.save();
	}
	public static FileConfiguration getDefaults() {
		if (defconfig == null) reloadDefaults();
		return defconfig;
	}
	public void setDefault() {
		setDefault("default");
	}
	public void setDefault(String key) {
		this.setDefault(getDefaults().getNode(key));
	}
	public void setDefault(ConfigurationNode node) {
		this.load(node);
		for (CartProperties prop : this) {
			prop.load(node);
		}
	}
	public void setDefault(Player player) {
		if (player == null) {
			//Set default
			this.setDefault();
		} else {
			//Load it
			for (ConfigurationNode node : getDefaults().getNodes()) {
				if (player.hasPermission("train.properties." + node.getName())) {
					this.setDefault(node);
					break;
				}
			}
		}
	}
	
	public void tryUpdate() {
		MinecartGroup g = this.getGroup();
		if (g != null) g.update();
	}
	
	/*
	 * Loading and saving
	 */
	public static void load() {
		FileConfiguration config = new FileConfiguration(TrainCarts.plugin, propertiesFile);
		config.load();
		for (ConfigurationNode node : config.getNodes()) {
			TrainProperties prop = new TrainProperties();
			prop.trainname = node.getName();
			prop.load(node);
			properties.put(prop.trainname, prop);
		}
	}
	public static void save() {
		FileConfiguration config = new FileConfiguration(TrainCarts.plugin, propertiesFile);
		for (TrainProperties prop : properties.values()) {
			//does this train even exist?!
			if (OfflineGroupManager.contains(prop.getTrainName())) {
				ConfigurationNode train = config.getNode(prop.getTrainName());
				prop.save(train);
				if (train.getKeys().isEmpty()) {
					config.remove(prop.getTrainName());
				}
			} else {
				config.remove(prop.getTrainName());
			}
		}
		config.save();
	}
	public void load(ConfigurationNode node) {
		this.allowLinking = node.get("allowLinking", this.allowLinking);
		this.trainCollision = node.get("trainCollision", this.trainCollision);
		this.slowDown = node.get("slowDown", this.slowDown);
		this.pushMobs = node.get("pushAway.mobs", this.pushMobs);
		this.pushPlayers = node.get("pushAway.players", this.pushPlayers);
		this.pushMisc = node.get("pushAway.misc", this.pushMisc);
		this.speedLimit = MathUtil.limit(node.get("speedLimit", this.speedLimit), 0, 20);
		this.requirePoweredMinecart = node.get("requirePoweredMinecart", this.requirePoweredMinecart);
		this.keepChunksLoaded = node.get("keepChunksLoaded", this.keepChunksLoaded);
		this.ignoreStations = node.get("ignoreStations", this.ignoreStations);
		for (ConfigurationNode cart : node.getNode("carts").getNodes()) {
			try {
				CartProperties prop = CartProperties.get(UUID.fromString(cart.getName()), this);
				this.add(prop);
				prop.load(cart);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	public void load(TrainProperties source) {
		this.allowLinking = source.allowLinking;
		this.trainCollision = source.trainCollision;
		this.slowDown = source.slowDown;
		this.pushMobs = source.pushMobs;
		this.pushPlayers = source.pushPlayers;
		this.pushMisc = source.pushMisc;
		this.speedLimit = MathUtil.limit(source.speedLimit, 0, 20);
		this.requirePoweredMinecart = source.requirePoweredMinecart;
		this.keepChunksLoaded = source.keepChunksLoaded;
		this.clear();
		this.addAll(source);
	}
	public void save(ConfigurationNode node) {
		this.save(node, true);
	}
	public void save(ConfigurationNode node, boolean savecarts) {	
		this.save(node, savecarts, true);
	}
	public void save(ConfigurationNode node, boolean savecarts, boolean minimal) {		
		if (minimal) {
			node.set("allowLinking", this.allowLinking ? null : false);
			node.set("requirePoweredMinecart", this.requirePoweredMinecart ? true : null);
			node.set("trainCollision", this.trainCollision ? null : false);
			node.set("keepChunksLoaded", this.keepChunksLoaded ? true : null);
			node.set("speedLimit", this.speedLimit != 0.4 ? this.speedLimit : null);
			node.set("slowDown", this.slowDown ? null : false);
			if (this.pushMobs || this.pushPlayers || !this.pushMisc) {
				node.set("pushAway.mobs", this.pushMobs ? true : null);
				node.set("pushAway.players", this.pushPlayers ? true : null);
				node.set("pushAway.misc", this.pushMisc ? null : false);
			} else {
				node.remove("pushAway");
			}
			node.set("ignoreStations", this.ignoreStations ? true : null);
		} else {
			node.set("allowLinking", this.allowLinking);
			node.set("requirePoweredMinecart", this.requirePoweredMinecart);
			node.set("trainCollision", this.trainCollision);
			node.set("keepChunksLoaded", this.keepChunksLoaded);
			node.set("speedLimit", this.speedLimit);
			node.set("slowDown", this.slowDown);
			node.set("pushAway.mobs", this.pushMobs);
			node.set("pushAway.players", this.pushPlayers);
			node.set("pushAway.misc", this.pushMisc);
			node.set("ignoreStations", this.ignoreStations);
		}
		if (savecarts) {
			ConfigurationNode carts = node.getNode("carts");
			for (CartProperties prop : this) {
				ConfigurationNode cart = carts.getNode(prop.getUUID().toString());
				prop.save(cart, minimal);
				if (cart.getKeys().isEmpty()) carts.remove(cart.getName());
			}
		}
	}
}
