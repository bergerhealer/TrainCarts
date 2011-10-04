package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;

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
	public List<String> owners = new ArrayList<String>();
	public List<String> passengers = new ArrayList<String>();
	public String enterMessage = null;
	public List<String> tags = new ArrayList<String>();
	public boolean trainCollision = true;
	public boolean slowDown = true;
	
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
	
	public boolean hasTag(String tag) {
		if (tag.startsWith("!")) {
			return !this.tags.contains(tag.substring(1));
		} else {
			return this.tags.contains(tag);
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
	public boolean isOwner(Player player) {
		if (owners.size() == 0) {
			return canBeOwner(player);
		} else if (player.hasPermission("train.command.globalproperties")) {
			return true;
		}
		for (String owner : owners) {
			if (owner.equalsIgnoreCase(player.getName())) return true;
		}
		return false;
	}
	public boolean isPassenger(Entity entered) {
		if (entered instanceof Player) {
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
	
	private TrainProperties(String trainname) {
		properties.put(trainname, this);
		this.trainname = trainname;
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
		this.owners = config.getStringList(key + ".owners", this.owners);
		this.passengers = config.getStringList(key + ".passengers", this.passengers);
		this.allowMobsEnter = config.getBoolean(key + ".allowMobsEnter", this.allowMobsEnter);
		this.enterMessage = config.getString(key + ".enterMessage", this.enterMessage);
		this.allowLinking = config.getBoolean(key + ".allowLinking", this.allowLinking);
		this.trainCollision = config.getBoolean(key + ".trainCollision", this.trainCollision);
		this.tags = config.getStringList(key + ".tags", this.tags);
		this.slowDown = config.getBoolean(key + ".slowDown", this.slowDown);	
	}
	public static void load(String filename) {
		Configuration config = new Configuration(new File(filename));
		config.load();
		for (String trainname : config.getKeys()) {
			get(trainname).load(config, trainname);
		}
	}
	public static void save(String filename) {
		Configuration config = new Configuration(new File(filename));
		for (TrainProperties prop : properties.values()) {
			//does this train even exist?!
			if (GroupManager.contains(prop.getTrainName())) {
				config.setProperty(prop.getTrainName() + ".owners", prop.owners);
				config.setProperty(prop.getTrainName() + ".passengers", prop.passengers);
				config.setProperty(prop.getTrainName() + ".allowMobsEnter", prop.allowMobsEnter);
				config.setProperty(prop.getTrainName() + ".enterMessage", prop.enterMessage);
				config.setProperty(prop.getTrainName() + ".allowLinking", prop.allowLinking);
				config.setProperty(prop.getTrainName() + ".trainCollision", prop.trainCollision);
				config.setProperty(prop.getTrainName() + ".tags", prop.tags);
				config.setProperty(prop.getTrainName() + ".slowDown", prop.slowDown);
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
				//default
				defconfig.setProperty("default.owners", new ArrayList<String>());
				defconfig.setProperty("default.passengers", new ArrayList<String>());
				defconfig.setProperty("default.allowMobsEnter", true);
				defconfig.setProperty("default.enterMessage", null);
				defconfig.setProperty("default.allowLinking", true);
				defconfig.setProperty("default.trainCollision", true);
				defconfig.setProperty("default.tags", new ArrayList<String>());
				defconfig.setProperty("default.slowDown", true);
				//admin
				defconfig.setProperty("admin.owners", new ArrayList<String>());
				defconfig.setProperty("admin.passengers", new ArrayList<String>());
				defconfig.setProperty("admin.allowMobsEnter", true);
				defconfig.setProperty("admin.enterMessage", null);
				defconfig.setProperty("admin.allowLinking", true);
				defconfig.setProperty("admin.trainCollision", true);
				defconfig.setProperty("admin.tags", new ArrayList<String>());
				defconfig.setProperty("admin.slowDown", true);
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
			for (String key : defconfig.getKeys()) {
				if (player.hasPermission("train.properties." + key)) {
					this.load(defconfig, key);
					return;
				}
			}
			//Set owner
			if (TrainCarts.setOwnerOnPlacement) {
				this.owners.add(player.getName());
			}
		}
	}
}
