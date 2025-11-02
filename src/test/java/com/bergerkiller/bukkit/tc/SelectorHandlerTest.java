package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.junit.Before;
import org.junit.Test;

import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorException;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerConditionOption;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerRegistry;

/**
 * Tests the Selector command expansion logic
 */
public class SelectorHandlerTest {
    private final SelectorHandlerRegistry registry = new SelectorHandlerRegistry(null /* I know, ew */);

    @Before
    public void initRegistry() {
        // Simple handler that expands to the selector name and
        // provided arguments. Easy way to verify the entire system works.
        registry.register("test", new SelectorHandler() {

            @Override
            public Collection<String> handle(CommandSender sender, String selector, List<SelectorCondition> conditions) throws SelectorException {
                List<String> replacements = new ArrayList<String>();
                replacements.add(selector);
                for (SelectorCondition condition : conditions) {
                    replacements.add(condition.getKey());
                    replacements.add(condition.getValue());
                }
                return replacements;
            }

            @Override
            public List<SelectorHandlerConditionOption> options(CommandSender sender, String selector, List<SelectorCondition> conditions) {
                return Collections.emptyList();
            }
        });
    }

    @Test
    public void testHandlerNoSelectors() {
        assertEquals(Arrays.asList("command test"), registry.expandCommands(null, "command test"));
        assertEquals(Arrays.asList("command pre test"), registry.expandCommands(null, "command pre test"));
        assertEquals(Arrays.asList("command test post"), registry.expandCommands(null, "command test post"));
        assertEquals(Arrays.asList("command pre test post"), registry.expandCommands(null, "command pre test post"));
    }

    @Test
    public void testHandlerDifferentSelectors() {
        assertEquals(Arrays.asList("command @other"), registry.expandCommands(null, "command @other"));
        assertEquals(Arrays.asList("command pre @other"), registry.expandCommands(null, "command pre @other"));
        assertEquals(Arrays.asList("command @other post"), registry.expandCommands(null, "command @other post"));
        assertEquals(Arrays.asList("command pre @other post"), registry.expandCommands(null, "command pre @other post"));
    }

    @Test
    public void testHandlerNoArgs() {
        assertEquals(Arrays.asList("command test"), registry.expandCommands(null, "command @test"));
        assertEquals(Arrays.asList("command pre test"), registry.expandCommands(null, "command pre @test"));
        assertEquals(Arrays.asList("command test post"), registry.expandCommands(null, "command @test post"));
        assertEquals(Arrays.asList("command pre test post"), registry.expandCommands(null, "command pre @test post"));
    }

    @Test
    public void testHandlerSingleArg() {
        assertEquals(Arrays.asList("command test",
                                   "command a",
                                   "command b"), registry.expandCommands(null, "command @test[a=b]"));
        assertEquals(Arrays.asList("command pre test",
                                   "command pre a",
                                   "command pre b"), registry.expandCommands(null, "command pre @test[a=b]"));
        assertEquals(Arrays.asList("command test post",
                                   "command a post",
                                   "command b post"), registry.expandCommands(null, "command @test[a=b] post"));
        assertEquals(Arrays.asList("command pre test post",
                                   "command pre a post",
                                   "command pre b post"), registry.expandCommands(null, "command pre @test[a=b] post"));
        assertEquals(Arrays.asList("command pre test post",
                                   "command pre a post",
                                   "command pre b post"), registry.expandCommands(null, "command pre @test[a='b'] post"));

        // The quote should get parsed, and then dropped again as it does not need escaping as value
        assertEquals(Arrays.asList("command pre test post",
                                   "command pre a post",
                                   "command pre b post"), registry.expandCommands(null, "command pre @test[a=\"b\"] post"));

        // The quote gets unescaped but it contains contents with escaped " quotes
        // These end up as the value ("b"), which then must be escaped again when output as expanded command argument
        assertEquals(Arrays.asList("command pre test post",
                                   "command pre a post",
                                   "command pre \"\\\"b\\\"\" post"), registry.expandCommands(null, "command pre @test[a=\"\\\"b\\\"\"] post"));
    }

