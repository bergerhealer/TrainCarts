package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.controller.player.pmc.PlayerMovementController;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityPlayerHandle;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Keeps track of the collision surface created to represent various
 * {@link CollisionSurface} instances. This tracker is per player.<br>
 * <br>
 * For moving surfaces, keeps players on these surfaces using the player movement
 * controller. For non-moving surfaces, spawns shulkers so the player can walk on them/
 * can't walk through walls. There is also some logic for the interaction between
 * the two (jumping from surface onto shulkers).
 */
public class CollisionSurfaceTracker {
    /** How many ticks of no movement until a surface is considered "not moving" */
    private static final int TICKS_UNTIL_NOT_MOVING = 2;

    private final AttachmentViewer viewer;
    private final int shulkerViewDistance;
    private final PlayerPusher playerPusher;
    private final ShulkerTracker shulkerCache;
    private final CollisionFloorTileGrid floorTiles;
    private final EnumMap<BlockFace, CollisionWallTileGrid> wallTiles;
    private final List<CollisionSurfaceImpl> surfaces = new ArrayList<>();
    private ActiveSurface activeSurface = null;
    private AttachmentViewer.MovementController pmc = null;
    private boolean initialUpdate = true;

    public CollisionSurfaceTracker(AttachmentViewer viewer, int shulkerViewDistance) {
        this.viewer = viewer;
        this.shulkerViewDistance = shulkerViewDistance;

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
        // Update all surfaces, and if they are not moving, spawn in shulkers for them
        for (CollisionSurfaceImpl surface : surfaces) {
            surface.nextUpdate();

            if (!surface.isMoving()) {
                surface.spawnShulkers();
            }
        }

        // This actually spawns/moves/destroys the shulkers applied in the previous step
        floorTiles.update();
        wallTiles.values().removeIf(wallTiles -> {
            wallTiles.update();
            return wallTiles.isEmpty();
        });
        shulkerCache.update(viewer, playerPusher);

        // Check the active surface is still valid (has a shape) or was removed (null)
        // When removed, we disable the player movement controller managing it and 'release' the player
        // Normal world physics take over then.
        if (activeSurface != null) {
            if (activeSurface.surface.shape == null) {
                leaveSurface(true);
            } else {
                activeSurface.lastKnownShape = activeSurface.surface.shape;
            }
        }

        // If player has no previous position, see if the player is really close to a surface (spawning in / teleported)
        // If so right away put the player onto that surface to avoid falling through
        if (initialUpdate && activeSurface == null) {
            Vector currPos = viewer.getPlayer().getLocation().toVector();
            for (CollisionSurfaceImpl surface : surfaces) {
                if (!surface.isMoving()) {
                    continue;
                }

                Vector relativePos = surface.computeRelativePosition(currPos);
                if (Math.abs(relativePos.getY()) < 0.01) {
                    Vector halfSize = surface.shape.getSize().clone().multiply(0.5);
                    if (Math.abs(relativePos.getX()) <= halfSize.getX() && Math.abs(relativePos.getZ()) <= halfSize.getZ()) {
                        relativePos.setY(0.0);
                        setOnSurface(surface, relativePos);
                        break;
                    }
                }
            }
        }

        Vector newPlayerPosition;
        if (activeSurface != null) {
            // If the player has an active surface, update physics relative to it
            if (pmc == null) {
                pmc = viewer.controlMovement();
            }

            activeSurface.updatePhysics(viewer, pmc);
            newPlayerPosition = activeSurface.getAbsolutePosition();
            if (!pmc.update(newPlayerPosition, activeSurface.airborne)) {
                // Hit a block, disable the surface, don't sync position though
                leaveSurface(false);
            }

        } else {
            // If not, let world physics take care of it and simply track the player's actual position
            newPlayerPosition = viewer.getPlayer().getLocation().toVector();
        }

        // If the player is not on a surface or is airborne, test whether the player is intersecting with
        // a surface as part of movement. If so, set this intersected surface as the new active surface.
        //
        // Do not do this while walking on a surface, as there is no point.
        if (activeSurface == null || activeSurface.airborne) {
            double bestTheta = Double.MAX_VALUE;
            CollisionSurfaceImpl bestSurface = null;
            Vector bestPosOnSurface = null;
            for (CollisionSurfaceImpl s : surfaces) {
                Vector oldRelativePosition = s.lastRelativePosition;
                Vector newRelativePosition = s.computeRelativePosition(newPlayerPosition);
                s.lastRelativePosition = newRelativePosition;

                // If no previous relative position is known, initialize it right now
                if (oldRelativePosition == null) {
                    continue;
                }

                // If y did not change sign then we are still on the same side of the shape and did not pass through it
                if ((oldRelativePosition.getY() >= 0.0) == (newRelativePosition.getY() >= 0.0)) {
                    continue;
                }

                //TODO: More advanced check where on the surface we intersected
                double intersectTheta = 1.0;
                Vector intersectPosition = newRelativePosition.clone().setY(0.0);
                Vector halfSize = s.shape.getSize().clone().multiply(0.5);
                if (Math.abs(intersectPosition.getX()) > halfSize.getX() || Math.abs(intersectPosition.getZ()) > halfSize.getZ()) {
                    continue;
                }

                // Keep best
                if (intersectTheta < bestTheta) {
                    bestTheta = intersectTheta;
                    bestSurface = s;
                    bestPosOnSurface = intersectPosition;
                }
            }

            if (bestSurface != null) {
                setOnSurface(bestSurface, bestPosOnSurface);
            }

        } else {
            // Ensure made invalid
            surfaces.forEach(s -> s.lastRelativePosition = null);
        }
    }

