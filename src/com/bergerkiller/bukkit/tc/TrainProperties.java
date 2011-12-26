package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;

import com.bergerkiller.bukkit.config.ConfigurationNode;
import com.bergerkiller.bukkit.config.FileConfiguration;

public class TrainProperties {
	private static HashMap<String, TrainProperties> properties = new HashMap<String, TrainProperties>();
	public static TrainProperties get(String trainname) {
		if (trainname == null) return null;
		TrainProperties prop = properties.get(trainname);
		if (prop == null) {
			return new TrainProperties(trainname);
		}
		return prop;
	}
	public static boolean exists(String trainname) {
		return properties.containsKey(trainname);
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
	
	private final Set<CartProperties> cartproperties = new HashSet<CartProperties>();
	public Set<CartProperties> getCarts() {
		return this.cartproperties;
	}
	
	protected void addCart(MinecartMember member) {
		this.cartproperties.add(member.getProperties());
	}
	protected void removeCart(MinecartMember member) {
		this.cartproperties.remove(member.getProperties());
	}
	
	/*
	 * Owners
	 */
	public boolean hasOwners() {
		for (CartProperties prop : this.cartproperties) {
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
		for (CartProperties prop : this.cartproperties) {
			if (prop.isOwner(player)) return true;
		}
		return false;
	}	
	public boolean isDirectOwner(Player player) {
		return this.isDirectOwner(player.getName().toLowerCase());
	}
	public boolean isDirectOwner(String player) {
		for (CartProperties prop : this.cartproperties) {
			if (prop.isOwner(player)) return true;
		}
		return false;
	}
	
	/*
	 * Tags
	 */
	public boolean hasTag(String tag) {
		for (CartProperties prop : this.cartproperties) {
			if (prop.hasTag(tag)) return true;
		}
		return false;
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
	 * Destinations
	 */
	public void setDestination(String destination) {
		for (CartProperties prop : this.cartproperties) {
			prop.destination = destination;
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
		this.remove();
		this.trainname = newtrainname;
		properties.put(newtrainname, this);
		return this;
	}
	
	public static void load(String filename) {
		FileConfiguration config = new FileConfiguration(new File(filename));
		config.load();
		for (ConfigurationNode node : config.getNodes()) {
			get(node.getName()).load(node);
		}
	}
	public static void save(String filename) {
		FileConfiguration config = new FileConfiguration(new File(filename));
		for (TrainProperties prop : properties.values()) {
			//does this train even exist?!
			if (GroupManager.contains(prop.getTrainName())) {
				prop.save(config.getNode(prop.getTrainName()));
			} else {
				config.set(prop.getTrainName(), null);
			}
		}
		config.save();
	}
	public static void init(String filename) {
		load(filename);
	}
	public static void deinit(String filename) {
		save(filename);
		deffile = null;
		defconfig = null;
		properties.clear();
		properties = null;
	}
    
	/*
	 * Train properties defaults
	 */
	private static File deffile = null;
	private static FileConfiguration defconfig = null;

	public static FileConfiguration getDefaults() {
		if (deffile == null) {
			deffile = new File(TrainCarts.plugin.getDataFolder() + File.separator + "defaultflags.yml");
			defconfig = new FileConfiguration(deffile);
			if (deffile.exists()) defconfig.load();
			TrainProperties prop = new TrainProperties();
			if (!defconfig.contains("default")) prop.save(defconfig.getNode("default"));
			if (!defconfig.contains("admin")) prop.save(defconfig.getNode("admin"));
			if (!defconfig.contains("station")) {
				prop.pushMisc = true;
				prop.pushMobs = true;
				prop.pushPlayers = true;
				prop.save(defconfig.getNode("station"));
			}
			defconfig.save();
		}
		defconfig.load();
		return defconfig;
	}
	public void setDefault() {
		setDefault("default");
	}
	public void setDefault(String key) {
		this.load(getDefaults().getNode(key));
	}
	public void setDefault(Player player) {
		if (player == null) {
			//Set default
			setDefault();
		} else {
			getDefaults();
			//Load it
			for (ConfigurationNode node : defconfig.getNodes()) {
				if (player.hasPermission("train.properties." + node.getName())) {
					this.load(node);
					break;
				}
			}
		}
	}
	
	
	public void load(ConfigurationNode node) {
		this.allowLinking = node.get("allowLinking", this.allowLinking);
		this.trainCollision = node.get("trainCollision", this.trainCollision);
		this.slowDown = node.get("slowDown", this.slowDown);
		this.pushMobs = node.get("pushAway.mobs", this.pushMobs);
		this.pushPlayers = node.get("pushAway.players", this.pushPlayers);
		this.pushMisc = node.get("pushAway.misc", this.pushMisc);
		this.speedLimit = Util.limit(node.get("speedLimit", this.speedLimit), 0, 20);
		this.requirePoweredMinecart = node.get("requirePoweredMinecart", this.requirePoweredMinecart);
		this.keepChunksLoaded = node.get("keepChunksLoaded", this.keepChunksLoaded);
	}
	public void load(TrainProperties source) {
		this.allowLinking = source.allowLinking;
		this.trainCollision = source.trainCollision;
		this.slowDown = source.slowDown;
		this.pushMobs = source.pushMobs;
		this.pushPlayers = source.pushPlayers;
		this.pushMisc = source.pushMisc;
		this.speedLimit = Util.limit(source.speedLimit, 0, 20);
		this.requirePoweredMinecart = source.requirePoweredMinecart;
		this.keepChunksLoaded = source.keepChunksLoaded;
	}
	public void save(ConfigurationNode node) {		
		node.set("allowLinking", this.allowLinking ? null : false);
		node.set("requirePoweredMinecart", this.requirePoweredMinecart ? true : null);
		node.set("trainCollision", this.trainCollision ? null : false);
		node.set("keepChunksLoaded", this.keepChunksLoaded ? true : null);
		node.set("speedLimit", this.speedLimit != 0.4 ? this.speedLimit : null);
		node.set("slowDown", this.slowDown ? null : false);
		node.set("pushAway.mobs", this.pushMobs ? true : null);
		node.set("pushAway.players", this.pushPlayers ? true : null);
		node.set("pushAway.misc", this.pushMisc ? null : false);
	}
}
