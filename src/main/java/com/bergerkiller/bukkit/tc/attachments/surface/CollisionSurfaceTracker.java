package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.EnumMap;

/**
 * Keeps track of the collision surface created to represent various
 * {@link CollisionSurface} instances. This tracker is per player.
 */
public class CollisionSurfaceTracker {
    private final AttachmentViewer viewer;
    private final int viewDistance;
    private final PlayerPusher playerPusher;
    private final ShulkerTracker shulkerCache;
    private final CollisionFloorTileGrid floorTiles;
    private final EnumMap<BlockFace, CollisionWallTileGrid> wallTiles;

    public CollisionSurfaceTracker(AttachmentViewer viewer, int viewDistance) {
        this.viewer = viewer;
        this.viewDistance = viewDistance;

        this.playerPusher = new PlayerPusher(viewer);
        this.shulkerCache = new ShulkerTracker();
        this.floorTiles = new CollisionFloorTileGrid(shulkerCache);
        this.wallTiles = new EnumMap<>(BlockFace.class);
    }

    /**
     * Called every tick to update the display of shulkers for all surface shapes that
     * have been added. Also cleans up information of cleared collision surfaces
     * (that have not received further updates).
     */
    public void update() {
        floorTiles.update();
        wallTiles.values().removeIf(wallTiles -> {
            wallTiles.update();
            return wallTiles.isEmpty();
        });

        shulkerCache.update(viewer, playerPusher);
    }

    /**
     * Creates a new CollisionSurface. Until further add methods are called on the surface,
     * nothing else is stored or tracked.
     *
     * @return CollisionSurface
     */
    public CollisionSurface createSurface() {
        return new CollisionSurfaceImpl();
    }

    private CollisionWallTileGrid getWallTiles(BlockFace face) {
        return wallTiles.computeIfAbsent(face, f -> new CollisionWallTileGrid(shulkerCache, f));
    }

    private class CollisionSurfaceImpl implements CollisionSurface {
        private int updateCounter = 0;

        public int getUpdateCounter() {
            return updateCounter;
        }

        @Override
        public void addSurface(OrientedBoundingBox surface) {
            Vector playerPos = viewer.getPlayer().getLocation().toVector();
            OBBSurfaceContext context = new OBBSurfaceContext(surface, playerPos, viewDistance);
            if (context.isFullyClipped) {
                return;
            }

            if (context.isWall) {
                // Steep surfaces are projected as a wall

                // Decide what block-axis we 'project' onto the surface to make the wall
                // If the player is behind the surface, invert the face direction we project
                BlockFace face;
                if (Math.abs(context.normal.getX()) > Math.abs(context.normal.getZ())) {
                    face = (context.normal.getX() > 0.0 != context.isBackSide) ? BlockFace.WEST : BlockFace.EAST;
                } else {
                    face = (context.normal.getZ() > 0.0 != context.isBackSide) ? BlockFace.NORTH : BlockFace.SOUTH;
                }

                applyWallSurface(context, face);
            } else if ((context.normal.getY() < 0.0) != context.isBackSide) {
                // Horizontal surfaces that are above the player are projected as ceilings
                applyWallSurface(context, BlockFace.UP);
            } else if (context.normal.getY() > 0.95) {
                // Horizontal surfaces that are below the player and are mostly flat
                // These only require a single shulker per block
                applyLevelSurface(context);
            } else if (Math.abs(context.normal.getX()) < 0.2 || Math.abs(context.normal.getZ()) < 0.2) {
                // An axis-aligned sloped surface, needs 2 shulkers per block to represent
                applyAlignedSlopedSurface(context);
            } else {
                // A diagonal sloped surface, requires 4 shulkers per block to represent
                applyDiagonalSlopedSurface(context);
            }
        }

        @Override
        public void addFloorTile(int x, int z, CollisionFloorTileShape shape) {
            floorTiles.addFloorTile(this, x, z, shape);
        }

        @Override
        public void removeFloorTile(int x, int z) {
            floorTiles.removeFloorTile(this, x, z);
        }

        @Override
        public void addWallTile(BlockFace face, int x, int y, double value) {
            getWallTiles(face).addWallTile(this, x, y, value);
        }

        @Override
        public void removeWallTile(BlockFace face, int x, int y) {
            getWallTiles(face).removeWallTile(this, x, y);
        }

