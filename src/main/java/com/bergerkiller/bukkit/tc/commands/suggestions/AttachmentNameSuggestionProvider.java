package com.bergerkiller.bukkit.tc.commands.suggestions;

import cloud.commandframework.context.CommandContext;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Provides a list of suggestions for attachment names declared in a train or cart.
 * The type of attachments to list can be further filtered.
 */
public class AttachmentNameSuggestionProvider implements BiFunction<CommandContext<CommandSender>, String, List<String>> {
    private final boolean forTrain;
    private final Predicate<Attachment> filter;

    public static AttachmentNameSuggestionProvider forSeats(boolean forTrain) {
        return new AttachmentNameSuggestionProvider(forTrain, e -> e instanceof CartAttachmentSeat);
    }

    public AttachmentNameSuggestionProvider(boolean forTrain, Predicate<Attachment> filter) {
        this.forTrain = forTrain;
        this.filter = filter;
    }

    private List<String> names(MinecartMember<?> member) {
        return member.getAttachments().getRootAttachment().getNameLookup().names(filter);
    }

    @Override
    public List<String> apply(CommandContext<CommandSender> context, String s) {
        // Get list of animation names for the train or cart
        // May throw if permissions aren't set right for the player, return empty list then
        Set<String> names;
        try {
            if (forTrain) {
                TrainProperties properties = context.inject(TrainProperties.class).get();
                MinecartGroup group = properties.getHolder();
                if (group == null) {
                    return Collections.emptyList();
                } else if (group.size() == 1) {
                    return names(group.get(0));
                } else {
                    LinkedHashSet<String> unique = new LinkedHashSet<>();
                    for (MinecartMember<?> member : group) {
                        unique.addAll(names(member));
                    }
                    return new ArrayList<>(unique);
                }
            } else {
                CartProperties properties = context.inject(CartProperties.class).get();
                MinecartMember<?> member = properties.getHolder();
                if (member == null) {
                    return Collections.emptyList();
                } else {
                    return names(member);
                }
            }
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }
    }
}
