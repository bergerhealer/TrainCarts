package com.bergerkiller.bukkit.tc.portals.plugins;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.conversion.type.WrapperConversion;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.portals.PortalDestination;
import com.bergerkiller.bukkit.tc.portals.PortalProvider;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.portals.MVPortal;
import org.mvplugins.multiverse.portals.MultiversePortalsApi;
import org.mvplugins.multiverse.portals.PortalLocation;

/**
 * Handles teleportation to Multiverse-Portals portals.
 * This is for the recent version of multiverse-portals.
 */
public class MultiversePortalsProvider implements PortalProvider {
    private final MultiversePortalsApi api;

    public MultiversePortalsProvider(TrainCarts traincarts, Plugin plugin) {
        this.api = Bukkit.getServicesManager().load(MultiversePortalsApi.class);
        traincarts.log(Level.INFO, "Multiverse Portals detected, trains can be teleported to MV Portals");
    }

    @Override
    public PortalDestination getPortalDestination(World world, String portalName) {
        // Discover the portal and direction to spawn into
        MVPortal portal = this.api.getPortalManager().getPortal(portalName);
        Direction direction = Direction.NONE;
        if (portal == null) {
            int dirIdx = portalName.lastIndexOf(':');
            if (dirIdx == -1) {
                return null;
            }
            direction = Direction.parse(portalName.substring(dirIdx + 1));
            portalName = portalName.substring(0, dirIdx);
            portal = this.api.getPortalManager().getPortal(portalName);
            if (portal == null) {
                return null;
            }
        }

        // Find any rails inside the portal itself
        PortalLocation portalPos = portal.getPortalLocation();
        LoadedMultiverseWorld loadedMultiverseWorld = portalPos.getMVWorld();
        if (loadedMultiverseWorld == null) {
            return null; // world not loaded
        }

        World portalWorld = loadedMultiverseWorld.getBukkitWorld().getOrElse((World) null);
        if (portalWorld == null) {
            return null; // world not loaded
        }
        Block minBlock = WrapperConversion.toIntVector3FromVector(portalPos.getMinimum()).toBlock(portalWorld);
        Block maxBlock = WrapperConversion.toIntVector3FromVector(portalPos.getMaximum()).toBlock(portalWorld);
        return PortalDestination.findDestination(minBlock, maxBlock, direction);
    }
}
