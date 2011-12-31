package com.bergerkiller.bukkit.tc.permissions;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

public enum Permission {
	COMMAND_GLOBALPROPERTIES("train.command.globalproperties", PermissionDefault.OP),
	COMMAND_PROPERTIES("train.command.properties", PermissionDefault.TRUE),
	COMMAND_DESTROY("train.command.destroy", PermissionDefault.OP),
	COMMAND_DESTROYALL("train.command.destroyall", PermissionDefault.OP),
	COMMAND_REROUTE("train.command.reroute", PermissionDefault.OP),
	COMMAND_BREAKBLOCK("train.command.break", PermissionDefault.TRUE),
	COMMAND_BREAKBLOCK_ADMIN("train.command.breakblock.admin", PermissionDefault.OP),
	COMMAND_SETPUBLIC("train.command.setpublic", PermissionDefault.TRUE),
	COMMAND_SETOWNERS("train.command.setowners", PermissionDefault.TRUE),
	COMMAND_SETTAGS("train.command.settags", PermissionDefault.TRUE),
	COMMAND_MOBENTER("train.command.mobenter", PermissionDefault.TRUE),
	COMMAND_SETDESTINATION("train.command.setdestination", PermissionDefault.TRUE),
	COMMAND_PLAYERENTER("train.command.playerenter", PermissionDefault.TRUE),
	COMMAND_PLAYEREXIT("train.command.playerexit", PermissionDefault.TRUE),
	COMMAND_PICKUP("train.command.pickup", PermissionDefault.TRUE),
	COMMAND_SETLINKING("train.command.setlinking", PermissionDefault.TRUE),
	COMMAND_KEEPCHUNKSLOADED("train.command.keepchunksloaded", PermissionDefault.OP),
	COMMAND_PUSHING("train.command.pushing", PermissionDefault.TRUE),
	COMMAND_SLOWDOWN("train.command.slowdown", PermissionDefault.TRUE),
	COMMAND_SETCOLLIDE("train.command.setcollide", PermissionDefault.TRUE),
	COMMAND_SETSPEEDLIMIT("train.command.setspeedlimit", PermissionDefault.TRUE),
	COMMAND_SETPOWERCARTREQ("train.command.setpoweredcartrequirement", PermissionDefault.TRUE),
	COMMAND_RENAME("train.command.rename", PermissionDefault.TRUE),
	BUILD_STATION("train.build.station", PermissionDefault.OP),
	BUILD_SPAWNER("train.build.spawner", PermissionDefault.OP),
	BUILD_TRIGGER("train.build.trigger", PermissionDefault.OP),
	BUILD_DESTINATION("train.build.destination", PermissionDefault.OP),
	BUILD_SWITCHER("train.build.switcher", PermissionDefault.OP),
	BUILD_DESTRUCTOR("train.build.destructor", PermissionDefault.OP),
	BUILD_EJECTOR("train.build.ejector", PermissionDefault.OP),
	BUILD_PROPERTY("train.build.property", PermissionDefault.OP),
	BUILD_CHEST("train.build.chest", PermissionDefault.OP),
	BUILD_TELEPORTER("train.build.teleporter", PermissionDefault.OP),
	GENERAL_PLACE_MINECART("train.place.minecart", PermissionDefault.TRUE),
	GENERAL_PROPERTIES_DEFAULT("train.properties.default", PermissionDefault.NOT_OP),
	GENERAL_PROPERTIES_ADMIN("train.properties.admin", PermissionDefault.OP);
	
	private final String node;
	private final PermissionDefault def;
	private Permission(String node, PermissionDefault permdefault) {
		this.def = permdefault;
		this.node = node;
	}
	
	public final void handle(CommandSender sender) throws NoPermissionException {
		if (!(sender instanceof Player)) return;
		if (!this.has((Player) sender)) throw new NoPermissionException();
	}
	public final boolean has(Player player) {
		return player.hasPermission(this.node);
	}
	public final void register() {
		Bukkit.getServer().getPluginManager().addPermission(new org.bukkit.permissions.Permission(this.node, this.def));
	}
	public final static void registerAll() {
		for (Permission perm : values()) {
			perm.register();
		}
	}
	
	public String toString() {
		return this.node;
	}
	
}
