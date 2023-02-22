package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.OrientedBoundingBox;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.particle.VirtualFishingBoundingBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSizeBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutEntityTeleportHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.network.syncher.DataWatcherObjectHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityAgeableHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.monster.EntitySlimeHandle;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Is invisible, unless focused. Represents a box that players can interact with
 * to enter or destroy the cart.
 */
public class CartAttachmentHitBox extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "HITBOX";
        }

        @Override
        public double getSortPriority() {
            return 1.0;
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/hitbox.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentHitBox();
        }

        @Override
        public void createPositionMenu(PositionMenu.Builder builder) {
            builder.addRow(menu -> new MapWidgetSizeBox() {
                @Override
                public void onAttached() {
                    super.onAttached();

                    ConfigurationNode size = menu.getAttachment().getConfig().getNode("size");
                    setSize(size.get("x", 1.0), size.get("y", 1.0), size.get("z", 1.0));
                }

                @Override
                public void onSizeChanged() {
                    menu.updateConfig(config -> {
                        ConfigurationNode size = config.getNode("size");
                        size.set("x", x.getValue());
                        size.set("y", y.getValue());
                        size.set("z", z.getValue());
                    });
                }
            }.setBounds(25, 0, menu.getSliderWidth(), 35))
                    .addLabel(0, 3, "Size X")
                    .addLabel(0, 15, "Size Y")
                    .addLabel(0, 27, "Size Z")
                    .setSpacingAbove(3);
        }
    };

    private final OrientedBoundingBox bbox = new OrientedBoundingBox();
    private final Set<Player> nearbyViewers = new HashSet<>();
    private int hitboxEntityId = EntityUtil.getUniqueEntityId();
    private final UUID hitboxEntityUUID = UUID.randomUUID();
    private Box box = null; // Null if not spawned
    private double heightOffset = 0.0;
    private SizeMode sizeMode = SizeMode.SMALLEST;

    @Override
    public void onLoad(ConfigurationNode config) {
        ConfigurationNode sizeCfg = config.getNode("size");
        Vector size = new Vector(sizeCfg.get("x", 1.0),
                                 sizeCfg.get("y", 1.0),
                                 sizeCfg.get("z", 1.0));
        bbox.setSize(size);
        heightOffset = 0.5 * size.getY();

        // Compute the new size mode based on this size
        SizeMode newSizeMode = SizeMode.fromSize(Math.min(Math.min(size.getX(), size.getY()), size.getZ()));
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
            if (this.isFocused()) {
                box.makeVisible(viewer);
            } else {
                box.makeVisibleWithoutLines(viewer);
            }
        }

        updateHitBoxForViewer(viewer);
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
    private Vector getPOVLocation(Player player) {
        Vector p = getPOVLocationBottom(player);
        if (p != null) {
            p.setY(p.getY() - 0.5 * sizeMode.size);
        }
        return p;
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

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        if (box != null) {
            box.makeHidden(viewer);
        }
        despawnHitBoxForViewer(viewer);
    }

    @Override
    public void onFocus() {
        if (box == null) {
            box = new Box(bbox);
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                box.makeVisible(viewer);
            }
        } else {
            box.showLines(this.getAttachmentViewers());
        }
    }

    @Override
    public void onBlur() {
        if (box != null) {
            box.hideLines(this.getAttachmentViewers());
            box.tickLastHidden = CommonUtil.getServerTicks();
        }
    }

    @Override
    public void onTick() {
        if (box != null && !this.isFocused() &&
                (CommonUtil.getServerTicks() - box.tickLastHidden) > 40
        ) {
            for (AttachmentViewer viewer : this.getAttachmentViewers()) {
                box.makeHidden(viewer);
            }
            box = null;
        }
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        Quaternion orientation = transform.getRotation();
        bbox.setPosition(transform.toVector().add(orientation.upVector().multiply(heightOffset)));
        bbox.setOrientation(orientation);
    }

    @Override
    public void onMove(boolean absolute) {
        if (box != null) {
            box.move(this.getViewers());
        }

        for (AttachmentViewer viewer : this.getAttachmentViewers()) {
            updateHitBoxForViewer(viewer);
        }
    }

    private void updateHitBoxForViewer(AttachmentViewer viewer) {
        Vector pos = this.getPOVLocation(viewer.getPlayer());
        if (pos == null) {
            despawnHitBoxForViewer(viewer);
            return;
        }

        if (nearbyViewers.add(viewer.getPlayer())) {
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
        } else {
            // Update
            PacketPlayOutEntityTeleportHandle packet = PacketPlayOutEntityTeleportHandle.createNew(
                    this.hitboxEntityId, pos.getX(), pos.getY(), pos.getZ(),
                    0.0f, 0.0f, false);
            viewer.send(packet);
        }
    }

    private void despawnHitBoxForViewer(AttachmentViewer viewer) {
        if (nearbyViewers.remove(viewer.getPlayer())) {
            viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(this.hitboxEntityId));
        }
    }

    private static class Box {
        public final VirtualFishingBoundingBox entity = new VirtualFishingBoundingBox();
        public final OrientedBoundingBox bbox;
        private int tickLastHidden = 0;

        public Box(OrientedBoundingBox bbox) {
            this.bbox = bbox;
        }

        public void move(Iterable<Player> players) {
            entity.update(players, bbox);
        }

        public void makeVisible(AttachmentViewer viewer) {
            entity.spawn(viewer, bbox);
        }

        public void makeVisibleWithoutLines(AttachmentViewer viewer) {
            entity.spawnWithoutLines(viewer, bbox);
        }

        public void makeHidden(AttachmentViewer viewer) {
            entity.destroy(viewer);
        }

        public void showLines(Iterable<AttachmentViewer> viewers) {
            viewers.forEach(v -> entity.spawnLines(v, bbox));
        }

        public void hideLines(Iterable<AttachmentViewer> viewers) {
            viewers.forEach(entity::destroyLines);
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
                SLIME_SIZE_APPLIER.apply(datawatcher, slimeSize);
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

    private static final SlimeSizeApplier SLIME_SIZE_APPLIER;
    static {
        if (Common.hasCapability("Common:EntitySlimeHandle")) {
            SLIME_SIZE_APPLIER = createSlimeSizeApplier();
        } else {
            SLIME_SIZE_APPLIER = createLegacySlimeSizeApplier();
        }
    }

    private static SlimeSizeApplier createSlimeSizeApplier() {
        return (datawatcher, size) -> datawatcher.set(EntitySlimeHandle.DATA_SIZE, size);
    }

    // This stuff is to support older BKCL. Can be removed ideally.
    private static SlimeSizeApplier createLegacySlimeSizeApplier() {
        try {
            if (CommonCapabilities.DATAWATCHER_OBJECTS) {
                // Find the field meant for this
                Class<?> entitySlimeClass = CommonUtil.getClass("net.minecraft.world.entity.monster.EntitySlime");
                if (entitySlimeClass != null) {
                    for (java.lang.reflect.Field f : entitySlimeClass.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) &&
                                DataWatcherObjectHandle.T.isAssignableFrom(f.getType())
                        ) {
                            f.setAccessible(true);
                            final DataWatcher.Key<Integer> key = new DataWatcher.Key<Integer>(
                                    f.get(null), DataWatcher.Key.Type.INTEGER);
                            return (datawatcher, size) -> datawatcher.set(key, size);
                        }
                    }
                }
                throw new UnsupportedOperationException("Size DW field not found");
            } else {
                final DataWatcher.Key<Byte> KEY = DataWatcher.Key.Type.BYTE.createKey(null, 16);
                return (datawatcher, size) -> datawatcher.set(KEY, (byte) size);
            }
        } catch (Throwable t) {
            TrainCarts.plugin.getLogger().log(Level.SEVERE, "Broken slime stuff. Update BKCL.", t);
        }

        // NOOP
        return (datawatcher, size) -> {};
    }

    @FunctionalInterface
    private static interface SlimeSizeApplier {
        void apply(DataWatcher datawatcher, int size);
    }
}
