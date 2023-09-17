package com.bergerkiller.bukkit.tc.portals.plugins;

import java.util.logging.Level;

import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.mw.Portal;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.portals.PortalDestination;
import com.bergerkiller.bukkit.tc.portals.PortalProvider;

/**
 * Handles portal destinations for My Worlds [portal] signs
 */
public class MyWorldsPortalsProvider extends PortalProvider {

    public MyWorldsPortalsProvider(TrainCarts traincarts, Plugin plugin) {
        traincarts.log(Level.INFO, "MyWorlds detected, support for portal sign train teleportation added!");
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

        //Note: MyWorlds only supports front text side to put [portal] signs
        SignActionEvent dest_info = new SignActionEvent(RailLookup.TrackedSign.forRealSign(sign, true, null));
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
