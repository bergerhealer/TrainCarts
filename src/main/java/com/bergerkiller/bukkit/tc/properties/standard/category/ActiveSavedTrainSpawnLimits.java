package com.bergerkiller.bukkit.tc.properties.standard.category;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Saved train names that contain spawn limits that this train is part of with a single
 * count. An empty list means this train does not partake in any spawn limits.
 */
public class ActiveSavedTrainSpawnLimits extends FieldBackedStandardTrainProperty<List<String>> {
    @Override
    public List<String> getDefault() {
        return Collections.emptyList();
    }

    public void addSavedTrainToConfig(ConfigurationNode config, String savedTrainName) {
        List<String> names = config.getList("activeSavedTrainSpawnLimits", String.class);
        if (!names.contains(savedTrainName)) {
            names.add(savedTrainName);
        }
    }

    @Override
    public Optional<List<String>> readFromConfig(ConfigurationNode config) {
        if (config.contains("activeSavedTrainSpawnLimits")) {
            List<String> names = config.getList("activeSavedTrainSpawnLimits", String.class);
            names = Collections.unmodifiableList(new ArrayList<>(names)); // Safety. Should be immutable.
            return Optional.of(names);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<List<String>> value) {
        List<String> names;
        if (value.isPresent() && !(names = value.get()).isEmpty()) {
            config.set("activeSavedTrainSpawnLimits", names);
        } else {
            config.remove("activeSavedTrainSpawnLimits");
        }
    }

    @Override
    public List<String> getData(TrainInternalData data) {
        return data.activeSavedTrainSpawnLimits;
    }

    @Override
    public void setData(TrainInternalData data, List<String> value) {
        data.activeSavedTrainSpawnLimits = value;
    }
}
