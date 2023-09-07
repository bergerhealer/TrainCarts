package com.bergerkiller.bukkit.tc.portals;

import java.util.HashMap;

import org.bukkit.World;

/**
 * Manages all plugins that provide portal teleportation logic for use by TrainCarts.
 * Plugins can be registered with a {@link PortalProvider} which will be used to find the rails
 * and directions to teleport the train at.
 */
public class TCPortalManager {
    private static final HashMap<String, PortalProvider> portalProviders = new HashMap<String, PortalProvider>();

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
     */
    public static PortalDestination getPortalDestination(World world, String portalName) {
        PortalDestination dest = null;
        for (PortalProvider provider : portalProviders.values()) {
            dest = provider.getPortalDestination(world, portalName);
            if (dest != null) {
                break;
            }
        }
        return dest;
    }
}
