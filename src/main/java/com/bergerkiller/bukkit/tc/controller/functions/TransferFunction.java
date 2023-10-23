package com.bergerkiller.bukkit.tc.controller.functions;

import java.util.function.DoubleUnaryOperator;

/**
 * Takes in double input values and maps (transforms) the input into a new output value.<br>
 * <br>
 * This class is not multithread-safe.
 * 
 * @see #map(double)
 */
public interface TransferFunction extends DoubleUnaryOperator {
    /**
     * Maps the input value to an output value based on this transfer function.
     * For time-based transfer functions it assumes this method is called every tick.
     *
     * @param input Input value
     * @return Transfer Function Output value
     */
    double map(double input);

    @Override
    default double applyAsDouble(double v) {
        return map(v);
    }
}
