package com.bergerkiller.bukkit.tc.attachments.surface;

import com.bergerkiller.bukkit.common.wrappers.RelativeFlags;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutPositionHandle;
import com.bergerkiller.generated.net.minecraft.server.level.EntityPlayerHandle;
import com.bergerkiller.generated.net.minecraft.world.phys.AxisAlignedBBHandle;
import org.bukkit.Location;

/**
 * Pushes players away/up from a wall or floor when they spawn in.
 * This ensures players don't fall through the floor when the floor is activated.
 */
final class PlayerPusher {
    /** A small extra amount added to the push to ensure a small distance between shulker and player */
    private static final double PUSH_EXTRA = 1e-4;
    /** Extra upwards velocity given to negate gravity when pushing the player up */
    private static final double PUSH_UP_VELOCITY = 0.04;

    private final AttachmentViewer viewer;
    private final EntityPlayerHandle handle;
    private final Location viewerLocation;
    private AxisAlignedBBHandle bbox;
    private double pushX = 0.0;
    private double pushY = 0.0;
    private double pushZ = 0.0;

    public PlayerPusher(AttachmentViewer viewer) {
        this.viewer = viewer;
        this.handle = EntityPlayerHandle.fromBukkit(viewer.getPlayer());
        this.viewerLocation = viewer.getPlayer().getLocation();
        this.reset();
    }

    /**
     * Called before the collision surface is updated, resetting the position
     * to the current player position.
     */
    public void reset() {
        bbox = handle.getBoundingBox();
        viewer.getPlayer().getLocation(viewerLocation);
        pushX = 0.0;
        pushY = 0.0;
        pushZ = 0.0;
    }

    /**
     * If a push occurred in {@link #shulkerSpawned(Shulker)}, pushes the player
     * away to take this into account.
     */
    public void sendPush() {
        if (pushX != 0.0 || pushY != 0.0 || pushZ != 0.0) {
            RelativeFlags flags = RelativeFlags.RELATIVE_POSITION_ROTATION;
            flags = flags.withRelativeDelta();
            double x = 0.0, y = 0.0, z = 0.0;
            double deltaX = 0.0, deltaY = 0.0, deltaZ = 0.0;
            if (pushX != 0.0) {
                flags = flags.withAbsoluteDeltaX().withAbsoluteX();
                x = viewerLocation.getX() + pushX;
            }
            if (pushZ != 0.0) {
                flags = flags.withAbsoluteDeltaZ().withAbsoluteZ();
                z = viewerLocation.getZ() + pushZ;
            }
            if (pushY != 0.0) {
                flags = flags.withAbsoluteDeltaY().withAbsoluteY();
                y = viewerLocation.getY() + pushY;

                // Give a little bit of upwards momentum to make sure the player doesn't fall down
                // through the shulker
                if (pushY > 0.0) {
                    deltaY = PUSH_UP_VELOCITY;
                }
            }

            PacketPlayOutPositionHandle packet = PacketPlayOutPositionHandle.createNew(
                    x, y, z, 0.0f, 0.0f,
                    deltaX, deltaY, deltaZ, flags);
            viewer.send(packet);
        }
    }

    /**
     * Handles the spawning of a new shulker. If the shulker intersects with
     * the player's position, updates the player position to take this into account.
     *
     * @param shulker Shulker that spawned
     * @return True if the player was pushed away because of this shulker
     */
    public boolean shulkerSpawned(Shulker shulker) {
        if (!intersects(shulker.x, shulker.y, shulker.z)) {
            return false;
        }

        // Find the desired new position for the player so that they are pushed away by the shulker
        // Take the bounding box size of the player into account
        // This assumes player position is bottom-middle of the bbox.
        double newPushX, newPushY, newPushZ;
        switch (shulker.pushDirection) {
            case NORTH:
                newPushZ = (shulker.z - 0.5 - 0.5 * (bbox.getMaxZ() - bbox.getMinZ())) - viewerLocation.getZ() + PUSH_EXTRA;
                if (newPushZ < pushZ) {
                    pushZ = newPushZ;
                    return true;
                } else {
                    break;
                }
            case SOUTH:
                newPushZ = (shulker.z + 0.5 + 0.5 * (bbox.getMaxZ() - bbox.getMinZ())) - viewerLocation.getZ() - PUSH_EXTRA;
                if (newPushZ > pushZ) {
                    pushZ = newPushZ;
                    return true;
                } else {
                    break;
                }
            case WEST:
                newPushX = (shulker.x - 0.5 - 0.5 * (bbox.getMaxX() - bbox.getMinX())) - viewerLocation.getX() - PUSH_EXTRA;
                if (newPushX < pushX) {
                    pushX = newPushX;
                    return true;
                } else {
                    break;
                }
            case EAST:
                newPushX = (shulker.x + 0.5 + 0.5 * (bbox.getMaxX() - bbox.getMinX())) - viewerLocation.getX() + PUSH_EXTRA;
                if (newPushX > pushX) {
                    pushX = newPushX;
                    return true;
                } else {
                    break;
                }
            case DOWN:
                newPushY = (shulker.y - 0.5 - (bbox.getMaxY() - bbox.getMinY())) - viewerLocation.getY() - PUSH_EXTRA;
                if (newPushY < pushY) {
                    pushY = newPushY;
                    return true;
                } else {
                    break;
                }
            case UP:
                newPushY = (shulker.y + 0.5) - viewerLocation.getY() + PUSH_EXTRA;
                if (newPushY > pushY) {
                    pushY = newPushY;
                    return true;
                } else {
                    break;
                }
        }

        return false;
    }

    /**
     * Tests if the player bbox intersects with a unit cube centered at (x, y, z).
     *
     * @param x    center x of the unit cube
     * @param y    center y of the unit cube
     * @param z    center z of the unit cube
     * @return true if they intersect, false otherwise
     */
    private boolean intersects(double x, double y, double z) {
        AxisAlignedBBHandle bbox = this.bbox;

        // Take existing push offset into account, as we don't update bbox
        x -= pushX;
        y -= pushY;
        z -= pushZ;

        // Unit cube min/max
        double minX = x - 0.5;
        double maxX = x + 0.5;
        double minY = y - 0.5;
        double maxY = y + 0.5;
        double minZ = z - 0.5;
        double maxZ = z + 0.5;

        // Check overlap along each axis
        boolean overlapX = bbox.getMaxX() >= minX && bbox.getMinX() <= maxX;
        boolean overlapY = bbox.getMaxY() >= minY && bbox.getMinY() <= maxY;
        boolean overlapZ = bbox.getMaxZ() >= minZ && bbox.getMinZ() <= maxZ;

        return overlapX && overlapY && overlapZ;
    }
}
