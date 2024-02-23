package com.bergerkiller.bukkit.tc.properties.standard.type;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StreamUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Immutable configuration of the chunk loader feature of trains
 */
public final class ChunkLoadOptions {
    /** Default setting of the chunk load options property */
    public static final ChunkLoadOptions DEFAULT = new ChunkLoadOptions(Mode.DISABLED, 2);
    /** Default setting of the chunk load options when parsing the legacy 'false' value */
    public static final ChunkLoadOptions LEGACY_FALSE = new ChunkLoadOptions(Mode.DISABLED, 2);
    /** Default setting of the chunk load options when parsing the legacy 'true' value */
    public static final ChunkLoadOptions LEGACY_TRUE = new ChunkLoadOptions(Mode.FULL, 2);

    private final Mode mode;
    private final int radius;

    /**
     * Creates new immutable chunk load options
     *
     * @param mode Chunk loading mode
     * @param radius Chunk loading radius around the carts, in chunks
     * @return ChunkLoadOptions
     */
    public static ChunkLoadOptions of(Mode mode, int radius) {
        return new ChunkLoadOptions(mode, Math.max(0, radius));
    }

    private ChunkLoadOptions(Mode mode, int radius) {
        this.mode = mode;
        this.radius = radius;
    }

    /**
     * Gets the operating mode. This, in addition to enabling, checks
     * how loaded chunks should be simulated.
     *
     * @return Mode
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Gets whether chunk loading is enabled
     *
     * @return True if chunk loading is enabled
     */
    public boolean keepLoaded() {
        return mode != Mode.DISABLED;
    }

    /**
     * Gets the chunk loading radius. Lowest radius is 0, which
     * only loads the chunks the carts of the train are in.
     * Default radius TrainCarts uses is 2.
     *
     * @return Chunk loading radius
     */
    public int radius() {
        return radius;
    }

    /**
     * Returns new ChunkLoadOptions with mode updated
     *
     * @param newMode New Mode
     * @return Updated ChunkLoadOptions
     */
    public ChunkLoadOptions withMode(Mode newMode) {
        return of(newMode, radius);
    }

    /**
     * Returns new ChunkLoadOptions with radius updated
     *
     * @param newRadius New radius
     * @return Updated ChunkLoadOptions
     */
    public ChunkLoadOptions withRadius(int newRadius) {
        return of(mode, newRadius);
    }

    @Override
    public int hashCode() {
        return this.radius * 4 + this.mode.ordinal();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof ChunkLoadOptions) {
            ChunkLoadOptions other = (ChunkLoadOptions) o;
            return this.mode == other.mode && this.radius == other.radius;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ChunkLoadOptions{keepLoaded=" + keepLoaded() + ", mode=" + mode().name() + ", radius=" + radius() + "}";
    }

    /**
     * Chunk loading mode
     */
    public enum Mode {
        /** In this mode, does not load chunks at all. Equivalent to the 'false' mode */
        DISABLED(Arrays.asList("disabled", "false"), 0),
        /**
         * In this mode, loads the chunks and simulates entities and redstone.
         * Loads an additional 2-thick layer of chunks around the area kept loaded.
         * Equivalent to the 'true' mode.
         */
        FULL(Arrays.asList("full", "true"), 2),
        /**
         * In this mode, loads the chunks and simulates redstone.
         * Loads an additional 1-tick layer of chunks around the area kept loaded, which
         * are not simulated.
         */
        REDSTONE(Collections.singletonList("redstone"), 1),
        /**
         * In this mode, loads the chunks but does not run any redstone or entity simulation.
         * Ideal for travel along empty stretches with no redstone or entity contraptions.
         */
        MINIMAL(Collections.singletonList("minimal"), 0);

        private final List<String> names;
        private final int perChunkRadius;

        private static final List<String> allNames = Stream.of(Mode.values())
                .flatMap(m -> m.getNames().stream()).collect(StreamUtil.toUnmodifiableList());
        private static final Map<String, Mode> byName = new HashMap<>();
        static {
            for (Mode mode : Mode.values()) {
                for (String name : mode.getNames()) {
                    byName.put(name, mode);
                    byName.put(name.toUpperCase(Locale.ENGLISH), mode);
                }
            }
        }

        Mode(List<String> names, int perChunkRadius) {
            this.names = names;
            this.perChunkRadius = perChunkRadius;
        }

        public int getPerChunkRadius() {
            return perChunkRadius;
        }

        public List<String> getNames() {
            return names;
        }

        /**
         * Gets all possible names that should be suggested as options for the mode
         *
         * @return All names
         */
        public static List<String> getAllNames() {
            return allNames;
        }

        /**
         * Attempts to parse player input into one of the known modes. Supports
         * the names of {@link #getNames()} as well as all boolean-like names.
         *
         * @param name Name
         * @return Parsed mode, or Empty if it couldn't be matched
         */
        public static Optional<Mode> fromName(String name) {
            Mode mode = byName.get(name);
            if (mode != null) {
                return Optional.of(mode);
            }
            mode = byName.get(name.toUpperCase(Locale.ENGLISH));
            if (mode != null) {
                return Optional.of(mode);
            }
            if (ParseUtil.isBool(name)) {
                return Optional.of(ParseUtil.parseBool(name) ? Mode.FULL : Mode.DISABLED);
            }

            return Optional.empty();
        }
    }
}
