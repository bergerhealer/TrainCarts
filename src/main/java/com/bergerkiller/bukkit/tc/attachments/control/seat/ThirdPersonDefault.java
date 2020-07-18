package com.bergerkiller.bukkit.tc.attachments.control.seat;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;

public class ThirdPersonDefault {
    private final CartAttachmentSeat seat;

    public ThirdPersonDefault(CartAttachmentSeat seat) {
        this.seat = seat;
    }

    public void makeVisible(Player viewer) {
        seat.seated.makeVisible(viewer, true);
    }

    public void makeHidden(Player viewer) {
        seat.seated.makeHidden(viewer, true);
    }
}
