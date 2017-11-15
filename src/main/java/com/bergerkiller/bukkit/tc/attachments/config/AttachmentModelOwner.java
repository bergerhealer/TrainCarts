package com.bergerkiller.bukkit.tc.attachments.config;

/**
 * Someones that owns a model, and is interested in the changes for that model
 */
public interface AttachmentModelOwner {

    void onModelChanged(AttachmentModel model);
}
