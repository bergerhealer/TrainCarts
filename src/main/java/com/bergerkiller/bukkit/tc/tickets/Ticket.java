package com.bergerkiller.bukkit.tc.tickets;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * Manages the display and usage configuration for a train ticket.
 * Note that this is a singleton for a ticket category, used by all instances of this ticket.
 */
public class Ticket {
    private String _name;
    private boolean _playerBound = false;
    private int _maxNumberOfUses = 1;
    private long _validDuration = -1L;
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
    public long getValidDuration() {
        return this._validDuration;
    }

    /**
     * Sets the timespan in milliseconds for how long this ticket is valid once handed out.
     * A negative value indicates it is indefinitely valid.
     * 
     * @param durationMillis
     */
    public void setValidDuration(long durationMillis) {
        this._validDuration = durationMillis;
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
        this._playerBound = config.get("playerBound", false);
        this._maxNumberOfUses = config.get("maxNumberOfUses", 1);
        this._validDuration = config.get("validDurationMillis", -1L);
        this._properties = config.getNode("properties").clone();
    }

    /**
     * Saves all ticket information to a configuration node
     * 
     * @param config node to save to
     */
    public void save(ConfigurationNode config) {
        config.set("playerBound", this._playerBound);
        config.set("maxNumberOfUses", this._maxNumberOfUses);
        config.set("validDurationMillis", this._validDuration);

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
        return new ItemStack(Material.WOOD);
    }
}
