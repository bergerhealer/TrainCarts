package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.properties.api.IProperty;
import com.bergerkiller.bukkit.tc.properties.api.IPropertyRegistry;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseResult;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Contains train or cart property getters and setters
 */
public interface IProperties extends IParsable {

    /**
     * Gets a single property stored in this collection of properties
     * 
     * @param <T> Type of value the property has
     * @param property The property to read
     * @return Value of the property
     */
    <T> T get(IProperty<T> property);

    /**
     * Updates a single property stored in this collection of properties
     * 
     * @param <T> Type of value the property has
     * @param property The property to update
     * @param value The new value to assign for this property
     */
    <T> void set(IProperty<T> property, T value);

    /**
     * Uses a function to modify the value of a property in this
     * collection of properties
     * 
     * @param <T> Type of value the property has
     * @param property The property to update
     * @param operation The operation to perform on the current value
     * @return updated value, result of calling the operation function
     */
    default <T> T update(IProperty<T> property, Function<T, T> operation) {
        T old_value = get(property);
        T new_value = operation.apply(old_value);
        if (old_value != new_value) {
            set(property, new_value);
        }
        return new_value;
    }

    /**
     * Parses the property by name and attempts to parse the property. If successful,
     * applies the parsed value to these properties.
     * 
     * @param <T> Type of value the property has
     * @param name Name of the property to parse
     * @param input Input value to parse
     * @return Result of parsing, if not successful, the property will not have been set.
     *         Is never null, if parsing fails the {@link PropertyParseResult#getReason()}
     *         can be checked.
     */
    default PropertyParseResult<?> parseAndSet(String name, String input) {
        return IPropertyRegistry.instance().parseAndSet(this, name, input);
    }

    /**
     * Gets the YAML configuration that stores all these
     * properties. The returned value can be directly modified,
     * and any changes will be reflected in the train.<br>
     * <br>
     * To prevent de-synchronization, please use {@link #set(IProperty, Object)}
     * or other setters to make modifications.
     * 
     * @return configuration
     */
    ConfigurationNode getConfig();

    /**
     * Loads the information from the Configuration Node specified.
     * All previous properties are lost, and everything is reloaded.
     *
     * @param node Configuration node to load from
     */
    void load(ConfigurationNode node);

    /**
     * Copies the up-to-date configuration of {@link #getConfig()}
     * to the configuration node specified. This configuration
     * only stores the changed properties.
     *
     * @param node The configuration to save to
     */
    void save(ConfigurationNode node);

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
     * @return owners (unmodifiable and immutable)
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
     * @return owner permissions (unmodifiable and immutable)
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
     * Gets whether only owners of the train or cart can enter it (true),
     * or that anyone can regardless of ownership (false).
     *
     * @return True when only owners can enter, or false if anyone can
     */
    boolean getCanOnlyOwnersEnter();

    /**
     * Sets whether only owners of the train or cart can enter it (true),
     * or that anyone can regardless of ownership (false).
     *
     * @param state Whether only owners can enter
     */
    void setCanOnlyOwnersEnter(boolean state);

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
