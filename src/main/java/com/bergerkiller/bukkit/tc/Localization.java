package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.localization.LocalizationEnum;
import org.bukkit.ChatColor;

public class Localization extends LocalizationEnum {
    public static final Localization COMMAND_ABOUT = new Localization("command.about", "TrainCarts %0% - See WIKI page for more information");
    public static final Localization COMMAND_NOPERM = new Localization("command.noperm", ChatColor.RED + "You do not have permission, ask an admin to do this for you.");
    public static final Localization EDIT_NOSELECT = new Localization("edit.noselect", ChatColor.YELLOW + "You haven't selected a train to edit yet!");
    public static final Localization EDIT_NOTALLOWED = new Localization("edit.notallowed", ChatColor.RED + "You are not allowed to own trains!");
    public static final Localization EDIT_NONEFOUND = new Localization("edit.nonefound", ChatColor.RED + "You do not own any trains you can edit.");
    public static final Localization EDIT_NOTOWNED = new Localization("edit.notowned", ChatColor.RED + "You do not own this train!");
    public static final Localization SELECT_DESTINATION = new Localization("select.destination", ChatColor.YELLOW + "You have selected " + ChatColor.WHITE + "%0%" + ChatColor.YELLOW + " as your destination!");
    public static final Localization TICKET_EXPIRED = new Localization("ticket.expired", ChatColor.RED + "Your ticket for %0% is expired");
    public static final Localization TICKET_REQUIRED = new Localization("ticket.required", ChatColor.RED + "You do not own a ticket for this train!");
    public static final Localization TICKET_USED = new Localization("ticket.used", ChatColor.GREEN + "You have used your " + ChatColor.YELLOW + "%0%" + ChatColor.GREEN + " ticket!");
    public static final Localization TICKET_CONFLICT = new Localization("ticket.conflict", ChatColor.RED + "You own multiple tickets that can be used for this train. Please hold the right ticket in your hand!");
    public static final Localization TICKET_CONFLICT_OWNER = new Localization("ticket.ownerConflict", ChatColor.RED + "The train ticket %0% is not yours, it belongs to %1%!");

    // Note: these aren't really used anymore :(
    public static final Localization TICKET_ADD = new Localization("ticket.add", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You received %0% in your bank account!");
    public static final Localization TICKET_CHECK = new Localization("ticket.check", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You currently have %0% in your bank account!");
    public static final Localization TICKET_BUYFAIL = new Localization("ticket.buyfail", ChatColor.WHITE + "[Ticket System]" + ChatColor.RED + " You can't afford a Ticket for %0%, sorry.");
    public static final Localization TICKET_BUY = new Localization("ticket.buy", ChatColor.WHITE + "[Ticket System]" + ChatColor.YELLOW + " You bought a Ticket for %0%.");

    private Localization(String name, String defValue) {
        super(name, defValue);
    }

    @Override
    public String get(String... arguments) {
        return TrainCarts.plugin.getLocale(this.getName(), arguments);
    }
}
