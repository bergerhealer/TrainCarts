package com.bergerkiller.bukkit.tc.controller.components;

import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.cache.RailSignCache;
import com.bergerkiller.bukkit.tc.cache.RailSignCache.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.type.RailType;

/**
 * Represents a single piece of rails, occupying a single block where signs can be placed.
 * A rail piece consists of a rail block and rail type bound to a single Block in the minecraft world.
 * Extra properties about the rail are cached for faster lookup of this rarely changing information.
 */
public final class RailPiece {
    public static final RailPiece NONE = createWorldPlaceholder(null);
    private final RailType type;
    private final Block block;
    private final World world;
    private TrackedSign[] cachedSigns;

    private RailPiece(World world) {
        this.type = RailType.NONE;
        this.block = null;
        this.world = world;
        this.cachedSigns = null;
    }

    private RailPiece(RailType type, Block block) {
        this.type = type;
        this.block = block;
        this.world = block.getWorld();
        this.cachedSigns = null;
    }

    /**
     * Gets the type of rail
     * 
     * @return rail type
     */
    public RailType type() {
        return this.type;
    }

    /**
     * Gets the block where the rail is placed.
     * Returns null if there is no rail block referenced.
     * This is the case when {@link #isNone()} returns true.
     * 
     * @return rail block
     */
    public Block block() {
        return this.block;
    }

    /**
     * Gets whether this rail piece refers to missing rails.
     * The {@link #NONE} constant qualifies.
     * 
     * @return is none (block is null)
     */
    public boolean isNone() {
        return this.block == null;
    }

    /**
     * Asks the rail type whether the rail block of this rail piece uses block activation.
     * See also: {@link RailType#hasBlockActivation(Block)}.
     * 
     * @return True if block activation is used when driving on this rail piece
     */
    public boolean hasBlockActivation() {
        return this.type.hasBlockActivation(this.block);
    }

    /**
     * Gets the junctions that can possibly be switched on this rail piece
     * 
     * @return junctions
     */
    public List<RailJunction> getJunctions() {
        return this.type.getJunctions(this.block);
    }

    /**
     * Gets the world where the rail exists.
     * This will be available even when the rail block is not.
     * 
     * @return rail world
     */
    public World world() {
        return this.world;
    }

    /**
     * Retrieves an array of all signs active for this rail
     * 
     * @return array of signs
     */
    public TrackedSign[] signs() {
        if (this.cachedSigns == null) {
            this.cachedSigns = RailSignCache.discoverSigns(this.type, this.block);
        }
        return this.cachedSigns;
    }

    /**
     * Forces sign information to be refreshed the next time {@link #getSigns()} is called.
     */
    public void refreshSigns() {
        this.cachedSigns = null;
    }

    /**
     * Verifies the sign information that is cached still points to valid signs.
     * If this is not the case, all sign information will be refreshed the next time
     * {@link #getSigns()} is called.
     */
    public void verifySigns() {
        if (this.cachedSigns != null && !RailSignCache.verifySigns(this.cachedSigns)) {
            this.cachedSigns = null;
        }
    }

    @Override
    public int hashCode() {
        if (this.block == null) {
            return 0;
        } else {
            int hash = 17;
            hash = hash * 31 + System.identityHashCode(this.world);
            hash = hash * 31 + this.block.getX();
            hash = hash * 31 + this.block.getY();
            hash = hash * 31 + this.block.getZ();
            return hash;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof RailPiece) {
            RailPiece other = (RailPiece) o;
            return this.type == other.type &&
                   this.world == other.world &&
                   this.block == null ? (other.block == null) :
                       ( this.block.getX() == other.block.getX() &&
                         this.block.getY() == other.block.getY() &&
                         this.block.getZ() == other.block.getZ() );
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        if (this.block == null) {
            return "{" + this.type + " ?/?/?}";
        } else {
            return "{" +
                      this.type + " " +
                      this.block.getX() + "/" +
                      this.block.getY() + "/" +
                      this.block.getZ() +
                   "}";
        }
    }

    /**
     * Creates a new RailBlock for a type and block
     * 
     * @param type
     * @param block
     * @return RailPiece
     */
    public static RailPiece create(RailType type, Block block) {
        return new RailPiece(type, block);
    }

    /**
     * Creates a new RailBlock for a block. The rail type is inferred
     * from the rail block.
     * 
     * @param block
     * @return RailPiece
     */
    public static RailPiece create(Block block) {
        return create(RailType.getType(block), block);
    }

    /**
     * Creates a RailBlock that has no rail type or block, and acts as a placeholder
     * to define for a RailState what world is used.
     * 
     * @param world
     * @return rail block defining the world
     */
    public static RailPiece createWorldPlaceholder(World world) {
        return new RailPiece(world);
    }
}
