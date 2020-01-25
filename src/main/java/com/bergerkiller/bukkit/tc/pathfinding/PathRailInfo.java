package com.bergerkiller.bukkit.tc.pathfinding;

public enum PathRailInfo {
    /** No relevant signs */
    NONE,
    /** Blocker sign, movement is halted */
    BLOCKED,
    /** Node here, a destination or switcher sign is present */
    NODE
}
