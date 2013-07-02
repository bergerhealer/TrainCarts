package com.bergerkiller.bukkit.tc;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.localization.LocalizationEnum;

public class Localization extends LocalizationEnum {
	public static final Localization COMMAND_ABOUT = new Localization("command.about", "TrainCarts %0% - See WIKI page for more information");
	public static final Localization COMMAND_NOPERM = new Localization("command.noperm", ChatColor.RED + "You do not have permission, ask an admin to do this for you.");
	public static final Localization TICKET_ADD = new Localization("ticket.add", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You received %0% in your bank account!");
	public static final Localization TICKET_CHECK = new Localization("ticket.check", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You currently have %0% in your bank account!");
	public static final Localization TICKET_BUYFAIL = new Localization("ticket.buyfail", ChatColor.WHITE + "[Ticket System]" + ChatColor.RED + " You can't afford a Ticket for %0%, sorry.");
	public static final Localization TICKET_BUY = new Localization("ticket.buy", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You bought a Ticket for %0%.");
	public static final Localization EDIT_NOSELECT = new Localization("edit.noselect", ChatColor.YELLOW + "You haven't selected a train to edit yet!");
	public static final Localization EDIT_NOTALLOWED = new Localization("edit.notallowed", ChatColor.RED + "You are not allowed to own trains!");
	public static final Localization EDIT_NONEFOUND = new Localization("edit.nonefound", ChatColor.RED + "You do not own any trains you can edit.");
	public static final Localization EDIT_NOTOWNED = new Localization("edit.notowned", ChatColor.RED + "You do not own this train!");
	public static final Localization SELECT_DESTINATION = new Localization("select.destination", ChatColor.YELLOW + "You have selected " + ChatColor.WHITE + "%0%" + ChatColor.YELLOW + " as your destination!");

	private Localization(String name, String defValue) {
		super(name, defValue);
	}

	@Override
	public String get(String... arguments) {
		return TrainCarts.plugin.getLocale(this.getName(), arguments);
	}
}
