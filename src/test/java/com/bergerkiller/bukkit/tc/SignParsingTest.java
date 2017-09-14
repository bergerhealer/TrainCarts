package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.junit.Test;

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
}
