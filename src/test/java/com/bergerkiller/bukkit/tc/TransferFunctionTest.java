package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionConditional.Operator;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionCurve;
import org.junit.Test;

/**
 * Tests the correct functioning of transfer functions. These are used as part
 * of effect and animation automation.
 */
public class TransferFunctionTest {

    @Test
    public void testConditionalEqualOperator() {
        // If equal, must always return true
        assertTrue(Operator.EQUAL.compareWithHysteresis(true, 0.2, 0.2, 0.5));
        assertTrue(Operator.EQUAL.compareWithHysteresis(false, 0.2, 0.2, 0.5));

        // If not equal, but below hysteresis, must return true if it was true, false if it was false
        assertTrue(Operator.EQUAL.compareWithHysteresis(true, 0.2, 0.3, 0.5));
        assertFalse(Operator.EQUAL.compareWithHysteresis(false, 0.2, 0.3, 0.5));
        assertFalse(Operator.EQUAL.compareWithHysteresis(false, 0.3, 0.2, 0.5));

        // If beyond hysteresis, must toggle
        assertFalse(Operator.EQUAL.compareWithHysteresis(true, 0.2, 0.9, 0.5));
        assertFalse(Operator.EQUAL.compareWithHysteresis(true, 0.9, 0.2, 0.5));
    }

    @Test
    public void testConditionalNotEqualOperator() {
        // If equal, must always return false
        assertFalse(Operator.NOT_EQUAL.compareWithHysteresis(true, 0.2, 0.2, 0.5));
        assertFalse(Operator.NOT_EQUAL.compareWithHysteresis(false, 0.2, 0.2, 0.5));

        // If not equal, but below hysteresis, must return true if it was true, false if it was false
        assertTrue(Operator.NOT_EQUAL.compareWithHysteresis(true, 0.2, 0.3, 0.5));
        assertFalse(Operator.NOT_EQUAL.compareWithHysteresis(false, 0.2, 0.3, 0.5));
        assertFalse(Operator.NOT_EQUAL.compareWithHysteresis(false, 0.3, 0.2, 0.5));

        // If beyond hysteresis, must toggle
        assertTrue(Operator.NOT_EQUAL.compareWithHysteresis(false, 0.2, 0.9, 0.5));
        assertTrue(Operator.NOT_EQUAL.compareWithHysteresis(false, 0.9, 0.2, 0.5));
    }

    @Test
    public void testConditionalGreaterEqualThanOperator() {
        // If very much greater than (> h), must always return true
        assertTrue(Operator.GREATER_EQUAL_THAN.compareWithHysteresis(true, 0.8, 0.2, 0.5));
        assertTrue(Operator.GREATER_EQUAL_THAN.compareWithHysteresis(false, 0.8, 0.2, 0.5));

        // If very much lesser than (< h), must always return false
        assertFalse(Operator.GREATER_EQUAL_THAN.compareWithHysteresis(true, 0.2, 0.8, 0.5));
        assertFalse(Operator.GREATER_EQUAL_THAN.compareWithHysteresis(false, 0.2, 0.8, 0.5));

        // Within the hysteresis region, return original state
        assertTrue(Operator.GREATER_EQUAL_THAN.compareWithHysteresis(true, 0.2, 0.3, 0.5));
        assertTrue(Operator.GREATER_EQUAL_THAN.compareWithHysteresis(true, 0.3, 0.2, 0.5));
        assertFalse(Operator.GREATER_EQUAL_THAN.compareWithHysteresis(false, 0.2, 0.3, 0.5));
        assertFalse(Operator.GREATER_EQUAL_THAN.compareWithHysteresis(false, 0.3, 0.2, 0.5));
    }

    @Test
    public void testConditionalLesserEqualThanOperator() {
        // If very much lesser than (< h), must always return true
        assertTrue(Operator.LESSER_EQUAL_THAN.compareWithHysteresis(true, 0.2, 0.8, 0.5));
        assertTrue(Operator.LESSER_EQUAL_THAN.compareWithHysteresis(false, 0.2, 0.8, 0.5));

        // If very much greater than (> h), must always return false
        assertFalse(Operator.LESSER_EQUAL_THAN.compareWithHysteresis(true, 0.8, 0.2, 0.5));
        assertFalse(Operator.LESSER_EQUAL_THAN.compareWithHysteresis(false, 0.8, 0.2, 0.5));

        // Within the hysteresis region, return original state
        assertTrue(Operator.LESSER_EQUAL_THAN.compareWithHysteresis(true, 0.2, 0.3, 0.5));
        assertTrue(Operator.LESSER_EQUAL_THAN.compareWithHysteresis(true, 0.3, 0.2, 0.5));
        assertFalse(Operator.LESSER_EQUAL_THAN.compareWithHysteresis(false, 0.2, 0.3, 0.5));
        assertFalse(Operator.LESSER_EQUAL_THAN.compareWithHysteresis(false, 0.3, 0.2, 0.5));
    }

