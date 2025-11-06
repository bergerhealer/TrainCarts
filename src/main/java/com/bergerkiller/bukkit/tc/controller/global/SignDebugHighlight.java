package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.VirtualDisplayBlockEntity;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.phys.AxisAlignedBBHandle;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * Implements a debugging highlight meant for real sign blocks. Used to implement
 * {@link RailLookup.TrackedSign#showDebugHighlight(AttachmentViewer, RailLookup.TrackedSign.DebugDisplayOptions)}.
 * Displays it using a block display positioned at the sign.
 */
final class SignDebugHighlight implements Runnable {
    private final AttachmentViewer viewer;
    private VirtualDisplayBlockEntity entity = null;

    SignDebugHighlight(AttachmentViewer viewer) {
        this.viewer = viewer;
    }

    public void spawn(SignChangeTracker sign, RailLookup.TrackedSign.DebugDisplayOptions options) {
        Block block = sign.getBlock();
        AxisAlignedBBHandle bbox = BlockUtil.getInteractableBox(block);
        if (bbox == null) {
            viewer.getTrainCarts().getLogger().warning("Could not display bbox of sign because its interactable box is null!");
            return;
        }

        Matrix4x4 m = new Matrix4x4();
        m.translate(block.getX(), block.getY(), block.getZ());
        m.translate(bbox.getMinX() + 0.5, bbox.getMinY(), bbox.getMinZ() + 0.5);

        // Move the bbox slightly away from the text-side of the sign, so that text remains visible

        entity = new VirtualDisplayBlockEntity(null);
        entity.updatePosition(m);
        entity.setBlockData(BlockData.fromMaterial(Material.BLACK_STAINED_GLASS));
        entity.getMetadata().set(DisplayHandle.DATA_GLOW_COLOR_OVERRIDE, Util.toColor(options.getTeamColor()).asRGB());
        entity.getMetadata().setFlag(EntityHandle.DATA_FLAGS, EntityHandle.DATA_FLAG_GLOWING, true);
        entity.setScale(new Vector(
                bbox.getMaxX() - bbox.getMinX(),
                bbox.getMaxY() - bbox.getMinY(),
                bbox.getMaxZ() - bbox.getMinZ()
        ));
        entity.spawn(viewer, new Vector());
    }

    @Override
    public void run() {
        // De-spawn the highlight
        entity.destroy(viewer);
        //viewer.send(PacketPlayOutEntityDestroyHandle.createNewSingle(entityId));
    }
}
