package com.bergerkiller.bukkit.tc.attachments.api;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * Manages one or more trees of attachments on a more general level.
 * It controls aspects that are difficult for an attachment to do by itself,
 * in particular:
 * <ul>
 * <li>Creating new attachments from their configurations</li>
 * <li>Tracking what players can see the attachments, calling makeVisible
 * and makeHidden when required</li>
 * <li>Updating the position (root) transformation for the root attachments</li>
 * <li>Seating players inside seat-capable attachments</li>
 * <li>Providing context when needed (for example, that of a Traincarts Minecart)</li>
 * </ul>
 */
public interface AttachmentManager {

    /**
     * Gets the world the attachments are in
     * 
     * @return world
     */
    org.bukkit.World getWorld();

    /**
     * Gets the {@link AttachmentTypeRegistry} used to find and create new attachments from
     * configuration.
     * 
     * @return attachment type registry
     */
    default AttachmentTypeRegistry getTypeRegistry() {
        return AttachmentTypeRegistry.instance();
    }

    /**
     * Creates a new attachment by loading it from configuration.
     * No further operations, such as attaching it, are performed yet.
     * 
     * @param config
     * @return created attachment, null if no attachment could be detected in the config
     */
    default Attachment createAttachment(ConfigurationNode config) {
        AttachmentType attachmentType = getTypeRegistry().fromConfig(config);
        if (attachmentType == null) {
            return null; // invalid!
        }

        Attachment attachment = attachmentType.createController(config);
        AttachmentInternalState state = attachment.getInternalState();
        state.manager = this;
        state.onLoad(this.getClass(), attachmentType, config);

        for (ConfigurationNode childNode : config.getNodeList("attachments")) {
            Attachment child = createAttachment(childNode);
            if (child != null) {
                attachment.addChild(child);
            }
        }

        return attachment;
    }
}
