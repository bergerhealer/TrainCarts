package com.bergerkiller.bukkit.tc.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.cache.RailSignCache.TrackedSign;

/**
 * Stores information about sign skipping configurations.
 * This allows trains to skip a number of signs.
 */
public class SignSkipOptions {
    public int ignoreCtr = 0;
    public int skipCtr = 0;
    public String filter = "";
    private boolean isLoaded = false;
    private boolean hasSkippedSigns = false;
    private final Map<TrackedSign, Boolean> history = new HashMap<TrackedSign, Boolean>();
    private final List<BlockLocation> offlineSkippedSigns = new ArrayList<BlockLocation>();

    /**
     * Checks whether these sign skip options have any effect at all
     * 
     * @return True if active
     */
    public boolean isActive() {
        return ignoreCtr != 0 || skipCtr != 0 || hasSkippedSigns;
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
            for (TrackedSign sign : signs) {
                BlockLocation signPos = new BlockLocation(sign.signBlock);
                boolean state = this.offlineSkippedSigns.contains(signPos);
                if (state) {
                    this.hasSkippedSigns = true;
                }
                this.history.put(sign, state);
            }
            this.offlineSkippedSigns.clear();
        }
    }

    /**
     * Removes all sign skipping states (free memory)
     */
    public void unloadSigns() {
        if (this.isLoaded) {
            this.offlineSkippedSigns.clear();
            for (Map.Entry<TrackedSign, Boolean> entry : this.history.entrySet()) {
                if (entry.getValue().booleanValue()) {
                    this.offlineSkippedSigns.add(new BlockLocation(entry.getKey().signBlock));
                }
            }
            this.history.clear();
            this.isLoaded = false;
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

        // Not active; simplified logic to minimize wasted CPU
        if (!this.isActive()) {
            this.history.clear();
            for (TrackedSign sign : signs) {
                this.history.put(sign, Boolean.FALSE);
            }
            return;
        }

        // Remove states from history for signs that are no longer tracked
        Iterator<TrackedSign> historyIter = history.keySet().iterator();
        while (historyIter.hasNext()) {
            TrackedSign sign = historyIter.next();
            if (!signs.contains(sign)) {
                historyIter.remove();
            }
        }

        this.hasSkippedSigns = false;
        Iterator<TrackedSign> iter = signs.iterator();
        while (iter.hasNext()) {
            Boolean historyState = this.history.computeIfAbsent(iter.next(), sign -> {
                boolean passFilter = true;
                if (this.filter.length() > 0) {
                    if (sign.sign == null) {
                        passFilter = false; // should never happen, but just in case
                    } else {
                        passFilter = Util.getCleanLine(sign.sign, 1).toLowerCase(Locale.ENGLISH).startsWith(this.filter);
                    }
                }
                if (passFilter) {
                    if (this.ignoreCtr > 0) {
                        this.ignoreCtr--;
                    } else if (this.skipCtr > 0) {
                        this.skipCtr--;
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            });

            // When state is 'true', skip the sign
            if (historyState.booleanValue()) {
                this.hasSkippedSigns = true;
                iter.remove();
            }
        }
    }

    /**
     * Loads the settings for these options. If loadSigns is true, then
     * the tracked signs are loaded as well.
     * 
     * @param source
     * @param loadSigns
     */
    public void load(SignSkipOptions source, boolean loadSigns) {
        this.ignoreCtr = source.ignoreCtr;
        this.skipCtr = source.skipCtr;
        this.filter = source.filter;
        if (loadSigns) {
            this.isLoaded = source.isLoaded;
            this.hasSkippedSigns = source.hasSkippedSigns;
            this.history.clear();
            this.history.putAll(source.history);
            this.offlineSkippedSigns.clear();
            this.offlineSkippedSigns.addAll(source.offlineSkippedSigns);
            this.unloadSigns(); // make sure to reload them next time
        }
    }

    public void load(ConfigurationNode config) {
        this.ignoreCtr = config.get("ignoreCtr", 0);
        this.skipCtr = config.get("skipCtr", 0);
        this.filter = config.get("filter", "");
        this.offlineSkippedSigns.clear();
        if (config.contains("signs")) {
            for (String sign : config.getList("signs", String.class)) {
                BlockLocation loc = BlockLocation.parseLocation(sign);
                if (loc != null) {
                    this.offlineSkippedSigns.add(loc);
                }
            }
            if (!this.offlineSkippedSigns.isEmpty()) {
                this.hasSkippedSigns = true;
            }
        }
    }

    public void save(ConfigurationNode config) {
        config.set("ignoreCtr", this.ignoreCtr);
        config.set("skipCtr", this.skipCtr);
        config.set("filter", this.filter);

        // Save signs in an offline friendly format
        List<BlockLocation> signs = new ArrayList<BlockLocation>(this.offlineSkippedSigns);
        for (Map.Entry<TrackedSign, Boolean> entry : this.history.entrySet()) {
            if (entry.getValue().booleanValue()) {
                signs.add(new BlockLocation(entry.getKey().signBlock));
            }
        }
        if (!signs.isEmpty()) {
            List<String> signsText = new ArrayList<String>();
            for (BlockLocation loc : signs) {
                signsText.add(loc.toString());
            }
            config.set("signs", signsText);
        } else if (config.contains("signs")) {
            config.remove("signs");
        }
    }
}
