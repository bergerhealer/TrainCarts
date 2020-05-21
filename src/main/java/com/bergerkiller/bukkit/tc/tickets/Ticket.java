package com.bergerkiller.bukkit.tc.tickets;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * Manages the display and usage configuration for a train ticket.
 * Note that this is a singleton for a ticket category, used by all instances of this ticket.
 */
public class Ticket {
    private String _name;
    private String _realm = "";
    private boolean _playerBound = false;
    private int _maxNumberOfUses = 1;
    private long _expirationTime = -1L;
    private String _backgroundImagePath = "";
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
     * Gets the image path to a local file on the disk for the background image
     * that is displayed for this ticket. If empty or missing, the default builtin
     * image is displayed.
     * 
     * @return Ticket background image path
     */
    public String getBackgroundImagePath() {
        return this._backgroundImagePath;
    }

    /**
     * Sets the image path to a local file on the disk for the background image
     * that is displayed for this ticket. If empty or missing, the default builtin
     * image is displayed.<br>
     * <br>
     * By prefixing the path with <i>pluginname:</i> resources inside other plugin
     * jar files can be used as images. Easier is to use the
     * {@link #setBackgroundImagePluginPath(JavaPlugin, String)} method for this.
     * 
     * @param path The path to set to
     */
    public void setBackgroundImagePath(String path) {
        this._backgroundImagePath = path;
    }

    /**
     * Sets the background image to an image file resource in a plugin jar file.
     * 
     * @param plugin Plugin where the resource is stored
     * @param path Image resource path
     */
    public void setBackgroundImagePluginPath(JavaPlugin plugin, String path) {
        this._backgroundImagePath = plugin.getName() + ":" + path;
    }

    /**
     * Loads the background image. If the image is too small or can not be loaded,
     * the default image is returned instead.
     * 
     * @return background image
     */
    public MapTexture loadBackgroundImage() {
        if (this._backgroundImagePath.isEmpty()) {
            return getDefaultBackgroundImage();
        }

        int index = this._backgroundImagePath.indexOf(':');
        if (index != -1) {
            String pluginName = this._backgroundImagePath.substring(0, index);
            Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
            if (plugin instanceof JavaPlugin) {
                try {
                    MapTexture bg = MapTexture.loadPluginResource((JavaPlugin) plugin, this._backgroundImagePath.substring(index+1));
                    if (bg.getWidth() >= 128 && bg.getHeight() >= 128) {
                        return bg;
                    }
                } catch (RuntimeException ex) {
                    //TextureLoadException ignored
                }
                return getDefaultBackgroundImage();
            }
        }

        // Try from disk
        File imagesDir = TrainCarts.plugin.getDataFile("images");
        File imageFile = new File(this._backgroundImagePath);
        if (!imageFile.isAbsolute()) {
            imageFile = new File(imagesDir, this._backgroundImagePath);
        }

        // Verify that the image file is below the images directory
        if (!TCConfig.allowExternalTicketImagePaths) {
            boolean validLocation;
            try {
                File a = imageFile.getAbsoluteFile().getCanonicalFile();
                File b = imagesDir.getAbsoluteFile().getCanonicalFile();
                validLocation = a.toPath().startsWith(b.toPath());
            } catch (IOException ex) {
                validLocation = false;
            }
            if (!validLocation) {
                return getDefaultBackgroundImage();
            }
        }

        // Try loading
        try {
            MapTexture bg = MapTexture.fromImageFile(imageFile.getAbsolutePath());
            if (bg.getWidth() >= 128 && bg.getHeight() >= 128) {
                return bg;
            }
        } catch (RuntimeException ex) {
            //TextureLoadException ignored
        }
        return getDefaultBackgroundImage();
    }

    /**
     * Gets the default background image, used if no background image is set or valid
     * 
     * @return default background image
     */
    public static MapTexture getDefaultBackgroundImage() {
        return TrainCarts.plugin.loadTexture("com/bergerkiller/bukkit/tc/textures/tickets/train_ticket_bg.png");
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
        this._realm = config.get("ticketRealm", "");
        this._playerBound = config.get("playerBound", false);
        this._maxNumberOfUses = config.get("maxNumberOfUses", 1);
        this._expirationTime = config.get("expirationTimeMillis", -1L);
        this._backgroundImagePath = config.get("backgroundImagePath", "");
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
        config.set("backgroundImagePath", this._backgroundImagePath);

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
        ItemStack item = MapDisplay.createMapItem(TCTicketDisplay.class);
        CommonTagCompound tag = ItemUtil.getMetaTag(item);
        tag.putValue("plugin", "TrainCarts");
        tag.putValue("ticketName", this.getName());
        tag.putValue("ticketCreationTime", System.currentTimeMillis());
        tag.putValue("ticketNumberOfUses", 0);
        tag.putUUID("ticketOwner", owner.getUniqueId());
        tag.putValue("ticketOwnerName", owner.getDisplayName());
        ItemUtil.setDisplayName(item, "Train Ticket for " + this.getName());
        return item;
    }
}
