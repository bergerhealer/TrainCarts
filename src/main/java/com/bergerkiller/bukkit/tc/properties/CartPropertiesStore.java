package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores all the Cart Properties available by Minecart UUID
 */
public class CartPropertiesStore {
    protected static HashMap<UUID, CartProperties> editing = new HashMap<>();
    private static HashMap<UUID, CartProperties> properties = new HashMap<>();

    /**
     * Gets the cart properties by cart uuid
     * 
     * @param uuid
     * @return cart properties, null if not found
     */
    public static CartProperties getByUUID(UUID uuid) {
        return properties.get(uuid);
    }

    /**
     * Gets the properties of the Minecart the specified player is currently editing
     *
     * @param player that is editing
     * @return the Cart Properties the player is editing
     */
    public static CartProperties getEditing(Player player) {
        return getEditing(player.getUniqueId());
    }

    /**
     * Gets the properties of the Minecart the specified player is currently editing
     *
     * @param playerUUID of the player
     * @return the Cart Properties the player is editing
     */
    public static CartProperties getEditing(UUID playerUUID) {
        return editing.get(playerUUID);
    }

    /**
     * Sets the properties of the Minecart the specified player is currently editing
     *
     * @param player that is editing
     * @param properties to set to
     */
    public static void setEditing(Player player, CartProperties properties) {
        setEditing(player.getUniqueId(), properties);
    }

    /**
     * Sets the properties of the Minecart the specified player is currently editing
     *
     * @param playerUUID of the player
     * @param properties to set to
     */
    public static void setEditing(UUID playerUUID, CartProperties properties) {
        boolean changed;
        if (properties == null) {
            changed = (editing.remove(playerUUID) != null);
        } else {
            changed = editing.put(playerUUID, properties) != properties;
        }
        if (changed) {
            refreshAttachmentEditor(playerUUID);
        }
    }

    /**
     * Removes the CartProperties for the Minecart specified
     *
     * @param uuid of the Minecart
     */
    public static void remove(UUID uuid) {
        CartProperties prop = properties.remove(uuid);
        if (prop != null) {
            Iterator<Map.Entry<UUID, CartProperties>> iter = editing.entrySet().iterator();
            List<UUID> refreshPlayers = new ArrayList<UUID>(0);
            while (iter.hasNext()) {
                Map.Entry<UUID, CartProperties> e = iter.next();
                if (e.getValue() == prop) {
                    refreshPlayers.add(e.getKey());
                    iter.remove();
                }
            }
            TrainProperties tprop = prop.getTrainProperties();
            if (tprop != null) {
                tprop.remove(prop);
            }

            for (UUID playerUUID : refreshPlayers) {
                refreshAttachmentEditor(playerUUID);
            }
        }
    }

    private static void refreshAttachmentEditor(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            // Refresh attachment editor, if open
            AttachmentEditor editor = MapDisplay.getHeldDisplay(player, AttachmentEditor.class);
            if (editor != null) {
                editor.reload();
            }
        }
    }

    protected static void clearAllCarts() {
        properties.clear();
        editing.clear();
    }

    /**
     * Creates and initializes new cart properties. If existing properties
     * exist for the same UUID, it is overwritten.<br>
     * <br>
     * <b>Warning: must call onConfigurationChanged() on it at some point, or things break!</b>
     * 
     * @param train Train properties that this cart is part of
     * @param config Cart configuration
     * @param uuid The UUID of the Minecart
     * @return new cart properties
     */
    protected static CartProperties createNew(TrainProperties train, ConfigurationNode config, UUID uuid) {
        CartProperties prop = properties.get(uuid);
        if (prop != null) {
            prop.reassign(train, config);
        } else {
            prop = new CartProperties(train, config, uuid);
            properties.put(uuid, prop);
        }
        return prop;
    }

    /**
     * Creates the Cart Properties for the Minecart specified.
     * If by this UUID some are known, these are returned, otherwise new
     * properties with default initial values are created.
     *
     * @param member the properties belong to
     * @return the Cart Properties for the Minecart
     */
    public static CartProperties createForMember(MinecartMember<?> member) {
        UUID uuid = member.getEntity().getUniqueId();
        CartProperties prop = properties.get(uuid);
        if (prop != null) {
            prop.setHolder(member);
            return prop;
        }

        TrainProperties trainProperties = member.isUnloaded()
                ? null : member.getGroup().getProperties();

        prop = new CartProperties(trainProperties, new ConfigurationNode(), uuid);
        properties.put(uuid, prop);
        prop.setHolder(member);
        prop.onConfigurationChanged();
        return prop;
    }
}
