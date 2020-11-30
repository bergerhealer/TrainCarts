package com.bergerkiller.bukkit.tc.properties.standard;

import com.bergerkiller.bukkit.tc.properties.collision.CollisionConfig;

/**
 * Holds a <b>copy</b> of the train properties stored in YAML configuration
 * for faster access at runtime. Properties that aren't accessed
 * very often aren't stored here.
 */
public class FieldBackedStandardTrainPropertiesHolder {
    double speedLimit;
    CollisionConfig collision = CollisionConfig.DEFAULT;
}
