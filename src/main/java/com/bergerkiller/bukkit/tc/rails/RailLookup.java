package com.bergerkiller.bukkit.tc.rails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.controller.global.SignController;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.block.SignChangeTracker;
import com.bergerkiller.bukkit.common.offline.OfflineBlock;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.SignActionHeader;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.type.RailType;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.FakeSign;

/**
 * Manages the {@link WorldRailLookup} caches for all worlds they exist on.
 * The static utility methods make use of this class to find the right
 * lookup to use for a World.<br>
 * <br>
 * It is strongly recommended to use the {@link WorldRailLookup} directly
 * if you have it available to eliminate an unneeded HashMap lookup.
 */
public final class RailLookup {
    /***Value of the life timer when a bucket is removed */
    static final int LIFE_TIMER_DELETED = 0;
    /** Start value of the life timer */
    static final int LIFE_TIMER_START = 1;
    /** This is incremented every tick to force cached information to re-verify itself */
    static int lifeTimer = LIFE_TIMER_START;
    /** This is incremented every tick and forces cache rails-at-position information to be re-verified */
    static int lifeTimerAtPosition = LIFE_TIMER_START;
    /** Stores the (every tick incrementing) future tick when cached information expires */
    static int verifyTimer = LIFE_TIMER_START;

    // Constant arrays used for initialization checks
    static final DetectorRegion[] NO_DETECTOR_REGIONS = new DetectorRegion[0];
    static final TrackedSign[] NO_SIGNS = new TrackedSign[0];
    static final TrackedSign[] MISSING_RAILS_NO_SIGNS = new TrackedSign[0];
    static final List<MinecartMember<?>> DEFAULT_MEMBER_LIST = Collections.emptyList();

    // Stores all WorldRailLookup instances that are in use
    private static final IdentityHashMap<World, WorldRailLookupImpl> byWorld = new IdentityHashMap<>();

    /**
     * Gets the World-specific Rail Lookup. This is more efficient to use than this RailLookup's
     * static methods, as it eliminates an unneeded by-world lookup call.
     *
     * @param world World to find the World Rail Lookup cache for
     * @return World Rail Lookup cache for this World
     */
    public static WorldRailLookup forWorld(World world) {
        WorldRailLookupImpl lookup = (WorldRailLookupImpl) byWorld.get(world);
        if (lookup == null) {
            if (world == null) {
                return WorldRailLookup.NONE;
            }
            lookup = new WorldRailLookupImpl(TrainCarts.plugin, world);
            byWorld.put(world, lookup); // computeIfAbsent won't work, potential concurrent modification
                                        // if someone runs forWorld() during initialization

            lookup.initialize();
        }
        return lookup;
    }

    /**
     * Gets the World-specific Rail Lookup, if it has been initialized. Should only be used
     * when information must be stored in the lookup, which is also stored during initialization.
     *
     * @param world
     * @return World Rail Lookup cache for this World if initialized, otherwise {@link WorldRailLookup#NONE}
     */
    public static WorldRailLookup forWorldIfInitialized(World world) {
        // Bleh.
        IdentityHashMap<World, WorldRailLookup> byWorldCast = CommonUtil.unsafeCast(byWorld);
        return byWorldCast.getOrDefault(world, WorldRailLookup.NONE);
    }

    /**
     * Discovers the RailPieces that are active for controlling the movement of a Minecart
     * at the RailState position information specified. The caller should further filter which of
     * these rail pieces truly control the movement, but the returned array is efficiently
     * cached.<br>
     * <br>
     * Caller must make sure the World of the position block is loaded. If it is not, a
     * {@link WorldRailLookup.ClosedException} is thrown.
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
     * cached.<br>
     * <br>
     * Caller must make sure the World of the position block is loaded. If it is not, a
     * {@link WorldRailLookup.ClosedException} is thrown.
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
        return forWorldIfInitialized(railOfflineBlock.getLoadedWorld()).findMembersOnRail(railOfflineBlock);
    }

    /**
     * API Note: you should never have to call this function. It's used internally by RailPiece.<br>
     * <br>
     * Looks up cached rail information about the provided rail block, if such information is currently
     * cached. If not, {@link RailLookup.CachedRailPiece#NONE} is returned instead. If no rail lookup
     * cache is initialized yet for the World, assumes there is no cached data, and also returns NONE
     * without initializing a new cache.
     *
     * @param railOfflineBlock Rail offline block, key
     * @param railType Rail type
     * @return Rail piece information backed by this lookup cache, or NONE if missing.
     */
    public static RailLookup.CachedRailPiece lookupCachedRailPieceIfCached(final OfflineBlock railOfflineBlock,
                                                                           final RailType railType
    ) {
        return forWorldIfInitialized(railOfflineBlock.getLoadedWorld()).lookupCachedRailPieceIfCached(railOfflineBlock, railType);
    }

