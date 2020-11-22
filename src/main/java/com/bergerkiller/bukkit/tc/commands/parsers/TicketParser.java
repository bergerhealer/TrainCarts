package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.tickets.Ticket;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.captions.CaptionVariable;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import cloud.commandframework.exceptions.parsing.ParserException;

/**
 * Parses tickets by checking what ticket the sender is currently editing
 * TODO: Allow specifying the ticket name using a flag
 */
public class TicketParser implements ArgumentParser<CommandSender, Ticket> {

    @Override
    public ArgumentParseResult<Ticket> parse(
            CommandContext<CommandSender> commandContext,
            Queue<String> inputQueue
    ) {
        final String input = inputQueue.peek();
        if (input == null) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        Ticket ticket = TicketStore.getTicket(input);
        if (ticket == null) {
            return ArgumentParseResult.failure(new TicketParseException(input, commandContext,
                    Localization.COMMAND_TICKET_NOTFOUND));
        }

        inputQueue.poll();
        return ArgumentParseResult.success(ticket);
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        return TicketStore.getAll().stream()
                .map(Ticket::getName)
                .collect(Collectors.toList());
    }

    public static final class TicketParseException extends ParserException {

        private static final long serialVersionUID = -750027695781313281L;
        private final String input;

        /**
         * Construct a new enum parse exception
         *
         * @param input     Input
         * @param enumClass Enum class
         * @param context   Command context
         */
        public TicketParseException(
                final String input,
                final CommandContext<?> context,
                final Localization message
        ) {
            super(
                    TicketParser.class,
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
}
