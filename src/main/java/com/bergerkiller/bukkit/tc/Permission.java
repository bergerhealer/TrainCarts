package com.bergerkiller.bukkit.tc;

import org.bukkit.permissions.PermissionDefault;

import com.bergerkiller.bukkit.common.permissions.PermissionEnum;

public class Permission extends PermissionEnum {
	public static final Permission COMMAND_GLOBALPROPERTIES = new Permission("train.command.globalproperties", PermissionDefault.OP, "The player can edit the properties of all carts");
	public static final Permission COMMAND_PROPERTIES = new Permission("train.command.properties", PermissionDefault.TRUE, "The player can edit the properties of carts he owns");
	public static final Permission COMMAND_DESTROY = new Permission("train.command.destroy", PermissionDefault.OP, "The player can destroy owned carts through commands");
	public static final Permission COMMAND_DESTROYALL = new Permission("train.command.destroyall", PermissionDefault.OP, "The player can destroy all trains on the server");
	public static final Permission COMMAND_FIXBUGGED = new Permission("train.command.fixbugged", PermissionDefault.OP, "The player can destroy all bugged minecarts on the server");
	public static final Permission COMMAND_REROUTE = new Permission("train.command.reroute", PermissionDefault.OP, "The player can force path finding to re-calculate");
	public static final Permission COMMAND_RELOAD = new Permission("train.command.reload", PermissionDefault.OP, "The player can reload the configuration");
	public static final Permission COMMAND_BREAKBLOCK = new Permission("train.command.break", PermissionDefault.TRUE, "The player can set blocks the cart can break from a set list");
	public static final Permission COMMAND_BREAKBLOCK_ADMIN = new Permission("train.command.breakblock.admin", PermissionDefault.OP, "The player can set blocks the cart can break, any type");
	public static final Permission COMMAND_SETPUBLIC = new Permission("train.command.setpublic", PermissionDefault.TRUE, "The player can make his owned carts open for the public");
	public static final Permission COMMAND_SETOWNERS = new Permission("train.command.setowners", PermissionDefault.TRUE, "The player can set the owners of his owned carts");
	public static final Permission COMMAND_SETTAGS = new Permission("train.command.settags", PermissionDefault.TRUE, "The player can set the tags of his owned carts");
	public static final Permission COMMAND_MOBENTER = new Permission("train.command.mobenter", PermissionDefault.TRUE, "The player can set if mobs can enter his owned carts");
	public static final Permission COMMAND_SETDESTINATION = new Permission("train.command.setdestination", PermissionDefault.TRUE, "The player can set destinations for his owned carts");
	public static final Permission COMMAND_PLAYERENTER = new Permission("train.command.playerenter", PermissionDefault.TRUE, "The player can set if players can enter his owned carts");
	public static final Permission COMMAND_PLAYEREXIT = new Permission("train.command.playerexit", PermissionDefault.TRUE, "The player can set if players can exit his owned carts");
	public static final Permission COMMAND_PICKUP = new Permission("train.command.pickup", PermissionDefault.TRUE, "The player can set if his owned storage carts pick up items");
	public static final Permission COMMAND_SETLINKING = new Permission("train.command.setlinking", PermissionDefault.TRUE, "The player can set if his owned trains can link to other trains");
	public static final Permission COMMAND_KEEPCHUNKSLOADED = new Permission("train.command.keepchunksloaded", PermissionDefault.OP, "The player can if his owned trains keep nearby chunks loaded");
	public static final Permission COMMAND_INVINCIBLE = new Permission("train.command.invincible", PermissionDefault.OP, "The player can if his owned trains make it invincible");
	public static final Permission COMMAND_PUSHING = new Permission("train.command.pushing", PermissionDefault.TRUE, "The player can if his owned trains push away certain entities");
	public static final Permission COMMAND_SLOWDOWN = new Permission("train.command.slowdown", PermissionDefault.TRUE, "The player can if his owned trains slow down over time");
	public static final Permission COMMAND_SETCOLLIDE = new Permission("train.command.setcollide", PermissionDefault.TRUE, "The player can if his owned trains can collide");
	public static final Permission COMMAND_SETSPEEDLIMIT = new Permission("train.command.setspeedlimit", PermissionDefault.TRUE, "The player can set the maximum speed for his trains");
	public static final Permission COMMAND_SETPOWERCARTREQ = new Permission("train.command.setpoweredcartrequirement", PermissionDefault.TRUE, "The player can set if a powered minecart is needed for his train to stay alive");
	public static final Permission COMMAND_DEFAULT = new Permission("train.command.default", PermissionDefault.OP, "The player can apply default settings to his owned trains");
	public static final Permission COMMAND_RENAME = new Permission("train.command.rename", PermissionDefault.TRUE, "The player can rename his owned trains");
	public static final Permission COMMAND_DISPLAYNAME = new Permission("train.command.displayname", PermissionDefault.TRUE, "The player can change the display name of his owned trains");
	public static final Permission COMMAND_MANUALMOVE = new Permission("train.command.manualmove", PermissionDefault.TRUE, "Whether the player can change if trains can be moved by damaging them");
	public static final Permission COMMAND_SAVEALL = new Permission("train.command.saveall", PermissionDefault.OP, "Whether the player can save all TrainCarts stored information to file");
	public static final Permission COMMAND_MESSAGE = new Permission("train.command.message", PermissionDefault.OP, "Whether the player can add message shortcuts");
	public static final Permission COMMAND_PATHINFO = new Permission("train.command.pathinfo", PermissionDefault.OP, "Whether the player can view the route the train is following to reach the destination");
	public static final Permission COMMAND_PLAYERTAKE = new Permission("train.command.playertake", PermissionDefault.OP, "Whether the player can change if players take Minecarts with them when they leave");
	public static final Permission COMMAND_SOUND = new Permission("train.command.soundenabled", PermissionDefault.OP, "Whether the player can turn Minecart sound on or off");
	public static final Permission COMMAND_TELEPORT = new Permission("train.command.teleport", PermissionDefault.OP, "Whether the player can teleport to where trains are");
	public static final Permission COMMAND_CHANGEBLOCK = new Permission("train.command.changeblock", PermissionDefault.OP, "Whether the player can alter the type of block displayed in a minecart");
	public static final Permission BUILD_STATION = new Permission("train.build.station", PermissionDefault.OP, "The player can build train stations");
	public static final Permission BUILD_SPAWNER = new Permission("train.build.spawner", PermissionDefault.OP, "The player can build train spawners");
	public static final Permission SPAWNER_AUTOMATIC = new Permission("train.spawner.automatic", PermissionDefault.TRUE, "The player can build spawners which automatically create carts");
	public static final Permission SPAWNER_REGULAR = new Permission("train.spawner.regular", PermissionDefault.TRUE, "The player can build spawners which create regular minecarts");
	public static final Permission SPAWNER_POWERED = new Permission("train.spawner.powered", PermissionDefault.TRUE, "The player can build spawners which create powered minecarts");
	public static final Permission SPAWNER_STORAGE = new Permission("train.spawner.storage", PermissionDefault.TRUE, "The player can build spawners which create minecarts with chests");
	public static final Permission SPAWNER_TNT = new Permission("train.spawner.tnt", PermissionDefault.TRUE, "The player can build spawners which create minecarts with TNT");
	public static final Permission SPAWNER_HOPPER = new Permission("train.spawner.hopper", PermissionDefault.TRUE, "The player can build spawners which create minecarts with hoppers");
	public static final Permission SPAWNER_SPAWNER = new Permission("train.spawner.spawner", PermissionDefault.TRUE, "The player can build spawners which create minecarts with spawners");
	public static final Permission BUILD_TRIGGER = new Permission("train.build.trigger", PermissionDefault.OP, "The player can build train triggers");
	public static final Permission BUILD_DESTINATION = new Permission("train.build.destination", PermissionDefault.OP, "The player can build destinations");
	public static final Permission BUILD_SWITCHER = new Permission("train.build.switcher", PermissionDefault.OP, "The player can build track switchers");
	public static final Permission BUILD_DESTRUCTOR = new Permission("train.build.destructor", PermissionDefault.OP, "The player can build train destructors");
	public static final Permission BUILD_DETECTOR = new Permission("train.build.detector", PermissionDefault.OP, "The player can build train detectors");
	public static final Permission BUILD_EJECTOR = new Permission("train.build.ejector", PermissionDefault.OP, "The player can build train ejectors");
	public static final Permission BUILD_PROPERTY = new Permission("train.build.property", PermissionDefault.OP, "The player can build train property setters");
	public static final Permission BUILD_COLLECTOR = new Permission("train.build.collector", PermissionDefault.OP, "The player can build systems to let trains collect from storage blocks");
	public static final Permission BUILD_DEPOSITOR = new Permission("train.build.depositor", PermissionDefault.OP, "The player can build systems to fill storage blocks with items from trains");
	public static final Permission BUILD_ELEVATOR = new Permission("train.build.elevator", PermissionDefault.OP, "The player can build systems to teleport trains vertically");
	public static final Permission BUILD_TELEPORTER = new Permission("train.build.teleporter", PermissionDefault.OP, "The player can build train teleporters (portals)");
	public static final Permission BUILD_BLOCKER = new Permission("train.build.blocker", PermissionDefault.OP, "The player can build train blockers");
	public static final Permission BUILD_WAIT = new Permission("train.build.wait", PermissionDefault.OP, "The player can build train wait signs");
	public static final Permission BUILD_CRAFTER = new Permission("train.build.crafter", PermissionDefault.OP, "The player can build item crafter signs");
	public static final Permission BUILD_TICKET = new Permission("train.build.ticket", PermissionDefault.OP, "The player can build a sign that will charge money or ejects a player if he can't pay");
	public static final Permission BUILD_ANNOUNCER = new Permission("train.build.announcer", PermissionDefault.OP, "The player can build a sign that sends a message to all the players in a train");
	public static final Permission BUILD_EFFECT = new Permission("train.build.effect", PermissionDefault.OP, "The player can build a sign that can play an effect");
	public static final Permission BUILD_BLOCKCHANGER = new Permission("train.build.blockchanger", PermissionDefault.OP, "The player can build a sign that alters the block displayed in minecarts");
	public static final Permission BUILD_JUMPER = new Permission("train.build.jumper", PermissionDefault.OP, "The player can build a sign that can cause a train to jump in a certain direction");
	public static final Permission BUILD_LAUNCHER = new Permission("train.build.launcher", PermissionDefault.OP, "The player can build a sign that can launch trains");
	public static final Permission BUILD_ENTER = new Permission("train.build.enter", PermissionDefault.OP, "The player can build a sign that can make nearby players/mobs enter a train");
	public static final Permission GENERAL_PLACE_MINECART = new Permission("train.place.minecart", PermissionDefault.TRUE, "The player can place minecarts");
	public static final Permission GENERAL_PLACE_TRAINCART = new Permission("train.place.traincart", PermissionDefault.TRUE, "The player can place TrainCarts minecarts");
	public static final Permission BREAK_MINECART_SELF = new Permission("train.break.self", PermissionDefault.TRUE, "The player can break their own minecarts");
	public static final Permission BREAK_MINECART_OTHER = new Permission("train.break.other", PermissionDefault.TRUE, "The player can break any cart except for invincible ones");
	public static final Permission BREAK_MINECART_ANY = new Permission("train.break.any", PermissionDefault.OP, "The player can break all carts in the game");
	public static final Permission GENERAL_PROPERTIES_ADMIN = new Permission("train.properties.admin", PermissionDefault.OP, "Carts placed by this player get the admin properties");

	private Permission(final String node, final PermissionDefault permdefault, final String desc) {
		super(node, permdefault, desc);
	}	
}
