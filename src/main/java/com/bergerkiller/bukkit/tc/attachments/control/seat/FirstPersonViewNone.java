package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;

/**
 * Used when no player is sitting inside the seat
 */
public class FirstPersonViewNone extends FirstPersonView {

    public FirstPersonViewNone(CartAttachmentSeat seat) {
        super(seat, null);
    }

    @Override
    public void makeVisible(Player viewer, boolean isReload) {
    }

    @Override
    public void makeHidden(Player viewer, boolean isReload) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
    }
}