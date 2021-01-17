package com.bergerkiller.bukkit.tc.commands.selector;

/**
 * Exception thrown by selectors when something is wrong about
 * the provided input. Cancels the command.
 */
public class SelectorException extends RuntimeException {
    private static final long serialVersionUID = 2612157530512018265L;

    public SelectorException(String reason) {
        super(reason);
    }
}
