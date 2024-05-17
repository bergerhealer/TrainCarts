package com.bergerkiller.bukkit.tc.commands.parsers;

import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.properties.standard.type.ChunkLoadOptions;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.util.Optional;

/**
 * Parses a Chunk Load Options Mode
 */
public class ChunkLoadOptionsModeParser implements ArgumentParser<CommandSender, ChunkLoadOptions.Mode>, BlockingSuggestionProvider.Strings<CommandSender> {
    public static ParserDescriptor<CommandSender, ChunkLoadOptions.Mode> chunkLoadOptionsModeParser() {
        return ParserDescriptor.of(new ChunkLoadOptionsModeParser(), ChunkLoadOptions.Mode.class);
    }

    @Override
    public @NonNull ArgumentParseResult<ChunkLoadOptions.@NonNull Mode> parse(@NonNull CommandContext<@NonNull CommandSender> commandContext, @NonNull CommandInput commandInput) {
        Optional<ChunkLoadOptions.Mode> parsed = ChunkLoadOptions.Mode.fromName(commandInput.peekString());
        if (!parsed.isPresent()) {
            return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                    Localization.COMMAND_INPUT_CHUNK_LOADING_MODE_INVALID, commandInput.peekString()));
        }

        commandInput.readString();
        return ArgumentParseResult.success(parsed.get());
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<CommandSender> commandContext, @NonNull CommandInput input) {
        return ChunkLoadOptions.Mode.getAllNames();
    }
}
