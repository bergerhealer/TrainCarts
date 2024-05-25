package com.bergerkiller.bukkit.tc.commands.argument;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.commands.parsers.AttachmentByNameParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.context.CommandContext;

import java.util.List;

/**
 * A list of named attachments that were retrieved by using a certain name query.
 * Note that the retrieval is deferred, and {@link #attachments()} can throw on
 * first use of no attachments match. This is due to argument parsing order.
 *
 * @param <T> Attachment Type
 */
public final class AttachmentsByName<T extends Attachment> {
    private final String name;
    private final AttachmentByNameParser<T> parser; // Performs deferred parsing
    private final CommandContext<CommandSender> context;
    private List<T> attachments = null; // Deferred

    public AttachmentsByName(String name, AttachmentByNameParser<T> parser, CommandContext<CommandSender> context) {
        this.name = name;
        this.parser = parser;
        this.context = context;
    }

    public String name() {
        return name;
    }

    /**
     * Checks that attachments could be parsed. Throws an exception (handled by the command manager)
     * if no attachments were matched.
     */
    public void validate() {
        attachments();
    }

    public List<T> attachments() {
        if (attachments == null) {
            attachments = parser.parse(context, name);
        }
        return attachments;
    }
}
