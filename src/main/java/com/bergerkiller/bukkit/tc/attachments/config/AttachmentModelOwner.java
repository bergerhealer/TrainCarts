package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;

/**
 * Someones that owns a model, and is interested in the changes for that model
 */
public interface AttachmentModelOwner {

    /**
     * Called when a single leaf of the model tree hierarchy has changed, and needs
     * to be reloaded.
     * 
     * @param model       The model that changed
     * @param targetPath  The path to the leaf of the model tree that changed
     * @param config      The new configuration for this model
     */
    void onModelNodeChanged(AttachmentModel model, int[] targetPath, ConfigurationNode config);

    /**
     * Called when the entire model has changed and needs to be reloaded in its entirety
     * 
     * @param model that changed
     */
    void onModelChanged(AttachmentModel model);
}
