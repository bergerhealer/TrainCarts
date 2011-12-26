package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.commands.Commands;
import com.bergerkiller.bukkit.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.Listeners.CustomEvents;
import com.bergerkiller.bukkit.tc.Listeners.TCBlockListener;
import com.bergerkiller.bukkit.tc.Listeners.TCCustomListener;
import com.bergerkiller.bukkit.tc.Listeners.TCPlayerListener;
import com.bergerkiller.bukkit.tc.Listeners.TCVehicleListener;
import com.bergerkiller.bukkit.tc.Listeners.TCWorldListener;

public class TrainCarts extends JavaPlugin {
	/*
	 * Settings
	 */	
	public static double cartDistance;
	public static double turnedCartDistance;
	public static boolean removeDerailedCarts;
	public static double cartDistanceForcer;
	public static double turnedCartDistanceForcer;
	public static double nearCartDistanceFactor;
	public static double maxCartDistance;
	public static boolean breakCombinedCarts;
	public static boolean spawnItemDrops;
	public static double poweredCartBoost;
	public static Vector exitOffset = new Vector(0, 0, 0);
	public static double pushAwayForce;
	public static double launchForce;
	public static boolean pushAwayIgnoreGlobalOwners;
	public static boolean pushAwayIgnoreOwners;
	public static boolean useCoalFromStorageCart;
	public static boolean setOwnerOnPlacement;
	public static boolean keepChunksLoadedOnlyWhenMoving = false;

	public static boolean SignLinkEnabled = false;
	public static boolean MinecartManiaEnabled = false;
	public static boolean MyWorldsEnabled = false;

	public static String version;
	
	public static TrainCarts plugin;
	private final TCPlayerListener playerListener = new TCPlayerListener();
	private final TCWorldListener worldListener = new TCWorldListener();
	private final TCVehicleListener vehicleListener = new TCVehicleListener();	
	private final TCBlockListener blockListener = new TCBlockListener();	
	private final TCCustomListener customListener = new TCCustomListener();	

	private Task signtask;

	public void loadConfig() {
		FileConfiguration config = new FileConfiguration(this);
		boolean use = config.get("use", true);
		if (use) {
			double exitx, exity, exitz;
			config.load();
			cartDistance = config.get("normal.cartDistance", 1.5);
			cartDistanceForcer = config.get("normal.cartDistanceForcer", 0.1);	
			turnedCartDistance = config.get("turned.cartDistance", 1.6);
			turnedCartDistanceForcer = config.get("turned.cartDistanceForcer", 0.2);	
			nearCartDistanceFactor = config.get("nearCartDistanceFactor", 1.2);	
			removeDerailedCarts = config.get("removeDerailedCarts", false);
			maxCartDistance = config.get("maxCartDistance", (double) 4);
			breakCombinedCarts = config.get("breakCombinedCarts", false);
			spawnItemDrops = config.get("spawnItemDrops", true);
			poweredCartBoost = config.get("poweredCartBoost", 0.1);
			exitx = config.get("exitOffset.x", (double) 0);
			exity = config.get("exitOffset.y", (double) 0);
			exitz = config.get("exitOffset.z", (double) 0);
			launchForce = config.get("launchForce", 10.0);
			pushAwayForce = config.get("pushAwayForce", 0.2);
			pushAwayIgnoreGlobalOwners = config.get("pushAwayIgnoreGlobalOwners", false);
			pushAwayIgnoreOwners = config.get("pushAwayIgnoreOwners", true);
			useCoalFromStorageCart = config.get("useCoalFromStorageCart", false);
			setOwnerOnPlacement = config.get("setOwnerOnPlacement", true);
			keepChunksLoadedOnlyWhenMoving = config.get("keepChunksLoadedOnlyWhenMoving", false);
			config.set("use", true);
			exitOffset = new Vector(exitx, exity, exitz);
			config.save();
		}
	}

