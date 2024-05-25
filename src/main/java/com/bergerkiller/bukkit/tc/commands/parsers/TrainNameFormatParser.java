package com.bergerkiller.bukkit.tc.commands.parsers;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import com.bergerkiller.bukkit.common.cloud.parsers.QuotedArgumentParser;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.properties.standard.type.TrainNameFormat;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserDescriptor;

/**
 * Parses the train name format input provided by the user.
 * Extends (maps) the quoted string parser.
 */
public class TrainNameFormatParser implements QuotedArgumentParser<CommandSender, TrainNameFormat> {
    public static ParserDescriptor<CommandSender, TrainNameFormat> trainNameFormatParser() {
        return new TrainNameFormatParser().createDescriptor(TrainNameFormat.class);
    }

    @Override
    public ArgumentParseResult<TrainNameFormat> parseQuotedString(CommandContext<CommandSender> commandContext, String inputName) {
        TrainNameFormat name = TrainNameFormat.parse(inputName);
        TrainNameFormat.VerifyResult verify = name.verify();
        if (verify != TrainNameFormat.VerifyResult.OK) {
            return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                    verify.getMessage(), inputName));
        }
        return ArgumentParseResult.success(name);
    }
}
