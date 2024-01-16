package com.bergerkiller.bukkit.tc.signactions.mutex;

import com.bergerkiller.bukkit.tc.Util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Types of mutex zone slot behaviors that exist
 */
public enum MutexZoneSlotType {
    /** Normal slot type. If any block within is visited, no other train can enter */
    NORMAL,
    /** Smart slot type. Checks that rail blocks are crossed to block only conflicting paths */
    SMART;

    private static final MutexZoneSlotType[] SLOT_TYPES = MutexZoneSlotType.values();

    public static MutexZoneSlotType readFrom(InputStream stream) throws IOException {
        int typeOrd = Util.readVariableLengthInt(stream);
        return (typeOrd >= 0 && typeOrd < SLOT_TYPES.length) ?
                SLOT_TYPES[typeOrd] : NORMAL;
    }

    public void writeTo(OutputStream stream) throws IOException {
        Util.writeVariableLengthInt(stream, ordinal());
    }


}
