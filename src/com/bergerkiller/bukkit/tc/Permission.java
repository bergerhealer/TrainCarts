package com.bergerkiller.bukkit.tc;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

import com.bergerkiller.bukkit.common.permissions.IPermissionDefault;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;

public enum Permission implements IPermissionDefault {
	COMMAND_GLOBALPROPERTIES("train.command.globalproperties", PermissionDefault.OP, "The player can edit the properties of all carts"),
	COMMAND_PROPERTIES("train.command.properties", PermissionDefault.TRUE, "The player can edit the properties of carts he owns"),
	COMMAND_DESTROY("train.command.destroy", PermissionDefault.OP, "The player can destroy owned carts through commands"),
	COMMAND_DESTROYALL("train.command.destroyall", PermissionDefault.OP, "The player can destroy all trains on the server"),
	COMMAND_REROUTE("train.command.reroute", PermissionDefault.OP, "The player can force path finding to re-calculate"),
	COMMAND_RELOAD("train.command.reload", PermissionDefault.OP, "The player can reload the configuration"),
	COMMAND_BREAKBLOCK("train.command.break", PermissionDefault.TRUE, "The player can set blocks the cart can break from a set list"),
	COMMAND_BREAKBLOCK_ADMIN("train.command.breakblock.admin", PermissionDefault.OP, "The player can set blocks the cart can break, any type"),
	COMMAND_SETPUBLIC("train.command.setpublic", PermissionDefault.TRUE, "The player can make his owned carts open for the public"),
	COMMAND_SETOWNERS("train.command.setowners", PermissionDefault.TRUE, "The player can set the owners of his owned carts"),
	COMMAND_SETTAGS("train.command.settags", PermissionDefault.TRUE, "The player can set the tags of his owned carts"),
	COMMAND_MOBENTER("train.command.mobenter", PermissionDefault.TRUE, "The player can set if mobs can enter his owned carts"),
	COMMAND_SETDESTINATION("train.command.setdestination", PermissionDefault.TRUE, "The player can set destinations for his owned carts"),
	COMMAND_PLAYERENTER("train.command.playerenter", PermissionDefault.TRUE, "The player can set if players can enter his owned carts"),
	COMMAND_PLAYEREXIT("train.command.playerexit", PermissionDefault.TRUE, "The player can set if players can exit his owned carts"),
	COMMAND_PICKUP("train.command.pickup", PermissionDefault.TRUE, "The player can set if his owned storage carts pick up items"),
	COMMAND_SETLINKING("train.command.setlinking", PermissionDefault.TRUE, "The player can set if his owned trains can link to other trains"),
	COMMAND_KEEPCHUNKSLOADED("train.command.keepchunksloaded", PermissionDefault.OP, "The player can if his owned trains keep nearby chunks loaded"),
	COMMAND_PUSHING("train.command.pushing", PermissionDefault.TRUE, "The player can if his owned trains push away certain entities"),
	COMMAND_SLOWDOWN("train.command.slowdown", PermissionDefault.TRUE, "The player can if his owned trains slow down over time"),
	COMMAND_SETCOLLIDE("train.command.setcollide", PermissionDefault.TRUE, "The player can if his owned trains can collide"),
	COMMAND_SETSPEEDLIMIT("train.command.setspeedlimit", PermissionDefault.TRUE, "The player can set the maximum speed for his trains"),
	COMMAND_SETPOWERCARTREQ("train.command.setpoweredcartrequirement", PermissionDefault.TRUE, 
			"The player can set if a powered minecart is needed for his train to stay alive"),
			
	COMMAND_DEFAULT("train.command.default", PermissionDefault.OP, "The player can apply default settings to his owned trains"),
	COMMAND_RENAME("train.command.rename", PermissionDefault.TRUE, "The player can rename his owned trains"),
	COMMAND_DISPLAYNAME("train.command.displayname", PermissionDefault.TRUE, "The player can change the display name of his owned trains"),
	BUILD_STATION("train.build.station", PermissionDefault.OP, "The player can build train stations"),
	BUILD_SPAWNER("train.build.spawner", PermissionDefault.OP, "The player can build train spawners"),
	BUILD_TRIGGER("train.build.trigger", PermissionDefault.OP, "The player can build train triggers"),
	BUILD_DESTINATION("train.build.destination", PermissionDefault.OP, "The player can build destinations"),
	BUILD_SWITCHER("train.build.switcher", PermissionDefault.OP, "The player can build track switchers"),
	BUILD_DESTRUCTOR("train.build.destructor", PermissionDefault.OP, "The player can build train destructors"),
	BUILD_DETECTOR("train.build.detector", PermissionDefault.OP, "The player can build train detectors"),
	BUILD_EJECTOR("train.build.ejector", PermissionDefault.OP, "The player can build train ejectors"),
	BUILD_PROPERTY("train.build.property", PermissionDefault.OP, "The player can build train property setters"),
	BUILD_COLLECTOR("train.build.collector", PermissionDefault.OP, "The player can build systems to let trains collect from storage blocks"),
	BUILD_DEPOSITOR("train.build.depositor", PermissionDefault.OP, "The player can build systems to fill storage blocks with items from trains"),
	BUILD_ELEVATOR("train.build.elevator", PermissionDefault.OP, "The player can build systems to teleport trains vertically"),
	BUILD_TELEPORTER("train.build.teleporter", PermissionDefault.OP, "The player can build train teleporters (portals)"),
	BUILD_BLOCKER("train.build.blocker", PermissionDefault.OP, "The player can build train blockers"),
	BUILD_WAIT("train.build.wait", PermissionDefault.OP, "The player can build train wait signs"),
	BUILD_CRAFTER("train.build.crafter", PermissionDefault.OP, "The player can build item crafter signs"),
	BUILD_TICKET("train.build.ticket", PermissionDefault.OP, "The player can build a sign that will charge money or ejects a player if he can't pay"),
	BUILD_ANNOUNCER("train.build.announcer", PermissionDefault.OP, "The player can build a sign that sends a message to all the players in a train"),
	BUILD_EFFECT("train.build.effect", PermissionDefault.OP, "The player can build a sign that can play an effect"),
	
	GENERAL_PLACE_MINECART("train.place.minecart", PermissionDefault.TRUE, "The player can place minecarts"),
	GENERAL_PROPERTIES_DEFAULT("train.properties.default", PermissionDefault.NOT_OP, "Carts placed by this player get the default properties"),
	GENERAL_PROPERTIES_ADMIN("train.properties.admin", PermissionDefault.OP, "Carts placed by this player get the admin properties");
	
	private final String node;
	private final PermissionDefault def;
	private final String desc;
	private Permission(final String node, final PermissionDefault permdefault, final String desc) {
		this.def = permdefault;
		this.node = node;
		this.desc = desc;
	}
	
	public final void handle(CommandSender sender) throws NoPermissionException {
		if (!(sender instanceof Player)) return;
		if (!this.has((Player) sender)) throw new NoPermissionException();
	}
	public final boolean has(Player player) {
		return player.hasPermission(this.node);
	}
	
	public String toString() {
		return this.node;
	}

	@Override
	public String getName() {
		return this.node;
	}

	@Override
	public PermissionDefault getDefault() {
		return this.def;
	}

	@Override
	public String getDescription() {
		return this.desc;
	}
	
}
