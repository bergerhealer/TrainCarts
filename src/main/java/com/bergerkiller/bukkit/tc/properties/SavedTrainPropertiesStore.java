package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.config.FileConfiguration;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Stores the train and cart properties for trains that have been saved using /train save.
 * These properties can also be used on spawner signs, or fused into spawning items.
 */
public class SavedTrainPropertiesStore {
    private final FileConfiguration savedTrainsConfig;
    private final List<String> names = new ArrayList<String>();
    private boolean changed = false;

    public SavedTrainPropertiesStore(String filename) {
        this.savedTrainsConfig = new FileConfiguration(filename);
        this.savedTrainsConfig.load();
        this.names.addAll(this.savedTrainsConfig.getKeys());
    }

    public void save(boolean autosave) {
        if (autosave && !this.changed) {
            return;
        }
        this.savedTrainsConfig.save();
        this.changed = false;
    }

    /**
     * Saves the train information under a name
     * 
     * @param group to save
     * @param name to save as
     */
    public void save(MinecartGroup group, String name) {
        this.changed = true;
        ConfigurationNode config = this.savedTrainsConfig.getNode(name);
        config.clear();

        group.getProperties().save(config);
        config.remove("carts");

        List<ConfigurationNode> cartConfigList = new ArrayList<ConfigurationNode>();
        for (MinecartMember<?> member : group) {
            ConfigurationNode cartConfig = new ConfigurationNode();
            member.getProperties().save(cartConfig);
            cartConfig.set("entityType", member.getEntity().getType());
            cartConfig.set("flipped", member.getOrientationForward().dot(FaceUtil.faceToVector(member.getDirection())) < 0.0);
            cartConfig.remove("owners");
            cartConfigList.add(cartConfig);
        }
        config.setNodeList("carts", cartConfigList);

        this.names.remove(name);
        this.names.add(name);
    }

    /**
     * Gets the configuration for a saved train
     * 
     * @param name of the saved train
     * @return configuration
     */
    public ConfigurationNode getConfig(String name) {
        if (!this.savedTrainsConfig.isNode(name)) {
            return null;
        }
        return this.savedTrainsConfig.getNode(name);
    }

    /**
     * Attempts to find a String token that starts with the name of a saved train
     * 
     * @param text to find a name in
     * @return name found, null if none found
     */
    public String findName(String text) {
        String foundName = null;
        for (String name : this.names) {
            if (text.startsWith(name) && (foundName == null || name.length() > foundName.length())) {
                foundName = name;
            }
        }
        return foundName;
    }
}
