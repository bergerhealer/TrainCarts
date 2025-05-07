package com.bergerkiller.bukkit.tc.controller.persistence;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The /tag tags set for the Entity, called "Scoreboard tags" in Bukkit. Since 1.10.2.
 */
public class EntityTagsPersistentCartAttribute implements PersistentCartAttribute<CommonMinecart<?>> {
    @Override
    public void save(CommonMinecart<?> entity, ConfigurationNode data) {
        Set<String> tags = entity.getEntity().getScoreboardTags();
        if (!tags.isEmpty()) {
            data.set("entityTags", new ArrayList<>(tags));
        } else {
            data.remove("entityTags");
        }
    }

    @Override
    public void load(CommonMinecart<?> commonEntity, ConfigurationNode data) {
        if (data.contains("entityTags")) {
            Entity entity = commonEntity.getEntity();

            Set<String> existingTags = entity.getScoreboardTags();
            List<String> tags = data.getList("entityTags", String.class);
            for (String existingTag : existingTags) {
                if (!tags.contains(existingTag)) {
                    entity.removeScoreboardTag(existingTag);
                }
            }
            for (String tag : tags) {
                if (!existingTags.contains(tag)) {
                    entity.addScoreboardTag(tag);
                }
            }
        }
    }
}
