package com.bergerkiller.bukkit.tc.itemanimation;

import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.tc.attachments.VirtualDroppedItemEntity;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.ArmorStandHandle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * A packet-driven virtual dropped item used to play item transfer animations.
 * <p>
 * Two virtual entities are used:
 * <ol>
 *   <li>An invisible marker {@link EntityType#ARMOR_STAND} that acts as the
 *       moving mount. This entity has no gravity or collision, so it passes
 *       through blocks without glitching.</li>
 *   <li>A {@link VirtualDroppedItemEntity} that rides the mount and displays
 *       the item to players.</li>
 * </ol>
 * No real entities are ever spawned in the world; everything is packet-driven.
 */
public class ItemAnimationVirtualItem {
    /** View distance (blocks) within which players will see the animation. */
    private static final double VIEW_DISTANCE_SQUARED = 64.0 * 64.0;
    /** Distance off the ground dropped items spawn in */
    private static final double GROUND_OFFSET = 0.5;

    /** The invisible ArmorStand mount — handles all position synchronisation. */
    private final VirtualEntity mount;
    /** The dropped-item passenger — rides the mount and shows the item. */
    private final VirtualDroppedItemEntity item;

    private final World world;
    private final Vector currentPos;
    private final ItemStack itemStack;

    public ItemAnimationVirtualItem(Location location, ItemStack itemstack) {
        this.world = location.getWorld();
        this.currentPos = location.toVector();
        this.itemStack = itemstack;

        // --- Mount: invisible, no-gravity, marker ArmorStand ---
        this.mount = new VirtualEntity(null);
        this.mount.setEntityType(EntityType.ARMOR_STAND);
        this.mount.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.mount.getMetaData().set(EntityHandle.DATA_FLAGS,
                (byte) (EntityHandle.DATA_FLAG_INVISIBLE | EntityHandle.DATA_FLAG_FLYING));
        this.mount.getMetaData().set(ArmorStandHandle.DATA_ARMORSTAND_FLAGS,
                (byte) (ArmorStandHandle.DATA_FLAG_SET_MARKER |
                        ArmorStandHandle.DATA_FLAG_IS_SMALL |
                        ArmorStandHandle.DATA_FLAG_NO_BASEPLATE));
        this.mount.setRelativeOffset(0.0, -GROUND_OFFSET, 0.0);

        // --- Passenger: the visible dropped item ---
        this.item = new VirtualDroppedItemEntity();
        this.item.setItem(itemstack); // set before spawning — no viewers yet, metadata is buffered
        this.item.setRelativeOffset(0.0, -GROUND_OFFSET, 0.0);

        // Initialise positions so both entities know where to spawn
        final Vector zero = new Vector(0, 0, 0);
        this.mount.updatePosition(this.currentPos, zero);
        this.mount.syncPositionSilent();
        this.item.updatePosition(this.currentPos, zero);
        this.item.syncPositionSilent();

        // Spawn to all nearby players
        spawn();
    }

    /**
     * Spawns both virtual entities to all nearby players and mounts the item
     * inside the ArmorStand for each viewer.
     */
    private void spawn() {
        if (this.world == null) {
            return;
        }
        final Vector spawnMotion = new Vector(0, 0.1, 0);
        for (Player player : this.world.getPlayers()) {
            if (player.getLocation().toVector().distanceSquared(this.currentPos) <= VIEW_DISTANCE_SQUARED) {
                AttachmentViewer av = AttachmentViewer.forPlayer(player);
                this.mount.spawn(av, spawnMotion);
                this.item.spawn(av, spawnMotion);
                av.getVehicleMountController().mount(this.mount.getEntityId(), this.item.getEntityId());
            }
        }
    }

    /**
     * Moves the animation by the given direction vector and synchronises the
     * new position to all viewers. Only the mount is synced; the item follows
     * automatically as a passenger.
     *
     * @param dir Per-tick movement vector
     */
    public void update(Vector dir) {
        this.currentPos.add(dir);
        this.mount.updatePosition(this.currentPos, new Vector(0, 0, 0));
        this.mount.syncPosition(false);
    }

    /**
     * Destroys both virtual entities for all viewers.
     */
    public void die() {
        this.mount.destroyForAll();
        this.item.destroyForAll();
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public Location getLocation() {
        return this.currentPos.toLocation(this.world);
    }
}
