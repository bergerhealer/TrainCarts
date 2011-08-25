package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.logging.Level;

import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.util.config.Configuration;

import com.bergerkiller.bukkit.tc.Listeners.TCBlockListener;
import com.bergerkiller.bukkit.tc.Listeners.TCCustomListener;
import com.bergerkiller.bukkit.tc.Listeners.TCPlayerListener;
import com.bergerkiller.bukkit.tc.Listeners.TCVehicleListener;
import com.bergerkiller.bukkit.tc.Listeners.TCWorldListener;


public class TrainCarts extends JavaPlugin {
	/*
	 * Settings
	 */	
	public static double cartDistance = 1.5;
	public static double turnedCartDistance = 1.6;
	public static boolean removeDerailedCarts = false;
	public static double cartDistanceForcer = 0.2;
	public static double turnedCartDistanceForcer = 0.3;
	public static double nearCartDistanceFactor = 1.4;
	public static double maxCartSpeed = 0.35;
	public static double maxCartDistance = 4;
	public static boolean breakCombinedCarts = false;
	public static boolean spawnItemDrops = true;
	public static double poweredCartBoost = 0.1;
	public static Vector exitOffset = new Vector(0, 0, 0);
	public static double pushAwayAtVelocity = 0.1;
	public static double pushAwayForce = 0.2;
	public static boolean pushAwayMobs = false;
	public static boolean pushAwayPlayers = false;
	public static boolean pushAwayMisc = true;
	public static boolean keepChunksLoaded = true;
	public static boolean useCoalFromStorageCart = false;
		
	public static TrainCarts plugin;
	private final TCPlayerListener playerListener = new TCPlayerListener();
	private final TCWorldListener worldListener = new TCWorldListener();
	private final TCVehicleListener vehicleListener = new TCVehicleListener();	
	private final TCBlockListener blockListener = new TCBlockListener();	
	private final TCCustomListener customListener = new TCCustomListener();	
	
	private Task ctask;
	
	public void onEnable() {		
		plugin = this;
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_INTERACT_ENTITY, playerListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.VEHICLE_DESTROY, vehicleListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.VEHICLE_COLLISION_ENTITY, vehicleListener, Priority.Lowest, this);
		pm.registerEvent(Event.Type.VEHICLE_COLLISION_BLOCK, vehicleListener, Priority.Lowest, this);
		pm.registerEvent(Event.Type.VEHICLE_EXIT, vehicleListener, Priority.Monitor, this);	
		pm.registerEvent(Event.Type.VEHICLE_MOVE, vehicleListener, Priority.Monitor, this);	
		pm.registerEvent(Event.Type.CHUNK_UNLOAD, worldListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.CHUNK_LOAD, worldListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.REDSTONE_CHANGE, blockListener, Priority.Highest, this);
		if (this.getServer().getPluginManager().isPluginEnabled("MinecartManiaCore")) {
			Util.log(Level.INFO, "Minecart Mania detected, support added!");
			pm.registerEvent(Event.Type.CUSTOM_EVENT, customListener, Priority.Lowest, this);
		}
		
