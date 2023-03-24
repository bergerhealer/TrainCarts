package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.DebugUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

/**
 * Extra utilities to move a 1.19.4+ Display Block entity around
 */
public class VirtualDisplayBlockEntity extends VirtualDisplayEntity {
    private static final EntityType DISPLAY_ENTITY_TYPE = LogicUtil.tryMake(
            () -> EntityType.valueOf("BLOCK_DISPLAY"), null);

    // Properties
    private BlockData blockData;

    public VirtualDisplayBlockEntity(AttachmentManager manager) {
        super(manager, DISPLAY_ENTITY_TYPE);
        blockData = null;
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
