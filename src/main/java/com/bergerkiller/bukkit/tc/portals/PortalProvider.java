package com.bergerkiller.bukkit.tc.portals;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Some source of portal information
 */
public abstract class PortalProvider {

    /**
     * Called when the provider becomes available
     */
    public abstract void init(Plugin plugin);

    /**
     * Gets the destination rails and suggested spawn directions for a particular portal by name
     * 
     * @param world from which is teleported as a hint for the portal name
     * @param portalName to teleport to
     * @return portal destination information, or null if not found
     */
    public abstract PortalDestination getPortalDestination(World world, String portalName);
}