    /**
     * Completely clears the cache. Be very aware that this will cause some information to go
     * out of sync, if information is still stored, such as the Minecart Members that are on
     * particular rails. If that's a problem, use {@link #forceRecalculation()} instead.
     */
    public static void clear() {
        byWorld.values().forEach(WorldRailLookupImpl::close);
        byWorld.clear();
    }

    /**
     * Re-detects the SignAction registered for all signs cached on the server.
     * This is called when a sign action is registered or un-registered
     * while the server is running.
     */
    public static void redetectSignActions() {
        for (WorldRailLookup world : byWorld.values()) {
            world.redetectSignActions();
        }
    }

    /**
     * Forces all cached rail information about the specified Rail Type to be unloaded. In the event
     * persistent metadata is tied to it, like members, the data is lost. Members should refresh
     * this information as needed.
     *
     * @param type Rail Type to un-register
     */
    public static void forceUnloadRail(RailType type) {
        // For good measure, first clean up all rail types that aren't in use
        forceRecalculation();

        // Now forcefully unload stuff
        byWorld.values().forEach(lookup -> lookup.unloadRailType(type));
    }

    /**
     * Forces all cached information to be invalidated so that it is recalculated the next time
     * information is accessed. This should be called when registering and un-registering a rail
     * type, or when a rail type significantly alters behavior/reloads.
     */
    public static void forceRecalculation() {
        byWorld.values().forEach(WorldRailLookupImpl::refreshAllBuckets);

        // Increment life timer so that all rail access is re-validated
        // Set the timer to when buckets with life=1 expire (set earlier)
        lifeTimer = LIFE_TIMER_START + TCConfig.cacheExpireTicks + TCConfig.cacheVerificationTicks;
        lifeTimerAtPosition = LIFE_TIMER_START;
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
        for (Iterator<WorldRailLookupImpl> iter = byWorld.values().iterator(); iter.hasNext();) {
            WorldRailLookupImpl lookup = iter.next();
            if (lookup.checkCanBeRemoved()) {
                lookup.close();
                iter.remove();
            } else {
                lookup.update(deadTimeout);
            }
        }
        ++lifeTimerAtPosition;
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
        return RailLookup.forWorld(signblock.getWorld()).discoverRailPieceFromSign(signblock);
    }

