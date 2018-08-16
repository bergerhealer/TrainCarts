package com.bergerkiller.bukkit.tc.portals;

import java.util.HashSet;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

public class PortalDestination {
    private final Block railsBlock;
    private final BlockFace[] directions;

    public PortalDestination(Block railsBlock, BlockFace[] directions) {
        this.railsBlock = railsBlock;
        this.directions = directions;
    }

    public Block getRailsBlock() {
        return this.railsBlock;
    }

    public BlockFace[] getDirections() {
        return this.directions;
    }

    public boolean hasDirections() {
        return this.directions != null && this.directions.length > 0;
    }

    @Override
    public String toString() {
        String s = "{";
        s += "world=" + railsBlock.getWorld().getName();
        s += ", x=" + railsBlock.getX();
        s += ", y=" + railsBlock.getY();
        s += ", z=" + railsBlock.getZ();
        s += ", dirs=[";
        boolean f = true;
        for (BlockFace dir : directions) {
            if (f) {
                f = false;
            } else {
                s += ", ";
            }
            s += dir.name();
        }
        s += "]}";
        return s;
    }

    /**
     * Looks for suitable rails nearby a nether portal, checking all portal blocks for closeby rails
     * 
     * @param portalBlock to start looking from
     * @param direction to spawn into (preferred)
     * @return rail destination (null if not found)
     */
    public static PortalDestination findDestinationAtNetherPortal(Block portalBlock, Direction direction) {
        if (portalBlock == null) {
            return null;
        }

        // Collect all attached portal blocks
        HashSet<IntVector3> blocks = new HashSet<IntVector3>();
        IntVector3 pos = new IntVector3(portalBlock.getX(), portalBlock.getY(), portalBlock.getZ());
        World world = portalBlock.getWorld();
        discoverPortals(blocks, world, pos);

        // Fallback when no portals are found
        if (blocks.isEmpty()) {
            return findDestination(portalBlock, portalBlock, direction);
        }

        // Calculate min/max bounds and search in and around that area
        int minX = pos.x;
        int minY = pos.y;
        int minZ = pos.z;
        int maxX = pos.x;
        int maxY = pos.y;
        int maxZ = pos.z;
        for (IntVector3 block : blocks) {
            if (block.x < minX) minX = block.x;
            if (block.x > maxX) maxX = block.x;
            if (block.y < minY) minY = block.y;
            if (block.y > maxY) maxY = block.y;
            if (block.z < minZ) minZ = block.z;
            if (block.z > maxZ) maxZ = block.z;
        }
        return findDestination(
                world.getBlockAt(minX, minY, minZ),
                world.getBlockAt(maxX, maxY, maxZ),
                direction);
    }

    private static void discoverPortals(HashSet<IntVector3> blocks, World world, IntVector3 pos) {
        if (WorldUtil.getBlockData(world, pos).isType(Material.PORTAL) && blocks.add(pos)) {
            for (BlockFace face : FaceUtil.BLOCK_SIDES) {
                discoverPortals(blocks, world, pos.add(face));
            }
        }
    }

    /**
     * Looks for suitable rails inside or against a block region, selecting the destination if found.
     * The direction is used for the spawn direction and to filter the rails to spawn at.
     * 
     * @param regionMin minimum coordinates of the region
     * @param regionMax maximum coordinates of the region
     * @param direction preferred
     * @return destination, null if not found
     */
    public static PortalDestination findDestination(Block regionMin, Block regionMax, Direction direction) {
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

        // Try into the facing direction of the portal
        dest = findAgainst(regionMin, regionMax, portalFacing);
        if (dest != null) {
            return dest;
        }

        // Try into the opposite facing direction of the portal
        dest = findAgainst(regionMin, regionMax, portalFacing.getOppositeFace());
        if (dest != null) {
            return dest;
        }

        // Failed.
        return null;
    }

    public static PortalDestination findAgainst(Block regionMin, Block regionMax, BlockFace spawnDirection) {
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

    public static PortalDestination findRailDestination(Block rails, BlockFace direction) {
        RailType railType = RailType.getType(rails);
        if (railType != RailType.NONE) {
            return new PortalDestination(rails, new BlockFace[] {direction});
        } else {
            return null;
        }
    }
}
