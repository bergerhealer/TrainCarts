package com.bergerkiller.bukkit.tc.attachments.helper;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

/**
 * Schedules an attachment active state change
 */
public interface ActiveChangeHandler {

    /**
     * Schedules an active state change for an attachment
     *
     * @param attachment Attachment that changed active state
     * @param active New active state
     */
    void scheduleActiveChange(Attachment attachment, boolean active);
}
