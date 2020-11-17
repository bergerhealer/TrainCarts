package com.bergerkiller.bukkit.tc.commands.parsers;

import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.properties.SavedTrainProperties;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.captions.CaptionVariable;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import cloud.commandframework.exceptions.parsing.ParserException;

/**
 * Parser for SavedTrainProperties
 */
public class SavedTrainPropertiesParser implements ArgumentParser<CommandSender, SavedTrainProperties> {
    private final TrainCarts plugin;
    private final boolean mustHaveAccess;

    public SavedTrainPropertiesParser(TrainCarts plugin, boolean mustHaveAccess) {
        this.plugin = plugin;
        this.mustHaveAccess = mustHaveAccess;
    }

    public boolean isMustHaveAccess() {
        return this.mustHaveAccess;
    }

    @Override
    public ArgumentParseResult<SavedTrainProperties> parse(CommandContext<CommandSender> commandContext, Queue<String> inputQueue) {        
        final String input = inputQueue.peek();
        if (input == null) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    SavedTrainPropertiesParser.class,
                    commandContext
            ));
        }

        SavedTrainProperties properties = plugin.getSavedTrains().getProperties(input);
        if (properties == null) {
            return ArgumentParseResult.failure(new SavedTrainPropertiesParseException(input, commandContext,
                    Localization.COMMAND_SAVEDTRAIN_NOTFOUND));
        }

        /*
        System.out.println("CHECK " + mustHaveAccess + " force=" + commandContext.flags().hasFlag("force"));

        if (mustHaveAccess && !properties.hasPermission(commandContext.getSender())) {
            // Check if --force was specified
            boolean force = commandContext.flags().hasFlag("force");
            if (!Permission.COMMAND_SAVEDTRAIN_GLOBAL.has(commandContext.getSender())) {
                if (force) {
                    // Force was specified, but player has no permission for that
                    return ArgumentParseResult.failure(new SavedTrainPropertiesParseException(input, commandContext,
                            Localization.COMMAND_SAVEDTRAIN_GLOBAL_NOPERM));
                } else {
                    // No force was specified, was claimed and player has no permission
                    return ArgumentParseResult.failure(new SavedTrainPropertiesParseException(input, commandContext,
                            Localization.COMMAND_SAVEDTRAIN_CLAIMED));
                }
            } else if (!force) {
                return ArgumentParseResult.failure(new SavedTrainPropertiesParseException(input, commandContext,
                        Localization.COMMAND_SAVEDTRAIN_FORCE));
            }
        }
        */

        inputQueue.remove();
        return ArgumentParseResult.success(properties);
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        List<String> filtered;
        if (input.isEmpty()) {
            filtered = plugin.getSavedTrains().getNames();
        } else {
            filtered = plugin.getSavedTrains().getNames().stream()
                    .filter(s -> s.startsWith(input)).collect(Collectors.toList());
        }

        List<String> claimed = filtered.stream().filter(name -> {
            return plugin.getSavedTrains().hasPermission(commandContext.getSender(), name);
        }).collect(Collectors.toList());

        if (claimed.isEmpty()) {
            return filtered;
        } else {
            return claimed;
        }
    }

    public static final class SavedTrainPropertiesParseException extends ParserException {

        private static final long serialVersionUID = -750027695781313281L;
        private final String input;

        /**
         * Construct a new enum parse exception
         *
         * @param input     Input
         * @param enumClass Enum class
         * @param context   Command context
         */
        public SavedTrainPropertiesParseException(
                final String input,
                final CommandContext<?> context,
                final Localization message
        ) {
            super(
                    SavedTrainPropertiesParser.class,
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
