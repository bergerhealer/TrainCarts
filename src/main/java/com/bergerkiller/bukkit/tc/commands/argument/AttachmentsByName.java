package com.bergerkiller.bukkit.tc.commands.argument;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

import java.util.List;

/**
 * A list of named attachments that were retrieved by using a certain name query
 *
 * @param <T> Attachment Type
 */
public final class AttachmentsByName<T extends Attachment> {
    private final String name;
    private final List<T> attachments;

    public AttachmentsByName(String name, List<T> attachments) {
        this.name = name;
        this.attachments = attachments;
    }

    public String name() {
        return name;
    }

    public List<T> attachments() {
        return attachments;
    }
}
