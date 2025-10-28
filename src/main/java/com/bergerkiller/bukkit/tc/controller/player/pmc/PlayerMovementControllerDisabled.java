package com.bergerkiller.bukkit.tc.controller.player.pmc;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import org.bukkit.util.Vector;

/**
 * No-op controller. For viewers that are no longer online or when TrainCarts
 * is disabled.
 */
final class PlayerMovementControllerDisabled extends PlayerMovementController {
    protected PlayerMovementControllerDisabled(ControllerType type, AttachmentViewer viewer) {
        super(type, viewer);
    }

    @Override
    public HorizontalPlayerInput horizontalInput() {
        return HorizontalPlayerInput.NONE;
    }

    @Override
    public VerticalPlayerInput verticalInput() {
        return VerticalPlayerInput.NONE;
    }

    @Override
    protected void syncPosition(Vector position) {
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }
}
