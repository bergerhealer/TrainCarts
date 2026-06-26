package com.bergerkiller.bukkit.tc.portals.plugins;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.World;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.portals.PortalDestination;
import com.bergerkiller.bukkit.tc.portals.PortalProvider;

/**
 * Built-in TrainCarts portal provider that resolves standard TrainCarts teleport signs.
 * It does not provide portal-by-name lookups (getPortalDestination always returns null).
 */
public class TrainCartsPortalProvider implements PortalProvider {

    @Override
    public PortalDestination getPortalDestination(World world, String portalName, MinecartGroup group) {
        // TrainCarts default provider does not resolve portals by name
        return null;
    }

    @Override
    public String getPreferredDestination(SignActionEvent event) {
        if (event.getHeader().isValid() && event.isType("teleport")) {
            return event.getLine(2);
        }
        return null;
    }
}
