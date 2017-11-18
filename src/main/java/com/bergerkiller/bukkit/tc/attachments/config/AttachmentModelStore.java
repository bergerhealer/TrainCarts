package com.bergerkiller.bukkit.tc.attachments.config;

import java.util.HashMap;

import com.bergerkiller.bukkit.common.config.FileConfiguration;

/**
 * Stores the different models that are available
 */
public class AttachmentModelStore {
    private final FileConfiguration modelConfig;
    private final HashMap<String, AttachmentModel> models;

    public AttachmentModelStore(String filePath) {
        this.modelConfig = new FileConfiguration(filePath);
        this.models = new HashMap<String, AttachmentModel>();
    }

    public void load() {
        //modelConfig.load();

    }

    public void save(boolean autoSave) {
        //modelConfig.save();

    }

    /**
     * Gets an attachment model by name
     * 
     * @param name of the attachment model to get
     * @return attachment model, null if not found
     */
    public AttachmentModel get(String name) {
        return this.models.get(name);
    }

    /**
     * Stores a model, replacing anything that was stored before under this name
     * 
     * @param name
     * @param model
     */
    public void store(String name, AttachmentModel model) {
        this.models.put(name, model);
    }
}
