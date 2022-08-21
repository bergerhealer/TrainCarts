package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.Queue;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;

/**
 * Parses the train name format input provided by the user
 */
public class TrainNameFormatParser implements ArgumentParser<CommandSender, TrainNameFormat> {

    @Override
    public ArgumentParseResult<TrainNameFormat> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        String inputName = inputQueue.peek();
        TrainNameFormat name = TrainNameFormat.parse(inputName);
        TrainNameFormat.VerifyResult verify = name.verify();
        if (verify != TrainNameFormat.VerifyResult.OK) {
            return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                    verify.getMessage(), inputName));
        }

        inputQueue.poll();
        return ArgumentParseResult.success(name);
    }
}
