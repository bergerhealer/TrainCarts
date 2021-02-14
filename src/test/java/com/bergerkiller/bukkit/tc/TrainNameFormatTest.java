package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;

public class TrainNameFormatTest {

    @Test
    public void testTrainNameGenerator() {
        assertEquals("train1", TrainNameFormat.parse("train#").generate(1));
        assertEquals("train2", TrainNameFormat.parse("train#").generate(2));
        assertEquals("train20", TrainNameFormat.parse("train#").generate(20));
        assertEquals("train", TrainNameFormat.parse("train").generate(1));
        assertEquals("train2", TrainNameFormat.parse("train").generate(2));
        assertEquals("train20", TrainNameFormat.parse("train").generate(20));
        assertEquals("pre#post5", TrainNameFormat.parse("pre#post#").generate(5));
    }

    @Test
    public void testIsTrainNameMatchingFormat() {
        // True cases
        assertTrue(TrainNameFormat.parse("train").matches("train"));
        assertTrue(TrainNameFormat.parse("train").matches("train1"));
        assertTrue(TrainNameFormat.parse("train").matches("train12"));
        assertTrue(TrainNameFormat.parse("train#").matches("train1"));
        assertTrue(TrainNameFormat.parse("train#").matches("train12"));
        assertTrue(TrainNameFormat.parse("#train").matches("1train"));
        assertTrue(TrainNameFormat.parse("#train").matches("12train"));
        assertTrue(TrainNameFormat.parse("prefix#postfix").matches("prefix1postfix"));
        assertTrue(TrainNameFormat.parse("prefix#postfix").matches("prefix12postfix"));
        assertTrue(TrainNameFormat.parse("name##").matches("name#1"));
        assertTrue(TrainNameFormat.parse("name##").matches("name#12"));
        assertTrue(TrainNameFormat.parse("one#two#three").matches("one#two1three"));
        assertTrue(TrainNameFormat.parse("one#two#three").matches("one#two12three"));

        // False cases
        assertFalse(TrainNameFormat.parse("train").matches("other"));
        assertFalse(TrainNameFormat.parse("train").matches("other1"));
        assertFalse(TrainNameFormat.parse("train").matches("other12"));
        assertFalse(TrainNameFormat.parse("train#").matches("train"));
        assertFalse(TrainNameFormat.parse("train#").matches("traina"));
        assertFalse(TrainNameFormat.parse("train#").matches("trainab"));
        assertFalse(TrainNameFormat.parse("train#").matches("other1"));
        assertFalse(TrainNameFormat.parse("train#").matches("other12"));
        assertFalse(TrainNameFormat.parse("#train").matches("train"));
        assertFalse(TrainNameFormat.parse("#train").matches("atrain"));
        assertFalse(TrainNameFormat.parse("#train").matches("abtrain"));
        assertFalse(TrainNameFormat.parse("#train").matches("1other"));
        assertFalse(TrainNameFormat.parse("#train").matches("12other"));
        assertFalse(TrainNameFormat.parse("prefix#postfix").matches("prefixpostfix"));
        assertFalse(TrainNameFormat.parse("prefix#postfix").matches("prefixapostfix"));
        assertFalse(TrainNameFormat.parse("name##").matches("name1#"));
        assertFalse(TrainNameFormat.parse("one#two#three").matches("one1two1three"));
        assertFalse(TrainNameFormat.parse("one#two#three").matches("one1two#three"));
    }

    @Test
    public void testGuessFormat() {
        assertEquals(TrainNameFormat.parse("train"), TrainNameFormat.guess("train"));
        assertEquals(TrainNameFormat.parse("train#"), TrainNameFormat.guess("train12"));
        assertEquals(TrainNameFormat.parse("#train"), TrainNameFormat.guess("12train"));
        assertEquals(TrainNameFormat.parse("pre#post"), TrainNameFormat.guess("pre12post"));
        assertEquals(TrainNameFormat.parse("pre15mid#post"), TrainNameFormat.guess("pre15mid12post"));
    }
}
