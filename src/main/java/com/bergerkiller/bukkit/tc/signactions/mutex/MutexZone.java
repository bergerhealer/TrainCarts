package com.bergerkiller.bukkit.tc.signactions.mutex;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import org.bukkit.entity.Player;

public abstract class MutexZone {
    public final OfflineBlock signBlock;
    public final boolean signFront;
    public final String statement;
    public final MutexZoneSlot slot;
    public final MutexZoneSlotType type;
    private Boolean leversDown = null; // Avoids excessive block access

    protected MutexZone(OfflineBlock signBlock, boolean signFront, MutexZoneSlotType type, String name, String statement) {
        this.signBlock = signBlock;
        this.signFront = signFront;
        this.statement = statement;
        this.slot = MutexZoneCache.findSlot(name, this);
        this.type = type;
    }

    protected abstract void addToWorld(MutexZoneCacheWorld world);

    public abstract boolean containsBlock(IntVector3 block);
    public abstract boolean isNearby(IntVector3 block, int radius);
    public abstract void forAllContainedChunks(ChunkCoordConsumer action);
    public abstract long showDebugColorSeed();
    public abstract void showDebug(Player player, Color color);
    protected abstract void setLeversDown(boolean down);

    /**
     * Gets the amount of additional spacing trains should keep between themselves
     * and this mutex zone
     *
     * @param group Group for which spacing is requested
     * @return Extra spacing
     */
    public double getSpacing(MinecartGroup group) {
        return 0.0;
    }

    /**
     * Performs a hit collision test from a starting position moving into the direction motion vector
     * specified. Taken from OrientedBoundingBox in BKCommonLib, without the rotating part.
     *
     * @param posX
     * @param posY
     * @param posZ
     * @param motX
     * @param motY
     * @param motZ
     * @return Distance to this mutex zone's bounding box. or {@link Double#MAX_VALUE} if not hit
     */
    public abstract double hitTest(double posX, double posY, double posZ,
                                   double motX, double motY, double motZ);

    public Block getSignBlock() {
        return this.signBlock.getLoadedBlock();
    }

    /**
     * Gets whether this mutex was declared on the front side of the sign (true) or back
     * side (false, &gt;= MC 1.20 only)
     *
     * @return True if this mutex was declared on the front side of the sign
     */
    public boolean isSignFrontText() {
        return this.signFront;
    }

    public static IntVector3 getPosition(SignActionEvent info) {
        Location middlePos = info.getCenterLocation();
        if (middlePos != null) {
            return new IntVector3(middlePos);
        } else {
            return new IntVector3(info.getBlock());
        }
    }

    protected void setLevers(boolean down) {
        // Avoid spamming block data calls lots of times per tick
        // This function is just called a lot...
        {
            Boolean bState = Boolean.valueOf(down);
            if (leversDown != bState) {
                leversDown = bState;
            } else {
                return;
            }
        }

        setLeversDown(down);
    }

    /**
     * Called every tick while a certain member of a train is using it
     *
     * @param group Group
     */
    public void onUsed(MinecartGroup group) {
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{sign=" + signBlock + ",type=" + type.name() + "}";
    }

    public static MutexZone createCuboid(OfflineWorld world, IntVector3 signPosition, boolean isFrontText, MutexSignMetadata metadata) {
        return new MutexZoneCuboid(world.getBlockAt(signPosition), isFrontText, metadata.start, metadata.end, metadata.type, metadata.name, metadata.statement);
    }

    @FunctionalInterface
    public interface ChunkCoordConsumer {
        void accept(int cx, int cz);
    }
}
