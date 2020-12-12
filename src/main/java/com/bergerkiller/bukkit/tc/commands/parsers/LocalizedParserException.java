package com.bergerkiller.bukkit.tc.commands.parsers;

import com.bergerkiller.bukkit.tc.Localization;

import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.captions.CaptionVariable;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.ParserException;

/**
 * {@link ParserException} implementation with a message declared
 * using localization.
 */
public class LocalizedParserException extends ParserException {
    private static final long serialVersionUID = -750027695781313281L;
    private final String input;

    /**
     * Construct a new localized parser exception
     *
     * @param parserClass  Parser class
     * @param input     Input
     * @param context   Command context
     * @param message   Displayed message localization constant
     */
    public LocalizedParserException(
            final Class<? extends ArgumentParser<?, ?>> parserClass,
            final String input,
            final CommandContext<?> context,
            final Localization message
    ) {
        super(
                parserClass,
                context,
                message.getCaption(),
                CaptionVariable.of("input", input)
        );
        this.input = input;
    }

    /**
     * Get the input provided by the sender
     *
     * @return Input
     */
    public String getInput() {
        return this.input;
    }
}
