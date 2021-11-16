package com.bergerkiller.bukkit.tc.commands.selector;

/**
 * Represents a condition that is supported by a selector handler.
 * Provides information about the name, and what type of conditions
 * can be specified.
 */
public final class SelectorHandlerConditionOption {
    private final String _name;

    private SelectorHandlerConditionOption(String name) {
        this._name = name;
    }

    /**
     * Gets the unique name of this condition option
     *
     * @return name
     */
    public String name() {
        return this._name;
    }

    /**
     * Gets a condition option for a String argument
     *
     * @param name Name of the argument
     * @return selector handler condition option
     */
    public static SelectorHandlerConditionOption optionString(String name) {
        return new SelectorHandlerConditionOption(name);
    }
}
