package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.controller.spawnable.SpawnableGroup;
import com.bergerkiller.bukkit.tc.controller.spawnable.TrainSpawnPattern;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * Tests and verifies the correct parsing of train spawn patterns.
 * Does not test that the saved train by-name lookup is working properly.
 */
public class TrainSpawnPatternTest {

    @Test
    public void testTrainNameMatchingVeryShort() {
        assertNull(TrainSpawnPattern.findNameInSortedList(
                Collections.emptyList(), "Item"));
        assertNull(TrainSpawnPattern.findNameInSortedList(
                Collections.singletonList("Not"), "Item"));
        assertEquals("Not", TrainSpawnPattern.findNameInSortedList(
                Collections.singletonList("Not"), "Not"));
        assertEquals("Not", TrainSpawnPattern.findNameInSortedList(
                Collections.singletonList("Not"), "NotWithExtra"));
    }

    @Test
    public void testTrainNameMatchingShort() {
        List<String> testList = new ArrayList<>(Arrays.asList(
                "Green", "Blue", "One", "OneLonger", "Two"));
        Collections.sort(testList);

        assertEquals("Two", TrainSpawnPattern.findNameInSortedList(testList, "TwoWithMoreAfter"));
        assertEquals("Two", TrainSpawnPattern.findNameInSortedList(testList, "Two"));
        assertEquals("One", TrainSpawnPattern.findNameInSortedList(testList, "One"));
        assertEquals("One", TrainSpawnPattern.findNameInSortedList(testList, "OneLong"));
        assertEquals("OneLonger", TrainSpawnPattern.findNameInSortedList(testList, "OneLonger"));
        assertEquals("OneLonger", TrainSpawnPattern.findNameInSortedList(testList, "OneLongerWithMore"));
        assertNull(TrainSpawnPattern.findNameInSortedList(testList, "Twee"));
    }

    @Test
    public void testTrainNameMatchingLong() {
        List<String> testList = new ArrayList<>(Arrays.asList(
                "Green", "Blue", "One", "OneLonger", "Two",
                "Red", "Redder", "Pineapple", "Train12", "Train123"));
        Collections.sort(testList);

        assertEquals("Two", TrainSpawnPattern.findNameInSortedList(testList, "TwoWithMoreAfter"));
        assertEquals("Two", TrainSpawnPattern.findNameInSortedList(testList, "Two"));
        assertEquals("One", TrainSpawnPattern.findNameInSortedList(testList, "One"));
        assertEquals("One", TrainSpawnPattern.findNameInSortedList(testList, "OneLong"));
        assertEquals("OneLonger", TrainSpawnPattern.findNameInSortedList(testList, "OneLonger"));
        assertEquals("OneLonger", TrainSpawnPattern.findNameInSortedList(testList, "OneLongerWithMore"));
        assertEquals("Train12", TrainSpawnPattern.findNameInSortedList(testList, "Train12"));
        assertEquals("Train123", TrainSpawnPattern.findNameInSortedList(testList, "Train123"));
        assertEquals("Train12", TrainSpawnPattern.findNameInSortedList(testList, "Train124"));
        assertNull(TrainSpawnPattern.findNameInSortedList(testList, "Twee"));
    }

    @Test
    public void testChanceWeightComplex() {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("40%[mmm]60%[sss]", n -> null);

        assertFalse(pattern.quantity().hasChanceWeight());
        assertEquals(2, pattern.patterns().size());

        {
            TrainSpawnPattern.SequenceSpawnPattern seq = getPattern(pattern, 0, TrainSpawnPattern.SequenceSpawnPattern.class);
            assertEquals(1, seq.amount());
            assertTrue(seq.quantity().hasChanceWeight());
            assertEquals(40.0, seq.quantity().chanceWeight, 1e-8);
            assertEquals(3, seq.patterns().size());

            for (int i = 0; i < 3; i++) {
                TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(seq, i, TrainSpawnPattern.VanillaCartSpawnPattern.class);
                assertEquals(1, vm.amount());
                assertFalse(vm.quantity().hasChanceWeight());
                assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
            }
        }
        {
            TrainSpawnPattern.SequenceSpawnPattern seq = getPattern(pattern, 1, TrainSpawnPattern.SequenceSpawnPattern.class);
            assertEquals(1, seq.amount());
            assertTrue(seq.quantity().hasChanceWeight());
            assertEquals(60.0, seq.quantity().chanceWeight, 1e-8);
            assertEquals(3, seq.patterns().size());

            for (int i = 0; i < 3; i++) {
                TrainSpawnPattern.VanillaCartSpawnPattern vs = getPattern(seq, i, TrainSpawnPattern.VanillaCartSpawnPattern.class);
                assertEquals(1, vs.amount());
                assertFalse(vs.quantity().hasChanceWeight());
                assertEquals(SpawnableGroup.VanillaCartType.STORAGE, vs.type());
            }
        }
    }

