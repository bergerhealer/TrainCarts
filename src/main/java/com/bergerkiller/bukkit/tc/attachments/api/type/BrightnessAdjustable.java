package com.bergerkiller.bukkit.tc.attachments.api.type;

import com.bergerkiller.bukkit.common.wrappers.Brightness;

/**
 * Something that supports overriding the brightness (light) level of
 * the displayed model. This can be set to reflect the default {@link Brightness#UNSET}
 * brightness of where the attachment is at or it can be set to override it with a specific
 * block/sky light level.
 */
public interface BrightnessAdjustable {
    /**
     * Sets the brightness of the displayed attachment
     *
     * @param brightness Brightness level
     */
    void setBrightness(Brightness brightness);

    /**
     * Gets the brightness of the displayed attachment
     *
     * @return Brightness level
     */
    Brightness getBrightness();
}
