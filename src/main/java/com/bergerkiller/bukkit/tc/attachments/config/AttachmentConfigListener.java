package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

import java.util.function.Consumer;

/**
 * Listens for changes that happen in an attachment configuration. Listeners
 * can be added to the {@link AttachmentConfigTracker}, after which its
 * callbacks will be called when attachments are added/removed/changed.
 */
public interface AttachmentConfigListener {
    /**
     * Called when a change to an attachment was detected. Changes are notified
     * in a logical sequence. Can be overridden to handle multiple different
     * {@link AttachmentConfig.ChangeType Change Types} at once.
     *
     * @param change Change that occurred
     */
    default void onChange(AttachmentConfig.Change change) {
        change.callListener(this);
    }

    /**
     * Called when an attachment, and all its child attachments, need to be
     * added/created. The attachment child index can be used to as the location
     * in the list it should be added.
     *
     * @param attachment Attachment that was removed
     */
    default void onAttachmentAdded(AttachmentConfig attachment) {
    }

    /**
     * Called when an attachment, and all its child attachments, need to be
     * removed/destroyed.
     *
     * @param attachment Attachment that was removed
     */
    default void onAttachmentRemoved(AttachmentConfig attachment) {
    }

    /**
     * Called when an attachment configuration changed. The attachment should
     * have its configuration reloaded. Is not called if the attachment
     * type changed, in which case a remove-and-add is done.
     *
     * @param attachment Attachment whose configuration changed
     */
    default void onAttachmentChanged(AttachmentConfig attachment) {
    }

    /**
     * Called when some sort of action must be performed on all live {@link Attachment}
     * objects tied with a particular attachment configuration. Implementers should
     * pass the Attachment that belongs to this configuration to the action specified.
     * No error handling has to be performed.<br>
     * <br>
     * Not implementing this method will mean no actions can be performed on live
     * attachments. If this listener is merely listening for changes, then that is
     * fine. The caller might store the attachment supplied to perform other actions
     * later.<br>
     * <br>
     * Can only be called from the main thread.
     *
     * @param attachment Attachment configuration for which an action must be performed
     * @param action Action to perform on the live {@link Attachment}
     */
    default void onAttachmentAction(AttachmentConfig attachment, Consumer<Attachment> action) {
    }
}
