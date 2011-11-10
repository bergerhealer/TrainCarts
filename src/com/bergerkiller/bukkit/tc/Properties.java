package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.List;

public class Properties {
	
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
	
	public double speedLimit = 0.4;
	public boolean requirePoweredMinecart = false;
	public String destination = "";
	public boolean keepChunksLoaded = false;	
	
	/*
	 * Tags
	 */
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
		this.pushMobs = config.getBoolean(key + ".pushAway.mobs", this.pushMobs);
		this.pushPlayers = config.getBoolean(key + ".pushAway.players", this.pushPlayers);
		this.pushMisc = config.getBoolean(key + ".pushAway.misc", this.pushMisc);
		this.speedLimit = config.getDouble(key + ".speedLimit", this.speedLimit);
		this.requirePoweredMinecart = config.getBoolean(key + ".requirePoweredMinecart", this.requirePoweredMinecart);
		this.destination = config.getString(key + ".destination", this.destination);
		this.keepChunksLoaded = config.getBoolean(key + ".keepChunksLoaded", this.keepChunksLoaded);
	}
	public void load(Properties source) {
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
		this.pushMobs = source.pushMobs;
		this.pushPlayers = source.pushPlayers;
		this.pushMisc = source.pushMisc;
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
		config.set(key + ".pushAway.mobs", this.pushMobs);
		config.set(key + ".pushAway.players", this.pushPlayers);
		config.set(key + ".pushAway.misc", this.pushMisc);
	}

}
