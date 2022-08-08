package com.bergerkiller.bukkit.tc.rails;

import java.util.List;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.global.SignController;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCache;
import com.bergerkiller.bukkit.tc.signactions.mutex.MutexZoneCacheWorld;

/**
 * Retrieves and caches rails and information about rails, mapped to
 * the minecart and rail positions. The cache eliminates frequent lookups
 * using more expensive discovery methods. This lookup is of a single Bukkit
 * World.<br>
 * <br>
 * Per section of rail tied to rail block and type, the following information is cached:
 * <ul>
 * <li>Rail block and rail type
 * <li>Signs and their detected sign actions activated by them
 * <li>Minecart Members occupying the section of rails
 * </ul>
 * The cache stores information in memory for so long it is accessed, and the
 * cached information is automatically wiped when no longer used. The data is
 * validated every tick, such as by checking signs exist and the rail type still
 * detects the rail block as a valid rail.<br>
 * <br>
 * This lookup is not multi-thread safe and all access must be done from the main
 * Bukkit thread.<br>
 * <br>
 * It is very important to check {@link #isValid()} regularly, at least every tick, to make
 * sure the lookup is still valid. If it is not valid, it should be refreshed using
 * {@link #refresh()}.
 */
public interface WorldRailLookup {
    /**
     * World Rail Lookup used for non-loaded or disabled Worlds. Used by {@link RailPiece#NONE},
     * and when a particular World isn't loaded or available.
     */
    WorldRailLookup NONE = new WorldRailLookupNone();

    /**
     * Gets the World this WorldRailLookup is for. If the World is not (no longer) loaded,
     * returns null.
     *
     * @return World
     */
    World getWorld();

    /**
     * Gets the OfflineWorld this WorldRailLookup is for
     *
     * @return OfflineWorld
     */
    OfflineWorld getOfflineWorld();

    /**
     * Gets the Mutex Zone information of this world's rail lookup
     *
     * @return mutex zones
     * @see MutexZoneCache#forWorld(OfflineWorld)
     */
    MutexZoneCacheWorld getMutexZones();

    /**
     * Gets the Sign Controller of the world's rail lookup
     *
     * @return sign controller
     * @see SignController#forWorld(World)
     */
    SignControllerWorld getSignController();

    /**
     * Gets whether this World Rail Lookup is still valid, and can be used. This will return false
     * when the world it represents has unloaded, or the plugin shut down.
     *
     * @return True if still valid
     */
    boolean isValid();

    /**
     * Gets whether this World Rail Lookup is still valid, and is also meant for the World specified.
     * If this lookup is invalid or for a different World, returns false. This is useful for keeping
     * a rail lookup up-to-date for a changing world.
     *
     * @param world
     * @return True if this World Rail Lookup is valid and for the specified World
     */
    boolean isValidForWorld(World world);

    /**
     * Discovers the RailPieces that are active for controlling the movement of a Minecart
     * at the RailState position information specified. The caller should further filter which of
     * these rail pieces truly control the movement, but the returned array is efficiently
     * cached.
     *
     * @param state RailState with position information
     * @return Array of rail pieces active here, or an empty array if there are no rails nearby
     */
    RailPiece[] findAtStatePosition(RailState state);

    /**
     * Discovers the RailPieces that are active for controlling the movement of a Minecart
     * moving within the Block position specified. The caller should further filter which of
     * these rail pieces truly control the movement, but the returned array is efficiently
     * cached.
     *
     * @param positionBlock Block position
     * @return Array of rail pieces active here, or an empty array if there are no rails nearby
     */
    RailPiece[] findAtBlockPosition(OfflineBlock positionBlock);

    /**
     * API Note: you should never have to call this function. It's used internally by RailPiece.<br>
     * <br>
     * Looks up cached rail information about the provided rail block, if such information is currently
     * cached. If not, {@link RailLookup.CachedRailPiece#NONE} is returned instead.
     *
     * @param railOfflineBlock Rail offline block, key
     * @param railType Rail type
     * @return Rail piece information backed by this lookup cache, or NONE if missing.
     */
    RailLookup.CachedRailPiece lookupCachedRailPieceIfCached(final OfflineBlock railOfflineBlock,
                                                                    final RailType railType);

