package com.bergerkiller.bukkit.tc.utils;

import java.util.Collections;
import java.util.Set;

import com.bergerkiller.bukkit.common.BlockLocation;

/**
 * Stores information about sign skipping configurations.
 * This allows trains to skip a number of signs.
 * The {@link com.bergerkiller.bukkit.tc.controller.components.SignSkipTracker SignSkipTracker} enforces these options.
 */
public class SignSkipOptions {
    /**
     * Constant storing persistent data that is the default.
     * No skip signs were/are in use.
     */
    public static final SignSkipOptions NONE = new SignSkipOptions(0, 0, "", Collections.emptySet());

    public final int ignoreCounter;
    public final int skipCounter;
    public final String filter;
    public final Set<BlockLocation> skippedSigns;

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
     * Gets whether any sort of skip configuration is currently active
     * 
     * @return True if ignore counter or skip counter are set to a value
     */
    public boolean isActive() {
        return this.ignoreCounter != 0 || this.skipCounter != 0;
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
