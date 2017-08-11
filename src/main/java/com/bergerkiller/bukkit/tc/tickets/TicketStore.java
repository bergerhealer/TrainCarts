package com.bergerkiller.bukkit.tc.tickets;

import java.util.HashMap;
import java.util.Iterator;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.tc.TrainCarts;

public class TicketStore {
    public static final Ticket DEFAULT = new Ticket("");
    private static final String saveFileName = "tickets.yml";
    private static boolean hasChanges = false;
    private static final HashMap<String, Ticket> ticketMap = new HashMap<String, Ticket>();
    private static final HashMap<String, Ticket> editingMap = new HashMap<String, Ticket>();

    static {
        
    }

    /**
     * Creates a new ticket, using an existing ticket as a base configuration.
     * The static DEFAULT constant can be used when no ticket is explicitly specified.
     * A name for the ticket is automatically generated.
     * This function always succeeds.
     * 
     * @param baseTicket to use
     * @return newly created ticket
     */
    public static Ticket createTicket(Ticket baseTicket) {
        Ticket ticket = null;
        for (int i = 1; ticket == null; i++) {
            ticket = createTicket(baseTicket, "ticket" + i);
        }
        return ticket;
    }

    /**
     * Creates a new ticket, using an existing ticket as a base configuration.
     * The static DEFAULT constant can be used when no ticket is explicitly specified.
     * 
     * @param baseTicket to use
     * @param name for the new ticket
     * @return newly created ticket, null if it could not be created (name already used)
     */
    public static Ticket createTicket(Ticket baseTicket, String name) {
        if (ticketMap.containsKey(name)) {
            return null;
        }
        Ticket ticket = new Ticket(name);
        ticket.setProperties(baseTicket.getProperties());
        ticket.setPlayerBound(baseTicket.isPlayerBound());
        ticketMap.put(name, ticket);
        markChanged();
        return ticket;
    }

    /**
     * Looks up a ticket by name
     * 
     * @param ticketName for the ticket to find
     * @return ticket, null if not found
     */
    public static Ticket getTicket(String ticketName) {
        return ticketMap.get(ticketName);
    }

    /**
     * Removes a ticket by name. Returns true if the ticket was removed.
     * 
     * @param ticketName for the ticket to remove
     * @return True if removed, False if a ticket by this name did not exist
     */
    public static boolean removeTicket(String ticketName) {
        Ticket removed = ticketMap.remove(ticketName);
        if (removed == null) {
            return false;
        }

        // Remove ticket from all players editing it to ensure state is really gone
        Iterator<Ticket> editIter = editingMap.values().iterator();
        while (editIter.hasNext()) {
            if (editIter.next() == removed) {
                editIter.remove();
            }
        }
        markChanged();
        return true;
    }

    /**
     * Changes the name of an existing ticket
     * 
     * @param oldTicketName old name of the ticket
     * @param newTicketName new name of the ticket
     * @return True if renaming was successful (ticket existed and new name could be used), False if not
     */
    public static boolean renameTicket(String oldTicketName, String newTicketName) {
        if (oldTicketName.equals(newTicketName)) {
            return true;
        }
        if (ticketMap.containsKey(newTicketName)) {
            return false;
        }
        Ticket ticket = ticketMap.remove(oldTicketName);
        if (ticket == null) {
            return false;
        }
        ticket.setName(newTicketName);
        ticketMap.put(newTicketName, ticket);
        markChanged();
        return true;
    }

    /**
     * Gets the ticket a player is currently editing through commands
     *
     * @param playerName
     * @return ticket that is being edited
     */
    public static Ticket getEditing(String playerName) {
        return editingMap.get(playerName);
    }

    /**
     * Sets the ticket a player is currently editing through commands
     *
     * @param playerName
     * @param ticket to set as being edited
     */
    public static void setEditing(String playerName, Ticket ticket) {
        editingMap.put(playerName, ticket);
    }

    public static void markChanged() {
        hasChanges = true;
    }

    public static void load() {
        FileConfiguration config = new FileConfiguration(TrainCarts.plugin, saveFileName);
        config.load();

        // Clear before (re?)loading
        ticketMap.clear();
        editingMap.clear();

        // Load
        for (ConfigurationNode node : config.getNodes()) {
            Ticket ticket = new Ticket(node.getName());
            ticket.load(node);
            ticketMap.put(ticket.getName(), ticket);
        }
        hasChanges = false;
    }

    public static void save(boolean autosave) {
        if (autosave && !hasChanges) {
            return;
        }

        FileConfiguration config = new FileConfiguration(TrainCarts.plugin, saveFileName);
        for (Ticket ticket : ticketMap.values()) {
            ticket.save(config.getNode(ticket.getName()));
        }
        config.save();
        hasChanges = false;
    }
}
