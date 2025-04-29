package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.TrainCartsSignAction;
import com.bergerkiller.bukkit.tc.signactions.util.SignActionLookupMap;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the correct functioning of registering SignActions in the {@link SignActionLookupMap}.
 * Of importance is that signs are matched in the order as requested when registering.
 */
public class SignActionLookupMapTest {

    @Test
    public void testUnregisterTrainCarts() {
        SignActionLookupMap map = new SignActionLookupMap();

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
        SignActionLookupMap map = new SignActionLookupMap();

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
        SignActionLookupMap map = new SignActionLookupMap();
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
        SignActionLookupMap map = new SignActionLookupMap();
        map.register(new TestSignActionTCSign("one"), false);
        SignAction PRIORITY = map.register(new TestSignActionTCSignLegacy("one"), true);
        map.register(new TestSignActionTCSign("one"), false);

        assertEquals(PRIORITY, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testPriorityTrainCartsOverLegacy() {
        SignActionLookupMap map = new SignActionLookupMap();
        map.register(new TestSignActionTCSignLegacy("one"), false);
        SignAction PRIORITY = map.register(new TestSignActionTCSign("one"), true);
        map.register(new TestSignActionTCSignLegacy("one"), false);

        assertEquals(PRIORITY, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testPriorityTrainCarts() {
        SignActionLookupMap map = new SignActionLookupMap();
        map.register(new TestSignActionTCSign("one"), false);
        SignAction PRIORITY = map.register(new TestSignActionTCSign("one"), true);
        map.register(new TestSignActionTCSign("one"), false);

        assertEquals(PRIORITY, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testPriorityLegacy() {
        SignActionLookupMap map = new SignActionLookupMap();
        map.register(new TestSignActionTCSignLegacy("one"), false);
        SignAction PRIORITY = map.register(new TestSignActionTCSignLegacy("one"), true);
        map.register(new TestSignActionTCSignLegacy("one"), false);

        assertEquals(PRIORITY, lookupSign(map, "[train]", "one", "", ""));
    }

    @Test
    public void testSimpleTrainCarts() {
        SignActionLookupMap map = new SignActionLookupMap();
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
        SignActionLookupMap map = new SignActionLookupMap();
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
        SignActionLookupMap map = new SignActionLookupMap();
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
}
