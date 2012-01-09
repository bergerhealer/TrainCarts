package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.listeners.TCBlockListener;
import com.bergerkiller.bukkit.tc.listeners.TCCustomListener;
import com.bergerkiller.bukkit.tc.listeners.TCPlayerListener;
import com.bergerkiller.bukkit.tc.listeners.TCVehicleListener;
import com.bergerkiller.bukkit.tc.listeners.TCWorldListener;
import com.bergerkiller.bukkit.tc.permissions.Permission;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.utils.ItemUtil;

public class TrainCarts extends JavaPlugin {
	/*
	 * Settings
	 */	
	public static double cartDistance;
	public static double turnedCartDistance;
	public static double cartDistanceForcer;
	public static double turnedCartDistanceForcer;
	public static double nearCartDistanceFactor;
	public static double maxCartDistance;
	public static boolean breakCombinedCarts;
	public static boolean spawnItemDrops;
	public static double poweredCartBoost;
	public static Vector exitOffset;
	public static double pushAwayForce;
	public static double launchForce;
	public static boolean pushAwayIgnoreGlobalOwners;
	public static boolean pushAwayIgnoreOwners;
	public static boolean useCoalFromStorageCart;
	public static boolean setOwnerOnPlacement;
	public static boolean keepChunksLoadedOnlyWhenMoving;
	public static boolean playSoundAtStation;
	private Set<Material> allowedBlockBreakTypes = new HashSet<Material>();
	public static int maxDetectorLength;
	public static boolean stackMinecarts;

	public static boolean SignLinkEnabled = false;
	public static boolean MinecartManiaEnabled = false;
	public static boolean MyWorldsEnabled = false;
	public static boolean isShowcaseEnabled = false;
	public static boolean isSCSEnabled = false;
	public static Plugin bleedingMobsInstance = null;
	
	public static String version;
	
	public static TrainCarts plugin;
	private final TCPlayerListener playerListener = new TCPlayerListener();
	private final TCWorldListener worldListener = new TCWorldListener();
	private final TCVehicleListener vehicleListener = new TCVehicleListener();	
	private final TCBlockListener blockListener = new TCBlockListener();	
	private final TCCustomListener customListener = new TCCustomListener();	

	private Task signtask;

	public static boolean canBreak(Material type) {
		return plugin.allowedBlockBreakTypes.contains(type);
	}
	
	public void loadConfig() {
		FileConfiguration config = new FileConfiguration(this);
		if (config.get("use", true)) {
			double exitx, exity, exitz;
			config.load();
			cartDistance = config.get("normal.cartDistance", 1.5);
			cartDistanceForcer = config.get("normal.cartDistanceForcer", 0.1);	
			turnedCartDistance = config.get("turned.cartDistance", 1.6);
			turnedCartDistanceForcer = config.get("turned.cartDistanceForcer", 0.2);	
			nearCartDistanceFactor = config.get("nearCartDistanceFactor", 1.2);	
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
			playSoundAtStation = config.get("playSoundAtStation", true);
			keepChunksLoadedOnlyWhenMoving = config.get("keepChunksLoadedOnlyWhenMoving", false);
			maxDetectorLength = config.get("maxDetectorLength", 2000);
			stackMinecarts = config.get("stackMinecarts", true);
			allowedBlockBreakTypes.clear();
			if (config.contains("allowedBlockBreakTypes")) {
				for (String value : config.getList("allowedBlockBreakTypes", String.class)) {
					Material type = ItemUtil.getMaterial(value);
					if (type != null) allowedBlockBreakTypes.add(type);
				}
			} else {
				allowedBlockBreakTypes.add(Material.CROPS);
				allowedBlockBreakTypes.add(Material.LOG);
			}
			//set it again
			List<String> types = config.getList("allowedBlockBreakTypes", String.class);
			types.clear();
			for (Material mat : allowedBlockBreakTypes) {
				types.add(mat.toString());
			}
			
			config.set("use", true);
			exitOffset = new Vector(exitx, exity, exitz);
			config.save();
		}
	}

	private void registerEvent(Event.Type type, Listener listener, Priority priority) {
		this.getServer().getPluginManager().registerEvent(type, listener, priority, this);
	}
	
