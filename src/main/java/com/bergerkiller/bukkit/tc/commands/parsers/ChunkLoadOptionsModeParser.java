package com.bergerkiller.bukkit.tc.commands.parsers;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.properties.standard.type.ChunkLoadOptions;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;
import java.util.Queue;

/**
 * Parses a Chunk Load Options Mode
 */
public class ChunkLoadOptionsModeParser implements ArgumentParser<CommandSender, ChunkLoadOptions.Mode> {

    public ChunkLoadOptionsModeParser() {
    }

    @Override
    public ArgumentParseResult<ChunkLoadOptions.Mode> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        Optional<ChunkLoadOptions.Mode> parsed = ChunkLoadOptions.Mode.fromName(inputQueue.peek());
        if (!parsed.isPresent()) {
            return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                    Localization.COMMAND_INPUT_CHUNK_LOADING_MODE_INVALID, inputQueue.peek()));
        }

        inputQueue.poll();
        return ArgumentParseResult.success(parsed.get());
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        return ChunkLoadOptions.Mode.getAllNames();
    }
}
