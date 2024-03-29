package com.bergerkiller.bukkit.tc.attachments.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;

/**
 * Methods to implement for an attachment type.
 * The implementation should declare what icon to show, what menus to create
 * and how to create the final controller object.
 */
public interface AttachmentType {
    /**
     * The attachment type ID for a Model attachment. This is a special type of
     * attachment that transparently loads in another model as a child of the
     * attachment itself.
     */
    String MODEL_TYPE_ID = "MODEL";

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
     * Gets the plugin that provides this attachment type. By default queries the Class
     * Loader for this type's class to obtain this information.
     *
     * @return Plugin that provides this attachment type
     */
    default Plugin getPlugin() {
        return CommonUtil.getPluginByClass(this.getClass());
    }

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
     * Gets a priority which is used to sort this attachment type in the list of
     * selectable attachment types in the editor. By default returns 0. Types
     * with equal sort priority are then sorted alphabetically by name.
     *
     * @return Sort priority, higher priority types end in the back of the list
     */
    default double getSortPriority() { return 0.0; }

    /**
     * Gets whether this attachment type is shown inside the Attachment Editor's
     * attachment type list. By default checks for permission.
     *
     * @param player Player viewing the attachments
     * @return True if listed in the attachment editor
     */
    default boolean isListed(Player player) {
        return hasPermission(player);
    }

    /**
     * Gets whether a particular Player has permission to see, create and import this
     * type of attachment.
     *
     * @param player Player to check
     * @return True if the player has permission to edit/view/create this attachment type
     */
    default boolean hasPermission(Player player) {
        return true;
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
     * Called before configuration is loaded in by attachments, and before
     * {@link #createAppearanceTab(MapWidgetTabView.Tab, MapWidgetAttachmentNode)}
     * is called, to perform any needed configuration migrations. If your attachment
     * includes older configurations that have since changed, migrate those to the
     * new configuration format here. By default this method is a no-op.
     *
     * @param config Configuration to migrate
     */
    default void migrateConfiguration(ConfigurationNode config) {
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
     * @param tab The tab the appearance menu is created inside of
     * @param attachment The attachment node element, which includes attachment configuration
     */
    default void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
    }

    /**
     * Called when the position menu is shown for this type of attachment. Additional
     * rows can be added/inserted into the position menu relevant for this
     * attachment type.
     *
     * @param builder Builder for the position menu
     */
    default void createPositionMenu(PositionMenu.Builder builder) {
    }

    /**
     * Creates the controller for this attachment type.
     * Configuration will be loaded into this controller at a later stage.
     * 
     * @param config of the attachment
     * @return controller
     */
    Attachment createController(ConfigurationNode config);

    /**
     * Called when this type is registered in a registry
     * 
     * @param registry in which it was registered
     */
    default void onRegister(AttachmentTypeRegistry registry) {}

    /**
     * Called when this type is unregistered (removed) from a registry
     * 
     * @param registry from which it was unregistered
     */
    default void onUnregister(AttachmentTypeRegistry registry) {}
}
