package com.bergerkiller.bukkit.tc.portals;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.mw.Portal;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;

public class MyWorldsPortals extends PortalProvider {

    @Override
    public String getPluginName() {
        return "My_Worlds";
    }

    @Override
    public void init() {
        TrainCarts.plugin.log(Level.INFO, "MyWorlds detected, support for portal sign train teleportation added!");
    }

    @Override
    public PortalDestination getPortalDestination(World world, String portalName) {
        Location destLoc = Portal.getPortalLocation(portalName, world.getName());
        if (destLoc == null) {
            return null;
        }

        Block sign = destLoc.getBlock();
        sign.getChunk(); //load the chunk
        if (!MaterialUtil.ISSIGN.get(sign)) {
            return null;
        }

        SignActionEvent dest_info = new SignActionEvent(sign);
        if (!dest_info.hasRails()) {
            return null;
        }

        return new PortalDestination(dest_info.getRails(), dest_info.getSpawnDirections());
    }

    /**
     * Gets the Portal destination name for a [portal] sign
     * 
     * @param portalLocation sign location
     * @return portal destination name, null if not found or set
     */
    public static String getPortalDestination(Location portalLocation) {
        Portal portal = Portal.get(portalLocation);
        if (portal == null) {
            return null;
        } else {
            return portal.getDestinationName();
        }
    }
}
