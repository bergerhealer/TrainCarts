package com.bergerkiller.bukkit.tc.rails;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.signtracker.SignChangeTrackerWrap;

/**
 * Manages the {@link WorldRailLookup} caches for all worlds they exist on.
 * The static utility methods make use of this class to find the right
 * lookup to use for a World.<br>
 * <br>
 * It is strongly recommended to use the {@link WorldRailLookup} directly
 * if you have it available to eliminate an unneeded HashMap lookup.
 */
public final class RailLookup {
    /** Start value of the life timer */
    static final int LIFE_TIMER_START = 1;
    /** This is incremented every tick to force cached information to re-verify itself */
    static int lifeTimer = LIFE_TIMER_START;
    /** Stores the (every tick incrementing) future tick when cached information expires */
    static int verifyTimer = LIFE_TIMER_START;

    // Constant arrays used for initialization checks
    static final TrackedSign[] NO_SIGNS = new TrackedSign[0];
    static final TrackedSign[] MISSING_RAILS_NO_SIGNS = new TrackedSign[0];
    static final List<MinecartMember<?>> DEFAULT_MEMBER_LIST = Collections.emptyList();
    private static final TrackedSignList SIGN_LIST_CACHE = new TrackedSignList();
    private static final Material WALL_SIGN_TYPE = getMaterial("LEGACY_WALL_SIGN");
    private static final Material SIGN_POST_TYPE = getMaterial("LEGACY_SIGN_POST");
    private static BlockFace[] SIGN_FACES_ORDERED = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN};

    // Stores all WorldRailLookup instances that are in use
    private static final IdentityHashMap<World, WorldRailLookup> byWorld = new IdentityHashMap<>();

    /**
     * Gets the World-specific Rail Lookup. This is more efficient to use than this RailLookup's
     * static methods, as it eliminates an unneeded by-world lookup call.
     *
     * @param world World to find the World Rail Lookup cache for
     * @return World Rail Lookup cache for this World
     */
    public static WorldRailLookup forWorld(World world) {
        // Note: the null check will run every time for null, because computeIfAbsent recalculates
        //       null all the time.
        return byWorld.computeIfAbsent(world, w -> w == null
                ? WorldRailLookup.NONE : new WorldRailLookup(w));
    }

    /**
     * Discovers the RailPieces that are active for controlling the movement of a Minecart
     * at the RailState position information specified. The caller should further filter which of
     * these rail pieces truly control the movement, but the returned array is efficiently
     * cached.
     *
     * @param state RailState with position information
     * @return Array of rail pieces active here, or an empty array if there are no rails nearby
     */
    public static RailPiece[] findAtStatePosition(RailState state) {
        return state.railLookup().findAtStatePosition(state);
    }

    /**
     * Discovers the RailPieces that are active for controlling the movement of a Minecart
     * moving within the Block position specified. The caller should further filter which of
     * these rail pieces truly control the movement, but the returned array is efficiently
     * cached.
     *
     * @param positionBlock Block position
     * @return Array of rail pieces active here, or an empty array if there are no rails nearby
     */
    public static RailPiece[] findAtBlockPosition(OfflineBlock positionBlock) {
        return forWorld(positionBlock.getLoadedWorld()).findAtBlockPosition(positionBlock);
    }

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
    public static List<MinecartMember<?>> findMembersOnRail(OfflineBlock railOfflineBlock) {
        return forWorld(railOfflineBlock.getLoadedWorld()).findMembersOnRail(railOfflineBlock);
    }

    /**
     * Completely clears the cache. Be very aware that this will cause some information to go
     * out of sync, if information is still stored, such as the Minecart Members that are on
     * particular rails. If that's a problem, use {@link #forceRecalculation()} instead.
     */
    public static void clear() {
        byWorld.values().forEach(WorldRailLookup::clear);
        byWorld.clear();
    }

    /**
     * Forces all cached information to be invalidated so that it is recalculated the next time
     * information is accessed. This should be called when registering and un-registering a rail
     * type, or when a rail type significantly alters behavior/reloads.
     */
    public static void forceRecalculation() {
        byWorld.values().forEach(WorldRailLookup::refreshAllBuckets);

        // Increment life timer so that all rail access is re-validated
        // Set the timer to when buckets with life=1 expire (set earlier)
        lifeTimer = LIFE_TIMER_START + TCConfig.cacheExpireTicks + TCConfig.cacheVerificationTicks;
        verifyTimer = ++lifeTimer + TCConfig.cacheVerificationTicks;
    }

    /**
     * Removes a particular member from all member lists of cached rail positions
     *
     * @param member
     */
    public static void removeMemberFromAll(MinecartMember<?> member) {
        for (WorldRailLookup lookup : byWorld.values()) {
            lookup.removeMemberFromAll(member);
        }
    }

    /**
     * Called every tick in the background to delete cached entries that haven't been accessed
     * in a while, so they can be properly regenerated and memory doesn't infinitely go up.
     */
    public static void update() {
        final int deadTimeout = lifeTimer - TCConfig.cacheExpireTicks - TCConfig.cacheVerificationTicks;
        for (Iterator<WorldRailLookup> iter = byWorld.values().iterator(); iter.hasNext();) {
            WorldRailLookup lookup = iter.next();
            if (!lookup.isValid()) {
                iter.remove();
            } else {
                lookup.update(deadTimeout);
            }
        }
        verifyTimer = ++lifeTimer + TCConfig.cacheVerificationTicks;
    }

    /**
     * Searches from the position of a sign block for the RailPiece that is coupled
     * with that sign, if that sign were to be triggered (by redstone, for example).
     * Returns {@link RailPiece#NONE} if no rails could be found.
     * 
     * @param signblock Block of the sign
     * @return rails piece information, NONE if the sign has no rails (rail block is null)
     */
    public static RailPiece discoverRailPieceFromSign(Block signblock) {
        if (signblock == null) {
            return RailPiece.NONE;
        }

        BlockData signblock_data = WorldUtil.getBlockData(signblock);
        final Block mainBlock;
        if (signblock_data.isType(WALL_SIGN_TYPE)) {
            mainBlock = signblock.getRelative(signblock_data.getAttachedFace());
        } else if (signblock_data.isType(SIGN_POST_TYPE)) {
            mainBlock = signblock;
        } else {
            return RailPiece.NONE;
        }

        // Check main block IS rails itself
        RailType railType = RailType.getType(mainBlock);
        if (railType != RailType.NONE) {
            return RailPiece.create(railType, mainBlock);
        }

        // Look further in all 6 possible directions
        boolean hasSigns;
        for (BlockFace dir : SIGN_FACES_ORDERED) {
            Block block = mainBlock;
            BlockData blockData;
            hasSigns = true;
            while (true) {
                // Go to the next block
                block = block.getRelative(dir);
                blockData = WorldUtil.getBlockData(block);

                // Check for rails
                railType = RailType.getType(block, blockData);
                BlockFace columnDir = railType.getSignColumnDirection(block);
                if (dir == columnDir.getOppositeFace()) {
                    return RailPiece.create(railType, block);
                }

                // End of the loop?
                if (!hasSigns) {
                    break;
                }

                // Go to the next block
                if (MaterialUtil.ISSIGN.get(blockData)) {
                    hasSigns = blockData.getAttachedFace() == BlockFace.DOWN;
                } else {
                    hasSigns = hasAttachedSigns(block);
                }
            }
        }
        return RailPiece.NONE;
    }

    /**
     * Discovers the signs belonging to a particular rail.
     * Unlike {@link RailPiece#signs()} this method does not look
     * the information up from a cache
     * 
     * @param railType of the rail
     * @param railBlock of the rail
     * @return signs belonging to this rail
     */
    public static TrackedSign[] discoverSignsAtRailPiece(RailPiece rail) {
        Block columnStart = rail.type().getSignColumnStart(rail.block());
        if (columnStart == null) {
            return RailLookup.NO_SIGNS;
        }

        BlockFace direction = rail.type().getSignColumnDirection(rail.block());
        if (direction == null || direction == BlockFace.SELF) {
            return RailLookup.NO_SIGNS;
        }

        try (TrackedSignList cache = SIGN_LIST_CACHE.start(rail)) {
            if (FaceUtil.isVertical(direction)) {
                // Vertical column can be optimized because we know up-front what chunks will be checked
                // First retrieve the chunk of the middle column, and the chunk-relative coordinates
                Chunk chunk = columnStart.getChunk();
                int rx = columnStart.getX() & 0xF;
                int ry = columnStart.getY();
                int rz = columnStart.getZ() & 0xF;

                int offsetCtr = 0;
                if (rx >= 1 && rx <= 14 && rz >= 1 && rz <= 14) {
                    // If all within the same chunk, this can be optimized
                    BlockFace[] faces = FaceUtil.AXIS;
                    while (true) {
                        if (WorldUtil.getBlockData(chunk, rx, ry, rz).isType(SIGN_POST_TYPE)) {
                            // Found a sign post. No need to check for wall signs.
                            cache.add(chunk.getBlock(rx, ry, rz));
                        } else {
                            // Check for wall signs
                            boolean foundSigns = false;
                            for (BlockFace face : faces) {
                                BlockData blockData = WorldUtil.getBlockData(chunk, rx + face.getModX(), ry, rz + face.getModZ());
                                if (MaterialUtil.ISSIGN.get(blockData) && blockData.getAttachedFace() == face.getOppositeFace()) {
                                    cache.add(chunk.getBlock(rx + face.getModX(), ry, rz + face.getModZ()));
                                    foundSigns = true;
                                }
                            }

                            // If no signs are found found and offset is 2, stop.
                            if (!foundSigns && offsetCtr > 1) {
                                break;
                            }
                        }

                        ry += direction.getModY();
                        offsetCtr++;
                    }
                } else {
                    // Work up/down. Initialize the 4 sides' chunk
                    VerticalSignColumn[] columns = VerticalSignColumn.ALL;
                    for (int i = 0; i < 4; i++) {
                        columns[i].load(chunk, rx, rz);
                    }
                    while (true) {
                        if (WorldUtil.getBlockData(chunk, rx, ry, rz).isType(SIGN_POST_TYPE)) {
                            // Found a sign post. No need to check for wall signs.
                            cache.add(chunk.getBlock(rx, ry, rz));
                        } else {
                            // Check for wall signs
                            boolean foundSigns = false;
                            for (int i = 0; i < 4; i++) {
                                foundSigns |= columns[i].findSign(cache, ry);
                            }

                            // If no signs are found found and offset is 2, stop.
                            if (!foundSigns && offsetCtr > 1) {
                                break;
                            }
                        }

                        ry += direction.getModY();
                        offsetCtr++;
                    }
                }
            } else {
                // Other directions are too tricky. Thankfully, they're hardly used at all.
                Block currentBlock = columnStart;
                int offsetCtr = 0;
                while (true) {
                    // Find one or more signs attached to the current block - continue
                    boolean foundSigns = false;
                    for (BlockFace face : FaceUtil.AXIS) {
                        Block b = currentBlock.getRelative(face);
                        BlockData blockData = WorldUtil.getBlockData(b);
                        if (MaterialUtil.ISSIGN.get(blockData) && blockData.getAttachedFace() == face.getOppositeFace()) {
                            foundSigns = true;
                            cache.add(b);
                        }
                    }

                    // No signs found here. If this is too far down, stop.
                    if (!foundSigns && offsetCtr > 1) {
                        break;
                    }

                    currentBlock = currentBlock.getRelative(direction);
                    offsetCtr++;
                }
            }

            // Pack result into a TrackedSign array
            return cache.build();
        }
    }

    /**
     * A RailPiece backed by this RailLookup's cache. Stores, besides the rail piece
     * information itself, the members and signs coupled with the rails.
     */
    public static abstract class CachedRailPiece extends RailPiece {
        /**
         * List of members occupying this bucket's block, treating the block as
         * a rail block. Is made immutable with an ArrayList when a member first
         * uses the rails, is the DEFAULT_MEMBER_LIST otherwise.
         */
        protected List<MinecartMember<?>> members;
        /**
         * Array of signs activated by this bucket's block, treating the block as
         * a rail block.
         */
        protected TrackedSign[] signs;

        /**
         * A cached rail piece with no valid information at all. {@link #verify()} will always
         * return false, so the caller will change this one out with the right one on first use.
         */
        public static final CachedRailPiece NONE = new CachedRailPiece() {
            @Override
            public boolean verify() {
                return false;
            }
        };
 
        private CachedRailPiece() {
            super();
            this.members = Collections.unmodifiableList(Collections.emptyList());
            this.signs = NO_SIGNS;
        }

        protected CachedRailPiece(WorldRailLookup railLookup, OfflineBlock offlineBlock, Block block, RailType type) {
            super(railLookup, offlineBlock, block, type);
            this.cached = this;
            this.members = DEFAULT_MEMBER_LIST;
            this.signs = NO_SIGNS;
        }

        /**
         * Checks whether these cached contents are still valid and refreshes information
         * if needed. Returns false if this cached instance is no longer valid, and a new
         * lookup is required.
         *
         * @return True if this information is still valid
         */
        public abstract boolean verify();

        /**
         * Gets a list of cached Minecart Members that occupy these rails.
         * Only valid if {@link #verify()} is true.
         *
         * @return members
         */
        public final List<MinecartMember<?>> cachedMembers() {
            return this.members;
        }

        /**
         * Gets a list of cached Minecart Members that occupy these rails.
         * The returned list is guaranteed to be mutable.
         * Only valid if {@link #verify()} is true.
         *
         * @return members
         */
        public final List<MinecartMember<?>> cachedMutableMembers() {
            List<MinecartMember<?>> result = this.members;
            if (result == DEFAULT_MEMBER_LIST) {
                this.members = result = new ArrayList<MinecartMember<?>>(2);
            }
            return result;
        }

        /**
         * Gets an array of cached tracked signs that are activated when trains drive
         * over these rails.
         * Only valid if {@link #verify()} is true.
         *
         * @return signs
         */
        public final TrackedSign[] cachedSigns() {
            return this.signs;
        }

        @Override
        public boolean equals(Object o) {
            return o == this; // Used when removing a value, we don't want normal equals there
        }
    }

    /**
     * A single sign that is tracked
     */
    public static class TrackedSign {
        final SignChangeTrackerWrap tracker;
        public final Sign sign;
        public final Block signBlock;
        public final RailPiece rail;
        /** @deprecated Is now part of {@link #rail} */
        @Deprecated
        public final RailType railType;
        /** @deprecated Is now part of {@link #rail} */
        @Deprecated
        public final Block railBlock;
        public final SignAction action;

        public TrackedSign(Block signBlock, RailPiece rail) {
            this(SignChangeTrackerWrap.track(signBlock), rail);
        }

        TrackedSign(SignChangeTrackerWrap tracker, RailPiece rail) {
            if (tracker.isRemoved()) {
                throw new IllegalArgumentException("There is no sign at " + tracker.getBlock());
            }

            // Canned assignment stuff
            this.tracker = tracker;
            this.sign = tracker.getSign();
            this.signBlock = tracker.getBlock();
            this.rail = rail;
            this.railType = rail.type();
            this.railBlock = rail.block();
            this.action = SignAction.getSignAction(createEvent(SignActionType.NONE));
        }

        /**
         * Verifies that this tracked sign is really still there on the server
         *
         * @return True if the sign is there
         */
        public boolean verify() {
            return BlockUtil.ISSIGN.get(signBlock);
        }

        /**
         * Initializes a new SignActionEvent using this tracked sign for sign and rail information
         *
         * @param action Sign action event type
         * @return new SignActionEvent
         */
        public SignActionEvent createEvent(SignActionType action) {
            return (new SignActionEvent(this.signBlock, this.sign, this.rail)).setAction(action);
        }

        /**
         * Executes a {@link SignActionEvent} with the given action type, for a MinecartMember.
         * If the member is unloaded or dead, the event is not fired.
         *
         * @param action Action to execute
         * @param member Member involved in the event
         * @see #createEvent(SignActionType)
         */
        public void executeEventForMember(SignActionType action, MinecartMember<?> member) {
            if (member.isInteractable()) {
                SignActionEvent event = createEvent(action);
                event.setMember(member);
                SignAction.executeOne(this.action, event);
            }
        }

        /**
         * Executes a {@link SignActionEvent} with the given action type, for a MinecartGroup.
         * If the group is unloaded, the event is not fired.
         *
         * @param action Action to execute
         * @param group Group involved in the event
         * @see #createEvent(SignActionType)
         */
        public void executeEventForGroup(SignActionType action, MinecartGroup group) {
            if (!group.isUnloaded()) {
                SignActionEvent event = createEvent(action);
                event.setGroup(group);
                SignAction.executeOne(this.action, event);
            }
        }

        /**
         * Checks whether the captured state of this tracked sign has the same
         * sign text as another.
         *
         * @param other TrackedSign to compare against
         * @return True if the text of the two signs are identical
         */
        public boolean hasIdenticalText(TrackedSign other) {
            return Arrays.equals(this.sign.getLines(), other.sign.getLines());
        }

        @Override
        public int hashCode() {
            return this.signBlock.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return ((TrackedSign) o).signBlock.equals(this.signBlock);
        }
    }

    /**
     * List cache used when generating the array of tracked signs
     */
    private static final class TrackedSignList implements AutoCloseable {
        private final List<TrackedSign> signs = new ArrayList<>();
        private RailPiece rail = null;

        public TrackedSignList start(RailPiece rail) {
            if (this.rail == null) {
                this.rail = rail;
                return this;
            } else {
                // Already opened, create a new one to prevent corruption
                TrackedSignList copy = new TrackedSignList();
                copy.rail = rail;
                return copy;
            }
        }

        @Override
        public void close() {
            this.signs.clear();
            this.rail = null;
        }

        public void add(Block signBlock) {
            SignChangeTrackerWrap tracker = SignChangeTrackerWrap.track(signBlock);
            if (!tracker.isRemoved()) {
                try {
                    this.signs.add(new TrackedSign(tracker, this.rail));
                } catch (Throwable t) {
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to load sign at " + signBlock, t);
                }
            }
        }

        public TrackedSign[] build() {
            List<TrackedSign> signs = this.signs;
            return signs.isEmpty() ? RailLookup.NO_SIGNS : signs.toArray(new TrackedSign[signs.size()]);
        }
    }

    /**
     * Temporarily stores the chunk and relative coordinates for a vertical slice of
     * wall signs for a column below/above rails being checked.
     */
    private static class VerticalSignColumn {
        public static final VerticalSignColumn[] ALL = new VerticalSignColumn[] {
                new VerticalSignColumn(BlockFace.WEST),
                new VerticalSignColumn(BlockFace.EAST),
                new VerticalSignColumn(BlockFace.NORTH),
                new VerticalSignColumn(BlockFace.SOUTH)
        };
        public final BlockFace face;
        public Chunk chunk;
        public int rx, rz;

        private VerticalSignColumn(BlockFace face) {
            this.face = face;
        }

        public void load(Chunk mainChunk, int main_rx, int main_rz) {
            this.rx = main_rx + this.face.getModX();
            this.rz = main_rz + this.face.getModZ();
            if (this.rx < 0 || this.rx >= 16) {
                this.chunk = mainChunk.getWorld().getChunkAt(mainChunk.getX() + this.face.getModX(), mainChunk.getZ());
                this.rx &= 0xF;
            } else if (this.rz < 0 || this.rz >= 16) {
                this.chunk = mainChunk.getWorld().getChunkAt(mainChunk.getX(), mainChunk.getZ() + this.face.getModZ());
                this.rz &= 0xF;
            } else {
                this.chunk = mainChunk;
            }
        }

        public boolean findSign(TrackedSignList rval, int ry) {
            BlockData blockData = WorldUtil.getBlockData(chunk, rx, ry, rz);
            if (MaterialUtil.ISSIGN.get(blockData) && blockData.getAttachedFace() == face.getOppositeFace()) {
                rval.add(chunk.getBlock(rx, ry, rz));
                return true;
            } else {
                return false;
            }
        }
    }

    private static boolean hasAttachedSigns(final Block middle) {
        for (BlockFace face : FaceUtil.AXIS) {
            Block b = middle.getRelative(face);
            BlockData blockData = WorldUtil.getBlockData(b);
            if (MaterialUtil.ISSIGN.get(blockData) && blockData.getAttachedFace() == face.getOppositeFace()) {
                return true;
            }
        }
        return false;
    }
}
