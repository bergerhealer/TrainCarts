package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;

public class TrainProperties {
	private static HashMap<String, String> editing = new HashMap<String, String>();
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
	public static TrainProperties getEditing(Player by) {
		return properties.get(editing.get(by.getName()));
	}
	
	private String trainname;
	public boolean allowMobsEnter = true;
	public boolean allowLinking = true;
	public boolean allowPlayerExit = true;
	public boolean allowPlayerEnter = true;
	public List<String> owners = new ArrayList<String>();
	public List<String> passengers = new ArrayList<String>();
	public String enterMessage = null;
	public List<String> tags = new ArrayList<String>();
	public boolean trainCollision = true;
	public boolean slowDown = true;
	public boolean pushMobs = false;
	public boolean pushPlayers = false;
	public boolean pushMisc = true;
	public boolean pushAtStation = true;
	public boolean pushAway = false;
	public double speedLimit = 0.4;
	public boolean requirePoweredMinecart = false;
	public String destination = "";
	public boolean keepChunksLoaded = false;
	
	public void showEnterMessage(Entity forEntity) {
		if (forEntity instanceof Player && enterMessage != null && !enterMessage.equals("")) {
			((Player) forEntity).sendMessage(ChatColor.YELLOW + enterMessage);
		}
	}
	
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
	
