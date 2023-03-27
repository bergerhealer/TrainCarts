package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import org.bukkit.util.Vector;

/**
 * Extra utilities to move a 1.19.4+ Display Block entity around
 */
public class VirtualDisplayBlockEntity extends VirtualDisplayEntity {
    /**
     * This multiplier is needed for the bounding box (clip box) size to make sure this
     * block remains visible even when rotated 45 degrees.
     */
    private static final double BBOX_FACT = 1.0 / MathUtil.HALFROOTOFTWO;

    // Properties
    private BlockData blockData;

    public VirtualDisplayBlockEntity(AttachmentManager manager) {
        super(manager, BLOCK_DISPLAY_ENTITY_TYPE);
        metadata.watch(DisplayHandle.DATA_WIDTH, (float) BBOX_FACT);
        metadata.watch(DisplayHandle.DATA_HEIGHT, (float) BBOX_FACT);
        blockData = null;
    }

    @Override
    protected void onScaleUpdated() {
        super.onScaleUpdated();
        float bb = (float) (BBOX_FACT * Math.max(Math.max(Math.abs(scale.getX()), Math.abs(scale.getY())), Math.abs(scale.getZ())));
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
