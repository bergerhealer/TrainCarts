package com.bergerkiller.bukkit.tc.attachments;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;

/**
 * Someone that manages one or more attachments
 */
public interface CartAttachmentOwner {

    /**
     * Called when one or more attachments have changed.
     * This indicates these attachments need to be re-mounted.
     */
    public void onAttachmentsChanged();

    /**
     * Gets the absolute position transformation that is applied to children of this owner
     * 
     * @param motion whether motion prediction must be performed (TODO: Remove this!)
     * @return absolute position transformation matrix
     */
    public Matrix4x4 getTransform(boolean motion);

    /**
     * Temporary: gets the last relative movement update that was performed.
     * Will be removed because it is a hack.
     * 
     * @return last movement update
     */
    public Vector getLastMovement();
}
