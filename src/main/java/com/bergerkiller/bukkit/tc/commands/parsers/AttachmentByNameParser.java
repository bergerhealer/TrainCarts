package com.bergerkiller.bukkit.tc.commands.parsers;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.commands.argument.AttachmentsByName;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;

/**
 * Parses attachments that match a specified name
 */
public class AttachmentByNameParser implements ArgumentParser<CommandSender, AttachmentsByName<Attachment>> {
    private final boolean forTrain;
    private final Predicate<Attachment> filter;
    private final Localization emptyMessage;

    public static AttachmentByNameParser seats(boolean forTrain) {
        return new AttachmentByNameParser(forTrain,
                a -> a instanceof CartAttachmentSeat,
                Localization.COMMAND_INPUT_ATTACHMENTS_NO_SEATS);
    }

    public static AttachmentByNameParser effects(boolean forTrain) {
        return new AttachmentByNameParser(forTrain,
                a -> a instanceof Attachment.EffectAttachment,
                Localization.COMMAND_INPUT_ATTACHMENTS_NO_EFFECTS);
    }

    public AttachmentByNameParser(boolean forTrain, Predicate<Attachment> filter, Localization emptyMessage) {
        this.forTrain = forTrain;
        this.filter = filter;
        this.emptyMessage = emptyMessage;
    }

    private List<String> names(MinecartMember<?> member) {
        return member.getAttachments().getRootAttachment().getNameLookup().names(filter);
    }

    private AttachmentNameLookup lookup(CommandContext<CommandSender> context) {
        try {
            if (forTrain) {
                TrainProperties properties = context.inject(TrainProperties.class).get();
                MinecartGroup group = properties.getHolder();
                if (group == null) {
                    return AttachmentNameLookup.EMPTY;
                } else {
                    return group.getAttachments().getNameLookup();
                }
            } else {
                CartProperties properties = context.inject(CartProperties.class).get();
                MinecartMember<?> member = properties.getHolder();
                if (member == null) {
                    return AttachmentNameLookup.EMPTY;
                } else {
                    return member.getAttachments().getNameLookup();
                }
            }
        } catch (RuntimeException ex) {
            return AttachmentNameLookup.EMPTY;
        }
    }

    @Override
    public ArgumentParseResult<AttachmentsByName<Attachment>> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        String name = inputQueue.peek();
        List<Attachment> result = lookup(commandContext).get(name, filter);
        if (result.isEmpty()) {
            return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                    this.emptyMessage, name));
        }

        inputQueue.poll();
        return ArgumentParseResult.success(new AttachmentsByName<>(name, result));
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        return lookup(commandContext).names(filter);
    }
}
