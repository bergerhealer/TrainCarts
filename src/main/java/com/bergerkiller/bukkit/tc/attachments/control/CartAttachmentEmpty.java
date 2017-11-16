package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.Player;

/**
 * Shows nothing. Acts as a placeholder node.
 */
public class CartAttachmentEmpty extends CartAttachment {

    @Override
    public void makeVisible(Player viewer) {
    }

    @Override
    public void makeHidden(Player viewer) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
    }

}
