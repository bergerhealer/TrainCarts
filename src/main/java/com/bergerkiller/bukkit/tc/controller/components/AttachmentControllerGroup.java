package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.attachments.helper.AttachmentUpdateTransformHelper;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Manages the attachments updates of all the carts of a train
 */
public class AttachmentControllerGroup {
    public static final int ABSOLUTE_UPDATE_INTERVAL = 200;
    public static final int MOVEMENT_UPDATE_INTERVAL = 3;
    private final MinecartGroup group;
    private int movementCounter;
    private int ticksSinceLocationSync = 0;

    public AttachmentControllerGroup(MinecartGroup group) {
        this.group = group;
    }

    public MinecartGroup getGroup() {
        return this.group;
    }

    public void syncPrePositionUpdate(AttachmentUpdateTransformHelper updater) {
        for (MinecartMember<?> member : this.group) {
            AttachmentControllerMember controller = member.getAttachments();
            controller.syncPrePositionUpdate();
            updater.start(controller.getRootAttachment(), controller.getLiveTransform());
        }
    }

    public void syncPostPositionUpdate() {
        try (Timings t = TCTimings.NETWORK_PERFORM_TICK.start()) {
            for (MinecartMember<?> member : this.group) {
                member.getAttachments().syncPostPositionUpdate();
            }
        }

        try (Timings t = TCTimings.NETWORK_PERFORM_MOVEMENT.start()) {
            // Sync movement every now and then
            boolean isUpdateTick = false;
            if (++movementCounter >= MOVEMENT_UPDATE_INTERVAL) {
                movementCounter = 0;
                isUpdateTick = true;
            }

            // Synchronize to the clients
            if (++this.ticksSinceLocationSync > ABSOLUTE_UPDATE_INTERVAL) {
                this.ticksSinceLocationSync = 0;

                // Perform absolute updates
                for (MinecartMember<?> member : group) {
                    member.getAttachments().syncMovement(true);
                }
            } else {
                // Perform relative updates
                boolean needsSync = isUpdateTick;
                if (!needsSync) {
                    for (MinecartMember<?> member : group) {
                        if (member.getEntity().isPositionChanged() || member.getEntity().getDataWatcher().isChanged()) {
                            needsSync = true;
                            break;
                        }
                    }
                }
                if (needsSync) {
                    // Perform actual updates
                    for (MinecartMember<?> member : group) {
                        member.getAttachments().syncMovement(false);
                    }
                }
            }
        }
    }
}
