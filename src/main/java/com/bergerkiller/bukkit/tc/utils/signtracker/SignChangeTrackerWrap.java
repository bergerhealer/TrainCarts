package com.bergerkiller.bukkit.tc.utils.signtracker;

import java.util.function.Function;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.bergerkiller.bukkit.common.Common;

/**
 * Wraps the newly-added BKCommonLib SignChangeTracker, if available. For older BKCL versions a fallback is used
 * which simply re-retrieves the BlockState every update check.<br>
 * <br>
 * Remove this when BKCommonLib 1.18.2-v2 or newer is a hard-dep!
 */
public abstract class SignChangeTrackerWrap {
    private static final Function<Block, SignChangeTrackerWrap> constructor;
    static {
        if (Common.hasCapability("Common:SignChangeTracker")) {
            constructor = SignChangeTrackerBKCL::new;
        } else {
            constructor = SignChangeTrackerFallback::new;
        }
    }

    public abstract boolean isRemoved();
    public abstract Block getBlock();
    public abstract Sign getSign();
    public abstract boolean update();

    /**
     * Tracks the sign block
     *
     * @param signBlock
     * @return Sign change tracker wrapper
     */
    public static SignChangeTrackerWrap track(Block signBlock) {
        return constructor.apply(signBlock);
    }
}
