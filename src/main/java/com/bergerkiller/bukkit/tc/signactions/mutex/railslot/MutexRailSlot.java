package com.bergerkiller.bukkit.tc.signactions.mutex.railslot;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneSlotType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A single occupied rail within the influence of a (smart) mutex zone.
 * Stores the tick timestamp when the rail slot was locked, and whether
 * this was a 'full' (normal mutex) lock or not.<br>
 * <br>
 * Externally the rail block and whether it was (attempted) to be locked
 * as a smart mutex is stored.
 */
public final class MutexRailSlot {
    /** Rail block coordinates */
    private final IntVector3 rail;
    /** The type of mutex zone slot behavior that locked this rail slot. */
    private MutexZoneSlotType type;
    /** Tick timestamp when this rail slot was last probed */
    private int ticksLastProbed;

    public MutexRailSlot(IntVector3 rail) {
        this.rail = rail;
        this.ticksLastProbed = -1; // Marked new
        this.type = MutexZoneSlotType.SMART;
    }

    void probe(int nowTicks) {
        this.ticksLastProbed = nowTicks;
    }

    void probe(MutexZoneSlotType type, int nowTicks) {
        if (type == MutexZoneSlotType.NORMAL) {
            this.type = type;
        }
        this.ticksLastProbed = nowTicks;
    }

    /**
     * Rail block coordinates
     *
     * @return rail block
     */
    public IntVector3 rail() {
        return this.rail;
    }

    /**
     * Gets the type of mutex zone slot behavior that (tried to) lock this
     * particular rail block.
     *
     * @return Mutex zone slot type
     */
    public MutexZoneSlotType type() {
        return this.type;
    }

    /**
     * Gets whether this rail is part of the 'full' mutex lock, which locks the entire
     * slot so no other train can enter at all.
     *
     * @return True if this rail locks fully
     */
    public boolean isFullLocking() {
        return this.type == MutexZoneSlotType.NORMAL;
    }

    /**
     * Gets whether this slot is newly added, and has not been probed before
     *
     * @return True if new
     */
    public boolean isNew() {
        return this.ticksLastProbed < 0;
    }

    /**
     * Gets the tick timestamp of when this rail block was last updated. This is when
     * the train actively probes this rail block and tells it to be entered.
     *
     * @return tick timestamp of last rail probe
     */
    public int ticksLastProbed() {
        return this.ticksLastProbed;
    }

    public void debugPrint(StringBuilder str) {
        str.append("[").append(rail.x).append("/").append(rail.y)
                .append("/").append(rail.z).append("]");
        str.append(" ").append(type.name());
    }

    public void writeTo(DataOutputStream stream) throws IOException {
        rail.write(stream);
        type.writeTo(stream);
    }

    public static MutexRailSlot read(DataInputStream stream) throws IOException {
        MutexRailSlot slot = new MutexRailSlot(IntVector3.read(stream));
        slot.type = MutexZoneSlotType.readFrom (stream);
        return slot;
    }
}
