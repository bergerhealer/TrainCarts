package com.bergerkiller.bukkit.tc.attachments.control.seat;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;

/**
 * Used when no player is sitting inside the seat
 */
public class FirstPersonViewNone extends FirstPersonView {

    public FirstPersonViewNone(CartAttachmentSeat seat) {
        super(seat, null);
    }

    @Override
    public void makeVisible(AttachmentViewer viewer, boolean isReload) {
    }

    @Override
    public void makeHidden(AttachmentViewer viewer, boolean isReload) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
    }
}