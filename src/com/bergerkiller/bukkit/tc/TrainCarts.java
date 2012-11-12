package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import net.minecraft.server.Entity;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.items.ItemParser;
import com.bergerkiller.bukkit.common.Operation;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.StringReplaceBundle;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.itemanimation.ItemAnimation;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionDetector;
import com.bergerkiller.bukkit.tc.signactions.SignActionSpawn;
import com.bergerkiller.bukkit.tc.statements.Statement;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;

public class TrainCarts extends PluginBase {

	/*
	 * Settings
	 */	
	public static double maxVelocity;
	public static double maxEjectDistance;
	public static double cartDistance;
	public static double turnedCartDistance;
	public static double cartDistanceForcer;
	public static double turnedCartDistanceForcer;
	public static double nearCartDistanceFactor;
	public static double maxCartDistance;
	public static boolean breakCombinedCarts;
	public static boolean spawnItemDrops;
	public static double poweredCartBoost;
	public static double poweredRailBoost;
	public static Vector exitOffset;
	public static double pushAwayForce;
	public static double launchForce;
	public static boolean collisionIgnoreGlobalOwners;
	public static boolean collisionIgnoreOwners;
	public static boolean useCoalFromStorageCart;
	public static boolean setOwnerOnPlacement;
	public static boolean keepChunksLoadedOnlyWhenMoving;
	public static boolean playSoundAtStation;
	private Set<Material> allowedBlockBreakTypes = new HashSet<Material>();
	private Set<String> disabledWorlds = new HashSet<String>();
	public static int maxDetectorLength;
	public static int maxMinecartStackSize;
	public static int defaultTransferRadius;
	public static boolean showTransferAnimations;
	public static boolean slowDownEmptyCarts;
	public static double slowDownMultiplierSlow;
	public static double slowDownMultiplierNormal;
	public static boolean refillAtStations;
	public static boolean instantCreativeDestroy;
	private static String currencyFormat;
	public static double manualMovementSpeed;
	public static final StringReplaceBundle messageShortcuts = new StringReplaceBundle();
	public static final StringReplaceBundle statementShortcuts = new StringReplaceBundle();

	public static boolean EssentialsEnabled = false;
	public static boolean SignLinkEnabled = false;
	public static boolean MinecartManiaEnabled = false;
	public static boolean MyWorldsEnabled = false;

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
		config.addHeader("http://www.minecraftwiki.net/wiki/Bukkit/TrainCarts");
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
		maxCartDistance = config.get("maxCartDistance", 4.0);

		config.setHeader("breakCombinedCarts", "\nWhether or not the combined carts (powered/storage minecarts) break up into two items");
		breakCombinedCarts = config.get("breakCombinedCarts", false);

		config.setHeader("spawnItemDrops", "\nWhether or not items drop when the minecarts break");
		spawnItemDrops = config.get("spawnItemDrops", true);

		config.setHeader("poweredCartBoost", "\nA performance boost to give to powered minecarts (0 = normal speed)");
		poweredCartBoost = config.get("poweredCartBoost", 0.1);

		config.setHeader("poweredRailBoost", "\nThe boosting factor of powered rails (default = 0.06)");
		poweredRailBoost = config.get("poweredRailBoost", 0.06);

		config.setHeader("maxVelocity", "\nThe maximum velocity (blocks/tick) a minecart can possibly have set");
		maxVelocity = config.get("maxVelocity", 5.0);
		
		config.setHeader("exitOffset", "\nThe XYZ offset used when a passenger exits a minecart");
		exitx = config.get("exitOffset.x", 0.0);
		exity = config.get("exitOffset.y", 0.0);
		exitz = config.get("exitOffset.z", 0.0);
		
		config.setHeader("slowDownMultiplier", "\nThe multiplier used to slow down minecarts");
		config.addHeader("slowDownMultiplier", "Normal is the default, slow is when the minecart is meant to slow down.");
		slowDownMultiplierNormal = config.get("slowDownMultiplier.normal", 0.997);
		slowDownMultiplierSlow = config.get("slowDownMultiplier.slow", 0.96);
		
		config.setHeader("maxEjectDistance", "\nThe maximum allowed ejection distance for eject signs");
		maxEjectDistance = config.get("maxEjectDistance", 10.0);

		config.setHeader("launchForce", "\nThe amount of velocity stations give when launching trains");
		launchForce = config.get("launchForce", 10.0);

