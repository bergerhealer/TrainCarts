package com.bergerkiller.bukkit.tc.properties;

import java.util.Collection;
import java.util.Set;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * Contains train or cart property getters and setters
 */
public interface IProperties extends IParsable {

	/**
	 * Gets the type name (train/cart) of these properties
	 * 
	 * @return the type name, 'train' or 'cart'
	 */
	public String getTypeName();

	/**
	 * Tests if the tag specified matches one of the tags set
	 * 
	 * @param tag to match
	 * @return True if matched, False if not
	 */
	public boolean matchTag(String tag);

	/**
	 * Tests if tags are contained
	 * 
	 * @return True if there are tags, False if not
	 */
	public boolean hasTags();

	/**
	 * Clears all set tags
	 */
	public void clearTags();

	/**
	 * Removes one or more tags
	 * 
	 * @param tags to remove
	 */
	public void removeTags(String... tag);
	
	/**
	 * Adds one or more tags
	 * 
	 * @param tags to add
	 */
	public void addTags(String... tags);

	/**
	 * Sets all the tags contained
	 * 
	 * @param tags to set to
	 */
	public void setTags(String... tags);

	/**
	 * Checks whether a given player has ownership, and can alter these
	 * properties or use the owner of these properties
	 * 
	 * @param player to check
	 * @return True if the player has ownership, False if not
	 */
	public boolean hasOwnership(Player player);

	/**
	 * Checks whether owners are set for (some of) the carts of this train
	 * 
	 * @return True if owners are set, False if not
	 */
	public boolean hasOwners();

	/**
	 * Gets a Set of all player owner names (lower cased)
	 * 
	 * @return owners
	 */
	public Set<String> getOwners();

	/**
	 * Checks whether owner permissions are set for (some of) the carts of this train
	 * 
	 * @return True if owner permissions are set, False if not
	 */
	public boolean hasOwnerPermissions();

	/**
	 * Gets a Set of all permission nodes granting players ownership
	 * 
	 * @return owner permissions
	 */
	public Set<String> getOwnerPermissions();

	/**
	 * Clears all owners set
	 */
	public void clearOwners();

	/**
	 * Clears all permission nodes used to define owners
	 */
	public void clearOwnerPermissions();

	/**
	 * Gets whether these properties can be altered by everyone.
	 * This is the case when no owners nor owner permissions are set.
	 * 
	 * @return True if owned by everyone, False if not
	 */
	public boolean isOwnedByEveryone();

	/**
	 * Gets all the tags set
	 * 
	 * @return collection of set tags
	 */
	public Collection<String> getTags();

	/**
	 * Gets whether a certain player is an owner set in these properties.
	 * This does not use ANY permissions set for the player, it's a mere lookup!
	 * 
	 * @param player to test
	 * @return True if it is an Owner, False if not
	 */
	public boolean isOwner(Player player);

	/**
	 * Sets whether nearby items are picked up by the Minecart(s)
	 * 
	 * @param pickup True if items are picked up, False if not
	 */
	public void setPickup(boolean pickup);

	/**
	 * Gets whether it can be publicly accessed
	 * 
	 * @return True or False
	 */
	public boolean isPublic();

	/**
	 * Sets whether it can be publicly accessed
	 * 
	 * @param state to set to
	 */
	public void setPublic(boolean state);

	/**
	 * Gets whether players can enter
	 * 
	 * @return True or False
	 */
	public boolean getPlayersEnter();

	/**
	 * Sets whether players can enter
	 * 
	 * @param state to set to
	 */
	public void setPlayersEnter(boolean state);

	/**
	 * Gets whether players can exit
	 * 
	 * @return True or False
	 */
	public boolean getPlayersExit();

	/**
	 * Sets whether players can exit
	 * 
	 * @param state to set to
	 */
	public void setPlayersExit(boolean state);
	
	/**
	 * Gets wether train is invincible
	 * 
	 * @return True or False
	 */
	public boolean isInvincible();
	
	/**
	 * Sets wether the train is invincible
	 * 
	 * @param enabled to set to
	 */
	public void setInvincible(boolean enabled);

	/**
	 * Sets whether items are dropped when this cart is broken
	 * 
	 * @param spawnDrops to set to
	 */
	public void setSpawnItemDrops(boolean spawnDrops);

	/**
	 * Gets whether items are dropped when this cart is broken
	 * 
	 * @return True if items are dropped, False if not
	 */
	public boolean getSpawnItemDrops();

	/**
	 * Clears the destination set
	 */
	public void clearDestination();

	/**
	 * Gets whether a destination is set
	 * 
	 * @return True if a destination is set, False if not
	 */
	public boolean hasDestination();

	/**
	 * Sets a destination
	 * 
	 * @param destination to set to
	 */
	public void setDestination(String destination);

	/**
	 * Gets the name of the last path finding node that was visited
	 * 
	 * @return last path node name
	 */
	public String getLastPathNode();

	/**
	 * Sets the name of the last path finding node that was visited
	 * 
	 * @param nodeName to set to
	 */
	public void setLastPathNode(String nodeName);

	/**
	 * Gets the destination
	 * 
	 * @return the destination that is set
	 */
	public String getDestination();

	/**
	 * Sets the Enter Message displayed when a player enters
	 * 
	 * @param message to set to
	 */
	public void setEnterMessage(String message);

	/**
	 * Gets a predicted location of the Minecart (and thus Train)
	 * 
	 * @return Block location of the minecart
	 */
	public BlockLocation getLocation();

	/**
	 * Loads the information from the Configuration Node specified
	 * 
	 * @param node to use
	 */
	public void load(ConfigurationNode node);

	/**
	 * Saves the information to the Configuration Node specified as a means of default<br>
	 * The full information is written
	 * 
	 * @param node to save to
	 */
	public void saveAsDefault(ConfigurationNode node);

	/**
	 * Saves the information to the Configuration Node specified as a means of state saving<br>
	 * Only changed information is written
	 * 
	 * @param node to save to
	 */
	public void save(ConfigurationNode node);

	/**
	 * Gets the owner of these properties
	 * 
	 * @return properties holder
	 */
	public IPropertiesHolder getHolder();

	/**
	 * Restores the Train of these properties if it is not already loaded.
	 * After this call a holder is available, if there is not, then the train or cart is gone.
	 * 
	 * @return True if the train or cart was really restored, False if it got lost
	 */
	public boolean restore();

	/**
	 * Gets whether these properties have a valid (loaded) owner
	 * 
	 * @return True if an owner (holder) was found, False if not
	 */
	public boolean hasHolder();
}
