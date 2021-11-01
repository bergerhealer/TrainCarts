package com.bergerkiller.bukkit.tc.offline.world;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.StreamUtil;

/**
 * Pairs an {@link OfflineWorld} with the block position. Can safely be used
 * as a key in hashmaps/sets.
 */
public final class OfflineBlock {
    private final OfflineWorld world;
    private final IntVector3 position;

    protected OfflineBlock(OfflineWorld world, IntVector3 position) {
        this.world = world;
        this.position = position;
    }

    /**
     * Gets an OfflineBlock representation of a Bukkit Block
     *
     * @param block Bukkit Block
     * @return Offline Block
     */
    public static OfflineBlock of(Block block) {
        return new OfflineBlock(OfflineWorld.of(block.getWorld()), new IntVector3(block));
    }

    /**
     * Gets the offline world where this block is at
     *
     * @return offline world
     */
    public OfflineWorld getWorld() {
        return this.world;
    }

    /**
     * Gets the unique ID of the World this OfflineBlock is on
     *
     * @return offline world UUID
     */
    public UUID getWorldUUID() {
        return this.world.getUniqueId();
    }

    /**
     * Gets the x/y/z coordinates where this block is at
     *
     * @return block coordinates
     */
    public IntVector3 getPosition() {
        return this.position;
    }

    /**
     * Gets the X-coordinate of the Offline Block
     *
     * @return Offline block X-coordinate
     */
    public int getX() {
        return this.position.x;
    }

    /**
     * Gets the Y-coordinate of the Offline Block
     *
     * @return Offline block Y-coordinate
     */
    public int getY() {
        return this.position.y;
    }

    /**
     * Gets the Z-coordinate of the Offline Block
     *
     * @return Offline block Z-coordinate
     */
    public int getZ() {
        return this.position.z;
    }

    /**
     * Gets the loaded Bukkit World where this block is at.
     * If this world is currently not loaded, returns <i>null</i>.
     *
     * @return loaded Bukkit World, or null if not loaded
     */
    public World getLoadedWorld() {
        return this.world.getLoadedWorld();
    }

    /**
     * Turns this OfflineBlock into a loaded Bukkit Block, if the world this
     * block is on is currently loaded.
     *
     * @return Block, or null if the world is currently not loaded
     */
    public Block getLoadedBlock() {
        return this.world.getLoadedBlockAt(this.position.x, this.position.y, this.position.z);
    }

    @Override
    public int hashCode() {
        return this.position.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof OfflineBlock) {
            OfflineBlock other = (OfflineBlock) o;
            return this.position.equals(other.position) &&
                   this.world.equals(other.world);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "{world=" + this.world + ", x=" + this.position.x + ", y=" +
                this.position.y + ", z=" + this.position.z + "}";
    }

    /**
     * Reads the details of an OfflineBlock from a data input stream
     *
     * @param stream Stream to read from
     * @return Decoded OfflineBlock
     * @throws IOException
     */
    public static OfflineBlock readFrom(DataInputStream stream) throws IOException {
        OfflineWorld world = OfflineWorld.of(StreamUtil.readUUID(stream));
        IntVector3 position = IntVector3.read(stream);
        return new OfflineBlock(world, position);
    }

    /**
     * Writes the details of an OfflineBlock to a data output stream
     *
     * @param stream Stream to write to
     * @param block Block to write to the stream
     * @throws IOException
     */
    public static void writeTo(DataOutputStream stream, OfflineBlock block) throws IOException {
        StreamUtil.writeUUID(stream, block.getWorldUUID());
        block.getPosition().write(stream);
    }
}
