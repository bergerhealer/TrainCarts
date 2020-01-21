package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;

/**
 * Methods to implement for an attachment type.
 * The implementation should declare what icon to show, what menus to create
 * and how to create the final controller object.
 */
public interface AttachmentType {

    /**
     * Gets the unique identifier of this attachment type. This is what is saved
     * to the YAML configuration of an attachment, so that it can successfully be
     * loaded again in the future. The lookup is case-insensitive. This ID should
     * not change.
     * 
     * @return attachment ID
     */
    String getID();

    /**
     * Gets the name of this attachment type. This is what is displayed in the
     * attachment type selection menu.<br>
     * <br>
     * By default returns {@link #getID()}
     * 
     * @return attachment type name
     */
    default String getName() {
        return getID();
    }

    /**
     * Gets the icon to show in the attachment editor for this attachment type.
     * The configuration can be used to customize the icon.<br>
     * <br>
     * By default returns an empty 16x16 image.
     * 
     * @param config of the attachment
     * @return appropriate icon for the attachment
     */
    default MapTexture getIcon(ConfigurationNode config) {
        return MapTexture.createEmpty(16, 16);
    }

    /**
     * Retrieves the default configuration when creating this attachment type from the editor
     * 
     * @param config The configuration to fill with default values
     */
    default void getDefaultConfig(ConfigurationNode config) {
    }

    /**
     * Fills the appearance tab menu in the attachment editor with information relevant for this
     * attachment type. By default adds nothing.
     * 
     * @param tab
     * @param config
     */
    default void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
    }

    /**
     * Creates the controller for this attachment type.
     * Configuration will be loaded into this controller at a later stage.
     * 
     * @param config of the attachment
     * @return controller
     */
    Attachment createController(ConfigurationNode config);
}
