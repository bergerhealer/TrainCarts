package com.bergerkiller.bukkit.tc.commands.parsers;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.commands.argument.AttachmentsByName;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Parses attachments that match a specified name
 */
public class AttachmentByNameParser<T extends Attachment> implements ArgumentParser<CommandSender, AttachmentsByName<T>>, BlockingSuggestionProvider.Strings<CommandSender> {
    private final boolean forTrain;
    private final Predicate<Attachment> filter;
    private final Localization emptyMessage;

    public static <T extends Attachment> AttachmentByNameParser<T> seats(boolean forTrain) {
        return new AttachmentByNameParser<T>(forTrain,
                a -> a instanceof CartAttachmentSeat,
                Localization.COMMAND_INPUT_ATTACHMENTS_NO_SEATS);
    }

    public static <T extends Attachment> AttachmentByNameParser<T> effects(boolean forTrain) {
        return new AttachmentByNameParser<T>(forTrain,
                a -> a instanceof Attachment.EffectAttachment,
                Localization.COMMAND_INPUT_ATTACHMENTS_NO_EFFECTS);
    }

    public AttachmentByNameParser(boolean forTrain, Predicate<Attachment> filter, Localization emptyMessage) {
        this.forTrain = forTrain;
        this.filter = filter;
        this.emptyMessage = emptyMessage;
    }

    @SuppressWarnings("unchecked")
    public List<T> parse(CommandContext<CommandSender> context, String name) {
        List<T> result = (List<T>) lookup(context).get(name, filter);
        if (result.isEmpty()) {
            throw new CloudLocalizedException(context, this.emptyMessage, name);
        }
        return result;
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
    public @NonNull ArgumentParseResult<@NonNull AttachmentsByName<T>> parse(@NonNull CommandContext<@NonNull CommandSender> commandContext, @NonNull CommandInput commandInput) {
        // Note: this stuff won't work, because the --train and --cart flags aren't parsed yet
        /*
        String name = inputQueue.peek();
        List<Attachment> result = lookup(commandContext).get(name, filter);
        if (result.isEmpty()) {
            return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                    this.emptyMessage, name));
        }
        inputQueue.poll();
         */

        String name = commandInput.readString();
        return ArgumentParseResult.success(new AttachmentsByName<>(name, this, commandContext));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(
            @NonNull CommandContext<CommandSender> commandContext,
            @NonNull CommandInput input
    ) {
        // Note: at this stage it is not possible to make use of the --train or --cart flags, as they are
        //       parsed after this argument. Just rely on the train the player has selected to provide a
        //       bare minimum for suggestions.
        //       Hopefully this is fixed in a future version of Cloud.

        if (!(commandContext.sender() instanceof Player)) {
            return Collections.emptyList();
        }

        CartProperties props = commandContext.inject(TrainCarts.class).get().getPlayer((Player) commandContext.sender()).getEditedCart();
        MinecartMember<?> member;
        if (props == null || (member = props.getHolder()) == null) {
            return Collections.emptyList();
        }

        AttachmentNameLookup lookup;
        if (forTrain) {
            lookup = member.getGroup().getAttachments().getNameLookup();
        } else {
            lookup = member.getAttachments().getNameLookup();
        }

        return lookup.names(filter);
    }
}
