package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.particle.VirtualBoundingBox;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundAddEntityPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundAddMobPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ClientboundSetEntityDataPacketHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.AgeableMobHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.InteractionHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.monster.cubemob.SlimeHandle;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an invisible box spawned near to a Player that the player can interact with.
 * Uses the interaction entity if available, and otherwise uses vanilla mobs of varying hitbox sizes.
 * The hitbox is spawned and moved in front of the player, depending on where the player looks,
 * to simulate the exact hitbox bounds.
 */
public class VirtualHitBoxEntity extends VirtualSpawnableObject {
    private final OrientedBoundingBox bbox = new OrientedBoundingBox();
    private final Set<Player> nearbyViewers = new HashSet<>();
    private final int hitboxEntityId = EntityUtil.getUniqueEntityId();
    private final UUID hitboxEntityUUID = UUID.randomUUID();
    private Box box = null; // Null if not spawned
    private double heightOffset = 0.0;
    private double minSize;
    private SizeMode sizeMode = SizeMode.SMALLEST;

    public VirtualHitBoxEntity() {
        this(null);
    }

    // This constructor probably isn't needed, but maybe someone wants it.
    public VirtualHitBoxEntity(AttachmentManager manager) {
        super(manager);
    }

    /**
     * Gets the bounding box that this particular hitbox entity occupies
     *
     * @return bounds
     */
    public OrientedBoundingBox getBoundingBox() {
        return bbox;
    }

    /**
     * Sets the x/y/z size of this hitbox entity
     *
     * @param size Size
     */
    public void setSize(Vector size) {
        setSize(new Vector3(size));
    }

    /**
     * Sets the x/y/z size of this hitbox entity
     *
     * @param size Size
     */
    public void setSize(Vector3 size) {
        bbox.setSize(new Vector(size.x, size.y, size.z));
        heightOffset = 0.5 * size.y;
        minSize = Math.min(Math.min(size.x, size.y), size.z);

        // Compute the new size mode based on this size
        SizeMode newSizeMode = SizeMode.fromSize(minSize);
        if (newSizeMode != sizeMode && !nearbyViewers.isEmpty()) {
            // Respawn for viewers
            for (AttachmentViewer viewer : this.getViewers()) {
                despawnHitBoxForViewer(viewer);
            }
            sizeMode = newSizeMode;
            for (AttachmentViewer viewer : this.getViewers()) {
                updateHitBoxForViewer(viewer);
            }
        } else {
            sizeMode = newSizeMode;

            // Update metadata for 1.19.4+ clients
            for (AttachmentViewer viewer : this.getViewers()) {
                if (viewer.supportsDisplayEntities()) {
                    updateInteractionMeta(viewer);
                    updateHitBoxForViewer(viewer);
                }
            }
        }
    }

    @Override
    protected void sendSpawnPackets(AttachmentViewer viewer, Vector motion) {
        if (box != null) {
            box.makeVisible(viewer);
        }

        updateHitBoxForViewer(viewer);
    }

    @Override
    protected void sendDestroyPackets(AttachmentViewer viewer) {
        if (box != null) {
            box.makeHidden(viewer);
        }

        despawnHitBoxForViewer(viewer);
    }

    @Override
    public void updatePosition(Matrix4x4 transform) {
        Quaternion orientation = transform.getRotation();
        bbox.setPosition(transform.toVector().add(orientation.upVector().multiply(heightOffset)));
        bbox.setOrientation(orientation);
        if (box != null) {
            box.update(bbox);
        }
    }

    @Override
    public void syncPosition(boolean absolute) {
        if (box != null) {
            box.sync();
        }

        for (AttachmentViewer viewer : this.getViewers()) {
            updateHitBoxForViewer(viewer);
        }
    }

    @Override
    public boolean containsEntityId(int entityId) {
        if (hitboxEntityId == entityId) {
            return true;
        }
        if (box != null && box.entity.containsEntityId(entityId)) {
            return true;
        }
        return false;
    }

    private void despawnHitBoxForViewer(AttachmentViewer viewer) {
        if (nearbyViewers.remove(viewer.getPlayer())) {
            viewer.send(ClientboundRemoveEntitiesPacketHandle.createNewSingle(this.hitboxEntityId));
        }
    }

