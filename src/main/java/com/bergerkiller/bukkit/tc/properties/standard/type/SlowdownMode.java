package com.bergerkiller.bukkit.tc.properties.standard.type;

import java.util.Locale;

/**
 * Type of Train Slowdown that can occur
 */
public enum SlowdownMode {
    /** Gradual slowing down of trains as they drive on rails */
    FRICTION,
    /** Slowing down or speeding up as the train moves up or down slopes */
    GRAVITY;

    public final String getKey() {
        return this.name().toLowerCase(Locale.ENGLISH);
    }
}
