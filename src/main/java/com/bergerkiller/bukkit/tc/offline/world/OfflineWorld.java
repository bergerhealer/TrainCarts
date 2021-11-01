package com.bergerkiller.bukkit.tc.offline.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Stores information about an offline world. The world uuid is always
 * available, with the Bukkit world also efficiently accessible
 * if the world happens to be loaded. If not, <i>null</i> is returned.
 */
public class OfflineWorld {
    public static final OfflineWorld NONE = new OfflineWorld(); // Speeds up of(world) in many places
    private static OfflineWorld cacheLastReturned = NONE; // Speeds up of(world)
    private static Map<UUID, OfflineWorld> worlds = new HashMap<UUID, OfflineWorld>();
    static final Function<UUID, Supplier<World>> toWorldSupplierFuncDefault = uuid -> (() -> Bukkit.getWorld(uuid));
    private static Function<UUID, Supplier<World>> toWorldSupplierFunc = toWorldSupplierFuncDefault;
    private final int hashCode;
    private final UUID worldUUID;
    protected Supplier<World> loadedWorldSupplier;

    /**
     * Installs a new factory for suppliers of the loaded Bukkit world given a world uuid.
     *
     * @param newToWorldSupplierFunc
     */
    static void setLoadedWorldSupplier(Function<UUID, Supplier<World>> newToWorldSupplierFunc) {
        toWorldSupplierFunc = newToWorldSupplierFunc;
        for (OfflineWorld world : worlds.values()) {
            world.loadedWorldSupplier = newToWorldSupplierFunc.apply(world.worldUUID);
        }
    }

    /**
     * Gets the OfflineWorld for a world with the specified
     * world uuid.
     *
     * @param worldUUID
     * @return OfflineWorld instance
     */
    public static OfflineWorld of(UUID worldUUID) {
        return worlds.computeIfAbsent(worldUUID, OfflineWorld::new);
    }

    /**
     * Gets the OfflineWorld for a given Bukkit World
     *
     * @param world
     * @return world
     */
    public static OfflineWorld of(World world) {
        if (cacheLastReturned.loadedWorldSupplier.get() == world) {
            return cacheLastReturned;
        }
        return cacheLastReturned = of(world.getUID());
    }

    private OfflineWorld() {
        this.worldUUID = null;
        this.hashCode = 0;
        this.loadedWorldSupplier = () -> null;
    }

    private OfflineWorld(UUID worldUUID) {
        this.worldUUID = worldUUID;
        this.hashCode = worldUUID.hashCode();
        this.loadedWorldSupplier = toWorldSupplierFunc.apply(worldUUID);
    }

    /**
     * Gets the unique world's UUID
     *
     * @return world uuid
     */
    public UUID getUniqueId() {
        return this.worldUUID;
    }

    /**
     * Gets whether this offline world is loaded
     *
     * @return True if loaded
     */
    public boolean isLoaded() {
        return this.loadedWorldSupplier.get() != null;
    }

    /**
     * Gets the loaded Bukkit World instance, if this world
     * is currently loaded. If not, returns <i>null</i>
     *
     * @return Bukkit World, or null if not loaded
     */
    public World getLoadedWorld() {
        return this.loadedWorldSupplier.get();
    }

    /**
     * Gets the OfflineBlock of a Block on this world
     *
     * @param position Coordinates of the Block
     * @return OfflineBlock for the Block at these coordinates
     */
    public OfflineBlock getBlockAt(IntVector3 position) {
        return new OfflineBlock(this, position);
    }

    /**
     * Gets the OfflineBlock of a Block on this world
     *
     * @param x X-coordinate of the Block
     * @param y Y-coordinate of the Block
     * @param z Z-coordinate of the Block
     * @return OfflineBlock for the Block at these coordinates
     */
    public OfflineBlock getBlockAt(int x, int y, int z) {
        return new OfflineBlock(this, new IntVector3(x, y, z));
    }

    /**
     * Gets a block at the given coordinates, if this offline
     * world is loaded. If not, returns null.
     *
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param z Z-coordinate
     * @return Block on this world at these coordinates, or null if this
     *         world is not loaded.
     */
    public Block getLoadedBlockAt(int x, int y, int z) {
        World world = this.loadedWorldSupplier.get();
        return (world == null) ? null : world.getBlockAt(x, y, z);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    @Override
    public String toString() {
        World world = this.loadedWorldSupplier.get();
        if (world != null) {
            return "OfflineWorld{uuid=" + this.worldUUID + ", name=" + world.getName() + "}";
        } else {
            return "OfflineWorld{uuid=" + this.worldUUID + "}";
        }
    }
}