    @Test
    public void testHandlerTwoArgs() {
        assertEquals(Arrays.asList("command test",
                                   "command a",
                                   "command b",
                                   "command c",
                                   "command d"), registry.expandCommands(null, "command @test[a=b,c=d]"));
        assertEquals(Arrays.asList("command pre test",
                                   "command pre a",
                                   "command pre b",
                                   "command pre c",
                                   "command pre d"), registry.expandCommands(null, "command pre @test[a=b,c=d]"));
        assertEquals(Arrays.asList("command test post",
                                   "command a post",
                                   "command b post",
                                   "command c post",
                                   "command d post"), registry.expandCommands(null, "command @test[a=b,c=d] post"));
        assertEquals(Arrays.asList("command pre test post",
                                   "command pre a post",
                                   "command pre b post",
                                   "command pre c post",
                                   "command pre d post"), registry.expandCommands(null, "command pre @test[a=b,c=d] post"));
        assertEquals(Arrays.asList("command pre test post",
                                   "command pre a post",
                                   "command pre b post",
                                   "command pre c post",
                                   "command pre d post"), registry.expandCommands(null, "command pre @test[a=\"b\",c=\"d\"] post"));
    }

    @Test
    public void testHandlerThreeArgs() {
        assertEquals(Arrays.asList("command test",
                                   "command a",
                                   "command b",
                                   "command c",
                                   "command d",
                                   "command e",
                                   "command f"), registry.expandCommands(null, "command @test[a=b,c=d,e=f]"));
        assertEquals(Arrays.asList("command pre test",
                                   "command pre a",
                                   "command pre b",
                                   "command pre c",
                                   "command pre d",
                                   "command pre e",
                                   "command pre f"), registry.expandCommands(null, "command pre @test[a=b,c=d,e=f]"));
        assertEquals(Arrays.asList("command test post",
                                   "command a post",
                                   "command b post",
                                   "command c post",
                                   "command d post",
                                   "command e post",
                                   "command f post"), registry.expandCommands(null, "command @test[a=b,c=d,e=f] post"));
        assertEquals(Arrays.asList("command pre test post",
                                   "command pre a post",
                                   "command pre b post",
                                   "command pre c post",
                                   "command pre d post",
                                   "command pre e post",
                                   "command pre f post"), registry.expandCommands(null, "command pre @test[a=b,c=d,e=f] post"));
    }

    @Test
    public void testHandlerRecursiveNoArgs() {
        assertEquals(Arrays.asList("command test test"), registry.expandCommands(null, "command @test @test"));
        assertEquals(Arrays.asList("command test mid test"), registry.expandCommands(null, "command @test mid @test"));
        assertEquals(Arrays.asList("command test test post"), registry.expandCommands(null, "command @test @test post"));
        assertEquals(Arrays.asList("command pre test test post"), registry.expandCommands(null, "command pre @test @test post"));
    }

    @Test
    public void testHandlerRecursiveSingleArg() {
        assertEquals(Arrays.asList("command test test",
                                   "command a test",
                                   "command b test",
                                   "command test c",
                                   "command a c",
                                   "command b c",
                                   "command test d",
                                   "command a d",
                                   "command b d"), registry.expandCommands(null, "command @test[a=b] @test[c=d]"));
    }

    @Test
    public void testHandlerAlternativeStart() {
        assertEquals(Arrays.asList("command --flag=test"), registry.expandCommands(null, "command --flag=@test"));
        assertEquals(Arrays.asList("command pre --flag=test"), registry.expandCommands(null, "command pre --flag=@test"));
        assertEquals(Arrays.asList("command --flag=test post"), registry.expandCommands(null, "command --flag=@test post"));
        assertEquals(Arrays.asList("command pre --flag=test post"), registry.expandCommands(null, "command pre --flag=@test post"));
    }

    @Test
    public void testHandlerRangeArgs() {
        assertEquals(Arrays.asList("command test",
                                   "command a",
                                   "command 1..5",
                                   "command b",
                                   "command 6.."), registry.expandCommands(null, "command @test[a=1..5,b=6..]"));
    }

    @Test
    public void testHandlerInvertedArgs() {
        assertEquals(Arrays.asList("command test",
                                   "command a",
                                   "command \"!b\""), registry.expandCommands(null, "command @test[a=!b]"));
    }

    @Test
    public void testHandlerWithDotKeyValues() {
        assertEquals(Arrays.asList("command test",
                                   "command a.b",
                                   "command value"), registry.expandCommands(null, "command @test[a.b=value]"));
    }

