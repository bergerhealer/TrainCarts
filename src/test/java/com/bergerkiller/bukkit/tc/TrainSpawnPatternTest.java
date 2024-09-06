package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.controller.spawnable.TrainSpawnPattern;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests and verifies the correct parsing of train spawn patterns.
 * Does not test that the saved train by-name lookup is working properly.
 */
public class TrainSpawnPatternTest {

    @Test
    public void testVanillaPattern() {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("mmmsspp", n -> null);
        assertEquals(1, pattern.amount());
        assertEquals(7, pattern.patterns().size());
        for (int i = 0; i < 3; i++) {
            TrainSpawnPattern.VanillaCartSpawnPattern v = getPattern(pattern, i, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, v.amount());
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, v.type());
        }
        for (int i = 3; i < 5; i++) {
            TrainSpawnPattern.VanillaCartSpawnPattern v = getPattern(pattern, i, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, v.amount());
            assertEquals(SpawnableGroup.VanillaCartType.STORAGE, v.type());
        }
        for (int i = 5; i < 7; i++) {
            TrainSpawnPattern.VanillaCartSpawnPattern v = getPattern(pattern, i, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, v.amount());
            assertEquals(SpawnableGroup.VanillaCartType.POWERED, v.type());
        }
    }

    @Test
    public void testVanillaPatternWithAmounts() {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("1m2m3m4s5s6p7p", n -> null);
        assertEquals(1, pattern.amount());
        assertEquals(7, pattern.patterns().size());
        for (int i = 0; i < 3; i++) {
            TrainSpawnPattern.VanillaCartSpawnPattern v = getPattern(pattern, i, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(i + 1, v.amount());
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, v.type());
        }
        for (int i = 3; i < 5; i++) {
            TrainSpawnPattern.VanillaCartSpawnPattern v = getPattern(pattern, i, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(i + 1, v.amount());
            assertEquals(SpawnableGroup.VanillaCartType.STORAGE, v.type());
        }
        for (int i = 5; i < 7; i++) {
            TrainSpawnPattern.VanillaCartSpawnPattern v = getPattern(pattern, i, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(i + 1, v.amount());
            assertEquals(SpawnableGroup.VanillaCartType.POWERED, v.type());
        }
    }

    @Test
    public void testTrainNamePattern() {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("12Green24BlueRed", n -> {
            if (n.startsWith("Green")) {
                return "Green";
            } else if (n.startsWith("Blue")) {
                return "Blue";
            } else if (n.startsWith("Red")) {
                return "Red";
            } else {
                return null;
            }
        });
        assertEquals(1, pattern.amount());
        assertEquals(3, pattern.patterns().size());

        TrainSpawnPattern.SavedTrainSpawnPattern green = getPattern(pattern, 0, TrainSpawnPattern.SavedTrainSpawnPattern.class);
        assertEquals(12, green.amount());
        assertEquals("Green", green.name());

        TrainSpawnPattern.SavedTrainSpawnPattern blue = getPattern(pattern, 1, TrainSpawnPattern.SavedTrainSpawnPattern.class);
        assertEquals(24, blue.amount());
        assertEquals("Blue", blue.name());

        TrainSpawnPattern.SavedTrainSpawnPattern red = getPattern(pattern, 2, TrainSpawnPattern.SavedTrainSpawnPattern.class);
        assertEquals(1, red.amount());
        assertEquals("Red", red.name());
    }

    private static <T extends TrainSpawnPattern> T getPattern(TrainSpawnPattern.ParsedSpawnPattern parsed, int index, Class<T> type) {
        TrainSpawnPattern pattern = parsed.patterns().get(index);
        if (!type.isInstance(pattern)) {
            throw new IllegalStateException("Expected type " + type.getName() + " at " + index + ", but was " + pattern.getClass().getName());
        }
        return type.cast(pattern);
    }
}
