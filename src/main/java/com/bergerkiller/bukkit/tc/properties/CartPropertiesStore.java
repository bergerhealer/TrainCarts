package com.bergerkiller.bukkit.tc.properties;

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
            if (tprop != null && tprop.contains(prop)) {
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
     * Gets the Cart Properties of the Minecart specified<br>
     * Constructs a new entry if none is contained.
     *
     * @param uuid  of the Minecart
     * @param train to link the Minecart to if not contained
     * @return The CartProperties for the Minecart
     */
    public static CartProperties get(UUID uuid, TrainProperties train) {
        return properties.computeIfAbsent(uuid, key -> new CartProperties(key, train));
    }

    /**
     * Gets the Cart Properties of the Minecart specified
     *
     * @param member the properties belong to
     * @return the Cart Properties for the Minecart
     */
    public static CartProperties get(MinecartMember<?> member) {
        CartProperties props = get(member.getEntity().getUniqueId(), member.isUnloaded() ? null : member.getGroup().getProperties());
        props.setHolder(member);
        return props;
    }
}
