package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.WorldRailLookup;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import java.util.List;

/**
 * A single Minecraft chunk, with information about the signs that exist inside.
 * Keeps track of whether these signs have been registered in the by-neighbouring-block
 * mapping and whether any of these feature sign action events to handle.
 */
public class SignControllerChunk {
    /** Unique key of this chunk, which encodes the chunk x/z coordinates */
    public final long chunkKey;
    /** All signs that exist inside this chunk, including ones that lack sign actions (non-tc signs) */
    private SignController.EntryList entries = SignController.EntryList.NONE;
    /**
     * All signs that exist inside this chunk that feature sign action events. These are signs that respond to redstone
     * and can be activated by trains. Lazily initialized when first used.
     */
    private SignController.EntryList entriesWithSignActions = null; // Not initialized
    /** Whether all these signs have been registered in the by-neighbouring-blocks logic */
    private LoadLevel neighbouringBlocksLoadLevel = LoadLevel.NOT_LOADED;
    /** The tick that the entries inside were last verified. At most we try to verify once a tick while in use */
    private int lastVerifyTick = -1;

    public static long getKeyOf(Chunk chunk) {
        return MathUtil.longHashToLong(chunk.getX(), chunk.getZ());
    }

    public SignControllerChunk(long chunkKey) {
        this.chunkKey = chunkKey;
    }

    /**
     * Called on chunk load to initialize all the entries that exist in the chunk.
     * Resets certain tracking states.
     *
     * @param entries Entries
     */
    public void initialize(List<SignController.Entry> entries) {
        this.entries = SignController.EntryList.of(entries);
        this.entriesWithSignActions = null; // Reset
        this.neighbouringBlocksLoadLevel = LoadLevel.NOT_LOADED; // Reset
    }

    public boolean hasSigns() {
        return entries.count() > 0;
    }

    public SignController.Entry[] getEntries() {
        return this.entries.unsortedValues();
    }

    /**
     * Adds a new entry to the entries-in-chunk list. If entries were previously registered
     * in the by-neighbouring-block mapping, then that is also done for this new entry.
     *
     * @param entry Entry to add
     */
    public void addEntry(SignController.Entry entry) {
        SignController.EntryList entries = this.entries;
        SignController.EntryList entriesWithSignActions = this.entriesWithSignActions;

        if (entriesWithSignActions == null) {
            // Only tracked in the all-list, neighbours not loaded. Very common case (chunk load)
            this.entries = entries.add(entry);
        } else if (!entry.hasSignActionEvents()) {
            // Only tracked in the all-list
            this.entries = entries.add(entry);
        } else if (entries == entriesWithSignActions) {
            // Same list, reuse list and add to both that way
            this.entries = this.entriesWithSignActions = entries.add(entry);
        } else {
            // Separate lists, must be added separately (some signs don't have sign actions)
            this.entries = entries.add(entry);
            this.entriesWithSignActions = entriesWithSignActions.add(entry);
        }

        LoadLevel neighbouringBlocksLoadLevel = this.neighbouringBlocksLoadLevel;
        if (neighbouringBlocksLoadLevel != LoadLevel.NOT_LOADED) {
            // Only happens when signs are added inside an already-loaded chunk.
            if (neighbouringBlocksLoadLevel == LoadLevel.ALL_SIGNS || entry.hasSignActionEvents()) {
                entry.registerInNeighbouringBlocks();
            }
        }
    }

    /**
     * Removes an entry from this mapping (sign removed)
     *
     * @param entry Entry that was removed
     */
    public void removeEntry(SignController.Entry entry) {
        this.entries = this.entries.filter(e -> e != entry);
        if (entry.hasSignActionEvents()) {
            this.entriesWithSignActions = null;
        }
        entry.unregisterInNeighbouringBlocks();
    }

