package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.bergerkiller.bukkit.tc.properties.registry.TCPropertyRegistry;

public class SignParsingTest {

    // On Mac sometimes these weird characters are typed into Signs for no good reason
    // trimInvalidCharacters removes these. This test ensures that this trimming works.
    @Test
    public void testTrimInvalidCharacters() {
        assertEquals("[train]", Util.cleanSignLine("[train]\uf701"));
        assertEquals("[train]", Util.cleanSignLine("\uf702[train]"));
        assertEquals("[train]", Util.cleanSignLine("\uf701[train]\uf702"));
        assertArrayEquals(new String[] {
                "[train]",
                "station",
                "5",
                "continue"
        }, Util.cleanSignLines(new String[] {
                "[train]",
                "\uf701station",
                "5",
                "continue\uf702"
        }));
    }

    @Test
    public void testPatternLiterals() {
        String pattern1 = "a|b|c";
        List<String> literals1 = TCPropertyRegistry.findPatternLiterals(pattern1);
        assertEquals(Arrays.asList("a", "b", "c"), literals1);

        String pattern2 = "(a|b|c)";
        List<String> literals2 = TCPropertyRegistry.findPatternLiterals(pattern2);
        assertEquals(Arrays.asList("a", "b", "c"), literals2);

        String pattern3 = "name";
        List<String> literals3 = TCPropertyRegistry.findPatternLiterals(pattern3);
        assertEquals(Arrays.asList("name"), literals3);

        String pattern4 = "^weird|cool";
        List<String> literals4 = TCPropertyRegistry.findPatternLiterals(pattern4);
        assertEquals(Collections.emptyList(), literals4);

        String pattern5 = "weird|cool$";
        List<String> literals5 = TCPropertyRegistry.findPatternLiterals(pattern5);
        assertEquals(Collections.emptyList(), literals5);

        String pattern6 = "text|(with)|invalid";
        List<String> literals6 = TCPropertyRegistry.findPatternLiterals(pattern6);
        assertEquals(Collections.emptyList(), literals6);

        String pattern7 = "text||with|invalid";
        List<String> literals7 = TCPropertyRegistry.findPatternLiterals(pattern7);
        assertEquals(Collections.emptyList(), literals7);

        String pattern8 = "|text|with|invalid";
        List<String> literals8 = TCPropertyRegistry.findPatternLiterals(pattern8);
        assertEquals(Collections.emptyList(), literals8);

        String pattern9 = "this|matches|anyway|";
        List<String> literals9 = TCPropertyRegistry.findPatternLiterals(pattern9);
        assertEquals(Arrays.asList("this", "matches", "anyway"), literals9);

        String pattern10 = "a a|b b|c c";
        List<String> literals10 = TCPropertyRegistry.findPatternLiterals(pattern10);
        assertEquals(Arrays.asList("a a", "b b", "c c"), literals10);
    }
}
