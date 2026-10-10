package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.LivingEntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.ArmorStandHandle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

/**
 * A specialized VirtualEntity that positions itself off-screen at a safe Y coordinate.
 * Used for mounting the player while spectating, to keep them out of view.
 * 
 * The safe Y is calculated as max(positionY + 64, worldMaxHeight) to prevent the player
 * from being inside blocks or visible on screen.
 */
public class VirtualOffScreenMount extends VirtualEntity {
    private final double worldMaxY;

    /**
     * Creates a new off-screen mount entity with safe Y positioning.
     * 
     * @param manager Attachment manager
     * @param world World to query for maximum height
     */
    public VirtualOffScreenMount(AttachmentManager manager, World world) {
        super(manager);
        this.worldMaxY = WorldUtil.getBlockBorder(world).max.y;

        // Configure as an invisible armor stand marker
        this.setEntityType(EntityType.ARMOR_STAND);
        this.setSyncMode(SyncMode.SEAT);

        // Make invisible and non-interactive
        this.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
        this.getMetaData().set(LivingEntityHandle.DATA_HEALTH, 10.0F);
        this.getMetaData().set(ArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                ArmorStandHandle.DATA_FLAG_SET_MARKER |
                ArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                ArmorStandHandle.DATA_FLAG_IS_SMALL));
    }

    @Override
    public void updatePosition(Vector position, Vector yawPitchRoll) {
        // Calculate safe Y as the maximum of: current position + 64, or world max height
        double safeY = Math.max(position.getY() + 64.0, worldMaxY);

        // Force Y to the safe position while keeping X and Z from the original position
        Vector safePos = position.clone();
        safePos.setY(safeY);
        super.updatePosition(safePos, yawPitchRoll);
    }
}
