package com.bergerkiller.bukkit.tc.properties.standard;

import java.util.EnumSet;

import com.bergerkiller.bukkit.tc.properties.collision.CollisionConfig;
import com.bergerkiller.bukkit.tc.utils.SignSkipOptions;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

/**
 * Holds a <b>copy</b> of the train properties stored in YAML configuration
 * for faster access at runtime. Properties that aren't accessed
 * very often aren't stored here.
 */
public class FieldBackedStandardTrainPropertiesHolder {
    double speedLimit;
    CollisionConfig collision;
    final EnumSet<SlowdownMode> slowdown = EnumSet.allOf(SlowdownMode.class);
    SignSkipOptions signSkipOptionsData;
}
