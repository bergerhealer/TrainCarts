package com.bergerkiller.bukkit.tc.portals;

import java.util.HashMap;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.portals.plugins.MultiversePortalsProvider;
import com.bergerkiller.bukkit.tc.portals.plugins.MyWorldsPortalsProvider;

/**
 * Manages all plugins that provide portal teleportation logic for use by TrainCarts.
 * Plugins can be registered with a {@link PortalProvider} which will be used to find the rails
 * and directions to teleport the train at.
 */
public class TCPortalManager {
    private static final HashMap<String, Class<? extends PortalProvider>> supportedProviders = new HashMap<String, Class<? extends PortalProvider>>();
    private static final HashMap<String, PortalProvider> portalProviders = new HashMap<String, PortalProvider>();

    static {
        addPortalSupport("My_Worlds", MyWorldsPortalsProvider.class);
        addPortalSupport("Multiverse-Portals", MultiversePortalsProvider.class);
    }

    /**
     * Registers a portal provider and the name of the plugin that provides it.
     * When the plugin is detected, the provider is automatically initialized.
     * 
     * @param pluginName of the plugin providing portal support
     * @param providerClass for handling portal logic
     */
    public static void addPortalSupport(String pluginName, Class<? extends PortalProvider> providerClass) {
        supportedProviders.put(pluginName, providerClass);
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
     * Internal use only: handles plugins enabling/disabling
     * 
     * @param pluginName
     * @param plugin
     * @param enabled
     */
    public static void updateProviders(String pluginName, Plugin plugin, boolean enabled) {
        if (!enabled) {
            portalProviders.remove(pluginName);
            return;
        }
        if (portalProviders.containsKey(pluginName)) {
            return;
        }

        // Detect new plugins that add support for portal destinations
        Class<? extends PortalProvider> providerClass = supportedProviders.get(pluginName);
        if (providerClass != null) {
            PortalProvider provider;
            try {
                provider = providerClass.newInstance();
                portalProviders.put(pluginName, provider);
                provider.init(plugin);
            } catch (Throwable t) {
                portalProviders.remove(pluginName);
                TrainCarts.plugin.getModuleLogger("Portals").log(Level.WARNING, 
                        "Failed to add teleport portal support for plugin " + pluginName, t);
            }
        }
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