    /**
     * API Note: you should never have to call this function. It's used internally by RailPiece.<br>
     * <br>
     * Looks up cached rail information about the provided rail block. If needed a new entry
     * is created in the cache to store the cached information, so that members or signs at this
     * block can be found. This will also succeed when the rail type doesn't exist at this block.
     *
     * @param railOfflineBlock Rail offline block, key
     * @param railBlock Bukkit Block version of railOfflineBlock
     * @param railType Rail type
     * @return Rail piece information backed by this lookup cache. The information is verified.
     * @throws RailLookup.RailTypeNotRegisteredException If the specified RailType is unloaded
     */
    RailLookup.CachedRailPiece lookupCachedRailPiece(final OfflineBlock railOfflineBlock,
                                                     final Block railBlock,
                                                     final RailType railType);

    /**
     * Gets a List of members that are driving on a particular rail block. If there are no
     * members driving on the Block, an empty list is returned. The returned list is
     * unmodifiable.<br>
     * <br>
     * <b>Important note: </b>This is not very reliable, because the same rail block could be
     * controlling multiple rail types. It is more reliable to use {@link RailPiece#members()}
     *
     * @param railCoordinates Block coordinates of the Rail Block
     * @return List of members on this block
     */
    List<MinecartMember<?>> findMembersOnRail(IntVector3 railCoordinates);

    /**
     * Gets a List of members that are driving on a particular rail block. If there are no
     * members driving on the Block, an empty list is returned. The returned list is
     * unmodifiable.<br>
     * <br>
     * <b>Important note: </b>This is not very reliable, because the same rail block could be
     * controlling multiple rail types. It is more reliable to use {@link RailPiece#members()}
     *
     * @param railOfflineBlock
     * @return List of members on this block
     */
    List<MinecartMember<?>> findMembersOnRail(OfflineBlock railOfflineBlock);

    /**
     * Removes a particular member from all member lists of cached rail positions
     *
     * @param member
     */
    void removeMemberFromAll(MinecartMember<?> member);

    /**
     * Discovers the signs belonging to a particular rail.
     * Unlike {@link RailPiece#signs()} this method does not look
     * the information up from a cache
     * 
     * @param railType of the rail
     * @param railBlock of the rail
     * @return signs belonging to this rail
     * @throws RailLookup.RailTypeNotRegisteredException If the specified rail's RailType is unloaded
     */
    TrackedSign[] discoverSignsAtRailPiece(RailPiece rail);

    /**
     * Searches from the position of a sign block for the RailPiece that is coupled
     * with that sign, if that sign were to be triggered (by redstone, for example).
     * Returns {@link RailPiece#NONE} if no rails could be found.
     * 
     * @param signblock Block of the sign
     * @return rails piece information, NONE if the sign has no rails (rail block is null)
     */
    RailPiece discoverRailPieceFromSign(Block signblock);

    /**
     * Sets/stores the Detector Regions that should be activated at particular rail block
     * coordinates.
     *
     * @param coordinates Rail Block coordinates
     * @param regions Detector Regions to be activated. Null stores none.
     */
    void storeDetectorRegions(IntVector3 coordinates, DetectorRegion[] regions);

    /**
     * Gets the Detector Regions that are activated at particular rail block coordinates
     *
     * @param coordinates Rail Block coordinates
     * @return Detector Regions at these coordinates
     */
    DetectorRegion[] getDetectorRegions(IntVector3 coordinates);

    /**
     * Exception thrown when a WorldRailLookup is used that is no longer valid because it has
     * been closed, or the World it is for isn't loaded.
     */
    public static class ClosedException extends IllegalStateException {
        private static final long serialVersionUID = -5457138086475585185L;

        public ClosedException() {
            super("World Rail Lookup cache is closed");
        }
    }
}
