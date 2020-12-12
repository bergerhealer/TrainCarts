package com.bergerkiller.bukkit.tc.properties.standard.category;

import java.util.Optional;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParseContext;
import com.bergerkiller.bukkit.tc.properties.api.PropertyParser;
import com.bergerkiller.bukkit.tc.properties.standard.fieldbacked.FieldBackedStandardTrainProperty;

/**
 * Controls whether trains keep chunks around them loaded, preventing the train
 * from unloading. Automatically loads the train when enabled for a currently
 * unloaded train.
 */
public final class KeepChunksLoadedProperty extends FieldBackedStandardTrainProperty<Boolean> {

    @PropertyParser("keeploaded|keepcloaded|loadchunks")
    public boolean parseKeepChunksLoaded(PropertyParseContext<Boolean> context) {
        return context.inputBoolean();
    }

    @Override
    public Boolean getDefault() {
        return Boolean.FALSE;
    }

    @Override
    public Boolean getData(TrainInternalData data) {
        return data.keepChunksLoaded;
    }

    @Override
    public void setData(TrainInternalData data, Boolean value) {
        data.keepChunksLoaded = value.booleanValue();
    }

    @Override
    public Optional<Boolean> readFromConfig(ConfigurationNode config) {
        return Util.getConfigOptional(config, "keepChunksLoaded", boolean.class);
    }

    @Override
    public void writeToConfig(ConfigurationNode config, Optional<Boolean> value) {
        Util.setConfigOptional(config, "keepChunksLoaded", value);
    }

    @Override
    public void onConfigurationChanged(TrainProperties properties) {
        super.onConfigurationChanged(properties);
        updateState(properties, this.get(properties));
    }

    @Override
    public void set(TrainProperties properties, Boolean value) {
        super.set(properties, value);
        updateState(properties, value.booleanValue());
    }

    private void updateState(TrainProperties properties, boolean keepLoaded) {
        // When turning keep chunks loaded on, load the train if presently unloaded
        if (keepLoaded) {
            properties.restore();
        }

        MinecartGroup group = properties.getHolder();
        if (group != null) {
            group.keepChunksLoaded(keepLoaded);
        }
    }
}
