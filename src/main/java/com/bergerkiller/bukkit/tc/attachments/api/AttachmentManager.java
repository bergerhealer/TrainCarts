package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.Collection;
import java.util.Map;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.config.CartAttachmentType;
import com.bergerkiller.bukkit.tc.attachments.control.PassengerController;

/**
 * Manages one or more trees of attachments on a more general level.
 * It controls aspects that are difficult for an attachment to do by itself,
 * in particular:
 * <ul>
 * <li>Creating new attachments from their configurations</li>
 * <li>Tracking what players can see the attachments, calling makeVisible
 * and makeHidden when required</li>
 * <li>Updating the position (root) transformation for the root attachments</li>
 * <li>Tracking the vehicle-passenger relationships of virtual entities inside attachments</li>
 * <li>Seating players inside seat-capable attachments</li>
 * <li>Providing context when needed (for example, that of a Traincarts Minecart)</li>
 * </ul>
 */
public interface AttachmentManager {

    /**
     * Gets the internal state providing some of the attachment managing API's
     * 
     * @return internal state
     */
    AttachmentManagerInternalState getInternalState();

    /**
     * Gets the world the attachments are in
     * 
     * @return world
     */
    org.bukkit.World getWorld();

    default void clearPassengerControllers() {
        this.getInternalState().passengerControllers.clear();
    }

    default void removePassengerController(Player viewer) {
        this.getInternalState().passengerControllers.remove(viewer);
    }

    default PassengerController getPassengerController(Player viewer) {
        return getPassengerController(viewer, true);
    }

    default PassengerController getPassengerController(Player viewer, boolean createIfNotFound) {
        Map<Player, PassengerController> mapping = this.getInternalState().passengerControllers;
        PassengerController controller = mapping.get(viewer);
        if (controller == null && createIfNotFound) {
            controller = new PassengerController(viewer);
            mapping.put(viewer, controller);
        }
        return controller;
    }

    default Collection<PassengerController> getPassengerControllers() {
        return this.getInternalState().passengerControllers.values();
    }

    /**
     * Creates a new attachment by loading it from configuration.
     * No further operations, such as attaching it, are performed yet.
     * 
     * @param config
     * @return created attachment, null if no attachment could be detected in the config
     */
    default Attachment createAttachment(ConfigurationNode config) {
        CartAttachmentType attachmentType = config.get("type", CartAttachmentType.EMPTY);
        if (attachmentType == null) {
            return null; // invalid!
        }

        Attachment attachment = attachmentType.createAttachment();
        AttachmentInternalState state = attachment.getInternalState();
        state.manager = this;
        state.onLoad(config);        

        for (ConfigurationNode childNode : config.getNodeList("attachments")) {
            Attachment child = createAttachment(childNode);
            if (child != null) {
                attachment.addChild(child);
            }
        }

        return attachment;
    }
}
