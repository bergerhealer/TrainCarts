package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerRegistry;

/**
 * Tests the Selector command expansion logic
 */
public class SelectorHandlerTest {
    private final SelectorHandlerRegistry registry = new SelectorHandlerRegistry();

    @Before
    public void initRegistry() {
        // Simple handler that expands to the selector name and
        // provided arguments. Easy way to verify the entire system works.
        registry.register("test", (sender, selector, args) -> {
            List<String> replacements = new ArrayList<String>();
            replacements.add(selector);
            for (Map.Entry<String, String> entry : args.entrySet()) {
                replacements.add(entry.getKey());
                replacements.add(entry.getValue());
            }
            return replacements;
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
}
