package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;

public class TicketCommands {

    public static boolean execute(CommandSender sender, String cmd, String[] args) throws NoPermissionException {
        Permission.TICKET_MANAGE.handle(sender);
        if (cmd.equals("edit")) {
            // Edit command for selecting new tickets to be edited
            if (args.length == 1) {
                Ticket ticket = TicketStore.getTicket(args[0]);
                if (ticket != null) {
                    TicketStore.setEditing(sender.getName(), ticket);
                    sender.sendMessage(ChatColor.GREEN + "You are now editing ticket " + ChatColor.YELLOW + ticket.getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "Ticket with the name " + args[0] + " does not exist!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "You must enter the name of the ticket to edit");
                sender.sendMessage(ChatColor.RED + "To see which tickets are available, use /train ticket list");
            }
            return true;
        } else if (cmd.equals("create")) {
            // Creating new tickets
            Ticket newTicket;
            if (args.length == 0) {
                newTicket = TicketStore.createTicket(TicketStore.DEFAULT);
            } else {
                newTicket = TicketStore.createTicket(TicketStore.DEFAULT, args[0]);
                if (newTicket == null) {
                    sender.sendMessage(ChatColor.RED + "Failed to create ticket: a ticket with the name " + args[0] + " already exists");
                    return true;
                }
            }
            sender.sendMessage(ChatColor.GREEN + "You have created a new ticket with the name " + ChatColor.YELLOW + newTicket.getName());
            TicketStore.setEditing(sender.getName(), newTicket);
            return true;
        } else if (cmd.equals("give")) {
            // Gives a ticket to a player as an item to use
            if (args.length >= 2) {
                Ticket ticket = TicketStore.getTicket(args[0]);
                if (ticket == null) {
                    sender.sendMessage(ChatColor.RED + "Failed to give ticket: ticket with name " + args[0] + " does not exist!");
                    return true;
                }
                for (int pIdx = 1; pIdx < args.length; pIdx++) {
                    String playerName = args[pIdx];
                    Player player = Bukkit.getPlayer(playerName); //eh?
                    if (player == null) {
                        sender.sendMessage(ChatColor.RED + "Failed to give ticket to player " + playerName + ": not online");
                    } else {
                        ItemStack item = ticket.createItem(player);
                        player.getInventory().addItem(item);
                        sender.sendMessage(ChatColor.GREEN + "Ticket " + ChatColor.YELLOW + ticket.getName() + ChatColor.GREEN + 
                                " given to player " + ChatColor.YELLOW + player.getName());
                    }
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Incorrect syntax. Use /train ticket give [ticketname] [playernames...]");
            }
            return true;
        }

        Ticket ticket = TicketStore.getEditing(sender.getName());
        if (ticket == null) {
            sender.sendMessage(ChatColor.RED + "You are not editing any tickets right now");
            sender.sendMessage(ChatColor.RED + "To create a new train ticket, use /train ticket create");
            sender.sendMessage(ChatColor.RED + "To edit an existing train ticket, use /train ticket edit [name]");
            return true;
        }

        // Creating new tickets by cloning an existing one
        if (cmd.equals("clone")) {
            Ticket newTicket;
            if (args.length == 0) {
                newTicket = TicketStore.createTicket(ticket);
            } else {
                newTicket = TicketStore.createTicket(ticket, args[0]);
                if (newTicket == null) {
                    sender.sendMessage(ChatColor.RED + "Failed to clone ticket: a ticket with the name " + args[0] + " already exists");
                    return true;
                }
            }
            sender.sendMessage(ChatColor.GREEN + "You cloned the ticket, creating a new ticket with the name " + ChatColor.YELLOW + newTicket.getName());
            TicketStore.setEditing(sender.getName(), newTicket);
            return true;
        }

        // Change the name of an existing ticket
        if (cmd.equals("rename") || cmd.equals("name")) {
            if (args.length == 1) {
                if (ticket.setName(args[0])) {
                    sender.sendMessage(ChatColor.GREEN + "Ticket has been renamed to " + ChatColor.YELLOW + ticket.getName());
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to rename ticket to " + args[0] + ": a ticket with this name already exists!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Incorrect syntax. To rename, use /train ticket rename [new_name]");
            }
            return true;
        }

        // Set a ticket realm
        if (cmd.equals("realm") || cmd.equals("setrealm")) {
            if (args.length == 1) {
                ticket.setRealm(args[0]);
                TicketStore.markChanged();
                sender.sendMessage(ChatColor.GREEN + "Ticket realm set to " + ChatColor.YELLOW + args[0]);
            } else {
                sender.sendMessage(ChatColor.RED + "Incorrect syntax. To set a realm, use /train ticket realm [realm]");
            }
            return true;
        }

        // Set the number of uses for the ticket
        if (cmd.equals("maxuses") || cmd.equals("maximumuses") || cmd.equals("uselimit")) {
            if (args.length == 1) {
                int numUses = 1;
                if (args[0].contains("inf") || args[0].contains("unl")) {
                    numUses = -1;
                } else {
                    numUses = ParseUtil.parseInt(args[0], 1);
                }
                ticket.setMaxNumberOfUses(numUses);
                TicketStore.markChanged();
                String numUsesText = (numUses >= 0) ? Integer.toString(numUses) : "unlimited";
                sender.sendMessage(ChatColor.GREEN + "Ticket maximum number of uses set to " + ChatColor.YELLOW + numUsesText);
            } else {
                sender.sendMessage(ChatColor.RED + "Incorrect syntax. To set the maximum number of uses, use /train ticket maxuses [max_uses]");
            }
            return true;
        }
        
        return true;
    }
}