    /**
     * Discovers the signs belonging to a particular rail.
     * Unlike {@link RailPiece#signs()} this method does not look
     * the information up from a cache
     * 
     * @param rail Rail block and type information
     * @return signs belonging to this rail
     */
    public static TrackedSign[] discoverSignsAtRailPiece(RailPiece rail) {
        return rail.railLookup().discoverSignsAtRailPiece(rail);
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
         * Array of detector regions activates by trains when they drive on this
         * bucket's rail block. All rail types occupying a certain rail block
         * share the same detector regions.
         */
        protected DetectorRegion[] detectorRegions;

        /**
         * A cached rail piece with no valid information at all. {@link #verify()} will always
         * return false, so the caller will change this one out with the right one on first use.
         */
        public static final CachedRailPiece NONE = new CachedRailPiece() {
            @Override
            public boolean verify() {
                return false;
            }

            @Override
            public boolean verifyExists() {
                return false;
            }

            @Override
            public void forceCacheVerification() {
            }
        };
 
        private CachedRailPiece() {
            super();
            this.members = Collections.unmodifiableList(Collections.emptyList());
            this.signs = NO_SIGNS;
            this.detectorRegions = NO_DETECTOR_REGIONS;
        }

        protected CachedRailPiece(WorldRailLookup railLookup, OfflineBlock offlineBlock, Block block, RailType type) {
            super(railLookup, offlineBlock, block, type);
            this.cached = this;
            this.members = DEFAULT_MEMBER_LIST;
            this.signs = NO_SIGNS;
            this.detectorRegions = NO_DETECTOR_REGIONS;
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
         * Verifies this cached rail piece is still mapped within the Rail Lookup cache.
         * Is faster than {@link #verify()}, as it skips checking the rail type is actually
         * still valid.
         *
         * @return True if this information is still validly cached
         */
        public abstract boolean verifyExists();

        /**
         * Forces rail type verification to occur the very next time {@link #verify()}
         * is called.
         */
        public abstract void forceCacheVerification();

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
        public final void redetectSignActions() {
            // For existing tracked sign instances, force action to be re-calculated
            for (TrackedSign sign : signs) {
                sign.redetectSignAction();
            }

            // Now also discover any new signs (it filters signs without actions by default)
            forceCacheVerification();
        }

        /**
         * Gets an array of detector regions that are activated when trains driver over
         * these rails.
         * Only valid if {@link #verifyExists()} or {@link #verify()} is true.
         *
         * @return detector regions
         */
        public final DetectorRegion[] cachedDetectorRegions() {
            return this.detectorRegions;
        }
    }

    /**
     * Exception thrown by the lookup cache if a rail type is specified that was not
     * registered inside the RailType lookup table. This might happen when a rail type
     * is unloaded while the server is running, or during server shutdown.
     */
    public static final class RailTypeNotRegisteredException extends IllegalArgumentException {
        private static final long serialVersionUID = -3651967639525705930L;

        public RailTypeNotRegisteredException(RailType type) {
            super("Rail type " + type + " is not registered");
        }
    }

    /**
     * A single sign that is tracked
     */
    public static abstract class TrackedSign {
        public final Sign sign;
        public final Block signBlock;
        /** @deprecated Use {@link #getRail()} instead */
        @Deprecated
        public RailPiece rail;
        /** @deprecated Use {@link #getRail()} instead */
        @Deprecated
        public RailType railType;
        /** @deprecated Use {@link #getRail()} instead */
        @Deprecated
        public Block railBlock;

        // Cached signblock hashCode(), because bukkit's one is slow
        private final int signBlockHashCode;

        // Cached properties parsed using sign lines
        private SignActionHeader cachedHeader = null;
        private boolean cachedActionSet = false;
        private SignAction cachedAction = null;

        TrackedSign(Sign sign, Block signBlock, RailPiece rail) {
            if (sign == null) {
                throw new IllegalArgumentException("There is no sign at " + signBlock);
            }

            // Canned assignment stuff
            this.sign = sign;
            this.signBlock = signBlock;
            this.signBlockHashCode = signBlock.hashCode();
            this.rail = rail;
            this.railType = rail.type();
            this.railBlock = rail.block();
        }

        // Only to be used by UnitTestTrackedSign() and cannot be used on a live server
        private TrackedSign() {
            this.sign = null;
            this.signBlock = null;
            this.signBlockHashCode = 0;
            this.rail = null;
            this.railType = RailType.NONE;
            this.railBlock = null;
        }

        /**
         * Verifies that this tracked sign with the same information is really still there on the server.
         * If the sign was removed, changed facing or the text was changed, will return false
         * to tell the system the signs need to be refreshed.
         *
         * @return True if the sign is there with the same sign information as this tracked sign
         */
        public abstract boolean verify();

        /**
         * Gets whether this TrackedSign refers to a Sign that has since been removed from the
         * server. This can happen when a sign is broken and this was detected during a previous
         * {@link #verify()}.
         *
         * @return True if this tracked sign was removed
         */
        public abstract boolean isRemoved();

        /**
         * Gets the facing orientation of this TrackedSign. This is used to decide the trigger directions
         * for the sign, among other things.
         *
         * @return Sign facing
         */
        public abstract BlockFace getFacing();

        /**
         * Gets the Block this TrackedSign is attached to. This is the Block that levers will be
         * toggled on. If there is no such block, null can be returned.
         *
         * @return Block the sign is attached to.
         */
        public abstract Block getAttachedBlock();

        /**
         * Sets the toggled level output state of this sign. This is called from
         * {@link SignActionEvent#setLevers(boolean)} to update state.
         *
         * @param output New output state
         */
        public void setOutput(boolean output) {
            Block attachedBlock = this.getAttachedBlock();
            if (attachedBlock != null) {
                BlockUtil.setLeversAroundBlock(attachedBlock, output);
            }
        }

        /**
         * Searches for additional signs below this sign which extend the number of lines. Custom
         * tracked sign implementations can return lines beyond the first 4 here. Some sign actions,
         * like the switcher, can use these extra lines for additional rules.
         *
         * @return Extra lines
         */
        public abstract String[] getExtraLines();

        /**
         * Gets the Redstone Power state of this sign, from a given BlockFace. Custom tracked sign
         * implementation can choose to always return on or off, or do something special with it.
         *
         * @param from BlockFace relative to the sign to check
         * @return PowerState of the sign
         */
        public abstract PowerState getPower(BlockFace from);

        /**
         * Gets whether this TrackedSign refers to a real sign block or not. If this is the case, then
         * metadata can be tied to the sign block, and this tracked sign can be obtained from a sign
         * block alone.
         *
         * @return True if this TrackedSign refers to a really-existing sign
         */
        public abstract boolean isRealSign();

        /**
         * Gets a line of text on this sign
         *
         * @param index Line index. Should support [0..3]
         * @return Line at the index
         * @throws IndexOutOfBoundsException
         */
        public abstract String getLine(int index) throws IndexOutOfBoundsException;

        /**
         * Sets a line of text on this sign
         *
         * @param index Line index. Should support [0..3]
         * @param line Line at the index
         * @throws IndexOutOfBoundsException
         */
        public abstract void setLine(int index, String line) throws IndexOutOfBoundsException;

        /**
         * Called when it is requested for this tracked sign to display a highlight of some sort
         * that identifies this sign to the viewer. For vanilla signs, this displays a glowing
         * box effect where the sign is located using the color specified. Other sign implementations
         * can choose to do with this what they please. If not implemented, the sign is simply
         * not going to be highlighted.<br>
         * <br>
         * Should return a {@link Runnable} callback that when run de-spawns / disables this highlight
         * again.
         *
         * @param viewer AttachmentViewer for whom to display it
         * @param options DebugDisplayOptions, which includes a ChatColor team color to use for any
         *                colored outline
         * @return A runnable callback to de-spawn the highlight again
         */
        public Runnable showDebugHighlight(AttachmentViewer viewer, DebugDisplayOptions options) {
            return () -> {};
        }

        /**
         * Parses the first line of this tracked sign to get the typical sign action header
         * Traincarts uses. This is the [train] syntax.
         *
         * @return Sign action header parsed from the first line of the sign
         */
        public SignActionHeader getHeader() {
            SignActionHeader header = this.cachedHeader;
            if (header == null) {
                this.cachedHeader = header = SignActionHeader.parse(Util.cleanSignLine(this.getLine(0)));
            }
            return header;
        }

        /**
         * Sets this TrackedSign to use a particular header, instead of calculating one from
         * scratch the next time {@link #getHeader()} is called.
         *
         * @param header Header to cache
         */
        public void setCachedHeader(SignActionHeader header) {
            this.cachedHeader = header;
        }

        /**
         * Gets the SignAction that matches the sign text contents of this tracked sign.
         * Is cached.
         *
         * @return SignAction of this sign
         */
        public SignAction getAction() {
            if (cachedActionSet) {
                return cachedAction;
            } else {
                cachedActionSet = true;
                return cachedAction = SignAction.getSignAction(this.createEvent(SignActionType.NONE));
            }
        }

        /**
         * Forces the SignAction to be re-detected the next time it is asked of this tracked
         * sign.
         */
        public void redetectSignAction() {
            cachedActionSet = false;
            cachedAction = null;
        }

        /**
         * Gets the RailPiece rail that activates this sign
         *
         * @return rail piece
         */
        public RailPiece getRail() {
            // Lazily initializes the rail piece if not retrieved yet
            RailPiece rail = this.rail;
            if (rail == null) {
                this.rail = rail = RailLookup.discoverRailPieceFromSign(this.signBlock);
                this.railBlock = rail.block();
                this.railType = rail.type();
            }
            return rail;
        }

        /**
         * Initializes a new SignActionEvent using this tracked sign for sign and rail information
         *
         * @param action Sign action event type
         * @return new SignActionEvent
         */
        public final SignActionEvent createEvent(SignActionType action) {
            return (new SignActionEvent(this)).setAction(action);
        }

        private final boolean canFireEvents() {
            return !isRemoved() && (this.rail == null || this.rail.type().isRegistered());
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
            executeEventForMember(action, member, null);
        }

        /**
         * Executes a {@link SignActionEvent} with the given action type, for a MinecartMember.
         * If the member is unloaded or dead, the event is not fired.<br>
         * <br>
         * A RailState can be passed specifying the state of the member when first activating this sign.
         * This is used for properly deciding the trigger directions/facing.
         *
         * @param action Action to execute
         * @param member Member involved in the event
         * @param enterState State of the member when first activating the sign
         * @see #createEvent(SignActionType)
         */
        public void executeEventForMember(SignActionType action, MinecartMember<?> member, RailState enterState) {
            if (canFireEvents() && member.isInteractable()) {
                SignActionEvent event = createEvent(action);
                event.setMember(member);
                event.overrideCartEnterState(enterState);
                SignAction.executeOne(this.getAction(), event);
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
            executeEventForGroup(action, group, null);
        }

        /**
         * Executes a {@link SignActionEvent} with the given action type, for a MinecartGroup.
         * If the group is unloaded, the event is not fired.<br>
         * <br>
         * A RailState can be passed specifying the state of the member when first activating this sign.
         * This is used for properly deciding the trigger directions/facing.
         *
         * @param action Action to execute
         * @param group Group involved in the event
         * @param enterState State of the train's first member when first activating the sign
         * @see #createEvent(SignActionType)
         */
        public void executeEventForGroup(SignActionType action, MinecartGroup group, RailState enterState) {
            if (canFireEvents()) {
                SignActionEvent event = createEvent(action);
                event.setGroup(group);
                event.overrideCartEnterState(enterState);
                SignAction.executeOne(this.getAction(), event);
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
            for (int i = 0; i < 4; i++) {
                if (!this.getLine(i).equals(other.getLine(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return this.signBlockHashCode;
        }

        /**
         * Gets a unique key by which this tracked sign can be identified. For real signs,
         * this is this the sign block with front/back parity. For custom signs a custom
         * tracking key can be added. A unique key is required to prevent one tracked sign
         * overwriting another during tracking by trains.<br>
         * <br>
         * This key should not change during the lifetime of this TrackedSign.<br>
         * <br>
         * Custom plugins can also register a supplier for the {@link TrackedSignLookup}
         * so that this tracked sign can be retrieved from the unique key. This is important
         * when restoring activated signs when trains reload (at server restart). Custom
         * key types can be registered there too, to make them persistently serialized.
         * If the key type is not String or UUID, a serializer for the key should be registered
         * too.
         *
         * @return unique key
         */
        public abstract Object getUniqueKey();

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        /**
         * Returns a new TrackedSign that tracks a real existing Sign Block.
         *
         * @param signTracker Sign Tracker for the Sign
         * @param rail Rail Piece the sign is for. If null, searches for the rail at the sign
         * @return TrackedSign for the sign tracked by the signTracker
         * @deprecated Tracks the front text of signs. Please use the alternative API to specify front or back
         *             to avoid possible broken behavior.
         */
        @Deprecated
        public static TrackedSign forRealSign(SignChangeTracker signTracker, RailPiece rail) {
            return forRealSign(signTracker, true, rail);
        }

        /**
         * Returns a new TrackedSign that tracks a real existing Sign Block.
         *
         * @param signBlock Sign Block
         * @param rail Rail Piece the sign is for. If null, searches for the rail at the sign
         * @return TrackedSign for the sign tracked by the signTracker
         * @deprecated Tracks the front text of signs. Please use the alternative API to specify front or back
         *             to avoid possible broken behavior.
         */
        @Deprecated
        public static TrackedSign forRealSign(Block signBlock, RailPiece rail) {
            return forRealSign(signBlock, true, rail);
        }

        /**
         * Returns a new TrackedSign that tracks a real existing Sign Block.
         *
         * @param sign Sign
         * @param rail Rail Piece the sign is for. If null, searches for the rail at the sign
         * @return TrackedSign for the sign tracked by the signTracker
         * @deprecated Tracks the front text of signs. Please use the alternative API to specify front or back
         *             to avoid possible broken behavior.
         */
        @Deprecated
        public static TrackedSign forRealSign(Sign sign, RailPiece rail) {
            return forRealSign(sign, true, rail);
        }

        /**
         * Returns a new TrackedSign that tracks a real existing Sign Block. Uses front text only.
         *
         * @param sign Sign. If null, searches for a Sign at the signBlock instead
         * @param signBlock Sign Block. If null, uses sign instead
         * @param rail Rail Piece the sign is for. If null, searches for the rail at the sign
         * @return TrackedSign for the sign tracked by the signTracker
         * @deprecated Tracks the front text of signs. Please use the alternative API to specify front or back
         *             to avoid possible broken behavior.
         */
        @Deprecated
        public static TrackedSign forRealSign(Sign sign, Block signBlock, RailPiece rail) {
            return forRealSign(sign, signBlock, true, rail);
        }

        /**
         * Returns a new TrackedSign that tracks a real existing Sign Block.
         *
         * @param signTracker Sign Tracker for the Sign
         * @param frontText Whether to represent the front text (true) or back text (false, MC 1.20+ only)
         * @param rail Rail Piece the sign is for. If null, searches for the rail at the sign
         * @return TrackedSign for the sign tracked by the signTracker
         */
        public static TrackedSign forRealSign(SignChangeTracker signTracker, boolean frontText, RailPiece rail) {
            if (signTracker.isRemoved()) {
                throw new IllegalArgumentException("Sign does not exist at sign block " + signTracker.getBlock());
            }
            if (rail == null) {
                rail = RailLookup.discoverRailPieceFromSign(signTracker.getBlock());
            }
            //TODO: Don't abuse static
            if (frontText) {
                return new TrackedRealSignFront(TrainCarts.plugin, signTracker, rail);
            } else {
                return new TrackedRealSignBack(TrainCarts.plugin, signTracker, rail);
            }
        }

        /**
         * Returns a new TrackedSign that tracks a real existing Sign Block.
         *
         * @param signBlock Sign Block
         * @param frontText Whether to represent the front text (true) or back text (false, MC 1.20+ only)
         * @param rail Rail Piece the sign is for. If null, searches for the rail at the sign
         * @return TrackedSign for the sign tracked by the signTracker
         */
        public static TrackedSign forRealSign(Block signBlock, boolean frontText, RailPiece rail) {
            if (signBlock == null) {
                throw new IllegalArgumentException("Sign block is null");
            }
            return forRealSign(SignChangeTracker.track(signBlock), frontText, rail);
        }

        /**
         * Returns a new TrackedSign that tracks a real existing Sign Block.
         *
         * @param sign Sign
         * @param frontText Whether to represent the front text (true) or back text (false, MC 1.20+ only)
         * @param rail Rail Piece the sign is for. If null, searches for the rail at the sign
         * @return TrackedSign for the sign tracked by the signTracker
         */
        public static TrackedSign forRealSign(Sign sign, boolean frontText, RailPiece rail) {
            if (sign == null) {
                throw new IllegalArgumentException("Sign is null");
            }
            return forRealSign(SignChangeTracker.track(sign), frontText, rail);
        }

        /**
         * Returns a new TrackedSign that tracks a real existing Sign Block.
         *
         * @param sign Sign. If null, searches for a Sign at the signBlock instead
         * @param signBlock Sign Block. If null, uses sign instead
         * @param frontText Whether to represent the front text (true) or back text (false, MC 1.20+ only)
         * @param rail Rail Piece the sign is for. If null, searches for the rail at the sign
         * @return TrackedSign for the sign tracked by the signTracker
         */
        public static TrackedSign forRealSign(Sign sign, Block signBlock, boolean frontText, RailPiece rail) {
            if (sign != null) {
                return forRealSign(SignChangeTracker.track(sign), frontText, rail);
            } else if (signBlock != null) {
                return forRealSign(SignChangeTracker.track(signBlock), frontText, rail);
            } else {
                throw new IllegalArgumentException("No sign or sign block specified (null)");
            }
        }

        /**
         * Options passed to {@link #showDebugHighlight(AttachmentViewer, DebugDisplayOptions)}
         */
        public interface DebugDisplayOptions extends TrainCarts.Provider {
            /**
             * ChatColor team color to use as the outline, in case a glow-effect outline is
             * used for display.
             *
             * @return Team Color
             */
            ChatColor getTeamColor();
        }
    }

    /**
     * Base implementation for a fake tracked sign. This behaves mostly like a Sign to Traincarts
     * but doesn't require an actual sign block to exist at the sign block coordinates.
     * Some sign actions require an actual addressable sign block, and won't support this type
     * of sign.
     */
    public static abstract class TrackedFakeSign extends TrackedSign {

        public TrackedFakeSign(RailPiece rail) {
            this(rail.block(), rail);
        }

        public TrackedFakeSign(Block signBlock, RailPiece rail) {
            super(FakeSign.create(signBlock), signBlock, rail);
            ((FakeSign) sign).setHandler(new FakeSign.Handler() {
                @Override
                public String getFrontLine(int index) {
                    return TrackedFakeSign.this.getLine(index);
                }

                @Override
                public void setFrontLine(int index, String text) {
                    TrackedFakeSign.this.setLine(index, text);
                }

                @Override
                public String getBackLine(int index) {
                    return "";
                }

                @Override
                public void setBackLine(int index, String text) {
                }
            });
        }

        /**
         * Gets a line of text on this fake sign
         *
         * @param index Line index. Should support [0..3]
         * @return Line at the index
         * @throws IndexOutOfBoundsException
         */
        public abstract String getLine(int index) throws IndexOutOfBoundsException;

        /**
         * Sets a line of text on this fake sign
         *
         * @param index Line index. Should support [0..3]
         * @param line Line at the index
         * @throws IndexOutOfBoundsException
         */
        public abstract void setLine(int index, String line) throws IndexOutOfBoundsException;

        @Override
        public boolean isRealSign() {
            return false;
        }

        @Override
        public Object getUniqueKey() {
            return this; // Use fake sign instance for uniqueness - can be changed
        }
    }

    /**
     * A tracked sign that refers to a real, physical sign. This sign can be found at
     * the sign block and has both a front and back side where text can be stored.
     * Uniquely capable of storing persistent metadata in the Offline sign metadata
     * store.
     */
    public static abstract class TrackedRealSign extends TrackedSign {

        protected TrackedRealSign(Sign sign, Block signBlock, RailPiece rail) {
            super(sign, signBlock, rail);
        }

        @Override
        public final boolean isRealSign() {
            return true;
        }

        /**
         * Gets whether this tracked sign refers to the front text of the sign (true)
         * or the back side (false, &gt;= MC 1.20 only)
         *
         * @return True if this tracked sign is for the front text
         */
        public abstract boolean isFrontText();

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append(this.getClass().getSimpleName()).append('{');
            str.append("world=").append(signBlock.getWorld().getName());
            str.append(", x=").append(signBlock.getX());
            str.append(", y=").append(signBlock.getY());
            str.append(", z=").append(signBlock.getZ());
            str.append(", side=").append(isFrontText() ? "front" : "back");
            str.append(", lines=[");
            for (int i = 0; i < 4; i++) {
                if (i > 0) {
                    str.append(" | ");
                }
                str.append(getLine(i));
            }
            str.append("]}");
            return str.toString();
        }
    }

    private static abstract class TrackedRealSignBase extends TrackedRealSign {
        protected final TrainCarts plugin;
        protected final SignChangeTracker tracker;
        protected final BlockFace facing;
        private final TrackedSignLookup.RealSignKey key;

        private TrackedRealSignBase(TrainCarts plugin, SignChangeTracker tracker, RailPiece rail, boolean front) {
            super(tracker.getSign(), tracker.getBlock(), rail);
            this.plugin = plugin;
            this.facing = tracker.getFacing();
            this.tracker = tracker;
            this.key = new TrackedSignLookup.RealSignKey(OfflineBlock.of(signBlock), front);
        }

        @Override
        public Object getUniqueKey() {
            return key;
        }

        @Override
        public boolean verify() {
            // If a change is detected, also inform the sign controller
            if (tracker.update()) {
                plugin.getSignController().notifySignChanged(tracker);
            }

            return !tracker.isRemoved() &&
                    tracker.getFacing() == facing &&
                    tracker.getSign() == sign;
        }

        @Override
        public boolean isRemoved() {
            return this.tracker.isRemoved();
        }

        @Override
        public boolean isFrontText() {
            return key.front;
        }

        @Override
        public String[] getExtraLines() {
            // If rail is none we sadly can't deduce the other signs
            // There is a way to do it, but it's freaking warm here in the Netherlands right now
            // and I don't want to bother writing block iteration or sign cache logic for it.
            RailPiece rail = this.getRail();
            if (rail.isNone()) {
                return StringUtil.EMPTY_ARRAY;
            }

            List<String> lines = new ArrayList<>();

            // Find other signs instances which are below this sign, repeatedly
            // Ignore tracked signs which match a SignAction by itself.
            // For this we use the SignController findNearby with a flag to include all
            // signs in the results, not just ones that have sign actions.
            //
            // RailPiece signs() cannot be used, because those signs are only those
            // that have sign actions.
            Block signBlock = this.signBlock.getRelative(BlockFace.DOWN);
            SignControllerWorld signController = plugin.getSignController().forWorld(rail.world());

            while (true) {
                SignController.Entry entry = signController.findForSign(signBlock, false);
                if (entry == null) {
                    break;
                }
                if (entry.sign.getFacing() != facing) {
                    break;
                }

                // Load the sign, make sure it isn't an action sign itself
                TrackedSign sign = isFrontText() ? entry.createFrontTrackedSign(rail)
                                                 : entry.createBackTrackedSign(rail);
                if (sign.getAction() != null) {
                    break;
                }

                for (int i = 0; i < 4; i++) {
                    lines.add(sign.getLine(i));
                }
                signBlock = signBlock.getRelative(BlockFace.DOWN);
            }

            return lines.toArray(new String[0]);
        }

        @Override
        public Runnable showDebugHighlight(AttachmentViewer viewer, DebugDisplayOptions options) {
            return SignController.spawnDebugHighlight(viewer, tracker, options);
        }

        @Override
        public Block getAttachedBlock() {
            return this.signBlock.getRelative(this.tracker.getAttachedFace());
        }

        @Override
        public PowerState getPower(BlockFace from) {
            return PowerState.get(this.signBlock, from, (getAction() != null)
                    ? PowerState.Options.SIGN_CONNECT_WIRE
                    : PowerState.Options.SIGN);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof TrackedRealSignBase) {
                return ((TrackedRealSignBase) o).key.equals(key);
            }
            return false;
        }
    }

    private static class TrackedRealSignFront extends TrackedRealSignBase {

        private TrackedRealSignFront(TrainCarts plugin, SignChangeTracker tracker, RailPiece rail) {
            super(plugin, tracker, rail, true);
        }

        @Override
        public BlockFace getFacing() {
            return this.facing;
        }

        @Override
        public String getLine(int index) throws IndexOutOfBoundsException {
            return this.tracker.getFrontLine(index);
        }

        @Override
        public void setLine(int index, String line) throws IndexOutOfBoundsException {
            this.tracker.setFrontLine(index, line);
        }
    }

    private static class TrackedRealSignBack extends TrackedRealSignBase {

        private TrackedRealSignBack(TrainCarts plugin, SignChangeTracker tracker, RailPiece rail) {
            super(plugin, tracker, rail, false);
        }

        @Override
        public BlockFace getFacing() {
            return this.facing.getOppositeFace();
        }

        @Override
        public String getLine(int index) throws IndexOutOfBoundsException {
            return this.tracker.getBackLine(index);
        }

        @Override
        public void setLine(int index, String line) throws IndexOutOfBoundsException {
            this.tracker.setBackLine(index, line);
        }
    }

    /**
     * TrackedSign to be used in Unit Tests. Cannot be used as a valid sign on an actual server,
     * because it lacks any sign block information and breaks various APIs.
     */
    public static final class UnitTestTrackedSign extends TrackedSign {
        private final String[] lines;

        public static UnitTestTrackedSign of(String... lines) {
            return new UnitTestTrackedSign(lines);
        }

        private UnitTestTrackedSign(String[] lines) {
            super();
            this.lines = lines;
        }

        @Override
        public boolean verify() {
            return true;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public BlockFace getFacing() {
            return BlockFace.NORTH;
        }

        @Override
        public Block getAttachedBlock() {
            return null;
        }

        @Override
        public String[] getExtraLines() {
            return new String[0];
        }

        @Override
        public PowerState getPower(BlockFace from) {
            return PowerState.NONE;
        }

        @Override
        public boolean isRealSign() {
            return false;
        }

        @Override
        public String getLine(int index) throws IndexOutOfBoundsException {
            return lines[index];
        }

        @Override
        public void setLine(int index, String line) throws IndexOutOfBoundsException {
            throw new UnsupportedOperationException("Not supported for unit test tracked signs");
        }

        @Override
        public Object getUniqueKey() {
            return this;
        }
    }
}
