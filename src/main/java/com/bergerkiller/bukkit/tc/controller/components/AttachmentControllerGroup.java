package com.bergerkiller.bukkit.tc.controller.components;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModelStore;
import com.bergerkiller.bukkit.tc.utils.SetCallbackCollector;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.attachments.helper.AttachmentUpdateTransformHelper;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Manages the attachments updates of all the carts of a train
 */
public class AttachmentControllerGroup
        implements SavedAttachmentModelStore.ModelUsing, AttachmentNameLookup.Supplier
{
    public static final int ABSOLUTE_UPDATE_INTERVAL = 200;
    public static final int MOVEMENT_UPDATE_INTERVAL = 3;
    private final MinecartGroup group;
    private int movementCounter;
    private int ticksSinceLocationSync = 0;
    private SoftReference<AttachmentNameLookup> cachedByNameLookup = new SoftReference<>(null);

    public AttachmentControllerGroup(MinecartGroup group) {
        this.group = group;
    }

    public MinecartGroup getGroup() {
        return this.group;
    }

    public void syncPrePositionUpdate(AttachmentUpdateTransformHelper updater) {
        for (MinecartMember<?> member : this.group) {
            member.getAttachments().syncPrePositionUpdate(updater);
        }
    }

    public void syncPositionAbsolute() {
        this.ticksSinceLocationSync = 0;
        /* Timings: performMovement  (Network) */
        {
            for (MinecartMember<?> member : group) {
                member.getAttachments().syncMovement(true);
            }
        }
    }

    public void syncPostPositionUpdate() {
        /* Timings: performTick  (Network) */
        {
            for (MinecartMember<?> member : this.group) {
                member.getAttachments().syncPostPositionUpdate();
            }
        }

        /* Timings: performMovement  (Network) */
        {
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
                        if (member.isUnloaded()) {
                            continue;
                        }
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

    /**
     * Synchronizes all attachments by first de-spawning and then re-spawning
     * all attachments to all viewers of the train.
     */
    public void syncRespawn() {
        List<RespawnedMember> members = new ArrayList<>(group.size());
        for (MinecartMember<?> member : group) {
            members.add(new RespawnedMember(member));
        }

        members.forEach(RespawnedMember::hide);
        group.getTrainCarts().getTrainUpdateController().syncPositions(Collections.singletonList(group));
        members.forEach(RespawnedMember::show);
    }

    @Override
    public void getUsedModels(SetCallbackCollector<SavedAttachmentModel> collector) {
        for (MinecartMember<?> member : group) {
            member.getAttachments().getUsedModels(collector);
        }
    }

    @Override
    public AttachmentNameLookup getNameLookup() {
        AttachmentNameLookup cached = cachedByNameLookup.get();
        if (cached == null || !cached.isValid()) {
            final ArrayList<AttachmentNameLookup> components = new ArrayList<>(group.size());
            for (MinecartMember<?> member : group) {
                components.add(member.getAttachments().getNameLookup());
            }
            cachedByNameLookup = new SoftReference<>(cached = AttachmentNameLookup.merge(components));
        }
        return cached;
    }

    /**
     * Called when MinecartMember instances are added or removed from the parent group.
     * Invalidates {@link #getNameLookup()}
     */
    public void notifyGroupCompositionChanged() {
        AttachmentNameLookup cached = cachedByNameLookup.get();
        if (cached != null) {
            cached.invalidate();
        }
    }

    private static class RespawnedMember {
        public final MinecartMember<?> member;
        private List<Player> players;

        public RespawnedMember(MinecartMember<?> member) {
            this.member = member;
            this.players = Collections.emptyList();
        }

        public void hide() {
            synchronized (member.getAttachments()) {
                players = new ArrayList<>(member.getAttachments().getViewers());
                member.getAttachments().makeHiddenForAll();
            }
        }

        public void show() {
            synchronized (member.getAttachments()) {
                for (Player viewer : this.players) {
                    if (!member.getAttachments().isViewer(viewer)) {
                        member.getAttachments().makeVisible(viewer);
                    }
                }
            }
        }
    }
}