    @Test
    public void testCurveUpdateDenyTriple() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .build();
        // This should fail
        assertFalse(curve.updateAt(0, 0.5, 0.5));
        assertEquals(0.0, curve.getInput(0), 0.0);
        assertEquals(0.0, curve.getOutput(0), 0.0);
    }

    @Test
    public void testCurveUpdateClamping() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .build();
        // Update second value and attempt to set it to before 0.0
        assertTrue(curve.updateAt(1, -0.5, -0.5));
        assertEquals(0.0, curve.getInput(1), 0.0);
        assertEquals(-0.5, curve.getOutput(1), 0.0);
        // Update second value and attempt to set it to after 1.0
        assertTrue(curve.updateAt(1, 1.5, 1.5));
        assertEquals(1.0, curve.getInput(1), 0.0);
        assertEquals(1.5, curve.getOutput(1), 0.0);
    }

    @Test
    public void testCurveUpdateSimple() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .build();
        // Update output only
        assertTrue(curve.updateAt(1, 0.5, 0.75));
        assertEquals(0.5, curve.getInput(1), 0.0);
        assertEquals(0.75, curve.getOutput(1), 0.0);
        // Move input backwards
        assertTrue(curve.updateAt(1, 0.25, 0.4));
        assertEquals(0.25, curve.getInput(1), 0.0);
        assertEquals(0.4, curve.getOutput(1), 0.0);
        // Move input forwards
        assertTrue(curve.updateAt(1, 0.75, 0.8));
        assertEquals(0.75, curve.getInput(1), 0.0);
        assertEquals(0.8, curve.getOutput(1), 0.0);
    }

    @Test
    public void testCurveRemoveClear() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .build();
        assertEquals(3, curve.size());
        assertFalse(curve.isEmpty());
        curve.removeAt(1);
        assertEquals(2, curve.size());
        assertFalse(curve.isEmpty());
        curve.removeAt(1);
        assertEquals(1, curve.size());
        assertFalse(curve.isEmpty());
        curve.removeAt(0);
        assertEquals(0, curve.size());
        assertTrue(curve.isEmpty());
    }

    @Test
    public void testCurveRemoveAtStart() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .build();
        curve.removeAt(0);
        assertCurveEquals(curve, new double[][] {
                { 0.5, 0.5 },
                { 1.0, 1.0 }
        });
    }

    @Test
    public void testCurveRemoveAtMid() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .build();
        curve.removeAt(1);
        assertCurveEquals(curve, new double[][] {
                { 0.0, 0.0 },
                { 1.0, 1.0 }
        });
    }

    @Test
    public void testCurveRemoveAtEnd() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .build();
        curve.removeAt(2);
        assertCurveEquals(curve, new double[][] {
                { 0.0, 0.0 },
                { 0.5, 0.5 }
        });
    }

    @Test
    public void testCurveMapInitialUpper() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(0.5, 1.0)
                .add(1.0, 2.0)
                .build();
        assertEquals(1.0, curve.map(0.5), 1e-10); // should pick highest if initial input
    }

    @Test
    public void testCurveMapWithStep() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(0.5, 1.0)
                .add(1.0, 2.0)
                .build();
        assertEquals(0.0, curve.map(-1.0), 1e-10);
        assertEquals(0.0, curve.map(0.0), 1e-10);
        assertEquals(0.25, curve.map(0.25), 1e-10);
        assertEquals(0.5, curve.map(0.5), 1e-10); // Should pick lowest, because it was lower before
        assertEquals(0.5, curve.map(0.5), 1e-10); // Should pick lowest, because it holds the value
        assertEquals(1.5, curve.map(0.75), 1e-10); // Should pick highest, because it is above the threshold
        assertEquals(1.0, curve.map(0.5), 1e-10); // should pick highest, because it was higher before
        assertEquals(1.0, curve.map(0.5), 1e-10); // should pick highest, because it holds the value
        assertEquals(0.25, curve.map(0.25), 1e-10);
        assertEquals(1.5, curve.map(0.75), 1e-10);
        assertEquals(2.0, curve.map(1.0), 1e-10);
        assertEquals(2.0, curve.map(1.5), 1e-10);
    }

    @Test
    public void testCurveAddThreeInOne() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(0.5, 0.75)
                .add(0.5, 1.0)
                .add(1.0, 1.0)
                .build();
        assertCurveEquals(curve, new double[][] {
                { 0.0, 0.0 },
                { 0.5, 0.5 },
                { 0.5, 1.0 },
                { 1.0, 1.0 }
        });
    }

    @Test
    public void testCurveAddTwoInOne() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(0.5, 1.0)
                .add(1.0, 1.0)
                .build();
        assertCurveEquals(curve, new double[][] {
                { 0.0, 0.0 },
                { 0.5, 0.5 },
                { 0.5, 1.0 },
                { 1.0, 1.0 }
        });
    }

    @Test
    public void testCurveAddRandom() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .add(0.0, 0.0)
                .build();
        assertCurveEquals(curve, new double[][] {
                { 0.0, 0.0 },
                { 0.5, 0.5 },
                { 1.0, 1.0 }
        });
    }

    @Test
    public void testCurveAddInverse() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(1.0, 1.0)
                .add(0.5, 0.5)
                .add(0.0, 0.0)
                .build();
        assertCurveEquals(curve, new double[][] {
                { 0.0, 0.0 },
                { 0.5, 0.5 },
                { 1.0, 1.0 }
        });
    }

    @Test
    public void testCurveAddSequential() {
        TransferFunctionCurve curve = TransferFunctionCurve.builder()
                .add(0.0, 0.0)
                .add(0.5, 0.5)
                .add(1.0, 1.0)
                .build();
        assertCurveEquals(curve, new double[][] {
                { 0.0, 0.0 },
                { 0.5, 0.5 },
                { 1.0, 1.0 }
        });
    }

    private static void assertCurveEquals(TransferFunctionCurve curve, double[][] values) {
        assertEquals(values.length, curve.size());
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i][0], curve.getInput(i), 0.0);
            assertEquals(values[i][1], curve.getOutput(i), 0.0);
        }
    }
}