    private void leaveSurface(boolean applyPosition) {
        if (pmc != null) {
            pmc.stop();
            pmc = null;
        }

        if (activeSurface != null) {
            Vector pos = applyPosition ? activeSurface.getAbsolutePosition() : new Vector();
            Vector vel = activeSurface.getAbsoluteVelocity();
            activeSurface = null;

            EntityPlayerHandle epHandle = EntityPlayerHandle.fromBukkit(viewer.getPlayer());
            if (applyPosition) {
                epHandle.setPosition(pos.getX(), pos.getY(), pos.getZ());
            }
            epHandle.setMotVector(vel);

            RelativeFlags flags = RelativeFlags.ABSOLUTE_POSITION
                    .withRelativeRotation()
                    .withAbsoluteDelta();
            if (!applyPosition) {
                flags = flags.withRelativePosition();
            }
            PacketPlayOutPositionHandle packet = PacketPlayOutPositionHandle.createNew(
                    pos.getX(), pos.getY(), pos.getZ(), 0.0f, 0.0f,
                    vel.getX(), vel.getY(), vel.getZ(), flags);
            viewer.send(packet);
        }
    }

    private void setOnSurface(CollisionSurfaceImpl surface, Vector relativePosition) {
        if (this.activeSurface == null) {
            checkSurfaceValid(surface);
            Vector vel = viewer.getPlayer().getVelocity();
            velocityRotation(surface.shape).invTransformPoint(vel);
            this.activeSurface = new ActiveSurface(surface, relativePosition, vel);
        } else if (this.activeSurface.surface != surface) {
            Vector vel = this.activeSurface.getAbsoluteVelocity();
            velocityRotation(surface.shape).invTransformPoint(vel);
            this.activeSurface = new ActiveSurface(surface, relativePosition, vel);
        } else {
            this.activeSurface.airborne = false;
            MathUtil.setVector(this.activeSurface.relativePosition, relativePosition);
        }
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

    private ActiveSurface getAbsoluteSurface(CollisionSurfaceImpl surface, Vector playerLocation, Vector playerVelocity) {
        checkSurfaceValid(surface);

        OrientedBoundingBox shape = surface.shape;

        // Calculate the position on the surface the player had
        Vector relativePosition = playerLocation.clone();
        relativePosition.subtract(shape.getPosition());
        shape.getOrientation().invTransformPoint(relativePosition);

        // Calculate the forward-relative velocity on the surface.
        // Y stays the same (jump) but the X/Z needs to be rotated the same way the surface is (yaw)
        Vector relativeVelocity = playerVelocity.clone();
        velocityRotation(shape).invTransformPoint(relativeVelocity);

        return new ActiveSurface(surface, relativePosition, relativeVelocity);
    }

    /**
     * The surface a player is actively walking on. The last surface the player is on
     * is 'tagged' and from then on all player movement is relative to it. This ensures the
     * player can walk on a moving surface (train).<br>
     * <br>
     * It's possible for the surface the player walks on to be de-spawned, in which case the
     * player is 'freed' automatically and normal physics take over until the player collides
     * with a different surface.
     */
    private static class ActiveSurface {
        /** Surface relative to which the player is moving */
        public final CollisionSurfaceImpl surface;
        /** Last bounding box shape of the surface (before it was removed...) */
        public OrientedBoundingBox lastKnownShape;
        /** Position of the player relative to the surface */
        public final Vector relativePosition;
        /** Velocity of the player relative to the surface (except Y, which is still world-absolute) */
        public final Vector relativeVelocity;
        /** When the player jumps, the player is set airborne and can land on another surface */
        public boolean airborne;

        public ActiveSurface(CollisionSurfaceImpl surface, Vector relativePosition, Vector relativeVelocity) {
            checkSurfaceValid(surface);

            // Assign surface
            this.surface = surface;
            this.lastKnownShape = surface.shape;
            this.relativePosition = relativePosition;
            this.relativeVelocity = relativeVelocity;
            this.airborne = false;
        }

        public void updatePhysics(AttachmentViewer viewer, AttachmentViewer.MovementController pmc) {
            AttachmentViewer.Input input = pmc.getInput();
            if (input.hasWalkInput()) {
                Vector vel = new Vector(0.1 * input.sidewaysSigNum(), 0.0, 0.1 * input.forwardsSigNum());
                Quaternion.fromLookDirection(viewer.getPlayer().getEyeLocation().getDirection().setY(0.0), new Vector(0, 1, 0))
                        .transformPoint(vel);
                velocityRotation(lastKnownShape).invTransformPoint(vel);
                this.relativeVelocity.add(vel);
            }

            this.relativePosition.add(this.relativeVelocity);
            this.relativeVelocity.multiply(0.6);

            Vector halfSize = lastKnownShape.getSize().clone().multiply(0.5);

            if (airborne) {
                // Gravity
                this.relativeVelocity.setY(this.relativeVelocity.getY() - 0.15);

            } else if (input.jumping() && !input.sneaking()) {
                this.relativeVelocity.setY(this.relativeVelocity.getY() + 1.8);
                airborne = true;
            } else if (Math.abs(this.relativePosition.getX()) > halfSize.getX() || Math.abs(this.relativePosition.getZ()) > halfSize.getZ()) {
                airborne = true;
            } else {
                // No vertical velocity
                this.relativeVelocity.setY(0.0);
                this.relativePosition.setY(0.0);
            }
        }

        public Vector getAbsolutePosition() {
            Vector pos = relativePosition.clone();
            lastKnownShape.getOrientation().transformPoint(pos);
            pos.add(lastKnownShape.getPosition());
            return pos;
        }

        public Vector getAbsoluteVelocity() {
            Vector rot = relativeVelocity.clone();
            velocityRotation(lastKnownShape).transformPoint(rot);
            return rot;
        }
    }

    private static void checkSurfaceValid(CollisionSurfaceImpl surface) {
        if (surface == null) {
            throw new IllegalArgumentException("Surface is null");
        }
        if (surface.shape == null) {
            throw new IllegalStateException("Surface was removed (shape is null)");
        }
    }

    private static Quaternion velocityRotation(OrientedBoundingBox shape) {
        return Quaternion.fromLookDirection(shape.getOrientation().forwardVector().setY(0.0));
    }

    /**
     * A single collision surface spawned in using the API for this viewer
     */
    private class CollisionSurfaceImpl implements CollisionSurface {
        private int updateCounter = 0;
        private OrientedBoundingBox shape;
        private int moveDetectedAtCount = 0;
        private boolean isMoving = false;

        /** The last-known relative position of the viewer to this surface shape */
        private Vector lastRelativePosition = null;

        public boolean isMoving() {
            return isMoving;
        }

        public void nextUpdate() {
            if (isMoving && (updateCounter - moveDetectedAtCount) >= TICKS_UNTIL_NOT_MOVING) {
                isMoving = false;
            }
            ++updateCounter;
        }

        public Vector computeRelativePosition(Vector absolutePosition) {
            Vector rel = absolutePosition.clone();
            rel.subtract(shape.getPosition());
            shape.getOrientation().invTransformPoint(rel);
            return rel;
        }

        @Override
        public int getUpdateCounter() {
            return updateCounter;
        }

        @Override
        public void remove() {
            setShape(null);
        }

        @Override
        public void setShape(OrientedBoundingBox shape) {
            // Handle removal/clear with null
            if (shape == null) {
                if (this.shape != null) {
                    surfaces.remove(this);
                    this.shape = null;
                    ++updateCounter;
                }
                return;
            }

            // When no shape was known before (=removed), add this surface to be updated
            // Do not yet spawn the shulkers, because we might find next tick that this shape is moving
            if (this.shape == null) {
                this.shape = new OrientedBoundingBox(shape.getPosition(), shape.getSize(), shape.getOrientation());
                surfaces.add(this);

                // Assume initially not moving to allow shulkers to spawn in right away
                this.isMoving = false;
                return;
            }

            // Check if the shape only moved downwards (elevator style) and did not change orientation or size
            // In that case, it's fine to keep using shulkers to represent this surface.
            // Do not set moveDetectedAtCount in that case, to allow shulkers to be used.
            if (
                    this.shape.getOrientation().equals(shape.getOrientation()) &&
                    this.shape.getSize().equals(shape.getSize())
            ) {
                Vector oldPos = this.shape.getPosition();
                Vector newPos = shape.getPosition();
                if (newPos.getX() == oldPos.getX() && newPos.getZ() == oldPos.getZ()) {
                    if (newPos.getY() == oldPos.getY()) {
                        // No change at all. Ignore.
                        return;
                    } else if (newPos.getY() < oldPos.getY()) {
                        // Moving downwards
                        this.shape.setPosition(newPos);
                        return;
                    }
                }
            }

            // Shape changed position/orientation/size in a way that acts as a moving surface
            // This despawns shulkers / keeps shulkers de-spawned and switches to movement control
            this.moveDetectedAtCount = this.updateCounter;
            this.isMoving = true;
            this.shape.setPosition(shape.getPosition());
            this.shape.setOrientation(shape.getOrientation());
            this.shape.setSize(shape.getSize());
        }

        public void spawnShulkers() {
            Vector playerPos = viewer.getPlayer().getLocation().toVector();
            OBBSurfaceContext context = new OBBSurfaceContext(shape, playerPos, shulkerViewDistance);
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

        /**
         * Adds or updates a single shaped floor tile that is a part of this surface.
         * Only a single tile can exist per x/z coordinate.
         *
         * @param x X-block coordinate
         * @param z Z-block coordinate
         * @param shape CollisionFloorTileShape
         */
        public void addFloorTile(int x, int z, CollisionFloorTileShape shape) {
            floorTiles.addFloorTile(this, x, z, shape);
        }

        /**
         * Removes any shaped floor tile that was added at a particular x/z block
         * coordinate previously. Can be called to undo a previous added shape.
         * Practically not used, since you can just call clear() and add all shapes
         * after, which is a lot more reliable.
         *
         * @param x X-block coordinate
         * @param z Z-block coordinate
         */
        public void removeFloorTile(int x, int z) {
            floorTiles.removeFloorTile(this, x, z);
        }

        /**
         * Adds a full-block wall tile, facing into a particular direction.
         * The x/y are relative to the facing direction (horizontally y==y and x is the
         * other axis, vertically the x==x and y is z).
         *
         * @param face Face direction of the player looking at the wall (opposite of normal)
         * @param x Face-relative X tile
         * @param y Face-relative Y tile
         * @param value Axis value of the wall, depending on facing (the other axis)
         */
        public void addWallTile(BlockFace face, int x, int y, double value) {
            getWallTiles(face).addWallTile(this, x, y, value);
        }

        /**
         * Removes a previously added full-block wall tile, facing into a particular direction.
         * The x/y are relative to the facing direction (horizontally y==y and x is the
         * other axis, vertically the x==x and y is z).
         *
         * @param face Face direction of the player looking at the wall (opposite of normal)
         * @param x Face-relative X tile
         * @param y Face-relative Y tile
         */
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
    }
}
