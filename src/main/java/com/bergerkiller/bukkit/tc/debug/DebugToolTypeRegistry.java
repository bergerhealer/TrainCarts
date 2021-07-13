package com.bergerkiller.bukkit.tc.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.tc.debug.types.DebugToolTypeListDestinations;
import com.bergerkiller.bukkit.tc.debug.types.DebugToolTypeRails;
import com.bergerkiller.bukkit.tc.debug.types.DebugToolTypeTrackDistance;

/**
 * Tracks all the debug tool types that exist
 */
public class DebugToolTypeRegistry {
    private static final List<ToolTypeItem> registry = new ArrayList<>();

    static {
        register(DebugToolTypeRails::new);
        register(DebugToolTypeListDestinations::new);
        register(n -> n.startsWith("Destination "), n -> new DebugToolTypeListDestinations(n.substring(12)));
        register(DebugToolTypeTrackDistance::new);
    }

    public static Optional<DebugToolType> match(String debugToolName) {
        for (ToolTypeItem item : registry) {
            if (item.condition.test(debugToolName)) {
                return Optional.of(item.factory.apply(debugToolName));
            }
        }
        return Optional.empty();
    }

    public static void register(Supplier<DebugToolType> constructor) {
        final String debugToolName = constructor.get().getIdentifier();
        register(n -> n.equalsIgnoreCase(debugToolName), n -> constructor.get());
    }

    public static void register(Predicate<String> condition, Function<String, DebugToolType> factory) {
        registry.add(new ToolTypeItem(condition, factory));
    }

    private static class ToolTypeItem {
        public final Predicate<String> condition;
        public final Function<String, DebugToolType> factory;

        public ToolTypeItem(Predicate<String> condition, Function<String, DebugToolType> factory) {
            this.condition = condition;
            this.factory = factory;
        }
    }
}