		// Deprecation backwards compatibility
		if (config.contains("pushAway")) {
			config.set("collision.ignoreOwners", config.get("pushAway.ignoreOwners", true));
			config.set("collision.ignoreGlobalOwners", config.get("pushAway.ignoreGlobalOwners", false));
			config.set("collision.pushAwayForce", config.get("pushAway.force", 0.2));
			config.remove("pushAway");
		}

		config.setHeader("pushAway", "\nSettings used when carts push away/aside others (if enabled)");
		config.setHeader("pushAway.ignoreOwners", "If train owners are ignored");
		config.setHeader("pushAway.ignoreGlobalOwners", "If global train owners are ignored");

		config.setHeader("collision", "\nSettings used when carts collide with entities");
		config.setHeader("collision.ignoreOwners", "If train owners are ignored");
		config.setHeader("collision.ignoreGlobalOwners", "If global train owners are ignored");
		config.setHeader("collision.pushAwayForce", "The amount of force at which minecarts push away others");
		collisionIgnoreOwners = config.get("collision.ignoreOwners", true);
		collisionIgnoreGlobalOwners = config.get("collision.ignoreGlobalOwners", false);
		pushAwayForce = config.get("collision.pushAwayForce", 0.2);

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
		defaultTransferRadius = MathUtil.clamp(config.get("defaultTransferRadius", 2), 1, 5);

		config.setHeader("slowDownEmptyCarts", "\nWhether or not empty minecarts slow down faster than occupied minecarts");
		slowDownEmptyCarts = config.get("slowDownEmptyCarts", false);

		config.setHeader("refillAtStations", "\nWhether storage minecarts get fuel when launching from stations");
		refillAtStations = config.get("refillAtStations", true);

		config.setHeader("instantCreativeDestroy", "\nWhether minecarts are instantly destroyed by creative players");
		config.addHeader("instantCreativeDestroy", "Note that manual minecart movement is not possible for creative players with this enabled");
		instantCreativeDestroy = config.get("instantCreativeDestroy", false);

		config.setHeader("manualMovementSpeed", "\nWhat velocity to set when a player tries to manually move a train (by damaging it)");
		manualMovementSpeed = config.get("manualMovementSpeed", 12.0);

		config.setHeader("currencyFormat", "\nThe currency Ticket signs will display in messages, %value% represents the displayed value");
		currencyFormat = config.get("currencyFormat", "%value% Dollars");

		config.setHeader("allowedBlockBreakTypes", "\nThe block materials that can be broken using minecarts");
		config.addHeader("allowedBlockBreakTypes", "Players with the admin block break permission can use any type");
		config.addHeader("allowedBlockBreakTypes", "Others have to use one from this list");
		allowedBlockBreakTypes.clear();
		if (config.contains("allowedBlockBreakTypes")) {
			for (String value : config.getList("allowedBlockBreakTypes", String.class)) {
				Material type = ParseUtil.parseMaterial(value, null);
				if (type != null) allowedBlockBreakTypes.add(type);
			}
		} else {
			allowedBlockBreakTypes.add(Material.CROPS);
			allowedBlockBreakTypes.add(Material.LOG);
		}
		
		config.setHeader("disabledWorlds", "\nA list of world names where TrainCarts should be disabled");
		config.addHeader("disabledWorlds", "World names are not case-sensitive");
		this.disabledWorlds.clear();
		if (!config.contains("disabledWorlds")) {
			ArrayList<String> defworlds = new ArrayList<String>();
			defworlds.add("DefaultWorld1");
			defworlds.add("DefaultWorld2");
			config.set("disabledWorlds", defworlds);
		}
		for (String world : config.getList("disabledWorlds", String.class)) {
			this.disabledWorlds.add(world.toLowerCase());
		}
		
		//set it again
		List<String> types = config.getList("allowedBlockBreakTypes", String.class);
		types.clear();
		for (Material mat : allowedBlockBreakTypes) {
			types.add(mat.toString());
		}
		
		config.setHeader("showTransferAnimations", "\nWhether or not to show item animations when transferring items");
		showTransferAnimations = config.get("showTransferAnimations", true);
		
		//message shortcuts
		config.setHeader("messageShortcuts", "\nSeveral shortcuts you can use on announce signs (text is replaced)");
		if (!config.contains("messageShortcuts")) {
			config.set("messageShortcuts.welcome", "&eWelcome to &f");
		}
		messageShortcuts.clear().load(config.getNode("messageShortcuts"));
		
		//statement shortcuts
		config.setHeader("statementShortcuts", "\nSeveral shortcuts you can use on switcher and detector signs (text is replaced)");
		if (!config.contains("statementShortcuts")) {
			config.set("statementShortcuts.diamond", "i@diamond");
		}
		statementShortcuts.clear().load(config.getNode("statementShortcuts"));
		
