package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Contains train or cart property getters and setters
 */
public interface IProperties extends IParsable {

    /**
     * Gets the type name (train/cart) of these properties
     *
     * @return the type name, 'train' or 'cart'
     */
    String getTypeName();

    /**
     * Tests if the tag specified matches one of the tags set
     *
     * @param tag to match
     * @return True if matched, False if not
     */
    boolean matchTag(String tag);

    /**
     * Tests if tags are contained
     *
     * @return True if there are tags, False if not
     */
    boolean hasTags();

    /**
     * Clears all set tags
     */
    void clearTags();

    /**
     * Removes one or more tags
     *
     * @param tags to remove
     */
    void removeTags(String... tags);

    /**
     * Adds one or more tags
     *
     * @param tags to add
     */
    void addTags(String... tags);

    /**
     * Checks whether a given player has ownership, and can alter these
     * properties or use the owner of these properties
     *
     * @param player to check
     * @return True if the player has ownership, False if not
     */
    boolean hasOwnership(Player player);

    /**
     * Checks whether owners are set for (some of) the carts of this train
     *
     * @return True if owners are set, False if not
     */
    boolean hasOwners();

    /**
     * Gets a Set of all player owner names (lower cased)
     *
     * @return owners
     */
    Set<String> getOwners();

    /**
     * Checks whether owner permissions are set for (some of) the carts of this train
     *
     * @return True if owner permissions are set, False if not
     */
    boolean hasOwnerPermissions();

    /**
     * Gets a Set of all permission nodes granting players ownership
     *
     * @return owner permissions
     */
    Set<String> getOwnerPermissions();

    /**
     * Clears all owners set
     */
    void clearOwners();

    /**
     * Clears all permission nodes used to define owners
     */
    void clearOwnerPermissions();

    /**
     * Gets whether these properties can be altered by everyone.
     * This is the case when no owners nor owner permissions are set.
     *
     * @return True if owned by everyone, False if not
     */
    boolean isOwnedByEveryone();

    /**
     * Gets all the tags set
     *
     * @return collection of set tags
     */
    Collection<String> getTags();

    /**
     * Sets all the tags contained
     *
     * @param tags to set to
     */
    void setTags(String... tags);

    /**
     * Gets whether a certain player is an owner set in these properties.
     * This does not use ANY permissions set for the player, it's a mere lookup!
     *
     * @param player to test
     * @return True if it is an Owner, False if not
     */
    boolean isOwner(Player player);

    /**
     * Sets whether nearby items are picked up by the Minecart(s)
     *
     * @param pickup True if items are picked up, False if not
     */
    void setPickup(boolean pickup);

    /**
     * Gets whether it can be publicly accessed
     *
     * @return True or False
     */
    boolean isPublic();

    /**
     * Sets whether it can be publicly accessed
     *
     * @param state to set to
     */
    void setPublic(boolean state);

    /**
     * Gets whether players can enter
     *
     * @return True or False
     */
    boolean getPlayersEnter();

    /**
     * Sets whether players can enter
     *
     * @param state to set to
     */
    void setPlayersEnter(boolean state);

    /**
     * Gets whether players can exit
     *
     * @return True or False
     */
    boolean getPlayersExit();

    /**
     * Sets whether players can exit
     *
     * @param state to set to
     */
    void setPlayersExit(boolean state);

    /**
     * Gets wether train is invincible
     *
     * @return True or False
     */
    boolean isInvincible();

    /**
     * Sets wether the train is invincible
     *
     * @param enabled to set to
     */
    void setInvincible(boolean enabled);

    /**
     * Gets whether items are dropped when this cart is broken
     *
     * @return True if items are dropped, False if not
     */
    boolean getSpawnItemDrops();

    /**
     * Sets whether items are dropped when this cart is broken
     *
     * @param spawnDrops to set to
     */
    void setSpawnItemDrops(boolean spawnDrops);

    /**
     * Clears the destination set
     */
    void clearDestination();

    /**
     * Gets whether a destination is set
     *
     * @return True if a destination is set, False if not
     */
    boolean hasDestination();

    /**
     * Gets the name of the last path finding node that was visited
     *
     * @return last path node name
     */
    String getLastPathNode();

    /**
     * Sets the name of the last path finding node that was visited
     *
     * @param nodeName to set to
     */
    void setLastPathNode(String nodeName);

    /**
     * Gets the destination
     *
     * @return the destination that is set
     */
    String getDestination();

    /**
     * Sets a destination
     *
     * @param destination to set to
     */
    void setDestination(String destination);

    /**
     * Gets a list of destinations, making up the route to travel.
     * When crossing destination signs, the next destination in the list is set.
     * 
     * @return route
     */
    List<String> getDestinationRoute();

    /**
     * Sets the list of destinations making up the route to travel.
     * When crossing destination signs, the next destination in the list is set.
     * 
     * @param route the route to set to
     */
    void setDestinationRoute(List<String> route);

    /**
     * Clears all destinations set in {@link #getDestinationRoute()}
     */
    void clearDestinationRoute();

    /**
     * Adds a destination to {@link #getDestinationRoute()}.
     * Duplicate destinations are allowed!
     * 
     * @param destination to add
     */
    void addDestinationToRoute(String destination);

    /**
     * Removes a destination from {@link #getDestinationRoute()}
     * 
     * @param destination to remove
     */
    void removeDestinationFromRoute(String destination);

    /**
     * Gets the index of the destination in the route of {@link #getDestinationRoute()}.
     * Returns -1 if the current destination is not part of the route, or no
     * route is set at all.
     * 
     * @return index of an item in the route list, -1 if none matches
     */
    int getCurrentRouteDestinationIndex();

    /**
     * Gets the next destination on the route set at {@link #getDestinationRoute()}.
     * If no route is set, or the current destination of this train is not part
     * of the route, then an empty String is returned.
     * 
     * @return next destination on the route, empty String if none exists
     */
    String getNextDestinationOnRoute();

    /**
     * Sets the Enter Message displayed when a player enters
     *
     * @param message to set to
     */
    void setEnterMessage(String message);

    /**
     * Gets a predicted location of the Minecart (and thus Train)
     *
     * @return Block location of the minecart
     */
    BlockLocation getLocation();

    /**
     * Loads the information from the Configuration Node specified
     *
     * @param node to use
     */
    void load(ConfigurationNode node);

    /**
     * Saves the information to the Configuration Node specified as a means of default<br>
     * The full information is written
     *
     * @param node to save to
     */
    void saveAsDefault(ConfigurationNode node);

    /**
     * Saves the information to the Configuration Node specified as a means of state saving<br>
     * Only changed information is written
     *
     * @param node to save to
     */
    void save(ConfigurationNode node);

    /**
     * Gets the owner of these properties
     *
     * @return properties holder
     */
    IPropertiesHolder getHolder();

    /**
     * Restores the Train of these properties if it is not already loaded.
     * After this call a holder is available, if there is not, then the train or cart is gone.
     *
     * @return True if the train or cart was really restored, False if it got lost
     */
    boolean restore();

    /**
     * Gets whether these properties have a valid (loaded) owner
     *
     * @return True if an owner (holder) was found, False if not
     */
    boolean hasHolder();
}
