package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.permissions.Permission;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.common.utils.EnumUtil;

public class TrainCarts extends PluginBase {
	
	public TrainCarts() {
		super(1818, 1846);
	}

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
					Material type = EnumUtil.parseMaterial(value, null);
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
	
	@Override
	public void updateDependency(Plugin plugin, String pluginName, boolean enabled) {
		if (pluginName.equals("SignLink")) {
			Task.stop(signtask);
			if (SignLinkEnabled = enabled) {
				log(Level.INFO, "SignLink detected, support for arrival signs added!");
				signtask = new Task(this) {
					public void run() {
						ArrivalSigns.updateAll();
					}
				};
				signtask.start(0, 10);
			} else {
				signtask = null;
			}
		} else if (pluginName.equals("My Worlds")) {
			if (MyWorldsEnabled = enabled) {
				log(Level.INFO, "MyWorlds detected, support for portal sign train teleportation added!");
			}
		} else if (pluginName.equals("Showcase")) {
			isShowcaseEnabled = enabled;
		} else if (pluginName.equals("ShowCaseStandalone")) {
			isSCSEnabled = enabled;
		} else if (pluginName.equals("BleedingMobs")) {
			bleedingMobsInstance = plugin;
		}
	}
		
	private Task cleanupTask;
		
	public void enable() {
		plugin = this;
		
		//registering
		this.register(TCListener.class);
		this.register("train", "cart");
		Permission.registerAll();
				
		//Load configuration
		loadConfig();
		
		//init reflection-made fields
		MinecartMemberTrackerEntry.initFields();

		//Init signs
		SignAction.init();
		
		//Load groups
		GroupManager.init(getDataFolder() + File.separator + "trains.groupdata");

		//Load properties
		TrainProperties.init();

		//Load destinations
		PathNode.init();

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
		cleanupTask.start(0, 10);
	}
	public void disable() {
		//Stop tasks
		if (signtask != null) signtask.stop();
		if (cleanupTask != null) cleanupTask.stop();

		//Save properties
		TrainProperties.deinit();
		
		//Save destinations
		PathNode.deinit();

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
	}

	public boolean command(CommandSender sender, String cmd, String[] args) {
		return Commands.execute(sender, cmd, args);
	}

	@Override
	public void permissions() {
		//TODO: Add permission defaults here
	}

}
