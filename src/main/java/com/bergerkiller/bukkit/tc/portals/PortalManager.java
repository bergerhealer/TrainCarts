package com.bergerkiller.bukkit.tc.portals;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.World;

public class PortalManager {
    private static final HashMap<String, PortalProvider> portalProviders = new HashMap<String, PortalProvider>();
    private static final ArrayList<PortalProvider> supportedProviders = new ArrayList<PortalProvider>();

    static {
        supportedProviders.add(new MyWorldsPortals());
    }

    public static boolean isAvailable(String pluginName) {
        return portalProviders.containsKey(pluginName);
    }

    public static void updateProviders(String pluginName, boolean enabled) {
        if (!enabled) {
            portalProviders.remove(pluginName);
            return;
        }
        if (portalProviders.containsKey(pluginName)) {
            return;
        }

        // Detect new plugins that add support for portal destinations
        for (PortalProvider provider : supportedProviders) {
            if (provider.getPluginName().equals(pluginName)) {
                portalProviders.put(pluginName, provider);
                provider.init();
            }
        }
    }

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