        public void applyWallSurface(OBBSurfaceContext context, BlockFace face) {
            context.initProjector(face);

            CollisionWallTileGrid wallGrid = getWallTiles(face);

            // For all blocks orthogonal to this axis and vertical, project onto the surface
            // into the face direction and calculate the distance to the surface.
            if (FaceUtil.isAlongY(face)) {
                for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                    for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                        if (!context.project(
                                x + 0.5,
                                face == BlockFace.DOWN ? context.cuboid.max.y : context.cuboid.min.y,
                                z + 0.5)
                        ) {
                            continue;
                        }

                        wallGrid.addWallTile(this, x, z, context.projectedPos.getY() + 0.5 * face.getModY());
                    }
                }
            } else if (FaceUtil.isAlongX(face)) {
                for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                    for (int y = context.cuboid.min.y; y < context.cuboid.max.y; y++) {
                        if (!context.project(
                                face == BlockFace.EAST ? context.cuboid.max.x : context.cuboid.min.x,
                                y + 0.5,
                                z + 0.5)
                        ) {
                            continue;
                        }

                        wallGrid.addWallTile(this, z, y, context.projectedPos.getX() + 0.5 * face.getModX());
                    }
                }
            } else {
                for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                    for (int y = context.cuboid.min.y; y < context.cuboid.max.y; y++) {
                        if (!context.project(
                                x + 0.5,
                                y + 0.5,
                                face == BlockFace.SOUTH ? context.cuboid.max.z : context.cuboid.min.z)
                        ) {
                            continue;
                        }

                        wallGrid.addWallTile(this, x, y, context.projectedPos.getZ() + 0.5 * face.getModZ());
                    }
                }
            }
        }

        /**
         * Projects an axis-aligned sloped surface from above downwards onto the surface.
         *
         * @param context OBBSurfaceContext
         */
        public void applyAlignedSlopedSurface(OBBSurfaceContext context) {
            // Figure out the axis of alignment

            CollisionFloorTileShape.AlignedAxis axis = (Math.abs(context.normal.getX()) > Math.abs(context.normal.getZ()))
                    ? CollisionFloorTileShape.AlignedAxis.X : CollisionFloorTileShape.AlignedAxis.Z;

            context.initProjector(BlockFace.DOWN);

            final double maxY = context.planeMax.getY() - 0.5;

            // For all blocks on the horizontal place, compute the point on the surface
            for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                    double yp, yn;

                    if (context.project(x + 0.5 + axis.getDx(), context.cuboid.max.y, z + 0.5 + axis.getDz())) {
                        yp = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }
                    if (context.project(x + 0.5 - axis.getDx(), context.cuboid.max.y, z + 0.5 - axis.getDz())) {
                        yn = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    // Avoid shulkers spawning higher than the player, as that restricts the walkable surface
                    if (yp > maxY && yn > maxY) {
                        continue;
                    }

                    yn = Math.min(maxY, yn);
                    yp = Math.min(maxY, yp);

                    addFloorTile(x, z, new CollisionFloorTileShape.AlignedSlope(axis, yp, yn));
                }
            }
        }

        /**
         * Projects a diagonal sloped surface from above downwards onto the surface.
         *
         * @param context OBBSurfaceContext
         */
        public void applyDiagonalSlopedSurface(OBBSurfaceContext context) {
            context.initProjector(BlockFace.DOWN);

            double maxY = context.planeMax.getY() - 0.5;

            // For all blocks on the horizontal place, compute the point on the surface
            for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                    double y00, y01, y10, y11;

                    if (context.project(x + 0.25, context.cuboid.max.y, z + 0.25)) {
                        y00 = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    if (context.project(x + 0.25, context.cuboid.max.y, z + 0.75)) {
                        y01 = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    if (context.project(x + 0.75, context.cuboid.max.y, z + 0.25)) {
                        y10 = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    if (context.project(x + 0.75, context.cuboid.max.y, z + 0.75)) {
                        y11 = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    // Avoid shulkers spawning higher than the player, as that restricts the walkable surface
                    if (y00 > maxY && y01 > maxY && y10 > maxY && y11 > maxY) {
                        continue;
                    }

                    y00 = Math.min(maxY, y00);
                    y01 = Math.min(maxY, y01);
                    y10 = Math.min(maxY, y10);
                    y11 = Math.min(maxY, y11);

                    addFloorTile(x, z, new CollisionFloorTileShape.ComplexTile(y00, y01, y10, y11));
                }
            }
        }

        /**
         * Projects a level surface from above downwards onto the surface.
         *
         * @param context OBBSurfaceContext
         */
        public void applyLevelSurface(OBBSurfaceContext context) {
            context.initProjector(BlockFace.DOWN);

            double maxY = context.planeMax.getY() - 0.5;

            // For all blocks on the horizontal place, compute the point on the surface
            for (int x = context.cuboid.min.x; x < context.cuboid.max.x; x++) {
                for (int z = context.cuboid.min.z; z < context.cuboid.max.z; z++) {
                    double y;
                    if (context.project(x + 0.5, context.cuboid.max.y, z + 0.5)) {
                        y = context.projectedPos.getY() - 0.5;
                    } else {
                        continue;
                    }

                    // Avoid shulkers spawning higher than the player, as that restricts the walkable surface
                    if (y > maxY) {
                        continue;
                    }

                    addFloorTile(x, z, new CollisionFloorTileShape.Level(y));
                }
            }
        }

        @Override
        public void clear() {
            ++updateCounter;
        }
    }
}
