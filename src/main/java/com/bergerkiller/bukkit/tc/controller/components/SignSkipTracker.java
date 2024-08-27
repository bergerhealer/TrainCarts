package com.bergerkiller.bukkit.tc.controller.components;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.SignTracker.ActiveSign;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.properties.standard.StandardProperties;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;

/**
 * Tracks the signs passed by a train or cart and applies {@link SignSkipOptions}
 * property to them.
 */
public class SignSkipTracker {
    private final IPropertiesHolder owner;
    private boolean isLoaded = false;
    private final Map<TrackedSign, Boolean> history = new HashMap<TrackedSign, Boolean>();

    public SignSkipTracker(IPropertiesHolder owner) {
        this.owner = owner;
    }

    /**
     * Gets whether a particular sign is being skipped. This means no events will
     * be fired for when this particular sign is entered or left.
     *
     * @param sign Sign
     * @return True if this sign is skipped
     */
    public boolean isSkipped(TrackedSign sign) {
        return Boolean.TRUE.equals(history.get(sign));
    }

    /**
     * Gets a List of all tracked signs that have been skipped
     *
     * @return List of skipped signs
     */
    public List<TrackedSign> getSkippedSigns() {
        if (history.isEmpty()) {
            return Collections.emptyList();
        }
        return history.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Sets a sign as being skipped
     *
     * @param sign Sign
     */
    public void setSkipped(SignTracker.ActiveSign sign) {
        setSkipped(sign, true);
    }

    /**
     * Sets a sign as being skipped
     *
     * @param sign Sign
     * @param skipped Whether it is skipped or not
     */
    public void setSkipped(SignTracker.ActiveSign sign, boolean skipped) {
        this.history.put(sign.getSign(), skipped);
    }

    /**
     * Initializes the sign skipping states. This is called when a train
     * is restored.
     * 
     * @param signs
     */
    public void loadSigns(List<SignTracker.ActiveSign> signs) {
        this.isLoaded = true;
        this.history.clear();
        if (signs.isEmpty()) {
            return;
        }

        // First store all entries in history that have state=true (stored in properties)
        // Then, add all other signs that exist
        // This feature is no longer used in new versions of traincarts, and is here
        // for backwards-compatibility. Will probably be removed in the near future.
        SignSkipOptions options = owner.getProperties().get(StandardProperties.SIGN_SKIP);
        if (options.hasSkippedSigns()) {
            // Signs were set to be skipped, more complicated initialization
            for (BlockLocation signPos : options.skippedSigns()) {
                for (SignTracker.ActiveSign sign : signs) {
                    Block signBlock = sign.getSign().signBlock;
                    if (signPos.x == signBlock.getX() &&
                            signPos.y == signBlock.getY() &&
                            signPos.z == signBlock.getZ() &&
                            signPos.world.equals(signBlock.getWorld().getName()))
                    {
                        this.history.put(sign.getSign(), Boolean.TRUE);
                        break;
                    }
                }
            }
        }

        for (SignTracker.ActiveSign sign : signs) {
            this.history.putIfAbsent(sign.getSign(), Boolean.FALSE);
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
     * Called when a new rails block is visited and new signs are loaded in. This tracker
     * does some maintenance work, such as removing signs from history that the train is
     * no longer seeing.
     *
     * @param signs New list of signs that the train or cart sees right now
     */
    public void onSignVisitStart(List<SignTracker.ActiveSign> signs) {
        // Load if needed
        if (!this.isLoaded) {
            this.loadSigns(signs);
        }

        // Remove states from history for signs that are no longer tracked
        {
            Iterator<Map.Entry<TrackedSign, Boolean>> iter = history.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<TrackedSign, Boolean> e = iter.next();
                boolean found = false;
                for (ActiveSign sign : signs) {
                    if (sign.getSign().equals(e.getKey())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    iter.remove();
                }
            }
        }

        // Now: For each sign onSignVisit(sign) is called
        //      Go by all signs and if they don't already exist, add them
        //      Check if they need to be skipped when doing so
    }

    /**
     * Called from the block tracker to filter the detected signs based on the skip settings.
     * The signs specified should contain all signs known to the minecart for proper functioning.
     * 
     * @param sign New or existing sign that is visited just now
     * @return true to see the sign, false to skip it
     */
    public boolean onSignVisit(SignTracker.ActiveSign sign) {
        Boolean isSignSkipped = this.history.computeIfAbsent(sign.getSign(), trackedSign -> {
            // This lambda runs when a sign is encountered it has not seen before
            // When this happens, run the filtering logic

            // Read settings
            IProperties properties = this.owner.getProperties();
            final SignSkipOptions options = properties.get(StandardProperties.SIGN_SKIP);

            // Shortcut: if not active, don't do anything
            if (!options.isActive()) {
                return Boolean.FALSE;
            }

            boolean passFilter = true;
            if (options.hasFilter()) {
                if (trackedSign.sign == null) {
                    passFilter = false; // should never happen, but just in case
                } else {
                    passFilter = Util.getCleanLine(trackedSign.sign, 1).toLowerCase(Locale.ENGLISH).startsWith(options.filter());
                }
            }

            Boolean isNewSignSkipped;
            if (passFilter) {
                // Track down below when the skipped signs change
                // When it does, we need to update the property to ensure persistence
                final SkipOptionChanges changes = new SkipOptionChanges(options);
                isNewSignSkipped = changes.handleSkip();

                // If options stored signs in the past, get rid of them
                // If skip offset/count changed, update as well
                if (options.hasSkippedSigns() || changes.countersChanged) {
                    properties.set(StandardProperties.SIGN_SKIP, SignSkipOptions.create(
                            changes.ignoreCounter,
                            changes.skipCounter,
                            options.filter(),
                            Collections.emptySet()
                    ));
                }
            } else {
                isNewSignSkipped = Boolean.FALSE;
            }

            return isNewSignSkipped;
        });

        // If skipped, return false to indicate it is filtered
        return !isSignSkipped;
    }

    private static final class SkipOptionChanges {
        public int ignoreCounter;
        public int skipCounter;
        public boolean countersChanged;

        public SkipOptionChanges(SignSkipOptions options) {
            this.ignoreCounter = options.ignoreCounter();
            this.skipCounter = options.skipCounter();
            this.countersChanged = false;
        }

        public Boolean handleSkip() {
            if (this.ignoreCounter > 0) {
                this.ignoreCounter--;
                this.countersChanged = true;
                return Boolean.FALSE;
            } else if (this.skipCounter > 0) {
                this.skipCounter--;
                this.countersChanged = true;
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        }
    }
}
