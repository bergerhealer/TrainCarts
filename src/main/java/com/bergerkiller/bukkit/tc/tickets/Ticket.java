package com.bergerkiller.bukkit.tc.tickets;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

/**
 * Manages the display and usage configuration for a train ticket.
 * Note that this is a singleton for a ticket category, used by all instances of this ticket.
 */
public class Ticket {
    private static final short TICKET_MAP_ID = 201;
    private String _name;
    private String _realm = "";
    private boolean _playerBound = false;
    private int _maxNumberOfUses = 1;
    private long _expirationTime = -1L;
    private ConfigurationNode _properties = new ConfigurationNode();

    public Ticket(String name) {
        this._name = name;
    }

    /**
     * Gets an unique name of this ticket
     * 
     * @return ticket name
     */
    public String getName() {
        return this._name;
    }

    /**
     * Sets a new unique name for this ticket
     *
     * @param name to set to
     * @return True if the new name was free to use and the ticket was renamed, False if not
     */
    public boolean setName(String name) {
        if (this._name.equals(name)) {
            return true; // prevents infinite recursion
        }
        String oldName = this._name;
        this._name = name; // do here to prevent infinite recursion
        if (TicketStore.renameTicket(oldName, this._name)) {
            return true; // success!
        }

        // Failed. Restore the name to undo changes
        this._name = oldName;
        return false;
    }

    /**
     * Gets the realm for which the tickets can be used.
     * Multiple tickets can belong the same realm, allowing you to set
     * ticket requirements for train in one go using the realm. This avoids
     * having to add all possible tickets to the trains themselves.
     * 
     * @return ticket realm
     */
    public String getRealm() {
        return this._realm;
    }

    /**
     * Sets the realm for which the tickets can be used.
     * See also: {@link #getRealm()}
     * 
     * @param realm to set to
     */
    public void setRealm(String realm) {
        this._realm = realm;
    }

    /**
     * Gets whether the ticket item can only used by the player it was initially given to
     * 
     * @return True if only the original recipient of the ticket can use it, False if not
     */
    public boolean isPlayerBound() {
        return this._playerBound;
    }

    /**
     * Sets whether the ticket item can only used by the player it was initially given to
     *
     * @param playerBound option to set to
     */
    public void setPlayerBound(boolean playerBound) {
        this._playerBound = playerBound;
    }

    /**
     * Gets the maximum number of times the ticket can be used
     * 
     * @return maximum number of uses
     */
    public int getMaxNumberOfUses() {
        return this._maxNumberOfUses;
    }

    /**
     * Sets the maximum number of times the ticket can be used
     * 
     * @param maxNumberOfUses to set to
     */
    public void setMaxNumberOfUses(int maxNumberOfUses) {
        this._maxNumberOfUses = maxNumberOfUses;
    }

    /**
     * Gets the timespan in milliseconds for how long this ticket is valid once handed out.
     * A negative value indicates it is indefinitely valid.
     * 
     * @return valid duration in milliseconds, -1 if indefinitely valid
     */
    public long getExpirationTime() {
        return this._expirationTime;
    }

    /**
     * Sets the timespan in milliseconds for how long this ticket is valid once handed out.
     * A negative value indicates it is indefinitely valid.
     * 
     * @param expirationTimeMillis
     */
    public void setExpirationTime(long expirationTimeMillis) {
        this._expirationTime = expirationTimeMillis;
    }

    /**
     * Gets all the properties that will be applied to trains when the ticket is used.
     * This can set destinations or tags, but also change other properties such as train speed.
     * 
     * @return properties set for the ticket (mutable)
     */
    public ConfigurationNode getProperties() {
        return _properties;
    }

    /**
     * Sets all the properties of this ticket to the properties set in a configuration node.
     * 
     * @param properties to set to
     */
    public void setProperties(ConfigurationNode properties) {
        this._properties = properties.clone();
        TicketStore.markChanged();
    }

    /**
     * Loads all ticket information from a configuration node
     * 
     * @param config to load from
     */
    public void load(ConfigurationNode config) {
        this._realm = config.get("realm", "");
        this._playerBound = config.get("playerBound", false);
        this._maxNumberOfUses = config.get("maxNumberOfUses", 1);
        this._expirationTime = config.get("expirationTimeMillis", -1L);
        this._properties = config.getNode("properties").clone();
    }

    /**
     * Saves all ticket information to a configuration node
     * 
     * @param config node to save to
     */
    public void save(ConfigurationNode config) {
        config.set("ticketRealm", this._realm);
        config.set("playerBound", this._playerBound);
        config.set("maxNumberOfUses", this._maxNumberOfUses);
        config.set("expirationTimeMillis", this._expirationTime);

        ConfigurationNode savedProps = config.getNode("properties");
        for (Map.Entry<String, Object> entry : this._properties.getValues().entrySet()) {
            savedProps.set(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Creates a new ticket item
     * 
     * @return ticket item
     */
    public ItemStack createItem(Player owner) {
        ItemStack item = ItemUtil.createItem(Material.MAP, TICKET_MAP_ID, 1);
        CommonTagCompound tag = ItemUtil.getMetaTag(item, true);
        tag.putValue("plugin", "TrainCarts");
        tag.putValue("ticketName", this.getName());
        tag.putValue("ticketCreationTime", System.currentTimeMillis());
        tag.putValue("ticketNumberOfUses", 0);
        tag.putUUID("ticketOwner", owner.getUniqueId());
        tag.putValue("ticketOwnerName", owner.getDisplayName());
        ItemUtil.setDisplayName(item, "Train Ticket");
        return item;
    }
}