	public void onEnable() {
		plugin = this;

		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_INTERACT_ENTITY, playerListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.VEHICLE_DESTROY, vehicleListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.VEHICLE_CREATE, vehicleListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.VEHICLE_COLLISION_ENTITY, vehicleListener, Priority.Lowest, this);
		pm.registerEvent(Event.Type.VEHICLE_COLLISION_BLOCK, vehicleListener, Priority.Lowest, this);
		pm.registerEvent(Event.Type.VEHICLE_EXIT, vehicleListener, Priority.Highest, this);	
		pm.registerEvent(Event.Type.VEHICLE_ENTER, vehicleListener, Priority.Highest, this);	
		pm.registerEvent(Event.Type.VEHICLE_DAMAGE, vehicleListener, Priority.Highest, this);	
		pm.registerEvent(Event.Type.CHUNK_UNLOAD, worldListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.CHUNK_LOAD, worldListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.REDSTONE_CHANGE, blockListener, Priority.Highest, this);
		pm.registerEvent(Event.Type.SIGN_CHANGE, blockListener, Priority.Highest, this);
		if (this.getServer().getPluginManager().isPluginEnabled("MinecartManiaCore")) {
			Util.log(Level.INFO, "Minecart Mania detected, support added!");
			MinecartManiaEnabled = true;
			pm.registerEvent(Event.Type.CUSTOM_EVENT, customListener, Priority.Lowest, this);
		}
		if (this.getServer().getPluginManager().isPluginEnabled("SignLink")) {
			Util.log(Level.INFO, "SignLink detected, support for arrival signs added!");
			SignLinkEnabled = true;
			signtask = new Task(this) {
				public void run() {
					ArrivalSigns.updateAll();
				}
			};
			signtask.startRepeating(10);
		}
		if (this.getServer().getPluginManager().isPluginEnabled("My Worlds")) {
			Util.log(Level.INFO, "MyWorlds detected, support for portal sign train teleportation added!");
			MyWorldsEnabled = true;
		}

		//Load configuration
		loadConfig();

		//Load groups
		GroupManager.init(getDataFolder() + File.separator + "trains.groupdata");

		//Load properties
		TrainProperties.init(getDataFolder() + File.separator + "trainflags.yml");

		//Load destinations
		Destinations.init(getDataFolder() + File.separator + "destinations.yml");

		//Load arrival times
		ArrivalSigns.init(getDataFolder() + File.separator + "arrivaltimes.txt");

		//Restore carts where possible
		GroupManager.refresh();

		//commands
		getCommand("train").setExecutor(this);
		getCommand("cart").setExecutor(this);

		//final msg
		version = this.getDescription().getVersion();
		Util.log(Level.INFO, "version " + version + " is enabled!");

	}
	public void onDisable() {
		//Stop tasks
		if (signtask != null) signtask.stop();

		//undo replacements for correct saving
		for (MinecartGroup mg : MinecartGroup.getGroups()) {
			GroupManager.hideGroup(mg);
		}
		//entities left behind?
		for (World w : Bukkit.getServer().getWorlds()) {
			for (Entity e : w.getEntities()) {
				GroupManager.hideGroup(e);
			}
		}

		//Save properties
		TrainProperties.deinit(getDataFolder() + File.separator + "trainflags.yml");

		//Save destinations
		Destinations.deinit(getDataFolder() + File.separator + "destinations.yml");

		//Save for next load
		GroupManager.deinit(getDataFolder() + File.separator + "trains.groupdata");

		//Save arrival times
		ArrivalSigns.deinit(getDataFolder() + File.separator + "arrivaltimes.txt");
		
		CustomEvents.deinit();

		plugin = null;
		
		System.out.println("TrainCarts disabled!");
	}

	public boolean onCommand(CommandSender sender, Command c, String cmd, String[] args) {
		return Commands.execute(sender, cmd, args);
	}
}
