package com.bergerkiller.bukkit.tc.controller.components;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.cache.RailSignCache.TrackedSign;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;

/**
 * Tracks the signs passed by a train or cart and applies {@link SignSkipOptions}
 * property to them.
 */
public class SignSkipTracker {
    private final IPropertiesHolder owner;
    private boolean isLoaded = false;
    private boolean hasSkippedSigns = false;
    private final Map<TrackedSign, Boolean> history = new HashMap<TrackedSign, Boolean>();

    public SignSkipTracker(IPropertiesHolder owner) {
        this.owner = owner;
    }

    /**
     * Initializes the sign skipping states. This is called when a train
     * is restored.
     * 
     * @param signs
     */
    public void loadSigns(List<TrackedSign> signs) {
        if (!this.isLoaded) {
            this.isLoaded = true;
            this.hasSkippedSigns = false;
            this.history.clear();

            // First store all entries in history that have state=true (stored in properties)
            // Then, add all other signs that exist
            SignSkipOptions options = owner.getProperties().get(StandardProperties.SIGN_SKIP);
            if (options.hasSkippedSigns()) {
                // Signs were set to be skipped, more complicated initialization
                for (BlockLocation signPos : options.skippedSigns()) {
                    for (TrackedSign sign : signs) {
                        Block signBlock = sign.signBlock;
                        if (signPos.x == signBlock.getX() &&
                            signPos.y == signBlock.getY() &&
                            signPos.z == signBlock.getZ() &&
                            signPos.world.equals(signBlock.getWorld().getName()))
                        {
                            this.history.put(sign, Boolean.TRUE);
                            this.hasSkippedSigns = true;
                            break;
                        }
                    }
                }
                for (TrackedSign sign : signs) {
                    if (!this.history.containsKey(sign)) {
                        this.history.put(sign, Boolean.FALSE);
                    }
                }
            } else {
                // Simplified
                for (TrackedSign sign : signs) {
                    this.history.put(sign, Boolean.FALSE);
                }
            }
        }
    }

    /**
     * Removes all sign skipping states (free memory)
     */
    public void unloadSigns() {
        if (this.isLoaded) {
            this.isLoaded = false;
            this.history.clear();
        }
    }

    /**
     * Called from the block tracker to filter the detected signs based on the skip settings.
     * The signs specified should contain all signs known to the minecart for proper functioning.
     * 
     * @param signs (modifiable!)
     */
    public void filterSigns(List<TrackedSign> signs) {
        // Load if needed
        if (!this.isLoaded) {
            this.loadSigns(signs);
        }

        // Read settings
        IProperties properties = this.owner.getProperties();
        final SignSkipOptions options = properties.get(StandardProperties.SIGN_SKIP);

        // Not active; simplified logic to minimize wasted CPU
        if (!options.isActive() && !this.hasSkippedSigns) {
            this.history.clear();
            for (TrackedSign sign : signs) {
                this.history.put(sign, Boolean.FALSE);
            }
            return;
        }

        // Track down below when the skipped signs change
        // When it does, we need to update the property to ensure persistence
        final SkipOptionChanges changes = new SkipOptionChanges(options);

        // Remove states from history for signs that are no longer tracked
        {
            Iterator<Map.Entry<TrackedSign, Boolean>> iter = history.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<TrackedSign, Boolean> e = iter.next();
                if (!signs.contains(e.getKey())) {
                    changes.skippedSignsChanged |= e.getValue().booleanValue();
                    iter.remove();
                }
            }
        }

        // Go by all signs and if they don't already exist, add them
        // Check if they need to be skipped when doing so
        this.hasSkippedSigns = false;
        Iterator<TrackedSign> iter = signs.iterator();
        while (iter.hasNext()) {
            Boolean historyState = this.history.computeIfAbsent(iter.next(), sign -> {
                boolean passFilter = true;
                if (options.hasFilter()) {
                    if (sign.sign == null) {
                        passFilter = false; // should never happen, but just in case
                    } else {
                        passFilter = Util.getCleanLine(sign.sign, 1).toLowerCase(Locale.ENGLISH).startsWith(options.filter());
                    }
                }
                return passFilter ? changes.handleSkip() : Boolean.FALSE;
            });

            // When state is 'true', skip the sign
            if (historyState.booleanValue()) {
                this.hasSkippedSigns = true;
                iter.remove();
            }
        }

        // Save skipped signs property again when it changes
        if (changes.skippedSignsChanged) {
            // Store the signs that are skipped in history
            if (this.hasSkippedSigns) {
                // Produce set of skipped signs and update
                Set<BlockLocation> skippedSigns = this.history.entrySet().stream()
                    .filter(e -> e.getValue().booleanValue())
                    .map(e -> new BlockLocation(e.getKey().signBlock))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

                // Store updated options
                properties.set(StandardProperties.SIGN_SKIP, SignSkipOptions.create(
                        changes.ignoreCounter,
                        changes.skipCounter,
                        options.filter(),
                        Collections.unmodifiableSet(skippedSigns)
                ));
            } else {
                // Had signs, clear them
                properties.set(StandardProperties.SIGN_SKIP, SignSkipOptions.create(
                        changes.ignoreCounter,
                        changes.skipCounter,
                        options.filter(),
                        Collections.emptySet()
                ));
            }
        } else if (changes.countersChanged) {
            // Only update counters
            properties.set(StandardProperties.SIGN_SKIP, SignSkipOptions.create(
                    changes.ignoreCounter,
                    changes.skipCounter,
                    options.filter(),
                    options.skippedSigns()
            ));
        }
    }

    private static final class SkipOptionChanges {
        public int ignoreCounter;
        public int skipCounter;
        public boolean countersChanged;
        public boolean skippedSignsChanged;

        public SkipOptionChanges(SignSkipOptions options) {
            this.ignoreCounter = options.ignoreCounter();
            this.skipCounter = options.skipCounter();
            this.countersChanged = false;
            this.skippedSignsChanged = false;
        }

        public Boolean handleSkip() {
            if (this.ignoreCounter > 0) {
                this.ignoreCounter--;
                this.countersChanged = true;
                return Boolean.FALSE;
            } else if (this.skipCounter > 0) {
                this.skipCounter--;
                this.countersChanged = true;
                this.skippedSignsChanged = true;
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        }
    }
}
