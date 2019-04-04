package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;

/**
 * Methods to implement for an attachment type.
 * The implementation should declare what icon to show, what menus to create
 * and how to create the final controller object.
 */
public interface AttachmentType {

    /**
     * Gets the name of this attachment type. This is what is displayed in the
     * attachment type selection menu, and is what is stored in the configuration
     * to uniquely identify it. It should not change during its lifetime.
     * 
     * @return attachment type name
     */
    String getName();

    /**
     * Gets the icon to show in the attachment editor for this attachment type.
     * The configuration can be used to customize the icon.
     * 
     * @param config of the attachment
     * @return appropriate icon for the attachment
     */
    MapTexture getIcon(ConfigurationNode config);

    /**
     * Creates the controller for this attachment type.
     * Configuration will be loaded into this controller at a later stage.
     * 
     * @param config of the attachment
     * @return controller
     */
    Attachment createController(ConfigurationNode config);
}
