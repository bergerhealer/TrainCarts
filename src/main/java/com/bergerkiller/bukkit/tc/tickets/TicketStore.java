package com.bergerkiller.bukkit.tc.tickets;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

import com.bergerkiller.bukkit.common.inventory.CommonItemMaterials;
import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

public class TicketStore {
    public static final Ticket DEFAULT = new Ticket("");
    private static final String saveFileName = "tickets.yml";
    private static boolean hasChanges = false;
    private static final HashMap<String, Ticket> ticketMap = new HashMap<String, Ticket>();
    private static final HashMap<UUID, Ticket> editingMap = new HashMap<UUID, Ticket>();

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
     * Gets all tickets available on the server
     * 
     * @return tickets
     */
    public static Collection<Ticket> getAll() {
        return ticketMap.values();
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
     * @param playerUUID Unique Id of the Player
     * @return ticket that is being edited
     */
    public static Ticket getEditing(UUID playerUUID) {
        return editingMap.get(playerUUID);
    }

    /**
     * Gets the ticket a player is currently editing through commands
     *
     * @param player Player to get it for
     * @return ticket that is being edited
     */
    public static Ticket getEditing(Player player) {
        return getEditing(player.getUniqueId());
    }

    /**
     * Sets the ticket a player is currently editing through commands
     *
     * @param playerUUID Unique Id of the Player
     * @param ticket to set as being edited
     */
    public static void setEditing(UUID playerUUID, Ticket ticket) {
        editingMap.put(playerUUID, ticket);
    }

    /**
     * Sets the ticket a player is currently editing through commands
     *
     * @param player Player to set it for
     * @param ticket to set as being edited
     */
    public static void setEditing(Player player, Ticket ticket) {
        setEditing(player.getUniqueId(), ticket);
    }

    /**
     * Gets whether a given item is a ticket item. This will return true also when the ticket
     * the item is referencing does not exist.
     * 
     * @param item to check
     * @return True if the item is a ticket item
     */
    public static boolean isTicketItem(ItemStack item) {
        return isTicketItem(CommonItemStack.of(item));
    }

    /**
     * Gets whether a given item is a ticket item. This will return true also when the ticket
     * the item is referencing does not exist.
     *
     * @param item to check. CommonItemStack, must not be null.
     * @return True if the item is a ticket item
     */
    public static boolean isTicketItem(CommonItemStack item) {
        if (!item.isType(CommonItemMaterials.FILLED_MAP)) {
            return false;
        }
        CommonTagCompound tag = item.getCustomData();
        return tag.containsKey("ticketName") && tag.getValue("plugin", "").equals("TrainCarts");
    }

    /**
     * Gets a ticket from a ticket item. Returns null if the item is not a ticket item, or if the item
     * references a non-existing ticket.
     *
     * @param item to get the ticket for
     * @return ticket for the item, null if item is not an existing ticket
     */
    public static Ticket getTicketFromItem(ItemStack item) {
        return getTicketFromItem(CommonItemStack.of(item));
    }

    /**
     * Gets a ticket from a ticket item. Returns null if the item is not a ticket item, or if the item
     * references a non-existing ticket.
     * 
     * @param item to get the ticket for. CommonItemStack, must not be null.
     * @return ticket for the item, null if item is not an existing ticket
     */
    public static Ticket getTicketFromItem(CommonItemStack item) {
        if (isTicketItem(item)) {
            return ticketMap.get(item.getCustomData().getValue("ticketName", ""));
        } else {
            return null;
        }
    }

    /**
     * Gets the number of times a ticket has been used
     *
     * @param item to read from
     * @return number of times the ticket has been used
     */
    public static int getNumberOfUses(ItemStack item) {
        return getNumberOfUses(CommonItemStack.of(item));
    }

    /**
     * Gets the number of times a ticket has been used
     * 
     * @param item to read from. CommonItemStack, must not be null.
     * @return number of times the ticket has been used
     */
    public static int getNumberOfUses(CommonItemStack item) {
        return item.getCustomData().getValue("ticketNumberOfUses", 0);
    }

    /**
     * Checks whether a ticket item has expired either the number of uses, and/or by time.
     *
     * @param item to check
     * @return True if the ticket item is expired
     */
    public static boolean isTicketExpired(ItemStack item) {
        return isTicketExpired(CommonItemStack.of(item));
    }

    /**
     * Checks whether a ticket item has expired either the number of uses, and/or by time.
     * 
     * @param item to check. CommonItemStack, must not be null.
     * @return True if the ticket item is expired
     */
    public static boolean isTicketExpired(CommonItemStack item) {
        Ticket ticket = getTicketFromItem(item);
        if (ticket == null) {
            return true;
        } else {
            CommonTagCompound tag = item.getCustomData();
            if (ticket.getMaxNumberOfUses() >= 0) {
                int numberOfUses = tag.getValue("ticketNumberOfUses", 0);
                if (numberOfUses >= ticket.getMaxNumberOfUses()) {
                    return true;
                }
            }
            if (ticket.getExpirationTime() >= 0) {
                long timeNow = System.currentTimeMillis();
                long timeCreated = tag.getValue("ticketCreationTime", timeNow);
                if (timeNow >= (timeCreated + ticket.getExpirationTime())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Checks whether a ticket item can be used by a player. Returns false when the player is not the owner
     * of the ticket. Returns true when he is, or when the ticket is not bound to a player.
     *
     * @param player to check
     * @param item to check
     * @return True if the player owns the ticket item and can use it
     */
    public static boolean isTicketOwner(Player player, ItemStack item) {
        return isTicketOwner(player, CommonItemStack.of(item));
    }

    /**
     * Checks whether a ticket item can be used by a player. Returns false when the player is not the owner
     * of the ticket. Returns true when he is, or when the ticket is not bound to a player.
     * 
     * @param player to check
     * @param item to check. CommonItemStack, must not be null.
     * @return True if the player owns the ticket item and can use it
     */
    public static boolean isTicketOwner(Player player, CommonItemStack item) {
        Ticket ticket = getTicketFromItem(item);
        if (ticket == null || !ticket.isPlayerBound()) {
            return true;
        } else {
            CommonTagCompound tag = item.getCustomData();
            UUID ownerUUID = tag.getUUID("ticketOwner");
            if (ownerUUID == null) {
                return true;
            } else {
                return ownerUUID.equals(player.getUniqueId());
            }
        }
    }

    /**
     * Handles the ticket requirements for a train. If no tickets are set for the train,
     * this function always return true. Otherwise, the player is checked for suitable tickets for the train.
     * Informative messages will be displayed to inform of failure or success in using the tickets.
     * When tickets run out of uses they are automatically removed from the inventory.
     * 
     * @param player to handle
     * @param trainProperties for the train the player is trying to enter or use
     * @return True when the player meets ticket requirements, False if not
     */
    public static boolean handleTickets(Player player, TrainProperties trainProperties) {
        if (trainProperties.getTickets().isEmpty()) {
            return true; // no tickets are used
        }

        // First check both player's hands for tickets. These have priority!
        CommonItemStack mainHand = CommonItemStack.of(HumanHand.getItemInMainHand(player));
        CommonItemStack offHand = CommonItemStack.of(HumanHand.getItemInOffHand(player));
        if (isSuitableTicket(mainHand, trainProperties)) {
            if (isSuitableTicket(offHand, trainProperties)) {
                Localization.TICKET_CONFLICT.message(player);
                return false;
            }
            if (preUseTicket(player, mainHand, trainProperties)) {
                HumanHand.setItemInMainHand(player, useTicketItem(mainHand).toBukkit());
                return true;
            } else {
                return false;
            }
        } else if (isSuitableTicket(offHand, trainProperties)) {
            if (preUseTicket(player, offHand, trainProperties)) {
                HumanHand.setItemInOffHand(player, useTicketItem(offHand).toBukkit());
                return true;
            } else {
                return false;
            }
        }

        // If either hand has a ticket, show an 'incorrect' message
        {
            Ticket mainHandTicket = getTicketFromItem(mainHand);
            Ticket offHandTicket = getTicketFromItem(offHand);
            if (mainHandTicket != null || offHandTicket != null) {
                if (mainHandTicket != null) {
                    Localization.TICKET_CONFLICT_TYPE.message(player, mainHandTicket.getName());
                }
                if (offHandTicket != null) {
                    Localization.TICKET_CONFLICT_TYPE.message(player, offHandTicket.getName());
                }
                return false;
            }            
        }

        // Handle tickets in the quickbar, then rest of inventory
        TicketHandleResult result = handleTicketsInventory(player, true, trainProperties);
        if (result == TicketHandleResult.MISSING) {
            result = handleTicketsInventory(player, false, trainProperties);
        }
        if (result == TicketHandleResult.MISSING) {
            Localization.TICKET_REQUIRED.message(player);
        }
        return (result == TicketHandleResult.OK);
    }

    private static enum TicketHandleResult {
        MISSING, FAILURE, OK
    }

    private static TicketHandleResult handleTicketsInventory(Player player, boolean quickbar, TrainProperties trainProperties) {
        // Now we know none of the items in the main hand can be used, check the rest of the inventory
        PlayerInventory inventory = player.getInventory();
        int ticketInvIndex = -1;
        int start = (quickbar ? 0 : 9);
        int end = (quickbar ? 9 : inventory.getSize());
        for (int i = start; i < end; i++) {
            CommonItemStack item = CommonItemStack.of(inventory.getItem(i));
            if (isSuitableTicket(item, trainProperties) && !isTicketExpired(item)) {
                if (ticketInvIndex != -1) {
                    // Multiple tickets could be used. Don't know which...
                    Localization.TICKET_CONFLICT.message(player);
                    return TicketHandleResult.FAILURE;
                } else {
                    ticketInvIndex = i;
                }
            }
        }

        if (ticketInvIndex == -1) {
            return TicketHandleResult.MISSING;
        }

        CommonItemStack ticketItem = CommonItemStack.of(inventory.getItem(ticketInvIndex));
        if (preUseTicket(player, ticketItem, trainProperties)) {
            inventory.setItem(ticketInvIndex, useTicketItem(ticketItem).toBukkit());
            return TicketHandleResult.OK;
        } else {
            return TicketHandleResult.FAILURE;
        }
    }

    private static boolean isSuitableTicket(CommonItemStack item, TrainProperties trainProperties) {
        Ticket ticket = getTicketFromItem(item);
        if (ticket != null) {
            for (String allowed : trainProperties.getTickets()) {
                if (ticket.getName().equals(allowed) || (!LogicUtil.nullOrEmpty(ticket.getRealm()) && ticket.getRealm().equals(allowed))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean preUseTicket(Player player, CommonItemStack item, TrainProperties trainProperties) {
        // Handle permissions and messages to the player
        String ticketName = item.getCustomData().getValue("ticketName", "UNKNOWN");
        if (!isTicketOwner(player, item)) {
            String ownerName = item.getCustomData().getValue("ticketOwnerName", "UNKNOWN");
            Localization.TICKET_CONFLICT_OWNER.message(player, ticketName, ownerName);
            return false;
        }
        if (isTicketExpired(item)) {
            Localization.TICKET_EXPIRED.message(player, ticketName);
            return false;
        }
        Localization.TICKET_USED.message(player, ticketName);

        // Apply ticket to the train
        Ticket ticket = getTicketFromItem(item);
        if (ticket != null) {
            ConfigurationNode ticketTrainProperties = ticket.getProperties().clone();
            ticketTrainProperties.remove("carts"); // this would break a LOT

            // Load train-level and cart-level properties from ticket defined properties
            trainProperties.apply(ticketTrainProperties);

            // Notify the train of these changes (triggers signs)
            MinecartGroup group = trainProperties.getHolder();
            if (group != null) {
                group.onPropertiesChanged();
            }
        }
        return true;
    }

    /**
     * Simulates a single use of an item. Returns the updated item that should be set
     * in the player inventory after wards. May return null if it should be removed.
     *
     * @param item CommonItemStack being used
     * @return Updated item
     */
    private static CommonItemStack useTicketItem(CommonItemStack item) {
        Ticket ticket = getTicketFromItem(item);
        if (ticket == null) {
            return CommonItemStack.empty();
        }

        item = item.clone();
        if (ticket.getMaxNumberOfUses() < 0 || item.getAmount() <= 1) {
            item.updateCustomData(tag -> {
                tag.putValue("ticketNumberOfUses", tag.getValue("ticketNumberOfUses", 0) + 1);
            });
        } else {
            item.setAmount(item.getAmount() - 1);
        }
        if (isTicketExpired(item)) {
            return CommonItemStack.empty();
        }

        return item;
    }

    public static void markChanged() {
        hasChanges = true;
    }

    public static void load(TrainCarts traincarts) {
        FileConfiguration config = new FileConfiguration(traincarts, saveFileName);
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

        // Create 'images' directory, if it does not already exist
        traincarts.getDataFile("images").mkdirs();
    }

    public static void save(TrainCarts traincarts, boolean autosave) {
        if (autosave && !hasChanges) {
            return;
        }

        FileConfiguration config = new FileConfiguration(traincarts, saveFileName);
        for (Ticket ticket : ticketMap.values()) {
            ticket.save(config.getNode(ticket.getName()));
        }
        config.save();
        hasChanges = false;
    }
}
