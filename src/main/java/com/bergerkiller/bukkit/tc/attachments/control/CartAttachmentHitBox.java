package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.particle.VirtualBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityAgeableHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.InteractionHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.monster.EntitySlimeHandle;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Is invisible, unless focused. Represents a box that players can interact with
 * to enter or destroy the cart.
 */
public class CartAttachmentHitBox extends CartAttachment {
    private static final Vector3 DEFAULT_SCALE = new Vector3(1.0, 1.0, 1.0);

    public static final AttachmentType TYPE = new BaseHitBoxType() {
        @Override
        public String getID() {
            return "HITBOX";
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentHitBox();
        }
    };

    private final OrientedBoundingBox bbox = new OrientedBoundingBox();
    private final Set<Player> nearbyViewers = new HashSet<>();
    private int hitboxEntityId = EntityUtil.getUniqueEntityId();
    private final UUID hitboxEntityUUID = UUID.randomUUID();
    private Box box = null; // Null if not spawned
    private double heightOffset = 0.0;
    private double minSize;
    private SizeMode sizeMode = SizeMode.SMALLEST;

    @Override
    public void onLoad(ConfigurationNode config) {
        Vector3 size = LogicUtil.fixNull(this.getConfiguredPosition().size, DEFAULT_SCALE);
        bbox.setSize(new Vector(size.x, size.y, size.z));
        heightOffset = 0.5 * size.y;
        minSize = Math.min(Math.min(size.x, size.y), size.z);

        // Compute the new size mode based on this size
        SizeMode newSizeMode = SizeMode.fromSize(minSize);
        if (newSizeMode != sizeMode && !nearbyViewers.isEmpty()) {
            // Respawn for viewers
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                despawnHitBoxForViewer(viewer);
            }
            sizeMode = newSizeMode;
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                updateHitBoxForViewer(viewer);
            }
        } else {
            sizeMode = newSizeMode;

            // Update metadata for 1.19.4+ clients
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                if (viewer.supportsDisplayEntities()) {
                    updateInteractionMeta(viewer);
                    updateHitBoxForViewer(viewer);
                }
            }
        }
    }

    @Override
    public void makeVisible(Player viewer) {
        makeVisible(AttachmentViewer.fallback(viewer));
    }

    @Override
    public void makeHidden(Player viewer) {
        makeHidden(AttachmentViewer.fallback(viewer));
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        if (box != null) {
            box.makeVisible(viewer);
        }

        updateHitBoxForViewer(viewer);
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        if (box != null) {
            box.makeHidden(viewer);
        }
        despawnHitBoxForViewer(viewer);
    }

    @Override
    public boolean containsEntityId(int id) {
        return id == hitboxEntityId;
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
     * Gets the oriented bounding box that defines the shape of this hitbox attachment
     * at the current time. Is kept updated.
     *
     * @return OrientedBoundingBox bbox
     */
    public OrientedBoundingBox getBoundingBox() {
        return bbox;
    }

    public void setBoxColor(ChatColor color) {
        if (color != null) {
            if (box == null) {
                box = new Box(getManager(), bbox);
                box.entity.setGlowColor(color);
                for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                    box.makeVisible(viewer);
                }
            } else {
                box.entity.setGlowColor(color);
            }
        } else {
            if (box != null) {
                box.entity.setGlowColor(null);
                box.tickLastHidden = CommonUtil.getServerTicks();
            }
        }
    }

    @Override
    public void onFocus() {
        setBoxColor(HelperMethods.getFocusGlowColor(this));
    }

    @Override
    public void onBlur() {
        setBoxColor(null);
    }

    @Override
    public void onTick() {
        if (box != null && !this.isFocused() &&
                (CommonUtil.getServerTicks() - box.tickLastHidden) > 40
        ) {
            box.entity.destroyForAll();
            box = null;
        }
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        Quaternion orientation = transform.getRotation();
        bbox.setPosition(transform.toVector().add(orientation.upVector().multiply(heightOffset)));
        bbox.setOrientation(orientation);
        if (box != null) {
            box.update(bbox);
        }
    }

    @Override
    public void onMove(boolean absolute) {
        if (box != null) {
            box.sync();
        }

        for (AttachmentViewer viewer : this.getAttachmentViewers()) {
            updateHitBoxForViewer(viewer);
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
                PacketPlayOutSpawnEntityHandle packet = PacketPlayOutSpawnEntityHandle.createNew();
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
                PacketPlayOutSpawnEntityLivingHandle packet = PacketPlayOutSpawnEntityLivingHandle.createNew();
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
            PacketPlayOutEntityTeleportHandle packet = PacketPlayOutEntityTeleportHandle.createNew(
                    this.hitboxEntityId, pos.getX(), pos.getY(), pos.getZ(),
                    0.0f, 0.0f, false);
            viewer.send(packet);
        }
    }

    private void updateInteractionMeta(AttachmentViewer viewer) {
        DataWatcher meta = new DataWatcher();
        meta.set(InteractionHandle.DATA_WIDTH, (float) minSize);
        meta.set(InteractionHandle.DATA_HEIGHT, (float) minSize);
        viewer.send(PacketPlayOutEntityMetadataHandle.createNew(hitboxEntityId, meta, true));
    }

    private void despawnHitBoxForViewer(AttachmentViewer viewer) {
        if (nearbyViewers.remove(viewer.getPlayer())) {
            viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(this.hitboxEntityId));
        }
    }

    private static class Box {
        public final VirtualBoundingBox entity;
        private int tickLastHidden = 0;

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

    private enum SizeMode {
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
                datawatcher.set(EntityAgeableHandle.DATA_IS_BABY, true);
            } else if (slimeSize != 0) {
                datawatcher.set(EntitySlimeHandle.DATA_SIZE, slimeSize);
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

    protected static abstract class BaseHitBoxType implements AttachmentType {
        @Override
        public double getSortPriority() {
            return 1.0;
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/hitbox.png");
        }

        @Override
        public void migrateConfiguration(ConfigurationNode config) {
            if (config.isNode("size")) {
                ConfigurationNode size = config.getNode("size");
                config.set("position.sizeX", size.get("x", 1.0));
                config.set("position.sizeY", size.get("y", 1.0));
                config.set("position.sizeZ", size.get("z", 1.0));
                size.remove();
            }
        }

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addRow(menu -> new MapWidgetSizeBox() {
                        @Override
                        public void onAttached() {
                            super.onAttached();

                            setSize(menu.getPositionConfigValue("sizeX", DEFAULT_SCALE.x),
                                    menu.getPositionConfigValue("sizeY", DEFAULT_SCALE.y),
                                    menu.getPositionConfigValue("sizeZ", DEFAULT_SCALE.z));
                        }

                        @Override
                        public void onSizeChanged() {
                            menu.updatePositionConfig(config -> {
                                config.set("sizeX", x.getValue());
                                config.set("sizeY", y.getValue());
                                config.set("sizeZ", z.getValue());
                            });
                        }
                    }.setBounds(25, 0, menu.getSliderWidth(), 35))
                    .addLabel(0, 3, "Size X")
                    .addLabel(0, 15, "Size Y")
                    .addLabel(0, 27, "Size Z")
                    .setSpacingAbove(3);
        }
    }
}
