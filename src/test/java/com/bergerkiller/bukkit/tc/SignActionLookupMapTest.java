package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.TrainCartsSignAction;
import com.bergerkiller.bukkit.tc.signactions.util.SignActionLookupMap;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * Tests the correct functioning of registering SignActions in the {@link SignActionLookupMap}.
 * Of importance is that signs are matched in the order as requested when registering.
 */
public class SignActionLookupMapTest {

    @Test
    @Ignore
    public void benchmark() {
        RandomIdentifierMaker maker = new RandomIdentifierMaker();
        List<String> uniqueIdentifiers = IntStream.range(0, 50)
                .mapToObj(i -> maker.nextIdentifier())
                .collect(Collectors.toList());

        SignActionLookupMap mapOptimized = SignActionLookupMap.create();
        SignActionLookupMap mapUnoptimized = SignActionLookupMap.createBasicUnoptimized();
        for (String uniqueIdentifier : uniqueIdentifiers) {
            SignAction action = maker.nextAction(uniqueIdentifier);
            mapOptimized.register(action);
            mapUnoptimized.register(action);
        }

        List<SignActionEvent> testEvents = uniqueIdentifiers.stream()
                .map(identifier -> new SignActionEvent(RailLookup.UnitTestTrackedSign.of(new String[] {
                        "[+train]", identifier, "", ""
                })))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(testEvents);

        // Warm both lookups up at least once
        benchmarkLookupRun(testEvents, mapOptimized);
        benchmarkLookupRun(testEvents, mapUnoptimized);

        // Time it bois
        long startTime, endTime;
        int numRuns = 100000;

        {
            startTime = System.currentTimeMillis();
            for (int n = 0; n < numRuns; n++) {
                benchmarkLookupRun(testEvents, mapOptimized);
            }
            endTime = System.currentTimeMillis();
            System.out.println("Optimized took " + ((double) (endTime - startTime) / numRuns / testEvents.size() * 1000.0) + "us/lookup");
        }

        {
            startTime = System.currentTimeMillis();
            for (int n = 0; n < numRuns; n++) {
                benchmarkLookupRun(testEvents, mapUnoptimized);
            }
            endTime = System.currentTimeMillis();
            System.out.println("Unoptimized took " + ((double) (endTime - startTime) / numRuns / testEvents.size() * 1000.0) + "us/lookup");
        }
    }

    private void benchmarkLookupRun(List<SignActionEvent> events, SignActionLookupMap map) {
        for (SignActionEvent event : events) {
            map.lookup(event);
        }
    }

