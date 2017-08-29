package com.bergerkiller.bukkit.tc.utils;

import java.util.Locale;

/**
 * Type of Train Slowdown that can occur
 */
public enum SlowdownMode {
    FRICTION, GRAVITY;

    public final String getKey() {
        return this.name().toLowerCase(Locale.ENGLISH);
    }
}