    @Test
    public void testChanceWeightSimpleWithAmounts() {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("30%3m60%4s", n -> null);

        assertEquals(2, pattern.patterns().size());

        {
            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(3, vm.amount());
            assertTrue(vm.quantity().hasChanceWeight());
            assertEquals(30.0, vm.quantity().chanceWeight, 1e-8);
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
        }
        {
            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 1, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(4, vm.amount());
            assertTrue(vm.quantity().hasChanceWeight());
            assertEquals(60.0, vm.quantity().chanceWeight, 1e-8);
            assertEquals(SpawnableGroup.VanillaCartType.STORAGE, vm.type());
        }
    }

    @Test
    public void testChanceWeightSimple() {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("30%m60%s", n -> null);

        assertEquals(2, pattern.patterns().size());

        {
            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, vm.amount());
            assertTrue(vm.quantity().hasChanceWeight());
            assertEquals(30.0, vm.quantity().chanceWeight, 1e-8);
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
        }
        {
            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 1, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, vm.amount());
            assertTrue(vm.quantity().hasChanceWeight());
            assertEquals(60.0, vm.quantity().chanceWeight, 1e-8);
            assertEquals(SpawnableGroup.VanillaCartType.STORAGE, vm.type());
        }
    }

    @Test
    public void testCenterModeComplex() {
        {
            TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("[2[3m]", n -> null);
            assertEquals(SpawnableGroup.CenterMode.RIGHT, pattern.centerMode());
            verifyCenterModeComplexPattern(pattern);
        }
        {
            TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("2[3m]]", n -> null);
            assertEquals(SpawnableGroup.CenterMode.LEFT, pattern.centerMode());
            verifyCenterModeComplexPattern(pattern);
        }
        {
            TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("[2[3m]]", n -> null);
            assertEquals(SpawnableGroup.CenterMode.MIDDLE, pattern.centerMode());
            verifyCenterModeComplexPattern(pattern);
        }
    }

    private void verifyCenterModeComplexPattern(TrainSpawnPattern.ParsedSpawnPattern pattern) {
        assertEquals(2, pattern.amount());
        assertEquals(1, pattern.patterns().size());

        TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
        assertEquals(3, vm.amount());
        assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
    }

    @Test
    public void testCenterModeSimple() {
        {
            TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("m", n -> null);
            assertEquals(SpawnableGroup.CenterMode.NONE, pattern.centerMode());
            assertEquals(1, pattern.amount());
            assertEquals(1, pattern.patterns().size());

            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, vm.amount());
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
        }
        {
            TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("[m", n -> null);
            assertEquals(SpawnableGroup.CenterMode.RIGHT, pattern.centerMode());
            assertEquals(1, pattern.amount());
            assertEquals(1, pattern.patterns().size());

            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, vm.amount());
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
        }
        {
            TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("m]", n -> null);
            assertEquals(SpawnableGroup.CenterMode.LEFT, pattern.centerMode());
            assertEquals(1, pattern.amount());
            assertEquals(1, pattern.patterns().size());

            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, vm.amount());
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
        }
        {
            TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("[m]", n -> null);
            assertEquals(SpawnableGroup.CenterMode.MIDDLE, pattern.centerMode());
            assertEquals(1, pattern.amount());
            assertEquals(1, pattern.patterns().size());

            // Note: is simplified, but center mode should stay
            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(1, vm.amount());
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
        }
    }

    @Test
    public void testSubSequencePatternComplex() {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("4TrainName5[2m3s][3[ms]]", n -> {
            if (n.startsWith("TrainName")) {
                return "TrainName";
            } else {
                return null;
            }
        });
        assertEquals(SpawnableGroup.CenterMode.NONE, pattern.centerMode());

        // Is not simplified, should have the 3 components
        assertEquals(1, pattern.amount());
        assertEquals(3, pattern.patterns().size());

        // Should have the TrainName as first item
        {
            TrainSpawnPattern.SavedTrainSpawnPattern st = getPattern(pattern, 0, TrainSpawnPattern.SavedTrainSpawnPattern.class);
            assertEquals(4, st.amount());
            assertEquals("TrainName", st.name());
        }

        // Should have a sequence as second item with the right amount prefix
        {
            TrainSpawnPattern.SequenceSpawnPattern seq = getPattern(pattern, 1, TrainSpawnPattern.SequenceSpawnPattern.class);
            assertEquals(5, seq.amount());
            assertEquals(2, seq.patterns().size());

            // Should have the two vanilla cart patterns with right amounts
            {
                TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(seq, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
                assertEquals(2, vm.amount());
                assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
            }
            {
                TrainSpawnPattern.VanillaCartSpawnPattern vs = getPattern(seq, 1, TrainSpawnPattern.VanillaCartSpawnPattern.class);
                assertEquals(3, vs.amount());
                assertEquals(SpawnableGroup.VanillaCartType.STORAGE, vs.type());
            }
        }

        // Should have a sequence as third item, with inside another sequence
        {
            TrainSpawnPattern.SequenceSpawnPattern seq = getPattern(pattern, 2, TrainSpawnPattern.SequenceSpawnPattern.class);
            assertEquals(1, seq.amount());
            assertEquals(1, seq.patterns().size());

            {
                TrainSpawnPattern.SequenceSpawnPattern subSeq = getPattern(seq, 0, TrainSpawnPattern.SequenceSpawnPattern.class);

                // Should have the two vanilla cart patterns with right amounts (1 each since there is no quantity prefix)
                {
                    TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(subSeq, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
                    assertEquals(1, vm.amount());
                    assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
                }
                {
                    TrainSpawnPattern.VanillaCartSpawnPattern vs = getPattern(subSeq, 1, TrainSpawnPattern.VanillaCartSpawnPattern.class);
                    assertEquals(1, vs.amount());
                    assertEquals(SpawnableGroup.VanillaCartType.STORAGE, vs.type());
                }
            }
        }
    }

    @Test
    public void testSubSequencePatternSimple() {
        TrainSpawnPattern.ParsedSpawnPattern pattern = TrainSpawnPattern.parse("5[2m3s]", n -> null);
        assertEquals(SpawnableGroup.CenterMode.NONE, pattern.centerMode());
        assertEquals(5, pattern.amount()); // Is simplified, so should be 5

        // Should have the two vanilla cart patterns
        assertEquals(2, pattern.patterns().size());
        {
            TrainSpawnPattern.VanillaCartSpawnPattern vm = getPattern(pattern, 0, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(2, vm.amount());
            assertEquals(SpawnableGroup.VanillaCartType.RIDEABLE, vm.type());
        }
        {
            TrainSpawnPattern.VanillaCartSpawnPattern vs = getPattern(pattern, 1, TrainSpawnPattern.VanillaCartSpawnPattern.class);
            assertEquals(3, vs.amount());
            assertEquals(SpawnableGroup.VanillaCartType.STORAGE, vs.type());
        }
    }

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

    private static <T extends TrainSpawnPattern> T getPattern(TrainSpawnPattern.SequenceSpawnPattern sequence, int index, Class<T> type) {
        TrainSpawnPattern pattern = sequence.patterns().get(index);
        if (!type.isInstance(pattern)) {
            throw new IllegalStateException("Expected type " + type.getName() + " at " + index + ", but was " + pattern.getClass().getName());
        }
        return type.cast(pattern);
    }
}
