package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;

public class TrainProperties extends Properties {
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
		TrainProperties prop = properties.get(editing.get(by.getName()));
		if (prop == null) {
			MinecartGroup g = MinecartGroup.get(by.getVehicle());
			if (g == null) {
				//not found: match nearby
				for (Entity e : by.getNearbyEntities(5, 5, 5)) {
					MinecartGroup gnew = MinecartGroup.get(e);
					if (gnew != null) {
						if (g == null) {
							//set it
							g = gnew;
						} else {
							//found two: cancel operation
							g = null;
							break;
						}
					}
				}
			}
			if (g != null) prop = g.getProperties();
		}
		return prop;
	}

	private String trainname;	
	private boolean isStation = false;
	private Properties saved = new Properties();

	public Properties getSaved() {
		return this.saved;
	}
	public void restore() {
		super.load(this.saved);
	}
	public void setStation(boolean value) {
		if (value == this.isStation) return;
		this.isStation = value;
		if (value) {
			super.load(getDefaults(), "station");
		} else {
			this.restore();
		}
	}
	public boolean isAtStation() {
		return this.isStation;
	}

	public void showEnterMessage(Entity forEntity) {
		if (forEntity instanceof Player && enterMessage != null && !enterMessage.equals("")) {
			((Player) forEntity).sendMessage(ChatColor.YELLOW + enterMessage);
		}
	}

	/*
	 * Owners and passengers
	 */
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

	/*
	 * Push away settings
	 */
	public boolean canPushAway(Entity entity) {
		if (entity instanceof Player) {
			if (this.pushPlayers) {
				if (!TrainCarts.pushAwayIgnoreOwners) return true;
				return !this.isOwner((Player) entity, TrainCarts.pushAwayIgnoreGlobalOwners);
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
			if (this.isStation) return false;
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
	public TrainProperties rename(String newtrainname) {
		this.remove();
		for (Map.Entry<String, String> edit : editing.entrySet()) {
			if (edit.getValue().equals(this.trainname)){
				edit.setValue(newtrainname);
			}
		}
		this.trainname = newtrainname;
		properties.put(newtrainname, this);
		return this;
	}

	public void load(Configuration config, String trainname) {
		this.saved.load(config, trainname);
		this.restore();
	}
	public void load(Properties properties) {
		if (properties instanceof TrainProperties) {
			this.saved.load(((TrainProperties) properties).saved);
		} else {
			this.saved.load(properties);
		}
		super.load(properties);
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
				prop.saved.save(config, prop.getTrainName());
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
		editing.clear();
		editing = null;
	}
    
	/*
	 * Train properties defaults
	 */
	private static File deffile = null;
	private static Configuration defconfig = null;

	public static Configuration getDefaults() {
		if (deffile == null) {
			deffile = new File(TrainCarts.plugin.getDataFolder() + File.separator + "defaultflags.yml");
			defconfig = new Configuration(deffile);
			if (deffile.exists()) defconfig.load();
			TrainProperties prop = new TrainProperties();
			if (!defconfig.contains("default")) prop.save(defconfig, "default");
			if (!defconfig.contains("admin")) prop.save(defconfig, "admin");
			if (!defconfig.contains("station")) {
				prop.pushMisc = true;
				prop.pushMobs = true;
				prop.pushPlayers = true;
				prop.save(defconfig, "station");
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
		this.load(getDefaults(), key);
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
