package com.bergerkiller.bukkit.tc.attachments.config;

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
}
