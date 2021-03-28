package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.EntityTracker;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Network controller that is used by Traincarts to override the way trains are synchronized
 * to players.
 *
 * @deprecated This class is on the way out. Please use {@link AttachmentControllerMember} instead.
 */
@Deprecated
public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> {
    private MinecartMember<?> member = null;

    /**
     * Gets the root attachment, representing the (attachments) based model
     * 
     * @return root attachment
     */
    public Attachment getRootAttachment() {
        MinecartMember<?> member = this.getMember();
        return (member == null) ? null : member.getAttachments().getRootAttachment();
    }

    /**
     * Finds the seat occupied by a passenger. IF the passenger is not inside a seat
     * of this cart currently, then the best matching seat is returned instead.
     * 
     * @param passenger
     * @return seat, null if this cart has no seats
     */
    public CartAttachmentSeat findSeat(Entity passenger) {
        MinecartMember<?> member = this.getMember();
        return (member == null) ? null : member.getAttachments().findSeat(passenger);
    }

    public Matrix4x4 getLiveTransform() {
        MinecartMember<?> member = this.getMember();
        return (member == null) ? null : member.getAttachments().getLiveTransform();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        this.getMember().getAttachments().onAttached();
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (this.member != null) {
            this.member.getAttachments().onDetached();
        }
    }

    @Override
    protected void onSyncPassengers(Player viewer, List<Entity> oldPassengers, List<Entity> newPassengers) {
        if (this.member != null) {
            this.member.getAttachments().onSyncPassengers(viewer, oldPassengers, newPassengers);
        }
    }

    @Override
    public void makeVisible(Player viewer) {
        //super.makeVisible(viewer);

        // If the entity backing this controller does not exist,
        // remove this tracker entry from the server.
        // It is not clear why this happens sometimes.
        // Do this in another tick to avoid concurrent modification exceptions.
        MinecartMember<?> member = this.getMember();
        if (member == null || !member.getEntity().isSpawned()) {
            World world = (this.entity == null) ? null : this.entity.getWorld();
            if (world != null) {
                CommonUtil.nextTick(new Runnable() {
                    @Override
                    public void run() {
                        if (entity != null) {
                            EntityTracker tracker = WorldUtil.getTracker(world);
                            EntityTrackerEntryHandle entry = tracker.getEntry(entity.getEntity());
                            if (entry != null && getHandle() == entry.getRaw()) {
                                tracker.stopTracking(entity.getEntity());
                            }
                        }
                    }
                });
            }
            return;
        }

        // Delegate to the attachment controller
        member.getAttachments().makeVisible(viewer);
    }

    @Override
    public void makeHidden(Player viewer, boolean instant) {
        //super.makeHidden(viewer, instant);

        // Delegate to the attachment controller
        MinecartMember<?> member = this.getMember();
        if (member != null) {
            member.getAttachments().makeHidden(viewer);
        }
    }

    @Override
    public void onTick() {
        // Sync self if member is unloaded
        MinecartMember<?> member = this.getMember();
        if (member != null && member.isUnloaded()) {
            member.getAttachments().syncUnloaded();
        }

        // Sync position, we don't really use this anyway
        this.locSynched.set(this.locLive);

        // Sync passengers
        this.syncPassengers();
    }

    public MinecartMember<?> getMember() {
        if (this.entity == null) {
            this.member = null;
        } else if (this.member == null) {
            this.member = this.entity.getController(MinecartMember.class);
        }
        return this.member;
    }
}