    @Test
    public void testUnregisterTrainCarts() {
        SignActionLookupMap map = SignActionLookupMap.create();

        SignAction one = map.register(new TestSignActionTCSign("one"), false);
        SignAction two = map.register(new TestSignActionTCSign("two"), false);

        assertEquals(one, lookupSign(map, "[train]", "one", "", ""));
        assertEquals(two, lookupSign(map, "[train]", "two", "", ""));

        map.unregister(one);
        assertNull(lookupSign(map, "[train]", "one", "", ""));
        assertEquals(two, lookupSign(map, "[train]", "two", "", ""));

        SignAction newOne = map.register(new TestSignActionTCSign("one"), false);
        assertEquals(newOne, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testUnregisterLegacy() {
        SignActionLookupMap map = SignActionLookupMap.create();

        SignAction one = map.register(new TestSignActionTCSignLegacy("one"), false);
        SignAction two = map.register(new TestSignActionTCSignLegacy("two"), false);

        assertEquals(one, lookupSign(map, "[train]", "one", "", ""));
        assertEquals(two, lookupSign(map, "[train]", "two", "", ""));

        map.unregister(one);
        assertNull(lookupSign(map, "[train]", "one", "", ""));
        assertEquals(two, lookupSign(map, "[train]", "two", "", ""));

        SignAction newOne = map.register(new TestSignActionTCSignLegacy("one"), false);
        assertEquals(newOne, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testWaterfall() {
        SignActionLookupMap map = SignActionLookupMap.create();

        SignAction LEVEL_ABC = map.register(new TestSignActionTCSignLegacy("abc"), false);
        SignAction LEVEL_AB = map.register(new TestSignActionTCSignLegacy("ab"), false);
        SignAction LEVEL_A = map.register(new TestSignActionTCSignLegacy("a"), false);

        // Priority is false, so the previously registered "abc" entry should match "abc" before "a" and "ab" do.
        assertEquals(LEVEL_A, lookupSign(map, "[train]", "a", "", ""));
        assertEquals(LEVEL_AB, lookupSign(map, "[train]", "ab", "", ""));
        assertEquals(LEVEL_ABC, lookupSign(map, "[train]", "abc", "", ""));

        // With argument
        assertEquals(LEVEL_A, lookupSign(map, "[train]", "a2", "", ""));
        assertEquals(LEVEL_AB, lookupSign(map, "[train]", "ab2", "", ""));
        assertEquals(LEVEL_ABC, lookupSign(map, "[train]", "abc2", "", ""));
    }

    @Test
    public void testPriorityLegacyOverTrainCarts() {
        SignActionLookupMap map = SignActionLookupMap.create();

        map.register(new TestSignActionTCSign("one"), false);
        SignAction PRIORITY = map.register(new TestSignActionTCSignLegacy("one"), true);
        map.register(new TestSignActionTCSign("one"), false);

        assertEquals(PRIORITY, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testPriorityTrainCartsOverLegacy() {
        SignActionLookupMap map = SignActionLookupMap.create();

        map.register(new TestSignActionTCSignLegacy("one"), false);
        SignAction PRIORITY = map.register(new TestSignActionTCSign("one"), true);
        map.register(new TestSignActionTCSignLegacy("one"), false);

        assertEquals(PRIORITY, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testPriorityTrainCarts() {
        SignActionLookupMap map = SignActionLookupMap.create();

        map.register(new TestSignActionTCSign("one"), false);
        SignAction PRIORITY = map.register(new TestSignActionTCSign("one"), true);
        map.register(new TestSignActionTCSign("one"), false);

        assertEquals(PRIORITY, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testPriorityLegacy() {
        SignActionLookupMap map = SignActionLookupMap.create();

        map.register(new TestSignActionTCSignLegacy("one"), false);
        SignAction PRIORITY = map.register(new TestSignActionTCSignLegacy("one"), true);
        map.register(new TestSignActionTCSignLegacy("one"), false);

        assertEquals(PRIORITY, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testSimpleTrainCarts() {
        SignActionLookupMap map = SignActionLookupMap.create();

        SignAction TC_ONE = map.register(new TestSignActionTCSign("one"), false);
        SignAction TC_TWO = map.register(new TestSignActionTCSign("two"), false);
        SignAction TC_THREE = map.register(new TestSignActionTCSign("three"), false);
        SignAction TC_FOUR = map.register(new TestSignActionTCSign("four"), false);

        // Must match when exactly equal
        assertEquals(TC_ONE, lookupSign(map, "[train]", "one", "", ""));
        assertEquals(TC_TWO, lookupSign(map, "[train]", "two", "", ""));
        assertEquals(TC_THREE, lookupSign(map, "[train]", "three", "", ""));
        assertEquals(TC_FOUR, lookupSign(map, "[train]", "four", "", ""));

        // Must match when the second line starts with the identifier, space or no space
        assertEquals(TC_ONE, lookupSign(map, "[train]", "one arg", "", ""));
        assertEquals(TC_ONE, lookupSign(map, "[train]", "onearg", "", ""));
        assertEquals(TC_TWO, lookupSign(map, "[train]", "two arg", "", ""));
        assertEquals(TC_TWO, lookupSign(map, "[train]", "twoarg", "", ""));
        assertEquals(TC_THREE, lookupSign(map, "[train]", "three arg", "", ""));
        assertEquals(TC_THREE, lookupSign(map, "[train]", "threearg", "", ""));
        assertEquals(TC_FOUR, lookupSign(map, "[train]", "four arg", "", ""));
        assertEquals(TC_FOUR, lookupSign(map, "[train]", "fourarg", "", ""));

        // Must still work with mixed case
        assertEquals(TC_ONE, lookupSign(map, "[train]", "One", "", ""));
        assertEquals(TC_TWO, lookupSign(map, "[train]", "TWO", "", ""));
        assertEquals(TC_THREE, lookupSign(map, "[train]", "THREE", "", ""));
        assertEquals(TC_FOUR, lookupSign(map, "[train]", "Four", "", ""));

        // But must NOT match signs whose second line begin with one of the identifiers (wrong way around)
        assertNull(lookupSign(map, "[train]", "o", "", ""));
        assertNull(lookupSign(map, "[train]", "tw", "", ""));
        assertNull(lookupSign(map, "[train]", "th", "", ""));
        assertNull(lookupSign(map, "[train]", "f", "", ""));
    }

    @Test
    public void testSimpleLegacyOnly() {
        SignActionLookupMap map = SignActionLookupMap.create();

        SignAction NOT_TC_ONE = map.register(new TestSignActionTCSignLegacy("one"), false);
        SignAction NOT_TC_TWO = map.register(new TestSignActionTCSignLegacy("two"), false);
        SignAction NOT_TC_THREE = map.register(new TestSignActionTCSignLegacy("three"), false);
        SignAction NOT_TC_FOUR = map.register(new TestSignActionTCSignLegacy("four"), false);

        // Must match when exactly equal
        assertEquals(NOT_TC_ONE, lookupSign(map, "[train]", "one", "", ""));
        assertEquals(NOT_TC_TWO, lookupSign(map, "[train]", "two", "", ""));
        assertEquals(NOT_TC_THREE, lookupSign(map, "[train]", "three", "", ""));
        assertEquals(NOT_TC_FOUR, lookupSign(map, "[train]", "four", "", ""));

        // Must match when the second line starts with the identifier, space or no space
        assertEquals(NOT_TC_ONE, lookupSign(map, "[train]", "one arg", "", ""));
        assertEquals(NOT_TC_ONE, lookupSign(map, "[train]", "onearg", "", ""));
        assertEquals(NOT_TC_TWO, lookupSign(map, "[train]", "two arg", "", ""));
        assertEquals(NOT_TC_TWO, lookupSign(map, "[train]", "twoarg", "", ""));
        assertEquals(NOT_TC_THREE, lookupSign(map, "[train]", "three arg", "", ""));
        assertEquals(NOT_TC_THREE, lookupSign(map, "[train]", "threearg", "", ""));
        assertEquals(NOT_TC_FOUR, lookupSign(map, "[train]", "four arg", "", ""));
        assertEquals(NOT_TC_FOUR, lookupSign(map, "[train]", "fourarg", "", ""));

        // Must still work with mixed case
        assertEquals(NOT_TC_ONE, lookupSign(map, "[train]", "One", "", ""));
        assertEquals(NOT_TC_TWO, lookupSign(map, "[train]", "TWO", "", ""));
        assertEquals(NOT_TC_THREE, lookupSign(map, "[train]", "THREE", "", ""));
        assertEquals(NOT_TC_FOUR, lookupSign(map, "[train]", "Four", "", ""));

        // But must NOT match signs whose second line begin with one of the identifiers (wrong way around)
        assertNull(lookupSign(map, "[train]", "o", "", ""));
        assertNull(lookupSign(map, "[train]", "tw", "", ""));
        assertNull(lookupSign(map, "[train]", "th", "", ""));
        assertNull(lookupSign(map, "[train]", "f", "", ""));
    }

    @Test
    public void testSimpleMixed() {
        SignActionLookupMap map = SignActionLookupMap.create();

        SignAction TC_ONE = map.register(new TestSignActionTCSign("one"), false);
        SignAction NOT_TC_TWO = map.register(new TestSignActionTCSignLegacy("two"), false);
        SignAction TC_THREE = map.register(new TestSignActionTCSign("three"), false);
        SignAction NOT_TC_FOUR = map.register(new TestSignActionTCSignLegacy("four"), false);

        // Must match when exactly equal
        assertEquals(TC_ONE, lookupSign(map, "[train]", "one", "", ""));
        assertEquals(NOT_TC_TWO, lookupSign(map, "[train]", "two", "", ""));
        assertEquals(TC_THREE, lookupSign(map, "[train]", "three", "", ""));
        assertEquals(NOT_TC_FOUR, lookupSign(map, "[train]", "four", "", ""));

        // Must match when the second line starts with the identifier, space or no space
        assertEquals(TC_ONE, lookupSign(map, "[train]", "one arg", "", ""));
        assertEquals(TC_ONE, lookupSign(map, "[train]", "onearg", "", ""));
        assertEquals(NOT_TC_TWO, lookupSign(map, "[train]", "two arg", "", ""));
        assertEquals(NOT_TC_TWO, lookupSign(map, "[train]", "twoarg", "", ""));
        assertEquals(TC_THREE, lookupSign(map, "[train]", "three arg", "", ""));
        assertEquals(TC_THREE, lookupSign(map, "[train]", "threearg", "", ""));
        assertEquals(NOT_TC_FOUR, lookupSign(map, "[train]", "four arg", "", ""));
        assertEquals(NOT_TC_FOUR, lookupSign(map, "[train]", "fourarg", "", ""));

        // Must still work with mixed case
        assertEquals(TC_ONE, lookupSign(map, "[train]", "One", "", ""));
        assertEquals(NOT_TC_TWO, lookupSign(map, "[train]", "TWO", "", ""));
        assertEquals(TC_THREE, lookupSign(map, "[train]", "THREE", "", ""));
        assertEquals(NOT_TC_FOUR, lookupSign(map, "[train]", "Four", "", ""));

        // But must NOT match signs whose second line begin with one of the identifiers (wrong way around)
        assertNull(lookupSign(map, "[train]", "o", "", ""));
        assertNull(lookupSign(map, "[train]", "tw", "", ""));
        assertNull(lookupSign(map, "[train]", "th", "", ""));
        assertNull(lookupSign(map, "[train]", "f", "", ""));
    }

    private SignAction lookupSign(SignActionLookupMap map, String... lines) {
        SignActionEvent event = new SignActionEvent(RailLookup.UnitTestTrackedSign.of(lines));
        return map.lookup(event).map(SignActionLookupMap.Entry::action).orElse(null);
    }

    /**
     * Represents the TrainCartsSignAction API SignAction.
     * Has one or more type identifiers matched inside {@link #match(SignActionEvent)}.
     */
    private static class TestSignActionTCSign extends TrainCartsSignAction {

        public TestSignActionTCSign(String... typeIdentifiers) {
            super(typeIdentifiers);
        }

        @Override
        public void execute(SignActionEvent info) {
        }

        @Override
        public boolean build(SignChangeActionEvent event) {
            return true;
        }

        @Override
        public String toString() {
            return "TestSignActionTCSign{identifiers=" + String.join(",", getTypeIdentifiers()) + "}";
        }
    }

    /**
     * Represents an "old" [train] or [cart] sign (from a third-party addon) that does not yet
     * use the new TrainCartsSignAction API.
     * Has one or more type identifiers matched inside {@link #match(SignActionEvent)}.
     */
    private static class TestSignActionTCSignLegacy extends SignAction {
        private final String[] typeIdentifiers;

        public TestSignActionTCSignLegacy(String... typeIdentifiers) {
            this.typeIdentifiers = typeIdentifiers;
        }

        @Override
        public boolean match(SignActionEvent info) {
            return info.getMode() != SignActionMode.NONE && info.isType(typeIdentifiers);
        }

        @Override
        public void execute(SignActionEvent info) {
        }

        @Override
        public boolean build(SignChangeActionEvent event) {
            return true;
        }

        @Override
        public String toString() {
            return "TestSignActionTCSignLegacy{identifiers=" + String.join(",", typeIdentifiers) + "}";
        }
    }

    private static class RandomIdentifierMaker {
        private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";
        private final Random random = new Random();

        public SignAction nextAction(String identifier) {
            // Make 95% of the signs be a TC sign that is optimized
            // Given only 2 or 3 signs are non-TC this feels fair
            if (random.nextDouble() <= 0.95) {
                return new TestSignActionTCSign(identifier);
            } else {
                return new TestSignActionTCSignLegacy(identifier);
            }
        }

        public String nextIdentifier() {
            int length = 4 + random.nextInt(7); // Random length between 4 and 10
            StringBuilder sb = new StringBuilder(length);

            for (int i = 0; i < length; i++) {
                char randomChar = LETTERS.charAt(random.nextInt(LETTERS.length()));
                sb.append(randomChar);
            }

            return sb.toString();
        }
    }
}
