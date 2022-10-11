package com.bergerkiller.bukkit.tc.signactions.mutex;

/**
 * Types of mutex zone slot behaviors that exist
 */
public enum MutexZoneSlotType {
    /** Normal slot type. If any block within is visited, no other train can enter */
    NORMAL,
    /** Smart slot type. Checks that rail blocks are crossed to block only conflicting paths */
    SMART
}