    @Test
    public void testHandlerInvalidSyntax() {
        // This is mostly about making sure no errors are thrown
        assertEquals(Arrays.asList("command word@test"), registry.expandCommands(null, "command word@test"));
        assertEquals(Arrays.asList("command @test["), registry.expandCommands(null, "command @test["));
        assertEquals(Arrays.asList("command @test[]"), registry.expandCommands(null, "command @test[]"));
        assertEquals(Arrays.asList("command @test[ ]"), registry.expandCommands(null, "command @test[ ]"));
        assertEquals(Arrays.asList("command @test[=]"), registry.expandCommands(null, "command @test[=]"));
        assertEquals(Arrays.asList("command @test[a]"), registry.expandCommands(null, "command @test[a]"));
        assertEquals(Arrays.asList("command @test[a=]"), registry.expandCommands(null, "command @test[a=]"));
        assertEquals(Arrays.asList("command @test[=b]"), registry.expandCommands(null, "command @test[=b]"));
        assertEquals(Arrays.asList("command @test[a=b,]"), registry.expandCommands(null, "command @test[a=b,]"));
        assertEquals(Arrays.asList("command @test[a=b,a]"), registry.expandCommands(null, "command @test[a=b,a]"));
        assertEquals(Arrays.asList("command @test[a=b,c=]"), registry.expandCommands(null, "command @test[a=b,c=]"));
        assertEquals(Arrays.asList("command @test[a=b,=]"), registry.expandCommands(null, "command @test[a=b,=]"));
        assertEquals(Arrays.asList("command @test[a=b,=d]"), registry.expandCommands(null, "command @test[a=b,=d]"));
        assertEquals(Arrays.asList("command @test[a=b,c=d,]"), registry.expandCommands(null, "command @test[a=b,c=d,]"));
        assertEquals(Arrays.asList("command @test[a=b,c=d,=]"), registry.expandCommands(null, "command @test[a=b,c=d,=]"));
        assertEquals(Arrays.asList("command @test[a=b,c=d,e]"), registry.expandCommands(null, "command @test[a=b,c=d,e]"));
        assertEquals(Arrays.asList("command @test[a=b,c=d,e=]"), registry.expandCommands(null, "command @test[a=b,c=d,e=]"));
        assertEquals(Arrays.asList("command @test[a=b,c=d,=f]"), registry.expandCommands(null, "command @test[a=b,c=d,=f]"));
    }

