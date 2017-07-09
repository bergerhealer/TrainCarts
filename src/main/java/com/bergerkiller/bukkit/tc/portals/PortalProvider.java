package com.bergerkiller.bukkit.tc.portals;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

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

    /**
     * Looks for suitable rails inside or against a block region, selecting the destination if found.
     * The direction is used for the spawn direction and to filter the rails to spawn at.
     * 
     * @param regionMin minimum coordinates of the region
     * @param regionMax maximum coordinates of the region
     * @param direction preferred
     * @return destination, null if not found
     */
    protected PortalDestination findDestination(Block regionMin, Block regionMax, Direction direction) {
        // Transform the Direction into a BlockFace direction vector
        int dx = regionMax.getX() - regionMin.getX();
        int dy = regionMax.getY() - regionMin.getY();
        int dz = regionMax.getZ() - regionMin.getZ();
        BlockFace portalFacing;
        if (dx > dy && dz > dy) {
            portalFacing = BlockFace.UP;
        } else if (dx > dz) {
            portalFacing = BlockFace.SOUTH;
        } else {
            portalFacing = BlockFace.EAST;
        }
        BlockFace spawnDirection = direction.getDirection(portalFacing);
        PortalDestination dest = null;

        // Try inside the portal
        for (int y = regionMin.getY(); y <= regionMax.getY(); y++) {
            for (int x = regionMin.getX(); x <= regionMax.getX(); x++) {
                for (int z = regionMin.getZ(); z <= regionMax.getZ(); z++) {
                    Block block = regionMin.getWorld().getBlockAt(x, y, z);
                    dest = findRailDestination(block, spawnDirection);
                    if (dest != null) {
                        return dest;
                    }
                }
            }
        }

        // Try directly against the portal, facing the suggested spawn direction
        dest = findAgainst(regionMin, regionMax, spawnDirection);
        if (dest != null) {
            return dest;
        }

        // Try the opposite direction
        dest = findAgainst(regionMin, regionMax, spawnDirection.getOppositeFace());
        if (dest != null) {
            return dest;
        }

        // Failed.
        return null;
    }

    private PortalDestination findAgainst(Block regionMin, Block regionMax, BlockFace spawnDirection) {
        int x1 = regionMin.getX();
        int y1 = regionMin.getY();
        int z1 = regionMin.getZ();
        int x2 = regionMax.getX();
        int y2 = regionMax.getY();
        int z2 = regionMax.getZ();
        if (spawnDirection.getModY() != 0) {
            y1 = y2 = ((spawnDirection.getModY() > 0) ? regionMax.getY() : regionMin.getY()) + spawnDirection.getModY();
        } else if (spawnDirection.getModX() != 0) {
            x1 = x2 = ((spawnDirection.getModX() > 0) ? regionMax.getX() : regionMin.getX()) + spawnDirection.getModX();
        } else {
            z1 = z2 = ((spawnDirection.getModZ() > 0) ? regionMax.getZ() : regionMin.getZ()) + spawnDirection.getModZ();
        }
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    Block block = regionMin.getWorld().getBlockAt(x, y, z);
                    PortalDestination dest = findRailDestination(block, spawnDirection);
                    if (dest != null) {
                        return dest;
                    }
                }
            }
        }
        return null;
    }

    private PortalDestination findRailDestination(Block rails, BlockFace direction) {
        RailType railType = RailType.getType(rails);
        if (railType != RailType.NONE) {
            return new PortalDestination(rails, new BlockFace[] {direction});
        } else {
            return null;
        }
    }
}