		//parser shortcuts
		config.setHeader("itemShortcuts", "\nSeveral shortcuts you can use on signs to set the items");
		ConfigurationNode itemshort = config.getNode("itemShortcuts");
		parsers.clear();
		String burnables = itemshort.get("fuel", "");
		if (burnables.isEmpty()) {
			burnables = Util.getFurnaceItemString(true, false);
			itemshort.set("fuel", burnables);
		}
		parsers.put("fuel", Util.getParsers(burnables));
		String heatables = itemshort.get("heatable", "");
		if (heatables.isEmpty()) {
			heatables = Util.getFurnaceItemString(false, true);
			itemshort.set("heatable", heatables);
		}
		parsers.put("heatable", Util.getParsers(heatables));
		for (Map.Entry<String, String> entry : itemshort.getValues(String.class).entrySet()) {
			if (entry.getKey().equalsIgnoreCase("fuel")) continue;
			if (entry.getKey().equalsIgnoreCase("heatable")) continue;
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
		} else if (pluginName.equals("Essentials")) {
			EssentialsEnabled = enabled;
		}
	}

	@Override
	public int getMinimumLibVersion() {
		return 7;
	}

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

		//init statements
		Statement.init();

		//Init signs
		SignAction.init();

		//Load properties
		TrainProperties.load();

		//Convert Minecarts
		MinecartMemberStore.convertAll();

		//Load groups
		OfflineGroupManager.init(getDataFolder() + File.separator + "trains.groupdata");

		//Load destinations
		PathNode.init(getDataFolder() + File.separator + "destinations.dat");

		//Load arrival times
		ArrivalSigns.init(getDataFolder() + File.separator + "arrivaltimes.txt");

		//Load detector regions
		DetectorRegion.init(getDataFolder() + File.separator + "detectorregions.dat");	

		//Load detector sign locations
		SignActionDetector.init(getDataFolder() + File.separator + "detectorsigns.dat");

		//Load spawn sign locations
		SignActionSpawn.init(getDataFolder() + File.separator + "spawnsigns.dat");

		//Restore carts where possible
		OfflineGroupManager.refresh();

		//Properly dispose of partly-referenced carts
		CommonUtil.nextTick(new Runnable() {
			public void run() {
				OfflineGroupManager.removeBuggedMinecarts();
			}
		});
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

		//undo replacements for correct native saving
		for (MinecartGroup mg : MinecartGroup.getGroups()) {
			OfflineGroupManager.hideGroup(mg);
		}

		//Save properties
		TrainProperties.save();

		//Save destinations
		PathNode.deinit(getDataFolder() + File.separator + "destinations.dat");

		//Save arrival times
		ArrivalSigns.deinit(getDataFolder() + File.separator + "arrivaltimes.txt");

		//Save spawn sign locations
		SignActionSpawn.deinit(getDataFolder() + File.separator + "spawnsigns.dat");

		//Save detector sign locations
		SignActionDetector.deinit(getDataFolder() + File.separator + "detectorsigns.dat");

		//Save detector regions
		DetectorRegion.deinit(getDataFolder() + File.separator + "detectorregions.dat");	
		
		//clear statements
		Statement.deinit();

		//entities left behind?
		new Operation() {
			public void run() {
				this.doEntities();
			}
			public void handle(Entity entity) {
				OfflineGroupManager.hideGroup(entity);
			}
		};

		//Save for next load
		OfflineGroupManager.deinit(getDataFolder() + File.separator + "trains.groupdata");

		SignAction.deinit();

		ItemAnimation.deinit();
	}

	public boolean command(CommandSender sender, String cmd, String[] args) {
		return Commands.execute(sender, cmd, args);
	}

	@Override
	public void permissions() {
		this.loadPermissions(Permission.class);
	}

	/**
	 * Gets the Currency text to display a currency value
	 * 
	 * @param value to display
	 * @return currency text
	 */
	public static String getCurrencyText(double value) {
		return currencyFormat.replace("%value%", Double.toString(value));
	}

	public static boolean isWorldDisabled(BlockEvent event) {
		return isWorldDisabled(event.getBlock().getWorld());
	}
	public static boolean isWorldDisabled(Block worldContainer) {
		return isWorldDisabled(worldContainer.getWorld());
	}
	public static boolean isWorldDisabled(World world) {
		return isWorldDisabled(world.getName());
	}
	public static boolean isWorldDisabled(String worldname) {
		return plugin.disabledWorlds.contains(worldname.toLowerCase());
	}
	
}
