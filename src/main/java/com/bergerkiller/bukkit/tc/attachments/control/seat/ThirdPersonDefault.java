package com.bergerkiller.bukkit.tc.attachments.control.seat;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;

public class ThirdPersonDefault {
    private final CartAttachmentSeat seat;

    public ThirdPersonDefault(CartAttachmentSeat seat) {
        this.seat = seat;
    }

    public void makeVisible(AttachmentViewer viewer) {
        if (!seat.debug.isSeatedEntityHiddenBecauseOfPreview(viewer.getPlayer())) {
            seat.seated.makeVisible(viewer);
        }
    }

    public void makeHidden(AttachmentViewer viewer) {
        if (!seat.debug.isSeatedEntityHiddenBecauseOfPreview(viewer.getPlayer())) {
            seat.seated.makeHidden(viewer);
        }
    }
}
