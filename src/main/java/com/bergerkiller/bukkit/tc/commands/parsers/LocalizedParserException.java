package com.bergerkiller.bukkit.tc.commands.parsers;

import com.bergerkiller.bukkit.tc.Localization;

import cloud.commandframework.context.CommandContext;

/**
 * Exception thrown from parsers when provided input is invalid
 */
public class LocalizedParserException extends IllegalArgumentException {
    private static final long serialVersionUID = -5284037051953535599L;
    private final CommandContext<?> context;
    private final Localization message;
    private final String input;

    public LocalizedParserException(
            final CommandContext<?> context,
            final Localization message,
            final String input
    ) {
        this.context = context;
        this.message = message;
        this.input = input;
    }

    @Override
    public final String getMessage() {
        return this.message.get(this.input);
    }

    /**
     * Get the command context
     *
     * @return Command context
     */
    public final CommandContext<?> getContext() {
        return this.context;
    }
}