	public boolean hasTag(String[] requirements, boolean firstIsAllPossible, boolean lastIsAllPossible) {
		if (requirements.length == 0) return this.tags.size() > 0;
		for (String tag : this.tags) {
			int index = 0;
			boolean has = true;
			boolean first = true;
			for (int i = 0; i < requirements.length; i++) {
				if (requirements[i].length() == 0) continue;
				index = tag.indexOf(requirements[i], index);
				if (index == -1 || (first && !firstIsAllPossible && index != 0)) {
					has = false;
					break;
				} else {
					index += requirements[i].length();
				}
				first = false;
			}
			if (has) {
				if (lastIsAllPossible || index == tag.length()) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean hasTag(String tag) {
		if (tag.startsWith("!")) {
			return !hasTag(tag.substring(1));
		} else {
			if (tag.length() == 0) return false;
			return hasTag(tag.split("\\*"), tag.startsWith("*"), tag.endsWith("*"));
		}
	}
	public void addTags(String... tags) {
		for (String tag : tags) {
			if (!this.hasTag(tag)) this.tags.add(tag);
		}
	}
	public void setTags(String... tags) {
		this.tags.clear();
		this.addTags(tags);
	}
	
	public static boolean canBeOwner(Player player) {
		return player.hasPermission("train.command.properties") || 
				player.hasPermission("train.command.globalproperties");
	}
	public boolean isDirectOwner(Player player) {
		for (String owner : owners) {
			if (owner.equalsIgnoreCase(player.getName())) return true;
		}
		return false;
	}
	public boolean isOwner(Player player) {
		return this.isOwner(player, true);
	}
	public boolean isOwner(Player player, boolean checkGlobal) {
		if (owners.size() == 0) {
			return canBeOwner(player);
		} else if (checkGlobal && player.hasPermission("train.command.globalproperties")) {
			return true;
		} else {
			return this.isDirectOwner(player);
		}
	}
	public boolean isPassenger(Entity entered) {
		if (entered instanceof Player) {
			if (this.passengers.size() == 0) return true;
			Player player = (Player) entered;
			if (isOwner(player)) return true;
			for (String passenger : passengers) {
				if (passenger.equalsIgnoreCase(player.getName())) return true;
			}
			return false;
		} else {
			return this.allowMobsEnter;
		}
	}
	
	public boolean sharesOwner(TrainProperties properties) {
		if (this.owners.size() == 0) return true;
		if (properties.owners.size() == 0) return true;
		for (String owner1 : this.owners) {
			for (String owner2 : properties.owners) {
				if (owner1.equalsIgnoreCase(owner2)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean canPushAway(Entity entity) {
		if (entity instanceof Player) {
			if (this.pushPlayers) {
				return !this.isOwner((Player) entity, TrainCarts.pushAwayIgnoreGlobalOwners);
			}
		} else if (entity instanceof Creature || entity instanceof Slime || entity instanceof Ghast) {
			if (this.pushMobs) return true;
		} else {
			if (this.pushMisc) return true;
		}
		return false;
	}
	
	private TrainProperties() {};
	private TrainProperties(String trainname) {
		this.trainname = trainname;
		properties.put(trainname, this);
		this.setDefault();
	}
	
	public String getTrainName() {
		return this.trainname;
	}
	
	public void setEditing(Player player) {
		setEditing(player, false);
	}
	public void setEditing(Player player, boolean force) {
		if (force || isOwner(player)) {
			editing.put(player.getName(), this.getTrainName());
		}
	}
	
	public void remove() {
		properties.remove(this.trainname);
	}
	public void add() {
		properties.put(this.trainname, this);
	}
	public void rename(String newtrainname) {
		this.remove();
		for (Map.Entry<String, String> edit : editing.entrySet()) {
			if (edit.getValue().equals(this.trainname)){
				edit.setValue(newtrainname);
			}
		}
		this.trainname = newtrainname;
		properties.put(newtrainname, this);
	}

	public void load(Configuration config, String key) {
		this.owners = config.getListOf(key + ".owners", this.owners);
		this.passengers = config.getListOf(key + ".passengers", this.passengers);
		this.allowMobsEnter = config.getBoolean(key + ".allowMobsEnter", this.allowMobsEnter);
		this.allowPlayerEnter = config.getBoolean(key + ".allowPlayerEnter", this.allowPlayerEnter);
		this.allowPlayerExit = config.getBoolean(key + ".allowPlayerExit", this.allowPlayerExit);
		this.enterMessage = config.getString(key + ".enterMessage", this.enterMessage);
		this.allowLinking = config.getBoolean(key + ".allowLinking", this.allowLinking);
		this.trainCollision = config.getBoolean(key + ".trainCollision", this.trainCollision);
		this.tags = config.getListOf(key + ".tags", this.tags);
		this.slowDown = config.getBoolean(key + ".slowDown", this.slowDown);
		this.pushAway = config.getBoolean(key + ".pushAway.isPushing", this.pushAway);
		this.pushMobs = config.getBoolean(key + ".pushAway.mobs", this.pushMobs);
		this.pushPlayers = config.getBoolean(key + ".pushAway.players", this.pushPlayers);
		this.pushMisc = config.getBoolean(key + ".pushAway.misc", this.pushMisc);
		this.pushAtStation = config.getBoolean(key + ".pushAway.atStation", this.pushAtStation);
		this.speedLimit = config.getDouble(key + ".speedLimit", this.speedLimit);
		this.requirePoweredMinecart = config.getBoolean(key + ".requirePoweredMinecart", this.requirePoweredMinecart);
		this.destination = config.getString(key + ".destination", this.destination);
		this.keepChunksLoaded = config.getBoolean(key + ".keepChunksLoaded", this.keepChunksLoaded);
	}
	public void load(TrainProperties source) {
		this.owners.addAll(source.owners);
		this.passengers.addAll(source.passengers);
		this.allowMobsEnter = source.allowMobsEnter;
		this.allowPlayerEnter = source.allowPlayerEnter;
		this.allowPlayerExit = source.allowPlayerExit;
		this.enterMessage = source.enterMessage;
		this.allowLinking = source.allowLinking;
		this.trainCollision = source.trainCollision;
		this.tags.addAll(source.tags);
		this.slowDown = source.slowDown;
		this.pushAway = source.pushAway;
		this.pushMobs = source.pushMobs;
		this.pushPlayers = source.pushPlayers;
		this.pushMisc = source.pushMisc;
		this.pushAtStation = source.pushAtStation;
		this.speedLimit = source.speedLimit;
		this.requirePoweredMinecart = source.requirePoweredMinecart;
		this.destination = source.destination;
		this.keepChunksLoaded = source.keepChunksLoaded;
	}
	public void save(Configuration config, String key) {		
		config.set(key + ".owners", this.owners);
		config.set(key + ".passengers", this.passengers);
		config.set(key + ".allowMobsEnter", this.allowMobsEnter);
		config.set(key + ".allowPlayerEnter", this.allowPlayerEnter);
		config.set(key + ".allowPlayerExit", this.allowPlayerExit);
		config.set(key + ".enterMessage", this.enterMessage);
		config.set(key + ".allowLinking", this.allowLinking);
		config.set(key + ".requirePoweredMinecart", this.requirePoweredMinecart);
		config.set(key + ".trainCollision", this.trainCollision);
		config.set(key + ".keepChunksLoaded", this.keepChunksLoaded);
		config.set(key + ".tags", this.tags);
		config.set(key + ".speedLimit", this.speedLimit);
		config.set(key + ".slowDown", this.slowDown);
		config.set(key + ".destination", this.destination);
		config.set(key + ".pushAway.isPushing", this.pushAway);
		config.set(key + ".pushAway.mobs", this.pushMobs);
		config.set(key + ".pushAway.players", this.pushPlayers);
		config.set(key + ".pushAway.misc", this.pushMisc);
		config.set(key + ".pushAway.atStation", this.pushAtStation);
	}
	public static void load(String filename) {
		Configuration config = new Configuration(new File(filename));
		config.load();
		for (String trainname : config.getKeys(false)) {
			get(trainname).load(config, trainname);
		}
	}
	public static void save(String filename) {
		Configuration config = new Configuration(new File(filename));
		for (TrainProperties prop : properties.values()) {
			//does this train even exist?!
			if (GroupManager.contains(prop.getTrainName())) {
				prop.save(config, prop.getTrainName());
			} else {
				config.set(prop.getTrainName(), null);
			}
		}
		config.save();
	}

	/*
	 * Train properties defaults
	 */
	private static File deffile = null;
	private static Configuration defconfig = null;
		
	public static Configuration getDefaults() {
		if (deffile == null) {
			deffile = new File(TrainCarts.plugin.getDataFolder() + File.separator + "defaultflags.yml");
			boolean make = !deffile.exists();
			defconfig = new Configuration(deffile);
			if (make) {
				TrainProperties prop = new TrainProperties();
				//default
				prop.save(defconfig, "default");
				prop.save(defconfig, "admin");
				defconfig.save();
			}
		}
		defconfig.load();
		return defconfig;
	}
	public void setDefault() {
		setDefault("default");
	}
	public void setDefault(String key) {
		getDefaults();
		this.load(defconfig, key);
	}
	public void setDefault(Player player) {
		if (player == null) {
			//Set default
			setDefault();
		} else {
			getDefaults();
			//Load it
			for (String key : defconfig.getKeys(false)) {
				if (player.hasPermission("train.properties." + key)) {
					this.load(defconfig, key);
					break;
				}
			}
			//Set owner
			if (TrainCarts.setOwnerOnPlacement) {
				this.owners.add(player.getName());
			}
		}
	}
}
