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
	
	public void showEnterMessage(Entity forEntity) {
		if (forEntity instanceof Player && enterMessage != null && !enterMessage.equals("")) {
			((Player) forEntity).sendMessage(ChatColor.YELLOW + enterMessage);
		}
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

	public static void load(String filename) {
		Configuration config = new Configuration(new File(filename));
		config.load();
		for (String trainname : config.getKeys()) {
			TrainProperties prop = get(trainname);
			prop.owners = config.getStringList(trainname + ".owners", new ArrayList<String>());
			prop.passengers = config.getStringList(trainname + ".passengers", new ArrayList<String>());
			prop.allowMobsEnter = config.getBoolean(trainname + ".allowMobsEnter", prop.allowMobsEnter);
			prop.enterMessage = config.getString(trainname + ".enterMessage", prop.enterMessage);
			prop.allowLinking = config.getBoolean(trainname + ".allowLinking", prop.allowLinking);
		}
	}
	public static void save(String filename) {
		Configuration config = new Configuration(new File(filename));
		for (TrainProperties prop : properties.values()) {
			config.setProperty(prop.getTrainName() + ".owners", prop.owners);
			config.setProperty(prop.getTrainName() + ".passengers", prop.passengers);
			config.setProperty(prop.getTrainName() + ".allowMobsEnter", prop.allowMobsEnter);
			config.setProperty(prop.getTrainName() + ".enterMessage", prop.enterMessage);
			config.setProperty(prop.getTrainName() + ".allowLinking", prop.allowLinking);	
		}
		config.save();
	}
}