    /**
     * De-spawns the highlight box when this method has been called for the tick duration
     * specified after the last time {@link #spawnWireframe()} was called. This handles a delay
     * before the highlight box disappears when the player de-selects the hitbox attachment.
     *
     * @param tickDuration Tick duration after which the wireframe despawns
     */
    public void destroyWireframeAfter(int tickDuration) {
        if (box != null && (CommonUtil.getServerTicks() - box.tickLastSpawned) > tickDuration) {
            box.entity.destroyForAll();
            box = null;
        }
    }

    /**
     * Instantly de-spawns the highlight box, making the hitbox appear invisible to players.
     */
    public void destroyWireframe() {
        if (box != null) {
            box.entity.destroyForAll();
            box = null;
        }
    }

    /**
     * Spawns a wireframe box model showing the bounds of this hitbox. This will remain forever until
     * {@link #destroyWireframe()} or {@link #destroyWireframeAfter(int)} is called.
     */
    public void spawnWireframe() {
        spawnWireframe(null);
    }

    /**
     * Spawns a wireframe box model showing the bounds of this hitbox. This will remain forever until
     * {@link #destroyWireframe()} or {@link #destroyWireframeAfter(int)} is called.
     *
     * @param highlightColor Color to highlight on the wireframe. Use <i>null</i> to show a
     *                       non-glowing default black wireframe.
     */
    public void spawnWireframe(ChatColor highlightColor) {
        if (box == null) {
            box = new Box(manager, bbox);
            box.tickLastSpawned = CommonUtil.getServerTicks();
            box.entity.setGlowColor(highlightColor);
            for (AttachmentViewer viewer : this.getViewers()) {
                box.makeVisible(viewer);
            }
        } else {
            box.entity.setGlowColor(highlightColor);
            box.tickLastSpawned = CommonUtil.getServerTicks();
        }
    }