    @Test
    public void testConditionText() {
        SelectorCondition condition = SelectorCondition.parse("key", "value");
        assertTrue(condition.matchesText("value"));
        assertFalse(condition.matchesText("not_value"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionTextInverted() {
        SelectorCondition condition = SelectorCondition.parse("key", "!value");
        assertFalse(condition.matchesText("value"));
        assertTrue(condition.matchesText("not_value"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionTextWildcardA() {
        SelectorCondition condition = SelectorCondition.parse("key", "pre*mid*post");
        assertTrue(condition.matchesText("preAAAmidBBBpost"));
        assertTrue(condition.matchesText("premidpost"));
        assertFalse(condition.matchesText("premid"));
        assertFalse(condition.matchesText("midpost"));
        assertFalse(condition.matchesText("not_value"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionTextWildcardB() {
        SelectorCondition condition = SelectorCondition.parse("key", "*mid*post");
        assertTrue(condition.matchesText("AAAmidBBBpost"));
        assertTrue(condition.matchesText("midpost"));
        assertFalse(condition.matchesText("AAApost"));
        assertFalse(condition.matchesText("AAAmid"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionTextWildcardC() {
        SelectorCondition condition = SelectorCondition.parse("key", "pre*mid*");
        assertTrue(condition.matchesText("preAAAmidBBB"));
        assertTrue(condition.matchesText("premid"));
        assertFalse(condition.matchesText("preBBB"));
        assertFalse(condition.matchesText("midBBB"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionTextWildcardD() {
        SelectorCondition condition = SelectorCondition.parse("key", "a*");
        assertTrue(condition.matchesText("aBBB"));
        assertTrue(condition.matchesText("a"));
        assertFalse(condition.matchesText("b"));
        assertFalse(condition.matchesText("ba"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionTextWildcardE() {
        SelectorCondition condition = SelectorCondition.parse("key", "*b");
        assertTrue(condition.matchesText("AAAb"));
        assertTrue(condition.matchesText("b"));
        assertFalse(condition.matchesText("a"));
        assertFalse(condition.matchesText("bc"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionTextList() {
        SelectorCondition condition = SelectorCondition.parse("key", "a..b..c");
        assertTrue(condition.matchesText("a"));
        assertTrue(condition.matchesText("b"));
        assertTrue(condition.matchesText("c"));
        assertFalse(condition.matchesText("not_value"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionTextListInverted() {
        SelectorCondition condition = SelectorCondition.parse("key", "!a..b..c");
        assertTrue(condition.matchesText("not_value"));
        assertFalse(condition.matchesText("a"));
        assertFalse(condition.matchesText("b"));
        assertFalse(condition.matchesText("c"));
        assertNumberMatchingThrows(condition);
    }

    @Test
    public void testConditionNumber() {
        SelectorCondition condition = SelectorCondition.parse("key", "12");
        assertTrue(condition.matchesText("12"));
        assertTrue(condition.matchesNumber(12.0));
        assertTrue(condition.matchesNumber(12L));
        assertFalse(condition.matchesText("13"));
        assertFalse(condition.matchesNumber(13.0));
        assertFalse(condition.matchesNumber(13L));
    }

    @Test
    public void testConditionNumberInverted() {
        SelectorCondition condition = SelectorCondition.parse("key", "!12");
        assertTrue(condition.matchesText("13"));
        assertTrue(condition.matchesNumber(13.0));
        assertTrue(condition.matchesNumber(13L));
        assertFalse(condition.matchesText("12"));
        assertFalse(condition.matchesNumber(12.0));
        assertFalse(condition.matchesNumber(12L));
    }

    @Test
    public void testConditionNumberRange() {
        SelectorCondition condition = SelectorCondition.parse("key", "12..14");
        assertTrue(condition.matchesText("12"));
        assertTrue(condition.matchesText("14"));
        assertTrue(condition.matchesNumber(12.0));
        assertTrue(condition.matchesNumber(12L));
        assertTrue(condition.matchesNumber(13.0));
        assertTrue(condition.matchesNumber(13.5));
        assertTrue(condition.matchesNumber(13L));
        assertTrue(condition.matchesNumber(14.0));
        assertTrue(condition.matchesNumber(14L));
        assertFalse(condition.matchesNumber(11.9));
        assertFalse(condition.matchesNumber(11L));
        assertFalse(condition.matchesNumber(14.1));
        assertFalse(condition.matchesNumber(15L));
        assertFalse(condition.matchesText("13")); // Hmn.
        assertFalse(condition.matchesText("12..14")); // Probably better to do this
    }

    @Test
    public void testConditionNumberRangeInverted() {
        SelectorCondition condition = SelectorCondition.parse("key", "!12..14");
        assertTrue(condition.matchesNumber(11.9));
        assertTrue(condition.matchesNumber(11L));
        assertTrue(condition.matchesNumber(14.1));
        assertTrue(condition.matchesNumber(15L));
        assertTrue(condition.matchesText("13")); // Hmn.
        assertTrue(condition.matchesText("12..14")); // Probably better to do this
        assertFalse(condition.matchesText("12"));
        assertFalse(condition.matchesText("14"));
        assertFalse(condition.matchesNumber(12.0));
        assertFalse(condition.matchesNumber(12L));
        assertFalse(condition.matchesNumber(13.0));
        assertFalse(condition.matchesNumber(13.5));
        assertFalse(condition.matchesNumber(13L));
        assertFalse(condition.matchesNumber(14.0));
        assertFalse(condition.matchesNumber(14L));
    }

    @Test
    public void testConditionNumberRangeList() {
        SelectorCondition condition = SelectorCondition.parse("key", "1..3..5");
        assertTrue(condition.matchesText("1"));
        assertTrue(condition.matchesNumber(1.0));
        assertTrue(condition.matchesNumber(1L));
        assertTrue(condition.matchesText("3"));
        assertTrue(condition.matchesNumber(3.0));
        assertTrue(condition.matchesNumber(3L));
        assertTrue(condition.matchesText("5"));
        assertTrue(condition.matchesNumber(5.0));
        assertTrue(condition.matchesNumber(5L));
        assertFalse(condition.matchesText("2"));
        assertFalse(condition.matchesNumber(2.0));
        assertFalse(condition.matchesNumber(2L));
    }

    // By adding another .. and not putting a number, two values can be checked
    @Test
    public void testConditionNumberRangeListSpecial() {
        SelectorCondition condition = SelectorCondition.parse("key", "1..3..");
        assertTrue(condition.matchesText("1"));
        assertTrue(condition.matchesNumber(1.0));
        assertTrue(condition.matchesNumber(1L));
        assertTrue(condition.matchesText("3"));
        assertTrue(condition.matchesNumber(3.0));
        assertTrue(condition.matchesNumber(3L));
        assertFalse(condition.matchesText("2"));
        assertFalse(condition.matchesNumber(2.0));
        assertFalse(condition.matchesNumber(2L));
    }

    private static void assertNumberMatchingThrows(SelectorCondition condition) {
        try {
            condition.matchesNumber(1.0);
            fail("No exception was thrown");
        } catch (SelectorException ex) {}

        try {
            condition.matchesNumber(1L);
            fail("No exception was thrown");
        } catch (SelectorException ex) {}
    }
}

