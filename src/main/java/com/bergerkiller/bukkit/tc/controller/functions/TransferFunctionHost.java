package com.bergerkiller.bukkit.tc.controller.functions;

/**
 * Owns transfer functions. Provides information about what inputs
 * are available and other information that changes transfer function
 * behavior.
 */
public interface TransferFunctionSimulator {

    /**
     * Looks up an input transfer function by name. If this input is not
     * available (in this context) then null is returned.
     *
     * @param name Name of the input
     * @return Transfer function input by this name, or null if it does not
     *         exist or is not available (infinite recursion)
     */
    TransferFunctionInput findInput(String name);
}
