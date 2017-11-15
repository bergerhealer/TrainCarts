package com.bergerkiller.bukkit.tc.attachments.config;

import com.bergerkiller.bukkit.common.config.FileConfiguration;

/**
 * Stores the different models that are available
 */
public class AttachmentModels {
    private static FileConfiguration modelConfig = null;

    public static void init(String filePath) {
        modelConfig = new FileConfiguration(filePath);
        modelConfig.load();
        //TODO
    }

    public static void save(boolean autoSave, String filePath) {
        //TODO
    }

    
}
