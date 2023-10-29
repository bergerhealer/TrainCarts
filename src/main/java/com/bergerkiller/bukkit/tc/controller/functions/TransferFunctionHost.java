package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.tc.TrainCarts;

import java.util.List;

/**
 * Owns transfer functions. Provides information about what inputs
 * are available and other information that changes transfer function
 * behavior.
 */
public interface TransferFunctionHost extends TrainCarts.Provider {

    /**
     * Gets the registry used for loading/saving transfer functions
     *
     * @return Transfer Function Registry
     */
    TransferFunctionRegistry getRegistry();

    /**
     * Gets a List of all supported transfer function inputs
     *
     * @return Inputs
     */
    List<TransferFunction.Input> getInputs();

    /**
     * Looks up an input for transfer function by name. If this input is not
     * available (in this context) then null is returned.
     *
     * @param name Name of the input
     * @return Transfer function input by this name, or null if it does not
     *         exist or is not available (infinite recursion)
     */
    default TransferFunction.Input findInput(String name) {
        for (TransferFunction.Input input : getInputs()) {
            if (name.equals(input.name())) {
                return input;
            }
        }
        for (TransferFunction.Input input : getInputs()) {
            if (name.equalsIgnoreCase(input.name())) {
                return input;
            }
        }
        return null;
    }
}
