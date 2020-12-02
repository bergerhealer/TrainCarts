package com.bergerkiller.bukkit.tc.properties.standard.type;

import java.util.Collections;
import java.util.Set;

import com.bergerkiller.bukkit.common.BlockLocation;

/**
 * Stores information about sign skipping configurations.
 * This allows trains to skip a number of signs.
 * The {@link com.bergerkiller.bukkit.tc.controller.components.SignSkipTracker SignSkipTracker} enforces these options.
 */
public final class SignSkipOptions {
    /**
     * Constant storing persistent data that is the default.
     * No skip signs were/are in use.
     */
    public static final SignSkipOptions NONE = new SignSkipOptions(0, 0, "", Collections.emptySet());

    private final int ignoreCounter;
    private final int skipCounter;
    private final String filter;
    private final Set<BlockLocation> skippedSigns;

    private SignSkipOptions(
            int ignoreCounter,
            int skipCounter,
            String filter,
            Set<BlockLocation> signs
    ) {
        this.ignoreCounter = ignoreCounter;
        this.skipCounter = skipCounter;
        this.filter = filter;
        this.skippedSigns = signs;
    }

    /**
     * Gets the number of signs to ignore and allow anyway before the
     * {@link #skipCounter()} becomes active.
     * 
     * @return number of signs to ignore
     */
    public int ignoreCounter() {
        return this.ignoreCounter;
    }

    /**
     * Gets the number of signs to skip. Signs that are ignored
     * using the {@link #ignoreCounter()} are not skipped.
     * 
     * @return number of signs to skip
     */
    public int skipCounter() {
        return this.skipCounter;
    }

    /**
     * Gets whether a {@link #filter()} is currently used or not.
     * 
     * @return True if a filter is used
     */
    public boolean hasFilter() {
        return !this.filter.isEmpty();
    }

    /**
     * Gets the sign type filter applied as a filtering rule for
     * signs to ignore and skip. Only signs that start with this filter
     * text on the second line of the sign are ignored/skipped.
     * If empty, all signs apply.
     * 
     * @return filter rule
     */
    public String filter() {
        return this.filter;
    }

    /**
     * Gets whether there are any signs currently being skipped
     * by the train.
     * 
     * @return True if there are skipped signs
     */
    public boolean hasSkippedSigns() {
        return !this.skippedSigns.isEmpty();
    }

    /**
     * Gets the block locations where signs are located that are
     * currently suppressed by the skip behavior logic. When the train
     * spawns, these signs will not be triggered.
     * 
     * @return set of block locations of signs currently skipped
     */
    public Set<BlockLocation> skippedSigns() {
        return this.skippedSigns;
    }

    /**
     * Gets whether any sort of skip configuration is currently active
     * 
     * @return True if ignore counter or skip counter are set to a value
     */
    public boolean isActive() {
        return this.ignoreCounter != 0 || this.skipCounter != 0;
    }

    @Override
    public int hashCode() {
        return this.skipCounter;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof SignSkipOptions) {
            SignSkipOptions other = (SignSkipOptions) o;
            return this.ignoreCounter == other.ignoreCounter &&
                   this.skipCounter == other.skipCounter &&
                   this.filter.equals(other.filter) &&
                   this.skippedSigns.equals(other.skippedSigns);
        } else {
            return false;
        }
    }

    /**
     * Constructs new SignSkipOptions
     * 
     * @param ignoreCounter Number of signs to allow anyway until the skip counter is active
     * @param skipCounter Number of signs remaining to be skipped
     * @param filter Filter rule to apply (statements)
     * @return new options
     */
    public static SignSkipOptions create(
            int ignoreCounter,
            int skipCounter,
            String filter
    ) {
        return new SignSkipOptions(ignoreCounter, skipCounter, filter, Collections.emptySet());
    }

    /**
     * Constructs new SignSkipOptions
     * 
     * @param ignoreCounter Number of signs to allow anyway until the skip counter is active
     * @param skipCounter Number of signs remaining to be skipped
     * @param filter Filter rule to apply (statements)
     * @param skippedSigns Set of sign block locations that are actively being skipped
     * @return new options
     */
    public static SignSkipOptions create(
            int ignoreCounter,
            int skipCounter,
            String filter,
            Set<BlockLocation> skippedSigns
    ) {
        return new SignSkipOptions(ignoreCounter, skipCounter, filter, skippedSigns);
    }
}
