package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Vector3;

/**
 * Standard class for storing and representing an object's position.
 * Is typically used for attachments, with an editor widget menu of sliders
 * where the position, rotation and anchor can be configured.
 */
public class ObjectPosition {
    public PositionAnchorType anchor = PositionAnchorType.DEFAULT;
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
        this.anchor = PositionAnchorType.DEFAULT;
    }

    /**
     * Loads the position from the configuration specified
     * 
     * @param config
     */
    public void load(ConfigurationNode config) {
        this.reset();
        if (!config.isEmpty()) {
            this.position.x = config.get("posX", 0.0);
            this.position.y = config.get("posY", 0.0);
            this.position.z = config.get("posZ", 0.0);
            this.rotation.x = config.get("rotX", 0.0);
            this.rotation.y = config.get("rotY", 0.0);
            this.rotation.z = config.get("rotZ", 0.0);
            this.anchor = config.get("anchor", PositionAnchorType.DEFAULT);
            this.transform.translate(this.position);
            this.transform.rotateYawPitchRoll(this.rotation);
        }
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
