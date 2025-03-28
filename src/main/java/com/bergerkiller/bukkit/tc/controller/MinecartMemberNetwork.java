package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.RunOnceTask;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.EntityTracker;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.generated.net.minecraft.server.level.EntityTrackerEntryHandle;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Network controller that is used by Traincarts to override the way trains are synchronized
 * to players.
 *
 * @deprecated This class is on the way out. Please use
 * {@link com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember AttachmentControllerMember}
 * instead.
 */
@Deprecated
public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> {
    private final TrainCarts plugin;
    private final RunOnceTask verifyExistsCheck;
    private MinecartMember<?> member = null;
    private boolean isInProcessOfSpawning = false;

    public MinecartMemberNetwork(TrainCarts plugin) {
        this.plugin = plugin;
        this.verifyExistsCheck = new RunOnceTask(plugin) {
            @Override
            public void run() {
                // If the entity backing this controller does not exist,
                // remove this tracker entry from the server.
                // It is not clear why this happens sometimes.
                // Do this in another tick to avoid concurrent modification exceptions.
                MinecartMember<?> member = getMember();
                if (isInProcessOfSpawning) {
                    return;
                }
                if (entity != null && (member == null || !member.getEntity().isSpawned())) {
                    World world = entity.getWorld();
                    if (world != null) {
                        EntityTracker tracker = WorldUtil.getTracker(world);
                        EntityTrackerEntryHandle entry = tracker.getEntry(entity.getEntity());
                        if (entry != null && getHandle() == entry.getRaw()) {
                            tracker.stopTracking(entity.getEntity());
                        }
                    }
                }
            }
        };
    }

    /**
     * Sets whether the underlying Member is in the process of being spawned. In that case it skips the usual
     * checks for whether the entity is still alive/spawned.
     *
     * @param spawning True if in process of spawning
     */
    public void setInProcessOfSpawning(boolean spawning) {
        isInProcessOfSpawning = spawning;
    }

    /**
     * Gets the root attachment, representing the (attachments) based model
     * 
     * @return root attachment
     */
    public Attachment getRootAttachment() {
        MinecartMember<?> member = this.getMember();
        return (member != null && member.getAttachments().isAttached())
                ? member.getAttachments().getRootAttachment() : null;
    }

    /**
     * Finds the seat occupied by a passenger. If the passenger is not yet inside a seat
     * of this cart currently, then the best matching seat that the passenger would enter
     * is returned instead. This is important because the seat assignment occurs
     * slightly delayed.
     * 
     * @param passenger
     * @return seat, null if this cart has no seats for the passenger to enter
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
        this.verifyExistsCheck.cancel();
    }

    @Override
    protected void onPassengersChanged(List<Entity> oldPassengers, List<Entity> newPassengers) {
        if (this.member != null) {
            this.member.getAttachments().onPassengersChanged(oldPassengers, newPassengers);
        }
    }

    @Override
    public void makeVisible(Player viewer) {
        //super.makeVisible(viewer);

        verifyExistsCheck.start();

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
