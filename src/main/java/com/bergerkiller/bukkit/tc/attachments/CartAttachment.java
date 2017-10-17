package com.bergerkiller.bukkit.tc.attachments;

/**
 * An element that is attached to a Minecart, moving along with it
 */
public interface CartAttachment {

    /**
     * Called every tick to refresh this attachment
     */
    void onTick();

    void onSyncAtt(boolean absolute);
}
