package com.bergerkiller.bukkit.tc.offline.sign;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

/**
 * Stores the OfflineBlock position of a sign as well as the text side.
 * Can be used as a key in HashMaps.
 */
public final class OfflineSignSide {
    private final OfflineBlock block;
    private final boolean front;

    private OfflineSignSide(OfflineBlock block, boolean front) {
        this.block = block;
        this.front = front;
    }

    /**
     * Creates a new OfflineSignSide for a sign block and front-back value
     *
     * @param signBlock OfflineBlock where the sign is
     * @param front True if it is the front-side of the sign, False if back
     * @return new OfflineSignSide
     */
    public static OfflineSignSide of(OfflineBlock signBlock, boolean front) {
        return new OfflineSignSide(signBlock, front);
    }

    /**
     * Creates a new OfflineSignSide for a sign block and front-back value
     *
     * @param signBlock Block where the sign is
     * @param front True if it is the front-side of the sign, False if back
     * @return new OfflineSignSide
     */
    public static OfflineSignSide of(Block signBlock, boolean front) {
        return new OfflineSignSide(OfflineBlock.of(signBlock), front);
    }

    /**
     * Creates a new OfflineSignSide for a real tracked sign. Sign must be a real
     * sign, or an error is thrown.
     *
     * @param sign TrackedSign
     * @return new OfflineSignSide
     */
    public static OfflineSignSide of(RailLookup.TrackedSign sign) {
        if (sign instanceof RailLookup.TrackedRealSign) {
            return of(sign.signBlock, ((RailLookup.TrackedRealSign) sign).isFrontText());
        } else {
            throw new IllegalArgumentException("Input TrackedSign is not of a real sign");
        }
    }

    /**
     * Gets the offline world, which stores the world uuid
     * and the loaded world, if loaded.
     *
     * @return offline world
     */
    public OfflineWorld getWorld() {
        return this.block.getWorld();
    }

    /**
     * Gets the offline block where this sign is located
     *
     * @return offline block
     */
    public OfflineBlock getBlock() {
        return this.block;
    }

    /**
     * Gets the unique id of the world this sign is on
     *
     * @return world uuid
     */
    public UUID getWorldUUID() {
        return this.block.getWorldUUID();
    }

    /**
     * Gets the world this sign on, if this world is currently
     * loaded. If not, null is returned.
     *
     * @return World this offline sign is on
     */
    public World getLoadedWorld() {
        return this.block.getLoadedWorld();
    }

    /**
     * Gets the x/y/z block coordinates where the sign is located
     *
     * @return block position
     */
    public IntVector3 getPosition() {
        return this.block.getPosition();
    }

    /**
     * Gets the block this sign is at. If the world this sign is
     * on is currently not loaded, returns null. Use {@link #getPosition()}
     * if the position is all that is needed.
     *
     * @return Block the sign is on
     */
    public Block getLoadedBlock() {
        return this.block.getLoadedBlock();
    }

    /**
     * Gets whether this is the front side (true) or the back side
     * (false, &gt;= MC 1.20 only)
     *
     * @return True if front side
     */
    public boolean isFrontText() {
        return front;
    }

    @Override
    public int hashCode() {
        return block.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof OfflineSignSide) {
            OfflineSignSide side = (OfflineSignSide) o;
            return block.equals(side.block) && front == side.front;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "OfflineSignSide{block=" + block + ", side=" + (front ? "front" : "back") + "}";
    }
}
