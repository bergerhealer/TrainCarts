package com.bergerkiller.bukkit.tc.properties.api;

import java.util.List;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * Registers and tracks all possible cart and train properties.
 * All built-in properties are registered, other plugins can register
 * their own properties as well.
 */
public interface IPropertyRegistry {

    /**
     * Looks up a previously registered property by name.
     * The names returned by {@link IProperty#getNames()}
     * is used for this lookup. Returns null if a property
     * by this name does not exist.<br>
     * <br>
     * This lookup is case-insensitive and ignores empty space
     * around the property name.
     * 
     * @param name Name of the property to find
     * @return property matching this name, null if none is registered
     */
    IProperty<Object> find(String name);

    /**
     * Gets an unmodifiable list of all registered properties
     * 
     * @return unmodifiable list of all properties
     */
    List<IProperty<Object>> all();

    /**
     * Registers a new property
     * 
     * @param property The property to register
     */
    void register(IProperty<?> property);

    /**
     * Undoes previous registration of a property. This makes the
     * property unavailable again. Configuration data that stores
     * this property is not deleted.<br>
     * <br>
     * <b>Must be the exact same instance as used in {@link #register(IProperty)}</b>
     * 
     * @param property The property to unregister
     */
    void unregister(IProperty<?> property);

    /**
     * Registers all properties statically defined in a class, or all the
     * enum constants of an enumeration.
     * 
     * @param propertiesClass Class holding the static properties or enum constants
     * @see #register(IProperty)
     */
    default void registerAll(Class<?> propertiesClass) {
        for (IProperty<?> property : CommonUtil.getClassConstants(propertiesClass, IProperty.class)) {
            register(property);
        }
    }

    /**
     * Undoes previous registration of all properties statically defined in a class, or all the
     * enum constants of an enumeration.
     * 
     * @param propertiesClass Class holding the static properties or enum constants
     * @see #unregister(IProperty)
     */
    default void unregisterAll(Class<?> propertiesClass) {
        for (IProperty<?> property : CommonUtil.getClassConstants(propertiesClass, IProperty.class)) {
            unregister(property);
        }
    }

    /**
     * Convenience function, same as {@link TrainCarts#getPropertyRegistry()}.
     * 
     * @return property registry instance
     * @see TrainCarts#getPropertyRegistry()
     */
    public static IPropertyRegistry instance() {
        return TrainCarts.plugin.getPropertyRegistry();
    }
}
