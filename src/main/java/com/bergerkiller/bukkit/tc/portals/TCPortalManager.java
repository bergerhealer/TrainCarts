package com.bergerkiller.bukkit.tc.portals;

import java.util.HashMap;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.portals.plugins.TrainCartsPortalProvider;
import org.bukkit.World;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

/**
 * Manages all plugins that provide portal teleportation logic for use by TrainCarts.
 * Plugins can be registered with a {@link PortalProvider} which will be used to find the rails
 * and directions to teleport the train at.
 */
public class TCPortalManager {
    private static final HashMap<String, PortalProvider> portalProviders = new HashMap<String, PortalProvider>();
    static {
        // Register built-in TrainCarts portal provider which resolves standard [teleport] signs
        addPortalSupport("TrainCarts", new TrainCartsPortalProvider());
    }

    /**
     * Registers a portal provider and the name of the plugin that provides it.
     * When the plugin is detected, the provider is automatically initialized.
     * 
     * @param pluginName Name of the plugin providing portal support
     * @param provider Portal teleportation provider
     */
    public static void addPortalSupport(String pluginName, PortalProvider provider) {
        portalProviders.put(pluginName, provider);
    }

    /**
     * Un-registers a portal provider of a plugin
     *
     * @param pluginName Name of the plugin that was providing portal support
     */
    public static void removePortalSupport(String pluginName) {
        portalProviders.remove(pluginName);
    }

    /**
     * Checks whether a particular portal teleportation providing plugin is enabled
     * 
     * @param pluginName to check
     * @return True if available
     */
    public static boolean isAvailable(String pluginName) {
        return portalProviders.containsKey(pluginName);
    }

    /**
     * Gets the portal destination information for a particular portal name
     *
     * @param world from which to look (portal search hint)
     * @param portalName to find
     * @return destination information
     * @deprecated Please call the overload passing a MinecartGroup instead. If you really don't have one, pass <i>null</i>
     */
    @Deprecated
    public static PortalDestination getPortalDestination(World world, String portalName) {
        return getPortalDestination(world, portalName, null);
    }

    /**
     * Gets the portal destination information for a particular portal name
     * 
     * @param world from which to look (portal search hint)
     * @param portalName to find
     * @param group The MinecartGroup that is about to teleport and is looking for the destination.
     *              Use null to not provide it as a hint, and it will try to find a best guess destination.
     * @return destination information
     */
    public static PortalDestination getPortalDestination(World world, String portalName, MinecartGroup group) {
        PortalDestination dest = null;
        for (PortalProvider provider : portalProviders.values()) {
            dest = provider.getPortalDestination(world, portalName, group);
            if (dest != null) {
                break;
            }
        }
        return dest;
    }

    /**
     * Let providers optionally resolve a preferred destination name from a sign event.
     * Iterates providers in insertion order and returns the first non-null preferred destination.
     *
     * Note: provider exceptions are not swallowed here and will propagate to the caller.
     *
     * @param event sign action event
     * @return preferred destination name, or null if none provided by any provider
     */
    public static String getPreferredDestination(SignActionEvent event) {
        for (PortalProvider provider : portalProviders.values()) {
            String pref = provider.getPreferredDestination(event);
            if (pref != null) {
                return pref;
            }
        }
        return null;
    }
}