    private void updateHitBoxForViewer(AttachmentViewer viewer) {
        Vector pos = this.getPOVLocationBottom(viewer.getPlayer());
        if (pos == null) {
            despawnHitBoxForViewer(viewer);
            return;
        }

        boolean usesInteractionEntity = viewer.supportsDisplayEntities();
        if (usesInteractionEntity) {
            pos.setY(pos.getY() - 0.5 * minSize);
        } else {
            pos.setY(pos.getY() - 0.5 * sizeMode.size);
        }

        if (nearbyViewers.add(viewer.getPlayer())) {
            if (usesInteractionEntity) {
                // Spawn an interaction entity with metadata setup right
                ClientboundAddEntityPacketHandle packet = ClientboundAddEntityPacketHandle.createNew();
                packet.setEntityId(hitboxEntityId);
                packet.setEntityUUID(hitboxEntityUUID);
                packet.setEntityType(VirtualDisplayEntity.INTERACTION_ENTITY_TYPE);
                packet.setPosX(pos.getX());
                packet.setPosY(pos.getY());
                packet.setPosZ(pos.getZ());
                viewer.send(packet);
                updateInteractionMeta(viewer);
            } else {
                // spawn an invisible entity whose hitbox the player can hit
                // The entity is positioned in such a way the player hits it from that POV
                ClientboundAddMobPacketHandle packet = ClientboundAddMobPacketHandle.createNew();
                packet.setEntityId(hitboxEntityId);
                packet.setEntityUUID(hitboxEntityUUID);
                packet.setEntityType(sizeMode.type);
                packet.setPosX(pos.getX());
                packet.setPosY(pos.getY());
                packet.setPosZ(pos.getZ());

                DataWatcher meta = new DataWatcher();
                meta.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_FLYING | EntityHandle.DATA_FLAG_INVISIBLE));
                meta.set(EntityHandle.DATA_NO_GRAVITY, true);
                sizeMode.apply(meta);

                viewer.sendEntityLivingSpawnPacket(packet, meta);

                viewer.sendDisableCollision(hitboxEntityUUID);
            }
        } else {
            ClientboundEntityPositionSyncPacketHandle packet = ClientboundEntityPositionSyncPacketHandle.createNew(
                    this.hitboxEntityId,
                    pos.getX(), pos.getY(), pos.getZ(),
                    0.0f, 0.0f, false);
            viewer.send(packet);
        }
    }

    private void updateInteractionMeta(AttachmentViewer viewer) {
        DataWatcher meta = new DataWatcher();
        meta.set(InteractionHandle.DATA_WIDTH, (float) minSize);
        meta.set(InteractionHandle.DATA_HEIGHT, (float) minSize);
        viewer.send(ClientboundSetEntityDataPacketHandle.createNew(hitboxEntityId, meta, true).toCommonPacket());
    }

    /**
     * Calculates the position the invisible entity should have to optimally
     * be interacted with by a Player
     *
     * @param player
     * @return POV location
     */
    private Vector getPOVLocationBottom(Player player) {
        // See where on the box this player is looking at the hitbox
        Location eyeLoc = player.getEyeLocation();
        Vector eyePosition = eyeLoc.toVector();
        Vector eyeDirection = eyeLoc.getDirection();

        double distanceToBox = this.bbox.hitTest(eyePosition, eyeDirection);
        if (distanceToBox == Double.MAX_VALUE) {
            // Not looking at the box. Try estimating distance between box and player.
            // TODO: This algorithm could be a lot better, I guess.
            eyeDirection = this.bbox.getPosition().clone().subtract(eyePosition).normalize();
            if (Double.isNaN(eyeDirection.getX())) {
                // Player is exactly in the middle of the box for some reason
                return this.bbox.getPosition();
            } else {
                distanceToBox = this.bbox.hitTest(eyePosition, eyeDirection);
                if (distanceToBox == Double.MAX_VALUE) {
                    // This should never even happen! Fallback.
                    return this.bbox.getPosition();
                }
            }
        }

        // If too far away, despawn
        if (distanceToBox > 6.0) {
            return null;
        }

        // Check for being inside the bounding box itself
        if (distanceToBox == 0.0) {
            return eyePosition;
        }

        // Adjust for the size of the bounding box of the entity itself
        return eyePosition.add(eyeDirection.multiply(distanceToBox + 0.5 * sizeMode.size));
    }

    /**
     * A wireframe box showing the bounds of this hitbox
     */
    private static class Box {
        public final VirtualBoundingBox entity;
        private int tickLastSpawned;

        public Box(AttachmentManager manager, OrientedBoundingBox bbox) {
            this.entity = VirtualBoundingBox.create(manager);
            this.entity.update(bbox);
        }

        public void update(OrientedBoundingBox bbox) {
            this.entity.update(bbox);
        }

        public void sync() {
            this.entity.syncPosition(true);
        }

        public void makeVisible(AttachmentViewer viewer) {
            entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));
        }

        public void makeHidden(AttachmentViewer viewer) {
            entity.destroy(viewer);
        }
    }

    public enum SizeMode {
        // Note: sorted from largest to smallest
        SLIME_SZ8(8), // ~4.16
        SLIME_SZ7(7), // ~3.64
        SLIME_SZ6(6), // ~3.12
        SLIME_SZ5(5), // ~2.60
        SLIME_SZ4(4), // ~2.08
        SLIME_SZ3(3), // ~1.56
        SLIME_SZ2(2), // ~1.04
        PIG(0.9, EntityType.PIG, false),
        SLIME_SZ1(1), // ~0.54
        BABY_PIG(0.45, EntityType.PIG, true),
        RABBIT(0.4, EntityType.RABBIT, false),
        BABY_RABBIT(0.2, EntityType.RABBIT, true);

        public final double size;
        public final EntityType type;
        public final boolean baby;
        public final int slimeSize;

        public static final SizeMode SMALLEST;
        static {
            SMALLEST = SizeMode.values()[SizeMode.values().length - 1];
        }

        // Slimes only
        SizeMode(int slimeSize) {
            this.size = (double) (2.04F * (0.255F * (float) slimeSize));
            this.type = EntityType.SLIME;
            this.baby = false;
            this.slimeSize = slimeSize;
        }

        SizeMode(double size, EntityType type, boolean baby) {
            this.size = size;
            this.type = type;
            this.baby = baby;
            this.slimeSize = 0;
        }

        public void apply(DataWatcher datawatcher) {
            if (baby) {
                datawatcher.set(AgeableMobHandle.DATA_IS_BABY, true);
            } else if (slimeSize != 0) {
                datawatcher.set(SlimeHandle.DATA_SIZE, slimeSize);
            }
        }

        public static SizeMode fromSize(double minSize) {
            for (SizeMode mode : values()) {
                if (mode.size <= minSize) {
                    return mode;
                }
            }
            return SMALLEST;
        }
    }
}
