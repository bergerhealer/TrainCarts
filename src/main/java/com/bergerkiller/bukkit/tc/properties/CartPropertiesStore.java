package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import com.bergerkiller.bukkit.tc.controller.global.TrainCartsPlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

/**
 * Stores all the Cart Properties available by Minecart UUID
 */
public class CartPropertiesStore {
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
     * @see TrainCarts#getPlayer(Player)
     * @see TrainCartsPlayer#getEditedCart()
     */
    public static CartProperties getEditing(Player player) {
        return TrainCarts.plugin.getPlayer(player).getEditedCart();
    }

    /**
     * Gets the properties of the Minecart the specified player is currently editing
     *
     * @param playerUUID of the player
     * @return the Cart Properties the player is editing
     * @see TrainCarts#getPlayer(UUID)
     * @see TrainCartsPlayer#getEditedCart()
     */
    public static CartProperties getEditing(UUID playerUUID) {
        return TrainCarts.plugin.getPlayer(playerUUID).getEditedCart();
    }

    /**
     * Sets the properties of the Minecart the specified player is currently editing
     *
     * @param player that is editing
     * @param properties to set to
     * @see TrainCarts#getPlayer(Player)
     * @see TrainCartsPlayer#editCart(CartProperties)
     */
    public static void setEditing(Player player, CartProperties properties) {
        TrainCarts.plugin.getPlayer(player).editCart(properties);
    }

    /**
     * Sets the properties of the Minecart the specified player is currently editing
     *
     * @param playerUUID of the player
     * @param properties to set to
     * @see TrainCarts#getPlayer(UUID)
     * @see TrainCartsPlayer#editCart(CartProperties) 
     */
    public static void setEditing(UUID playerUUID, CartProperties properties) {
        TrainCarts.plugin.getPlayer(playerUUID).editCart(properties);
    }

    /**
     * Removes the CartProperties for the Minecart specified
     *
     * @param uuid of the Minecart
     */
    public static void remove(UUID uuid) {
        CartProperties prop = properties.remove(uuid);
        if (prop != null) {
            prop.removed = true;
            TrainProperties tprop = prop.getTrainProperties();
            if (tprop != null) {
                tprop.remove(prop);
            }
        }
    }

    protected static void clearAllCarts() {
        properties.values().forEach(p -> p.removed = true);
        properties.clear();
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
            prop = new CartProperties(TrainCarts.plugin, train, config, uuid);
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

        prop = new CartProperties(member.getTrainCarts(), trainProperties, new ConfigurationNode(), uuid);
        properties.put(uuid, prop);
        prop.setHolder(member);
        prop.onConfigurationChanged();
        return prop;
    }
}
