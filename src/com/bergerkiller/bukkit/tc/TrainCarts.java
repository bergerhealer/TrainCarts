package com.bergerkiller.bukkit.tc;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.Listeners.CustomEvents;
import com.bergerkiller.bukkit.tc.Listeners.TCBlockListener;
import com.bergerkiller.bukkit.tc.Listeners.TCCustomListener;
import com.bergerkiller.bukkit.tc.Listeners.TCPlayerListener;
import com.bergerkiller.bukkit.tc.Listeners.TCVehicleListener;
import com.bergerkiller.bukkit.tc.Listeners.TCWorldListener;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;

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
	public static boolean pushAwayIgnoreGlobalOwners;
	public static boolean pushAwayIgnoreOwners;
	public static boolean useCoalFromStorageCart;
	public static boolean setOwnerOnPlacement;

	public static boolean SignLinkEnabled = false;
	public static boolean MinecartManiaEnabled = false;
	public static boolean MyWorldsEnabled = false;

	public static TrainCarts plugin;
	private final TCPlayerListener playerListener = new TCPlayerListener();
	private final TCWorldListener worldListener = new TCWorldListener();
	private final TCVehicleListener vehicleListener = new TCVehicleListener();	
	private final TCBlockListener blockListener = new TCBlockListener();	
	private final TCCustomListener customListener = new TCCustomListener();	

	private Task ctask;
	private Task signtask;
	private String version;

	public void loadConfig() {
		Configuration config = new Configuration(this);
		boolean use = config.getBoolean("use", true);
		if (use) {
			double exitx, exity, exitz;
			config.load();
			cartDistance = config.parse("normal.cartDistance", 1.5);
			cartDistanceForcer = config.parse("normal.cartDistanceForcer", 0.1);	
			turnedCartDistance = config.parse("turned.cartDistance", 1.6);
			turnedCartDistanceForcer = config.parse("turned.cartDistanceForcer", 0.2);	
			nearCartDistanceFactor = config.parse("nearCartDistanceFactor", 1.2);	
			removeDerailedCarts = config.parse("removeDerailedCarts", false);
			maxCartDistance = config.parse("maxCartDistance", (double) 4);
			breakCombinedCarts = config.parse("breakCombinedCarts", false);
			spawnItemDrops = config.parse("spawnItemDrops", true);
			poweredCartBoost = config.parse("poweredCartBoost", 0.1);
			exitx = config.parse("exitOffset.x", (double) 0);
			exity = config.parse("exitOffset.y", (double) 0);
			exitz = config.parse("exitOffset.z", (double) 0);
			pushAwayForce = config.parse("pushAwayForce", 0.2);
			pushAwayIgnoreGlobalOwners = config.parse("pushAwayIgnoreGlobalOwners", false);
			pushAwayIgnoreOwners = config.parse("pushAwayIgnoreOwners", true);
			useCoalFromStorageCart = config.parse("useCoalFromStorageCart", false);
			setOwnerOnPlacement = config.parse("setOwnerOnPlacement", true);
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

		//clean groups from dead and derailed carts and form new groups
		ctask = new Task(this) {
			public void run() {
				MinecartGroup.updateGroups();
			}
		};
		ctask.startRepeating(10L);

		//commands
		getCommand("train").setExecutor(this);

		//final msg
		version = this.getDescription().getVersion();
		Util.log(Level.INFO, "version " + version + " is enabled!");

	}
	public void onDisable() {
		//Stop tasks
		if (ctask != null) ctask.stop();
		if (signtask != null) signtask.stop();

		//undo replacements for correct saving
		for (MinecartGroup mg : MinecartGroup.getGroups()) {
			GroupManager.hideGroup(mg);
			mg.reinitProperties();
			mg.stop();
		}
		//entities left behind?
		for (World w : Bukkit.getServer().getWorlds()) {
			for (Entity e : w.getEntities()) {
				net.minecraft.server.Entity ee = EntityUtil.getNative(e);
				if (ee instanceof MinecartMember) {
					MinecartMember mm = (MinecartMember) ee;
					if (!mm.dead) {
						MinecartGroup g = mm.getGroup();
						GroupManager.hideGroup(g);
						g.reinitProperties();
						g.stop();
					}
				}
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
		if (args.length == 0) {
			sender.sendMessage("TrainCarts " + version + " - See WIKI page for more information");
			return true;
		}
		cmd = args[0].toLowerCase();
		args = Util.remove(args, 0);
		if (cmd.equals("removeall") || cmd.equals("destroyall")) {
			if (!(sender instanceof Player) || ((Player) sender).hasPermission("train.command.remove")) {
				boolean destroy = cmd.equals("destroyall");
				if (args.length == 1) {
					String cname = args[0].toLowerCase();
					World w = null;
					for (World world : Bukkit.getServer().getWorlds()) {
						String wname = world.getName().toLowerCase();
						if (wname.equals(cname)) {
							w = world;
							break;
						}
					}
					if (w == null) {
						for (World world : Bukkit.getServer().getWorlds()) {
							String wname = world.getName().toLowerCase();
							if (wname.contains(cname)) {
								w = world;
								break;
							}
						}
					}
					if (w != null) {
						int count = GroupManager.removeAll(w, destroy);
						sender.sendMessage(ChatColor.RED + "All train information of '" + w.getName() + "' has been cleared!");
						if (destroy) {
							sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");	
						}
					} else {
						sender.sendMessage(ChatColor.RED + "World not found!");
					}
				} else {
					int count = GroupManager.removeAll(destroy);
					sender.sendMessage(ChatColor.RED + "All train information of this server has been cleared!");
					if (destroy) {
						sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");				
					}
				}
			} else {
				sender.sendMessage(ChatColor.RED + "You don't have permission to use this!");
			}
			return true;
		}
		if (sender instanceof Player) {
			Player p = (Player) sender;
			//get the train this player is editing
			TrainProperties tprop = TrainProperties.getEditing(p);
			//permissions
			if (tprop == null) {
				if (TrainProperties.canBeOwner(p)) {
					p.sendMessage(ChatColor.YELLOW + "You haven't selected a train to edit yet!");
				} else {
					p.sendMessage(ChatColor.RED + "You don't own a train you can change!");
				}
			} else if (!tprop.isOwner(p)) {
				p.sendMessage(ChatColor.RED + "You don't own this train!");
			} else {
				Properties prop = tprop.getSaved();
				//let's do stuff with it
				if (cmd.equals("edit")) {
                    if (args.length == 1) {
                    	prop = TrainProperties.get(args[0]);
                    	if (prop != null) {
                    		if (tprop.isOwner(p)) {
                    			tprop.setEditing(p);
                    			p.sendMessage(ChatColor.GREEN + "You are now editing train '" + tprop.getTrainName() + "'!");
                    		} else {
                    			p.sendMessage(ChatColor.YELLOW + "You do not own this train!");
                    		}
                    	} else {
                    		p.sendMessage(ChatColor.RED + "No train with name '" + args[0] + "' was found!");
                    	}
                    } else {
                    	p.sendMessage(ChatColor.YELLOW + "Please enter the train name your wish to edit!");
                    }
				} else if (cmd.equals("info") || cmd.equals("i")) {
					//warning message
					if (!tprop.isDirectOwner(p)) {
						if (prop.owners.size() == 0) {
							p.sendMessage(ChatColor.YELLOW + "Note: This train is not owned, claim it using /train claim!");
						} else {
							p.sendMessage(ChatColor.RED + "Warning: You do not own this train!");
						}
					}
					p.sendMessage(ChatColor.YELLOW + "Train name: " + ChatColor.WHITE + tprop.getTrainName());
					p.sendMessage(ChatColor.YELLOW + "Can be linked: " + ChatColor.WHITE + " " + prop.allowLinking);
					p.sendMessage(ChatColor.YELLOW + "Keep nearby chunks loaded: " + ChatColor.WHITE + " " + prop.keepChunksLoaded);
					if (prop.allowMobsEnter) {
						if (prop.allowPlayerEnter) {
							p.sendMessage(ChatColor.YELLOW + "Can be entered by: " + ChatColor.WHITE + " Mobs and Players");
						} else {
							p.sendMessage(ChatColor.YELLOW + "Can be entered by: " + ChatColor.WHITE + " Mobs");
						}
					} else if (prop.allowPlayerEnter) {
						p.sendMessage(ChatColor.YELLOW + "Can be entered by: " + ChatColor.WHITE + " Players");
					} else {
						p.sendMessage(ChatColor.YELLOW + "Can be entered by: " + ChatColor.RED + " No one");
					}
					p.sendMessage(ChatColor.YELLOW + "Can collide with other trains: " + ChatColor.WHITE + " " + prop.trainCollision);
					//push away
					ArrayList<String> pushlist = new ArrayList<String>();
					if (prop.pushMobs) pushlist.add("Mobs");
					if (prop.pushPlayers) pushlist.add("Players");
					if (prop.pushMisc) pushlist.add("Misc");
					if (pushlist.size() == 0) {
						p.sendMessage(ChatColor.YELLOW + "This train will never push anything.");
					} else {
						p.sendMessage(ChatColor.YELLOW + "Is pushing away " + ChatColor.WHITE + Util.combineNames(pushlist));
					}
					p.sendMessage(ChatColor.YELLOW + "Is at station: " + ChatColor.WHITE + tprop.isAtStation());
					p.sendMessage(ChatColor.YELLOW + "Enter message: " + ChatColor.WHITE + (prop.enterMessage == null ? "None" : prop.enterMessage));
					p.sendMessage(ChatColor.YELLOW + "Maximum speed: " + ChatColor.WHITE + prop.speedLimit + " blocks/tick");
					if (prop.tags.size() == 0) {
						p.sendMessage(ChatColor.YELLOW + "Tags: " + ChatColor.WHITE + "None");
					} else {
						p.sendMessage(ChatColor.YELLOW + "Tags: " + ChatColor.WHITE + " " + Util.combineNames(prop.tags));
					}
					if (prop.destination != null && !prop.destination.isEmpty()){					
						p.sendMessage(ChatColor.YELLOW + "This train will ignore tag switchers and will attempt to reach " + ChatColor.WHITE + prop.destination);
					}
					if (prop.owners.size() == 0) {
						p.sendMessage(ChatColor.YELLOW + "Owned by: " + ChatColor.WHITE + "Everyone");
					} else {
						p.sendMessage(ChatColor.YELLOW + "Owned by: " + ChatColor.WHITE + " " + Util.combineNames(prop.owners));
					}
					if (prop.passengers.size() == 0) {
						p.sendMessage(ChatColor.YELLOW + "Enterable by: " + ChatColor.WHITE + "Everyone");
					} else {
						p.sendMessage(ChatColor.YELLOW + "Enterable by: " + ChatColor.WHITE + " " + Util.combineNames(prop.passengers));
					}
				} else if (cmd.equals("linking") || cmd.equals("link")) {
					if (args.length == 1) {
						prop.allowLinking = Util.getBool(args[0]);
					}
					p.sendMessage(ChatColor.YELLOW + "Can be linked: " + ChatColor.WHITE + " " + prop.allowLinking);
				} else if (cmd.equals("keepchunksloaded")) {
					if (args.length == 1) {
						if (p.hasPermission("train.command.keepchunksloaded")) {
							prop.keepChunksLoaded = Util.getBool(args[0]);
						} else {
							p.sendMessage(ChatColor.RED + "You are not allowed to set this property for trains!");
						}
					}
					p.sendMessage(ChatColor.YELLOW + "Keep nearby chunks loaded: " + ChatColor.WHITE + " " + prop.keepChunksLoaded);
				} else if (cmd.equals("mobenter") || cmd.equals("mobsenter")) {
					if (args.length == 1) {
						prop.allowMobsEnter = Util.getBool(args[0]);
					}
					p.sendMessage(ChatColor.YELLOW + "Can be entered by mobs: " + ChatColor.WHITE + " " + prop.allowMobsEnter);
				} else if (cmd.equals("claim")) {
					prop.owners.clear();
					prop.owners.add(p.getName());
					p.sendMessage(ChatColor.YELLOW + "You claimed this train your own!");
					//==============================Push settings======================
				} else if (cmd.equals("pushmobs") || cmd.equals("pushplayers") || cmd.equals("pushmisc")) {
					String secarg = null;
					if (args.length == 1) {
						secarg = args[0];
					}
					String msg = ChatColor.YELLOW + "Pushes away ";
					if (cmd.equals("pushmobs")) {
						if (secarg != null)  prop.pushMobs = Util.getBool(secarg);
						msg += "mobs: " + ChatColor.WHITE + " " + prop.pushMobs;
					}
					if (cmd.equals("pushplayers")) {
						if (secarg != null) prop.pushPlayers = Util.getBool(secarg);
						msg += "players: " + ChatColor.WHITE + " " + prop.pushPlayers;
					}
					if (cmd.equals("pushmisc")) {
						if (secarg != null) prop.pushMisc = Util.getBool(secarg);
						msg += "misc. entities: " + ChatColor.WHITE + " " + prop.pushMisc;
					}
					p.sendMessage(msg);
				} else if (cmd.equals("pushplayers")) {
					if (args.length == 1) {
						prop.pushPlayers = Util.getBool(args[0]);
					}
					p.sendMessage(ChatColor.YELLOW + "Pushes away players: " + ChatColor.WHITE + " " + prop.pushPlayers);
				} else if (cmd.equals("pushmisc")) {
					if (args.length == 1) {
						prop.pushMisc = Util.getBool(args[0]);
					}
					p.sendMessage(ChatColor.YELLOW + "Pushes away misc. entities: " + ChatColor.WHITE + " " + prop.pushMisc);
					//==================================================================
				} else if (cmd.equals("playerenter")) {
					if (args.length == 1) {
						prop.allowPlayerEnter = Util.getBool(args[0]);
					}
					p.sendMessage(ChatColor.YELLOW + "Players can enter this train: " + ChatColor.WHITE + " " + prop.allowPlayerEnter);
				} else if (cmd.equals("playerleave") || cmd.equals("playerexit")) {
					if (args.length == 1) {
						if (p.hasPermission("train.command.playerexit")) {
							prop.allowPlayerExit = Util.getBool(args[0]);
						} else {
							p.sendMessage(ChatColor.RED + "You don't have permission, ask an admin to do this.");
						}
					}
					p.sendMessage(ChatColor.YELLOW + "Players can exit this train: " + ChatColor.WHITE + " " + prop.allowPlayerExit);

				} else if (cmd.equals("addowner") || cmd.equals("addowners")) {
					if (args.length == 0) {
						prop.owners.add(p.getName());
						p.sendMessage(ChatColor.YELLOW + "You added yourself as owner of this train!");
					} else {
						for (String owner : args) {
							prop.owners.add(owner);
						}
						p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as owners of this train!");
					}
				} else if (cmd.equals("setowner") || cmd.equals("setowners")) {
					if (args.length == 0) {
						prop.owners.clear();
						p.sendMessage(ChatColor.YELLOW + "All owners for this train are cleared!");
					} else {
						prop.owners.clear();
						for (String owner : args) {
							prop.owners.add(owner);
						}
						p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as owners of this train!");
					}
				} else if (cmd.equals("addtags") || cmd.equals("addtag")) {
					if (args.length == 0) {
						p.sendMessage(ChatColor.RED + "You need to give at least one tag to add!");
					} else {
						for (String tag : args) {
							prop.tags.add(tag);
						}
						p.sendMessage(ChatColor.YELLOW + "You added " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as tags for this train!");
					}
				} else if (cmd.equals("slowdown") || cmd.equals("slow") || cmd.equals("setslow") || cmd.equals("setslowdown")) {
					if (args.length == 1) {
						prop.slowDown = Util.getBool(args[0]);
					}
					p.sendMessage(ChatColor.YELLOW + "Slow down: " + ChatColor.WHITE + prop.slowDown);
				} else if (cmd.equals("settags") || cmd.equals("settag") || cmd.equals("tags") || cmd.equals("tag")) {
					if (args.length == 0) {
						prop.tags.clear();
						p.sendMessage(ChatColor.YELLOW + "All tags for this train are cleared!");
					} else {
						prop.tags.clear();
						for (String tag : args) {
							prop.tags.add(tag);
						}
						p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + Util.combineNames(args) + ChatColor.YELLOW + " as tags for this train!");
					}
				} else if (cmd.equals("dest") || cmd.equals("destination")) {
					if (args.length == 0) {
						prop.destination = "";
						p.sendMessage(ChatColor.YELLOW + "Destination for this train has been cleared!");
					} else {
						prop.destination = args[0];
						p.sendMessage(ChatColor.YELLOW + "You set " + ChatColor.WHITE + args[0] + ChatColor.YELLOW + " as destination for this train!");
					}
				} else if (cmd.equals("setcollide") || cmd.equals("setcollision") || cmd.equals("collision") || cmd.equals("collide")) {
					if (args.length == 1) {
						prop.trainCollision = Util.getBool(args[0]);
					}
					p.sendMessage(ChatColor.YELLOW + "Can collide with other trains: " + ChatColor.WHITE + prop.trainCollision);
				} else if (cmd.equals("speedlimit") || cmd.equals("maxspeed")) {
					if (args.length == 1) {
						try {
							prop.speedLimit = Double.parseDouble(args[0]);
						} catch (NumberFormatException ex) {
							prop.speedLimit = 0.4;
						}
					}
					p.sendMessage(ChatColor.YELLOW + "Maximum speed: " + ChatColor.WHITE + prop.speedLimit + " blocks/tick");
				} else if (cmd.equals("requirepoweredminecart") || cmd.equals("requirepowered")) {
					if (args.length == 1) {
						prop.requirePoweredMinecart = Util.getBool(args[0]);
					}
					p.sendMessage(ChatColor.YELLOW + "Requires powered minecart to stay alive: " + ChatColor.WHITE + prop.requirePoweredMinecart);
				} else if (cmd.equals("rename") || cmd.equals("setname") || cmd.equals("name")) {
					if (args.length == 0) {
						p.sendMessage(ChatColor.RED + "You forgot to pass a name along!");
					} else {
						String newname = Util.combine(" ", args);
						if (TrainProperties.exists(newname)) {
							p.sendMessage(ChatColor.RED + "This name is already taken!");
						} else {
							GroupManager.rename(tprop.getTrainName(), newname);
							p.sendMessage(ChatColor.YELLOW + "This train is now called " + ChatColor.WHITE + newname + ChatColor.YELLOW + "!");
						}
					}
				} else if (cmd.equals("reroute")){
					if (!(sender instanceof Player) || ((Player) sender).hasPermission("train.build.destination")) {
						Destinations.clear();
						sender.sendMessage("All train routings will be recalculated.");
					}
				} else if (cmd.equals("remove") || cmd.equals("destroy")) {
					MinecartGroup g = MinecartGroup.get(tprop.getTrainName());
					if (g != null) {
						g.destroy();
					} else {
						tprop.remove();
					}
					p.sendMessage(ChatColor.YELLOW + "The selected train has been destroyed!");
				} else {
					p.sendMessage(ChatColor.RED + "Unknown command: '" + cmd + "'!");
				}
				tprop.restore();
			}
		}
		return true;
	}
}
