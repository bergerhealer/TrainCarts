package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import org.bukkit.util.Vector;

/**
 * Extra utilities to move a 1.19.4+ Display Block entity around
 */
public class VirtualDisplayBlockEntity extends VirtualDisplayEntity {
    // Properties
    private BlockData blockData;

    /**
     * Creates DataWatchers with the base metadata default values for a new Block display entity
     */
    public static final DataWatcher.Prototype BLOCK_DISPLAY_METADATA = BASE_DISPLAY_METADATA.modify()
            .set(DisplayHandle.DATA_WIDTH, (float) BBOX_FACT)
            .set(DisplayHandle.DATA_HEIGHT, (float) BBOX_FACT)
            .create();

    public VirtualDisplayBlockEntity(AttachmentManager manager) {
        super(manager, BLOCK_DISPLAY_ENTITY_TYPE, BLOCK_DISPLAY_METADATA.create());
        blockData = null;
    }

    @Override
    protected void onScaleUpdated() {
        super.onScaleUpdated();
        float bb = (float) (BBOX_FACT * Util.absMaxAxis(scale));
        metadata.set(DisplayHandle.DATA_WIDTH, bb);
        metadata.set(DisplayHandle.DATA_HEIGHT, bb);
    }

    @Override
    protected Vector computeTranslation(Quaternion rotation) {
        Vector s = getScale();
        Vector v = new Vector(-0.5, 0.0, -0.5);
        v.setX(v.getX() * s.getX());
        v.setY(v.getY() * s.getY());
        v.setZ(v.getZ() * s.getZ());
        rotation.transformPoint(v);
        return v;
    }

    public BlockData getBlockData() {
        return blockData;
    }

    public void setBlockData(BlockData blockData) {
        if (this.blockData != blockData) {
            this.blockData = blockData;
            this.metadata.set(DisplayHandle.BlockDisplayHandle.DATA_BLOCK_STATE, blockData);
            syncMeta(); // Changes in item should occur immediately
        }
    }
}
