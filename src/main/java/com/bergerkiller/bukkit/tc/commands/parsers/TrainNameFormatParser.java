package com.bergerkiller.bukkit.tc.commands.parsers;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;

/**
 * Parses the train name format input provided by the user
 */
public class TrainNameFormatParser implements ArgumentParser<CommandSender, TrainNameFormat> {
    public static ParserDescriptor<CommandSender, TrainNameFormat> trainNameFormatParser() {
        return ParserDescriptor.of(new TrainNameFormatParser(), TrainNameFormat.class);
    }

    @Override
    public @NonNull ArgumentParseResult<@NonNull TrainNameFormat> parse(@NonNull CommandContext<@NonNull CommandSender> commandContext, @NonNull CommandInput commandInput) {
        String inputName = commandInput.peekString();
        TrainNameFormat name = TrainNameFormat.parse(inputName);
        TrainNameFormat.VerifyResult verify = name.verify();
        if (verify != TrainNameFormat.VerifyResult.OK) {
            return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                    verify.getMessage(), inputName));
        }

        commandInput.readString();
        return ArgumentParseResult.success(name);
    }
}