    /**
     * Called when the contents of a sign are changed so that the sign now does or does not
     * contain sign actions. Should refresh its own recordkeeping/registration of the entry.
     *
     * @param entry Entry
     * @param hasSignActions Whether entry has sign actions now, or not
     */
    public void updateEntryHasSignActions(SignController.Entry entry, boolean hasSignActions) {
        SignController.EntryList entriesWithSignActions = this.entriesWithSignActions;
        if (entriesWithSignActions != null) {
            if (!hasSignActions) {
                this.entriesWithSignActions = entriesWithSignActions.filter(e -> e != entry);
            } else if (!entriesWithSignActions.contains(entry)) {
                this.entriesWithSignActions = entriesWithSignActions.add(entry);
            }
        }

        LoadLevel neighbouringBlocksLoadLevel = this.neighbouringBlocksLoadLevel;
        if (neighbouringBlocksLoadLevel != LoadLevel.NOT_LOADED) {
            // Only happens when signs are added inside an already-loaded chunk.
            if (neighbouringBlocksLoadLevel == LoadLevel.ALL_SIGNS || hasSignActions) {
                entry.registerInNeighbouringBlocks();

                // Invalidate signs in the RailTracker by-rail lookup cache right away
                // It relies on this by-block lookup so it's likely invalid now
                {
                    WorldRailLookup railLookup = RailLookup.forWorldIfInitialized(entry.world.getWorld());
                    if (railLookup != null) {
                        railLookup.discoverRailPieceFromSign(entry.sign.getBlock()).forceCacheVerification();
                    }
                }
            }
        }
    }

    /**
     * Calls {@link SignController.Entry#verify()} on all the entries inside this chunk.
     * Signs that no longer exist are removed. Signs that change text are refreshed.
     * This includes going between detecting them as TrainCarts signs and not.
     */
    public void verifyEntries() {
        for (SignController.Entry e : getEntries()) {
            if (!e.verify()) {
                e.removeInvalidEntry();
            }
        }
    }

    /**
     * Checks whether this chunk has a particular sign nearby a given x/y/z.
     * This checks whether that could be the case, and may also return true when
     * this isn't certain.
     * Will register the entries into the by-neighbouring-block mapping if that
     * hasn't been done yet, and at least one relevant TrainCarts sign exists.
     *
     * @param x Search X-position
     * @param y Search Y-position
     * @param z Search Z-position
     * @param mustHaveSignActions Whether this method should only check for signs that
     *                            feature sign actions (redstone events, train activation).
     *                            Should be false when looking for all signs, such as is the
     *                            case for "signs below sign" extended text logic.
     * @param currentTick Current server tick value. Used to auto-verify signs periodically.
     * @return True if signs might be nearby
     */
    public boolean checkMayHaveSigns(int x, int y, int z, boolean mustHaveSignActions, int currentTick) {
        if (currentTick != lastVerifyTick) {
            lastVerifyTick = currentTick;
            verifyEntries();
        }

        SignController.EntryList entries;
        if (mustHaveSignActions) {
            // Cache a separate list of entries that have sign action events
            // If all signs in the chunk have them, will return the same entries instance
            entries = this.entriesWithSignActions;
            if (entries == null) {
                this.entriesWithSignActions = entries = this.entries.filter(SignController.Entry::hasSignActionEvents);
            }
        } else {
            // All signs in chunk
            entries = this.entries;
        }

        int entryCount = entries.count();
        if (entryCount == 0) {
            return false;
        }

        if (entryCount <= 20) {
            // Check whether any signs are neighbouring this x/y/z
            // When there's too many signs, omit searching for them as that would be unneededly slow
            boolean mayHaveSigns = false;
            for (SignController.Entry entry : entries.unsortedValues()) {
                Block b = entry.getBlock();
                if (
                        Math.abs(b.getX() - x) <= 2 &&
                        Math.abs(b.getY() - y) <= 2 &&
                        Math.abs(b.getZ() - z) <= 2
                ) {
                    mayHaveSigns = true;
                    break;
                }
            }
            if (!mayHaveSigns) {
                return false;
            }
        }

        // If the by-neighbouring-block logic hasn't yet been initialized for this chunk, do so now.
        // This registers all entries in the chunk so that they can be looked up efficiently by block coordinates
        LoadLevel currentLevel = this.neighbouringBlocksLoadLevel;
        LoadLevel requestedLevel = mustHaveSignActions ? LoadLevel.WITH_SIGN_ACTIONS_ONLY : LoadLevel.ALL_SIGNS;
        if (requestedLevel.level() > currentLevel.level()) {
            for (SignController.Entry e : entries.unsortedValues()) {
                e.registerInNeighbouringBlocks();
            }
            this.neighbouringBlocksLoadLevel = requestedLevel;
        }

        return true;
    }

    private enum LoadLevel {
        NOT_LOADED(0),
        WITH_SIGN_ACTIONS_ONLY(1),
        ALL_SIGNS(2);

        private final int level;

        LoadLevel(int level) {
            this.level = level;
        }

        public int level() {
            return level;
        }
    }
}
