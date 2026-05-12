package com.bergerkiller.bukkit.tc.portals;

import org.bukkit.World;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

/**
 * Some source of portal information
 */
public interface PortalProvider {

    /**
     * Gets the destination rails and suggested spawn directions for a particular portal by name
     *
     * @param world from which is teleported as a hint for the portal name
     * @param portalName to teleport to
     * @return portal destination information, or null if not found
     */
    PortalDestination getPortalDestination(World world, String portalName);

    /**
     * Optionally retrieve a preferred destination name from a sign event.
     * Providers can override this to provide sign-specific resolution logic.
     * The default implementation returns null.
     *
     * @param event sign action event
     * @return preferred portal destination name, or null if provider does not handle it
     */
    default String getPreferredDestination(SignActionEvent event) {
        return null;
    }
}
