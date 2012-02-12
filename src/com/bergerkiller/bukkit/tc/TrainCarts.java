package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import net.minecraft.server.Chunk;
import net.minecraft.server.Entity;
import net.minecraft.server.WorldServer;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.ItemParser;
import com.bergerkiller.bukkit.common.Operation;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.common.utils.EnumUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

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
	public static int maxMinecartStackSize;
	public static int defaultTransferRadius;
	public static boolean showTransferAnimations;
	
	public static boolean SignLinkEnabled = false;
	public static boolean MinecartManiaEnabled = false;
	public static boolean MyWorldsEnabled = false;
	public static boolean isShowcaseEnabled = false;
	public static boolean isSCSEnabled = false;
	public static Plugin bleedingMobsInstance = null;

	public static TrainCarts plugin;
	private Task signtask, cleanupTask;
	private Map<String, ItemParser[]> parsers = new HashMap<String, ItemParser[]>();

	public ItemParser[] getParsers(String key) {
		ItemParser[] rval = parsers.get(key.toLowerCase());
		return rval == null ? new ItemParser[0] : rval;
	}
	
	public static boolean canBreak(Material type) {
		return plugin.allowedBlockBreakTypes.contains(type);
	}

	public void loadConfig() {
		FileConfiguration config = new FileConfiguration(this);

		double exitx, exity, exitz;
		config.load();
		config.setHeader("This is the configuration file of TrainCarts");
		config.addHeader("In here you can tweak TrainCarts to what you want");
		config.addHeader("For more information, you can visit the following websites:");
		config.addHeader("http://wiki.bukkit.org/TrainCarts-Plugin");
		config.addHeader("http://forums.bukkit.org/threads/traincarts.29491/");

		config.setHeader("normal", "\nSettings for normally-aligned (straight) carts");
		config.setHeader("normal.cartDistance", "The distance between two carts in a train");
		config.setHeader("normal.cartDistanceForcer", "The factor at which this distance is kept");	
		cartDistance = config.get("normal.cartDistance", 1.5);
		cartDistanceForcer = config.get("normal.cartDistanceForcer", 0.1);	
		config.setHeader("normal", "\nThe distance between two normally-aligned (straight) carts in a train");

		config.setHeader("turned", "\nSettings for turned (in curve) carts");
		config.setHeader("turned.cartDistance", "The distance between two carts in a train");
		config.setHeader("turned.cartDistanceForcer", "The factor at which this distance is kept");
		turnedCartDistance = config.get("turned.cartDistance", 1.6);
		turnedCartDistanceForcer = config.get("turned.cartDistanceForcer", 0.2);	

		config.setHeader("nearCartDistanceFactor", "\nThe 'keep distance' factor to apply when carts are too close to each other");
		nearCartDistanceFactor = config.get("nearCartDistanceFactor", 1.2);	

		config.setHeader("maxCartDistance", "\nThe maximum allowed cart distance, after this distance the carts break apart");
		maxCartDistance = config.get("maxCartDistance", (double) 4);

		config.setHeader("breakCombinedCarts", "\nWhether or not the combined carts (powered/storage minecarts) break up into two items");
		breakCombinedCarts = config.get("breakCombinedCarts", false);

		config.setHeader("spawnItemDrops", "\nWhether or not items drop when the minecarts break");
		spawnItemDrops = config.get("spawnItemDrops", true);

		config.setHeader("poweredCartBoost", "\nA performance boost to give to powered minecarts (0 = normal speed)");
		poweredCartBoost = config.get("poweredCartBoost", 0.1);

		config.setHeader("exitOffset", "\nThe XYZ offset used when a passenger exits a minecart");
		exitx = config.get("exitOffset.x", (double) 0);
		exity = config.get("exitOffset.y", (double) 0);
		exitz = config.get("exitOffset.z", (double) 0);

		config.setHeader("launchForce", "\nThe amount of velocity stations give when launching trains");
		launchForce = config.get("launchForce", 10.0);

		config.setHeader("pushAway", "\nSettings used when carts push away/aside others (if enabled)");
		config.setHeader("pushAway.force", "The amount of force at which minecarts push away others");
		config.setHeader("pushAway.ignoreOwners", "If train owners are ignored");
		config.setHeader("pushAway.ignoreGlobalOwners", "If global train owners are ignored");
		pushAwayForce = config.get("pushAway.force", 0.2);
		pushAwayIgnoreOwners = config.get("pushAway.ignoreOwners", true);
		pushAwayIgnoreGlobalOwners = config.get("pushAway.ignoreGlobalOwners", false);

		config.setHeader("useCoalFromStorageCart", "\nWhether or not powered minecarts obtain their coal from attached storage minecarts");
		useCoalFromStorageCart = config.get("useCoalFromStorageCart", false);

		config.setHeader("setOwnerOnPlacement", "\nWhether or not the player that places a minecart is set owner");
		setOwnerOnPlacement = config.get("setOwnerOnPlacement", true);

		config.setHeader("playSoundAtStation", "\nWhether or not a hissing sound is made when trains stop at a station");
		playSoundAtStation = config.get("playSoundAtStation", true);

		config.setHeader("keepChunksLoadedOnlyWhenMoving", "\nWhether or not chunks are only kept loaded when the train is moving");
		keepChunksLoadedOnlyWhenMoving = config.get("keepChunksLoadedOnlyWhenMoving", false);

		config.setHeader("maxDetectorLength", "\nThe maximum length a detector region (between two detectors) can be");
		maxDetectorLength = config.get("maxDetectorLength", 2000);

		config.setHeader("maxMinecartStackSize", "\nThe maximum amount of minecart items that can be stacked in one item");
		maxMinecartStackSize = config.get("maxMinecartStackSize", 64);
		
		config.setHeader("defaultTransferRadius", "\nThe default radius chest/furnace sign systems look for the needed blocks");
		defaultTransferRadius = MathUtil.limit(config.get("defaultTransferRadius", 2), 1, 5);

		config.setHeader("allowedBlockBreakTypes", "\nThe block materials that can be broken using minecarts");
		config.addHeader("allowedBlockBreakTypes", "Players with the admin block break permission can use any type");
		config.addHeader("allowedBlockBreakTypes", "Others have to use one from this list");
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
		
		config.setHeader("showTransferAnimations", "\nWhether or not to show item animations when transferring items");
		showTransferAnimations = config.get("showTransferAnimations", true);
		
		//parser shortcuts
		config.setHeader("itemShortcuts", "\nSeveral shortcuts you can use on signs to set the items");
		ConfigurationNode itemshort = config.getNode("itemShortcuts");
		parsers.put("fuel", Util.getParsers(itemshort.get("fuel", "wood;coal;stick")));
		for (Map.Entry<String, String> entry : itemshort.getValues(String.class).entrySet()) {
			if (entry.getKey().equalsIgnoreCase("fuel")) continue;
			parsers.put(entry.getKey().toLowerCase(), Util.getParsers(entry.getValue()));
			itemshort.setRead(entry.getKey());
		}

		exitOffset = new Vector(exitx, exity, exitz);
		
		config.trim();
		config.save();
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

	@SuppressWarnings("rawtypes")
	public void enable() {
		plugin = this;

		//registering
		this.register(TCListener.class);
		this.register("train", "cart");

		//Load configuration
		loadConfig();
		
		//update max item stack
		if (maxMinecartStackSize != 1) {
			Util.setItemMaxSize(Material.MINECART, maxMinecartStackSize);
			Util.setItemMaxSize(Material.POWERED_MINECART, maxMinecartStackSize);
			Util.setItemMaxSize(Material.STORAGE_MINECART, maxMinecartStackSize);
		}
		
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
		
		//Properly dispose of partly-referenced carts
		new Operation(false) {
			private Set worldentities;
			@Override
			public void run() {
				this.worldentities = new HashSet();
				this.doWorlds();
			}
			@Override
			@SuppressWarnings("unchecked")
			public void handle(WorldServer world) {
				this.worldentities.clear();
				this.worldentities.addAll(world.entityList);
				this.doChunks(world);
			}
			@Override
			public void handle(Chunk chunk) {
				this.doEntities(chunk);
			}
			@Override
			public void handle(Entity entity) {
				if (!this.worldentities.contains(entity)) {
					//remove from chunk and tracker
					WorldUtil.getTracker(entity.world).untrackEntity(entity);
					entity.world.removeEntity(entity);
				}
			}
		}.start(1);
		
		//Start member removal task
		cleanupTask = new Task(this) {
			public void run() {
				MinecartMember.cleanUpDeadCarts();
			}
		}.start(0, 10);
	}
	public void disable() {
		//Stop tasks
		Task.stop(signtask);
		Task.stop(cleanupTask);

		//update max item stack
		if (maxMinecartStackSize != 1) {
			Util.setItemMaxSize(Material.MINECART, 1);
			Util.setItemMaxSize(Material.POWERED_MINECART, 1);
			Util.setItemMaxSize(Material.STORAGE_MINECART, 1);
		}
		
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
		new Operation() {
			public void run() {
				this.doEntities();
			}
			public void handle(Entity entity) {
				GroupManager.hideGroup(entity);
			}
		};

		//Save for next load
		GroupManager.deinit(getDataFolder() + File.separator + "trains.groupdata");

		SignAction.deinit();
		
		ItemAnimation.deinit();

		plugin = null;
	}

	public boolean command(CommandSender sender, String cmd, String[] args) {
		return Commands.execute(sender, cmd, args);
	}

	@Override
	public void permissions() {
		this.loadPermissions(Permission.class);
	}

}
