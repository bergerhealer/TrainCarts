package com.bergerkiller.bukkit.tc.attachments.particle;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.VirtualSpawnableObject;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.util.Vector;

/**
 * Represents the length of a train coupler. Shown using a cube, uses
 * a fishing line on older versions of the game.
 */
public abstract class VirtualTrainCoupler extends VirtualSpawnableObject {

    protected VirtualTrainCoupler(AttachmentManager manager) {
        super(manager);
    }

    /**
     * Updates the coupler displayed. Should be called at least once initially
     * before {@link #spawn(AttachmentViewer, Vector)} can be called.
     *
     * @param transform Base transform at which the coupler is positioned
     * @param length The length of the coupler that it extends from the base transform
     */
    public abstract void update(Matrix4x4 transform, double length);

    /**
     * Creates the most appropriate virtual train coupler entity
     *
     * @param manager AttachmentManager
     * @return VirtualTrainCoupler fake entity
     */
    public static VirtualTrainCoupler create(AttachmentManager manager) {
        if (CommonCapabilities.HAS_DISPLAY_ENTITY) {
            return new VirtualDisplayTrainCoupler(manager);
        } else {
            return new VirtualFishingTrainCoupler(manager);
        }
    }
}
