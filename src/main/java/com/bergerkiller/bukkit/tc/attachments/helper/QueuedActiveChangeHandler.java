package com.bergerkiller.bukkit.tc.attachments.helper;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

/**
 * Queues active changes so they can be processed instantly later
 */
public class QueuedActiveChangeHandler implements ActiveChangeHandler {
    private final Queue<PendingChange> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void scheduleActiveChange(Attachment attachment, boolean active) {
        this.queue.offer(new PendingChange(attachment, active));
    }

    /**
     * Processes all previously queued active changes
     */
    public void sync() {
        try {
            for (PendingChange pending : queue) {
                pending.attachment.setActive(pending.active);
            }
        } finally {
            queue.clear();
        }
    }

    private static final class PendingChange {
        public final Attachment attachment;
        public final boolean active;

        public PendingChange(Attachment attachment, boolean active) {
            this.attachment = attachment;
            this.active = active;
        }
    }
}
