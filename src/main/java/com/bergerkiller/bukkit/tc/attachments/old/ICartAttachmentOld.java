package com.bergerkiller.bukkit.tc.attachments.old;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.controller.Tickable;

/**
 * An element that is attached to a Minecart, moving along with it
 */
public interface ICartAttachmentOld extends Tickable {

    @Override
    void onTick();

    void onSyncAtt(boolean absolute);

    /**
     * Makes this attachment visible to a new player.
     * 
     * @param viewer to add
     * @return True if the viewer is a new viewer
     */
    boolean addViewer(Player viewer);

    /**
     * Makes this attachment hidden to a previously added viewer.
     * 
     * @param viewer to remove
     * @return True if the viewer was a viewer and is removed
     */
    boolean removeViewer(Player viewer);
}
