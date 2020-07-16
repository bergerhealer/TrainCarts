package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentAnchor;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;

/**
 * Standard class for storing and representing an object's position.
 * Is typically used for attachments, with an editor widget menu of sliders
 * where the position, rotation and anchor can be configured.
 */
public class ObjectPosition {
    public AttachmentAnchor anchor = AttachmentAnchor.DEFAULT;
    public Vector3 position = new Vector3();
    public Vector3 rotation = new Vector3();
    public Matrix4x4 transform = new Matrix4x4();
    private boolean _isDefault = true;

    /**
     * Resets to the default configuration
     */
    public void reset() {
        this._isDefault = true;
        this.position.x = 0.0;
        this.position.y = 0.0;
        this.position.z = 0.0;
        this.rotation.x = 0.0;
        this.rotation.y = 0.0;
        this.rotation.z = 0.0;
        this.transform.setIdentity();
        this.anchor = AttachmentAnchor.DEFAULT;
    }

    /**
     * Gets whether the position specifies a default behavior of attaching to the parent attachment
     * 
     * @param config
     * @return True if seat default behavior is active
     */
    public static boolean isDefaultSeatParent(ConfigurationNode config) {
        if (!config.isEmpty()) {
            if (config.contains("anchor")) {
                String name = config.get("anchor", AttachmentAnchor.DEFAULT.getName());
                if (name.equals(AttachmentAnchor.SEAT_PARENT.getName())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Loads the position from the configuration specified
     * 
     * @param attachment The attachment this object position is for (for anchor support checks)
     * @param config Configuration
     */
    public void load(Class<? extends AttachmentManager> managerType, AttachmentType attachmentType, ConfigurationNode config) {
        if (!config.isEmpty()) {
            this.anchor = AttachmentAnchor.find(managerType, attachmentType, config.get("anchor", AttachmentAnchor.DEFAULT.getName()));
            if (this.anchor != AttachmentAnchor.SEAT_PARENT) {
                this._isDefault = false;
                this.position.x = config.get("posX", 0.0);
                this.position.y = config.get("posY", 0.0);
                this.position.z = config.get("posZ", 0.0);
                this.rotation.x = config.get("rotX", 0.0);
                this.rotation.y = config.get("rotY", 0.0);
                this.rotation.z = config.get("rotZ", 0.0);
                this.initTransform();
                return;
            }
        }

        // Default (seat in parent or 0/0/0)
        this.reset();
    }

    /**
     * Initializes the transform. Should be called after the position and/or
     * rotation vector is changed.
     */
    public void initTransform() {
        this.transform.setIdentity();
        this.transform.translate(this.position);
        this.transform.rotateYawPitchRoll(this.rotation);
    }

    /**
     * Gets whether the object's position is not configured, and that a default
     * position must be assumed.
     * 
     * @return True if the default position is used
     */
    public boolean isDefault() {
        return this._isDefault;
    }
}