	private void initDependencies() {
		if (this.getServer().getPluginManager().isPluginEnabled("MinecartManiaCore")) {
			Util.log(Level.INFO, "Minecart Mania detected, support added!");
			MinecartManiaEnabled = true;
			registerEvent(Event.Type.CUSTOM_EVENT, customListener, Priority.Lowest);
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
		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			public void run() {
				PluginManager pm = getServer().getPluginManager();
				isShowcaseEnabled = pm.isPluginEnabled("Showcase");
				isSCSEnabled = pm.isPluginEnabled("ShowCaseStandalone");
				bleedingMobsInstance = pm.getPlugin("BleedingMobs");
			}
		}, 1);
	}
	
	private Task cleanupTask;
	public void onEnable() {
		plugin = this;
		Permission.registerAll();
		registerEvent(Event.Type.PLAYER_INTERACT_ENTITY, playerListener, Priority.Highest);
		registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Priority.Highest);
		registerEvent(Event.Type.PLAYER_PICKUP_ITEM, playerListener, Priority.High);
		registerEvent(Event.Type.VEHICLE_DESTROY, vehicleListener, Priority.Monitor);
		registerEvent(Event.Type.VEHICLE_CREATE, vehicleListener, Priority.Highest);
		registerEvent(Event.Type.VEHICLE_COLLISION_ENTITY, vehicleListener, Priority.Lowest);
		registerEvent(Event.Type.VEHICLE_COLLISION_BLOCK, vehicleListener, Priority.Lowest);
		registerEvent(Event.Type.VEHICLE_EXIT, vehicleListener, Priority.Highest);	
		registerEvent(Event.Type.VEHICLE_ENTER, vehicleListener, Priority.Highest);	
		registerEvent(Event.Type.VEHICLE_DAMAGE, vehicleListener, Priority.Highest);	
		registerEvent(Event.Type.CHUNK_UNLOAD, worldListener, Priority.Monitor);
		registerEvent(Event.Type.CHUNK_LOAD, worldListener, Priority.Monitor);
		registerEvent(Event.Type.REDSTONE_CHANGE, blockListener, Priority.Highest);
		registerEvent(Event.Type.SIGN_CHANGE, blockListener, Priority.Highest);
		registerEvent(Event.Type.BLOCK_BREAK, blockListener, Priority.Monitor);
		initDependencies();
		
		//Load configuration
		loadConfig();

		//Init signs
		SignAction.init();
		
		//Load groups
		GroupManager.init(getDataFolder() + File.separator + "trains.groupdata");

		//Load properties
		TrainProperties.init();

		//Load destinations
		Destinations.init();

		//Load arrival times
		ArrivalSigns.init(getDataFolder() + File.separator + "arrivaltimes.txt");
		
		//Load detector regions
		DetectorRegion.init(getDataFolder() + File.separator + "detectorregions.dat");	
		
		//Load detector sign locations
		SignActionDetector.init(getDataFolder() + File.separator + "detectorsigns.dat");
		
		//Restore carts where possible
		GroupManager.refresh();
		
		//Start member removal task
		cleanupTask = new Task(this) {
			public void run() {
				MinecartMember.cleanUpDeadCarts();
			}
		};
		cleanupTask.startRepeating(10);

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
		if (cleanupTask != null) cleanupTask.stop();

		//Save properties
		TrainProperties.deinit();
		
		//Save destinations
		Destinations.deinit();

		//Save arrival times
		ArrivalSigns.deinit(getDataFolder() + File.separator + "arrivaltimes.txt");
		
		//Save detector sign locations
		SignActionDetector.deinit(getDataFolder() + File.separator + "detectorsigns.dat");
		
		//Save detector regions
		DetectorRegion.deinit(getDataFolder() + File.separator + "detectorregions.dat");	
		
		//undo replacements for correct native saving
		for (MinecartGroup mg : MinecartGroup.getGroups()) {
			GroupManager.hideGroup(mg);
		}
		//entities left behind?
		for (World w : Bukkit.getServer().getWorlds()) {
			for (Entity e : w.getEntities()) {
				GroupManager.hideGroup(e);
			}
		}
		
		//Save for next load
		GroupManager.deinit(getDataFolder() + File.separator + "trains.groupdata");
		
		SignAction.deinit();
		
		plugin = null;
		
		System.out.println("TrainCarts disabled!");
	}

	public boolean onCommand(CommandSender sender, Command c, String cmd, String[] args) {
		return Commands.execute(sender, cmd, args);
	}
}