		//Load from config
		Configuration config = this.getConfiguration();
		boolean use = config.getBoolean("use", true);
		if (use) {
			double exitx, exity, exitz;
			cartDistance = config.getDouble("normal.cartDistance", cartDistance);
			cartDistanceForcer = config.getDouble("normal.cartDistanceForcer", cartDistanceForcer);	
			turnedCartDistance = config.getDouble("turned.cartDistance", turnedCartDistance);
			turnedCartDistanceForcer = config.getDouble("turned.cartDistanceForcer", turnedCartDistanceForcer);	
			nearCartDistanceFactor = config.getDouble("nearCartDistanceFactor", nearCartDistanceFactor);	
			removeDerailedCarts = config.getBoolean("removeDerailedCarts", removeDerailedCarts);
			maxCartSpeed = config.getDouble("maxCartSpeed", maxCartSpeed);
			maxCartDistance = config.getDouble("maxCartDistance", maxCartDistance);
			breakCombinedCarts = config.getBoolean("breakCombinedCarts", breakCombinedCarts);
			spawnItemDrops = config.getBoolean("spawnItemDrops", spawnItemDrops);
			poweredCartBoost = config.getDouble("poweredCartBoost", poweredCartBoost);
			exitx = config.getDouble("exitOffset.x", exitOffset.getX());
			exity = config.getDouble("exitOffset.y", exitOffset.getY());
			exitz = config.getDouble("exitOffset.z", exitOffset.getZ());
			exitOffset = new Vector(exitx, exity, exitz);
			pushAwayAtVelocity = config.getDouble("pushAway.atVelocity", pushAwayAtVelocity);
			pushAwayForce = config.getDouble("pushAway.force", pushAwayForce);
			pushAwayMobs = config.getBoolean("pushAway.pushMobs", pushAwayMobs);
			pushAwayPlayers = config.getBoolean("pushAway.pushPlayers", pushAwayPlayers);
			pushAwayMisc = config.getBoolean("pushAway.pushMisc", pushAwayMisc);
			keepChunksLoaded = config.getBoolean("keepChunksLoaded", keepChunksLoaded);
			useCoalFromStorageCart = config.getBoolean("useCoalFromStorageCart", useCoalFromStorageCart);
			
			//save it again (no file was there or invalid)
			config.setHeader("# In here you can change the settings for the TrainCarts plugin", 
					"# Please note that the default settings are usually the best", 
					"# Changing these settings can cause the plugin to fail", 
					"# To reset the settings, remove this file or set 'use' to false to ignore these settings", 
					"# ======================================================================================", 
					"# cartDistance is the distance kept between normal/turned carts in a group", 
					"# cartDistanceForcer is the factor applied to adjust this distance, too high and it will bump violently", 
					"# nearCartDistanceFactor is the factor applied to the regular forcers if the carts are too close to each other", 
					"# removeDerailedCarts sets if carts without tracks underneath are cleared from the group", 
					"# maxCartSpeed is the maximum speed to use for trains. Use a value between 0 and ~0.4, > 0.4 can cause derailments!", 
					"# maxCartDistance sets the distance at which carts are thrown out of their group",  
					"# breakCombinedCarts sets if combined carts (e.g. PoweredMinecart) breaks up into multiple parts upon breaking (e.g. furnace and minecart)", 
					"# spawnItemDrops sets if items are spawned when breaking a minecart", 
					"# exitOffset is the relative offset of where a player is teleported when exiting a Minecart", 
					"# pushAway settings are settings that push mobs, players, items and others away from a riding minecart", 
					"#    atVelocity sets the velocity of the minecart at which it starts pushing others", 
					"#    force sets the pushing force of a pushing Minecart", 
					"#    pushMobs sets if mobs are pushed away",
					"#    pushPlayers sets if players are pushed away", 
					"#    pushMisc sets if misc. entities, such as boats and dropped items, are pushed away", 
					"# keepChunksLoaded sets if chunks are loaded near minecart Trains");
			config.setProperty("use", use);
			config.setProperty("normal.cartDistance", cartDistance);
			config.setProperty("normal.cartDistanceForcer", cartDistanceForcer);
			config.setProperty("turned.cartDistance", turnedCartDistance);
			config.setProperty("turned.cartDistanceForcer", turnedCartDistanceForcer);
			config.setProperty("nearCartDistanceFactor", nearCartDistanceFactor);
			config.setProperty("removeDerailedCarts", removeDerailedCarts);
			config.setProperty("maxCartSpeed", maxCartSpeed);
			config.setProperty("maxCartDistance", maxCartDistance);
			config.setProperty("breakCombinedCarts", breakCombinedCarts);
			config.setProperty("spawnItemDrops", spawnItemDrops);
			config.setProperty("poweredCartBoost", poweredCartBoost);
			config.setProperty("exitOffset.x", exitx);
			config.setProperty("exitOffset.y", exity);
			config.setProperty("exitOffset.z", exitz);
			config.setProperty("pushAway.atVelocity", pushAwayAtVelocity);
			config.setProperty("pushAway.force", pushAwayForce);
			config.setProperty("pushAway.pushMobs", pushAwayMobs);
			config.setProperty("pushAway.pushPlayer", pushAwayPlayers);
			config.setProperty("pushAway.pushMisc", pushAwayMisc);
			config.setProperty("keepChunksLoaded", keepChunksLoaded);
			config.setProperty("useCoalFromStorageCart", useCoalFromStorageCart);
			config.save();
		}
		
		//Load groups
		GroupManager.loadGroups(getDataFolder() + File.separator + "trains.groupdata");
		
		//Restore carts where possible
		GroupManager.refresh();
		
		//clean groups from dead and derailed carts and form new groups
		ctask = new Task(this) {
			public void run() {
				MinecartGroup.updateGroups();
			}
		};
		ctask.startRepeating(5L);
    		
        //final msg
        PluginDescriptionFile pdfFile = this.getDescription();
        Util.log(Level.INFO, "version " + pdfFile.getVersion() + " is enabled!" );
	}
	public void onDisable() {
		//Stop tasks
		ctask.stop();
		
		//undo replacements for correct saving
		for (MinecartGroup mg : MinecartGroup.getGroups()) {
			GroupManager.hideGroup(mg);
		}
		
		//Save for next load
		GroupManager.saveGroups(getDataFolder() + File.separator + "trains.groupdata");
		
		
		//MinecartMember.undoReplacement();
		
		
		
		
		System.out.println("TrainCarts disabled!");
	}
		
}
