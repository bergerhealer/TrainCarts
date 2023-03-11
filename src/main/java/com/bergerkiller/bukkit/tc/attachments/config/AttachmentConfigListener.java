package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

import java.util.function.Consumer;

/**
 * Listens for changes that happen in an attachment configuration. Listeners
 * can be added to the {@link AttachmentConfigTracker}, after which its
 * callbacks will be called when attachments are added/removed/changed.<br>
 * <br>
 * After a series of these events have been handled and the listener is
 * up-to-date with the current configuration,
 * {@link #onSynchronized(AttachmentConfig)} is called with the root attachment
 * configuration.
 */
public interface AttachmentConfigListener {
    /**
     * Called when a change to an attachment was detected. Changes are notified
     * in a logical sequence. Can be overridden to handle multiple different
     * {@link AttachmentConfig.ChangeType Change Types} at once. All series
     * of changes end in {@link AttachmentConfig.ChangeType#SYNCHRONIZED}.
     *
     * @param change Change that occurred
     */
    default void onChange(AttachmentConfig.Change change) {
        change.changeType().callback().accept(this, change.attachment());
    }

    /**
     * Called when an attachment, and all its child attachments, need to be
     * added/created. The attachment child index can be used to as the location
     * in the list it should be added.
     *
     * @param attachmentConfig Attachment that was removed
     */
    default void onAttachmentAdded(AttachmentConfig attachmentConfig) {
    }

    /**
     * Called when an attachment, and all its child attachments, need to be
     * removed/destroyed.
     *
     * @param attachmentConfig Attachment that was removed
     */
    default void onAttachmentRemoved(AttachmentConfig attachmentConfig) {
    }

    /**
     * Called when an attachment configuration changed. The attachment should
     * have its configuration reloaded. Is not called if the attachment
     * type changed, in which case a remove-and-add is done.
     *
     * @param attachmentConfig Attachment whose configuration changed
     */
    default void onAttachmentChanged(AttachmentConfig attachmentConfig) {
    }

    /**
     * Called after all attachment removals, additions and changes have completed and
     * this listener is up-to-date with the current state of the configuration. Callers
     * can use the input root attachment to synchronize their state.
     *
     * @param rootAttachmentConfig Root Attachment after all changes have been applied
     */
    default void onSynchronized(AttachmentConfig rootAttachmentConfig) {
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
     * @param attachmentConfig Attachment configuration for which an action must be performed
     * @param action Action to perform on the live {@link Attachment}
     */
    default void onAttachmentAction(AttachmentConfig attachmentConfig, Consumer<Attachment> action) {
    }
}
