package com.bergerkiller.bukkit.tc.properties;

import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Stores all the Cart Properties available by Minecart UUID
 */
public class CartPropertiesStore {
	private static HashMap<UUID, CartProperties> properties = new HashMap<UUID, CartProperties>();
	protected static HashMap<String, CartProperties> editing = new HashMap<String, CartProperties>();

	/**
	 * Gets the properties of the Minecart the specified player is currently editing
	 * 
	 * @param player
	 * @return the Cart Properties the player is editing
	 */
	public static CartProperties getEditing(Player player) {
		return getEditing(player.getName());
	}

	/**
	 * Gets the properties of the Minecart the specified player is currently editing
	 * 
	 * @param playername of the player
	 * @return the Cart Properties the player is editing
	 */
	public static CartProperties getEditing(String playername) {
		return editing.get(playername.toLowerCase());
	}

	/**
	 * Sets the properties of the Minecart the specified player is currently editing
	 * 
	 * @param player
	 * @param properties to set to
	 */
	public static void setEditing(Player player, CartProperties properties) {
		setEditing(player.getName(), properties);
	}

	/**
	 * Sets the properties of the Minecart the specified player is currently editing
	 * 
	 * @param playername of the player
	 * @param properties to set to
	 */
	public static void setEditing(String playername, CartProperties properties) {
		if (properties == null) {
			editing.remove(playername.toLowerCase());
		} else {
			editing.put(playername.toLowerCase(), properties);
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
			Iterator<CartProperties> iter = editing.values().iterator();
			while (iter.hasNext()) {
				if (iter.next() == prop) {
					iter.remove();
				}
			}
			TrainProperties tprop = prop.getTrainProperties();
			if (tprop.contains(prop)) {
				tprop.remove(prop);
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
	 * @param uuid of the Minecart
	 * @param train to link the Minecart to if not contained
	 * @return The CartProperties for the Minecart
	 */
	public static CartProperties get(UUID uuid, TrainProperties train) {
		CartProperties prop = properties.get(uuid);
		if (prop == null) {
			prop = new CartProperties(uuid, train);
			properties.put(uuid, prop);
		}
		return prop;
	}

	/**
	 * Gets the Cart Properties of the Minecart specified
	 * 
	 * @param member the properties belong to
	 * @return the Cart Properties for the Minecart
	 */
	public static CartProperties get(MinecartMember<?> member) {
		return get(member.getEntity().getUniqueId(), member.isUnloaded() ? null : member.getGroup().getProperties());
	}
}
