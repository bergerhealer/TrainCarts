package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModelOwner;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;

/**
 * Holds the attachment tree of a single MinecartMember
 * and performs the various stages of network synchronization
 * for them.
 *
 * This component translates the model configuration
 * into attachment controllers. It automatically updates the
 * controllers when this configuration changes.
 */
public class AttachmentControllerMember implements AttachmentModelOwner, AttachmentManager {
    private final MinecartMember<?> member;
    private Attachment rootAttachment;
    private List<CartAttachmentSeat> seatAttachments = new ArrayList<CartAttachmentSeat>();
    private Map<Player, SeatHint> seatHints = new HashMap<Player, SeatHint>();
    private Set<Player> viewers = new HashSet<Player>();
    protected final ToggledState networkInvalid = new ToggledState();

    private long animationCurrentTime = 0;
    private double animationDeltaTime = 0.0;

    public AttachmentControllerMember(MinecartMember<?> member) {
        this.member = member;
    }

    // Called by NetworkController
    public synchronized void onAttached() {
        this.animationCurrentTime = System.currentTimeMillis();
        this.animationDeltaTime = 0.0;
        this.member.getProperties().getModel().addOwner(this);
    }

    // Called by NetworkController
    public synchronized void onDetached() {
        if (this.rootAttachment != null) {
            HelperMethods.perform_onDetached(this.rootAttachment);
        }
        this.member.getProperties().getModel().removeOwner(this);
    }

    /**
     * Gets the MinecartMember this Attachment Controller is for
     *
     * @return owner member
     */
    public MinecartMember<?> getMember() {
        return this.member;
    }

    /**
     * Called by MinecartGroup to fix a problem with a not-set network controller
     */
    @SuppressWarnings("deprecation")
    public void fixNetworkController() {
        if (this.networkInvalid.clear()) {
            EntityNetworkController<?> controller = this.member.getEntity().getNetworkController();
            if (!(controller instanceof com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork)) {
                this.member.getEntity().setNetworkController(new com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork());
            }
        }
    }

    /**
     * Gets the root attachment, representing the (attachments) based model
     * 
     * @return root attachment
     */
    public Attachment getRootAttachment() {
        // Set attachment to a fallback if for whatever reason it is null
        if (this.rootAttachment == null) {
            this.onModelChanged(AttachmentModel.getDefaultModel(this.member.getEntity().getType()));
        }
        // Return
        return this.rootAttachment;
    }

    /**
     * Gets the delta time in seconds of the current animation frame for the current tick
     * 
     * @return animation delta time in seconds
     */
    public double getAnimationDeltaTime() {
        return this.animationDeltaTime;
    }

    /**
     * Called by the NetworkController to synchronize the passengers of the vehicle.
     * This puts entities into seats.
     *
     * @param viewer
     * @param oldPassengers
     * @param newPassengers
     */
    public synchronized void onSyncPassengers(Player viewer, List<Entity> oldPassengers, List<Entity> newPassengers) {
        // Clear passengers that have ejected
        for (CartAttachmentSeat seat : this.seatAttachments) {
            Entity oldPassenger = seat.getEntity();
            if (!newPassengers.contains(oldPassenger)) {
                seat.setEntity(null);
            }
        }

        // Add passengers that have entered
        for (Entity newPassenger : newPassengers) {
            if (findCurrentSeatOfEntity(newPassenger) == null) {
                CartAttachmentSeat newSeat = findNewSeatOfEntity(newPassenger);
                if (newSeat != null) {
                    newSeat.setEntity(newPassenger);
                }
            }
        }
    }

    /**
     * Handles switching seats (when a player clicks on a different seat while already seated
     * in the same cart)
     * 
     * @param passenger
     */
    public boolean changeSeats(Entity passenger) {
        // See what seat attachment this passenger currently occupies
        CartAttachmentSeat old_seat = findCurrentSeatOfEntity(passenger);
        CartAttachmentSeat new_seat = findNewSeatOfEntity(passenger);
        if (old_seat == null || new_seat == null || old_seat == new_seat) {
            return false;
        }

        old_seat.setEntity(null);
        new_seat.setEntity(passenger);
        return true;
    }

    private CartAttachmentSeat findNewSeatOfEntity(Entity passenger) {
        SeatHint seatHint = this.seatHints.get(passenger);
        List<CartAttachmentSeat> sortedSeats;
        if (seatHint != null && !seatHint.isExpired()) {
            // Use seat hint
            sortedSeats = seatHint.seats;
        } else {
            // Get the LAST known position
            // We can not use current, because that is set to the location of the Minecart
            Vector position = new Vector();
            {
                EntityHandle handle = EntityHandle.fromBukkit(passenger);
                position.setX(handle.getLastX());
                position.setY(handle.getLastY());
                position.setZ(handle.getLastZ());
            }

            // Find seats sorted by closeness to the Entity
            sortedSeats = this.getSeatsClosestToPosition(position);
        }

        // Find first free seat, or fail
        for (CartAttachmentSeat seat : sortedSeats) {
            if (seat.canEnter(passenger)) {
                return seat;
            }
        }
        return null;
    }

    private CartAttachmentSeat findCurrentSeatOfEntity(Entity passenger) {
        for (CartAttachmentSeat seat : this.seatAttachments) {
            if (seat.getEntity() == passenger) {
                return seat;
            }
        }
        return null;
    }

    private List<CartAttachmentSeat> getSeatsClosestToPosition(Vector position) {
        if (this.seatAttachments.size() <= 1) {
            return Collections.unmodifiableList(this.seatAttachments);
        }

        ArrayList<CartAttachmentSeat> result = new ArrayList<CartAttachmentSeat>(this.seatAttachments);
        Collections.sort(result, (o1, o2) -> {
            double d1 = o1.getTransform().toVector().distanceSquared(position);
            double d2 = o2.getTransform().toVector().distanceSquared(position);
            return Double.compare(d1, d2);
        });
        return Collections.unmodifiableList(result);
    }

    private List<CartAttachmentSeat> getSeatsClosestToHitTest(Location eyeLocation) {
        if (this.seatAttachments.size() <= 1) {
            return Collections.unmodifiableList(this.seatAttachments);
        }

        Matrix4x4 cameraTransform = new Matrix4x4();
        cameraTransform.translateRotate(eyeLocation);
        cameraTransform.invert();

        ArrayList<CartAttachmentSeat> result = new ArrayList<CartAttachmentSeat>(this.seatAttachments);
        Collections.sort(result, (o1, o2) -> {
            double d1 = getViewDistance(cameraTransform, o1.getTransform().toVector());
            double d2 = getViewDistance(cameraTransform, o2.getTransform().toVector());
            return Double.compare(d1, d2);
        });
        return Collections.unmodifiableList(result);
    }

    private static double getViewDistance(Matrix4x4 cameraTransform, Vector pos) {
        pos = pos.clone();
        cameraTransform.transformPoint(pos);
        if (pos.getZ() >= 1e-6 && pos.getZ() <= 5.0) {
            return Math.sqrt(pos.getX()*pos.getX()+pos.getY()*pos.getY());
        } else {
            return 5.0 + pos.length();
        }
    }

    /**
     * Finds the seat occupied by a passenger. IF the passenger is not inside a seat
     * of this cart currently, then the best matching seat is returned instead.
     * 
     * @param passenger
     * @return seat, null if this cart has no seats
     */
    public synchronized CartAttachmentSeat findSeat(Entity passenger) {
        if (this.seatAttachments.isEmpty()) {
            return null;
        }
        for (CartAttachmentSeat seat : this.seatAttachments) {
            if (seat.getEntity() == passenger) {
                return seat;
            }
        }

        return getSeatsClosestToPosition(passenger.getLocation().toVector()).get(0);
    }

    // Called from NetworkController
    public synchronized void makeVisible(Player viewer) {
        viewers.add(viewer);
        HelperMethods.makeVisibleRecursive(this.getRootAttachment(), true, viewer);
    }

    // Called from NetworkController
    public synchronized void makeHidden(Player viewer) {
        //super.makeHidden(viewer, instant);

        viewers.remove(viewer);
        if (this.rootAttachment != null) {
            HelperMethods.makeHiddenRecursive(this.rootAttachment, true, viewer);
        }
    }

    /**
     * Gets the viewers viewing this cart's attachments
     *
     * @return viewers
     */
    public Set<Player> getViewers() {
        return this.viewers;
    }

    /**
     * Gets whether a given player is viewing the attachments of this cart
     *
     * @param player Viewer
     * @return True if visible to this player
     */
    public synchronized boolean isViewer(Player player) {
        return this.viewers.contains(player);
    }

    /**
     * Gets whether an Entity Id specified is used by an attachment
     * 
     * @param entityId
     * @return True if an attachment uses this Entity Id
     */
    public synchronized boolean isAttachment(int entityId) {
        return HelperMethods.findAttachmentWithEntityId(this.rootAttachment, entityId) != null;
    }

    /**
     * Checks what seat a player is looking at, and stores that seat for later entering operations.
     * The information is stored for at most 2 ticks before it is invalidated.
     * 
     * @param player
     */
    public void storeSeatHint(Player player) {
        this.seatHints.put(player, new SeatHint(this.getSeatsClosestToHitTest(player.getEyeLocation())));
    }

    /**
     * Called by the network controller if this MinecartMember is unloaded.
     * It should do a special kind of no-movement no-logic sync.
     */
    public void syncUnloaded() {
        this.syncMovement(false);
    }

    /**
     * Attachment controller isn't updated when the entity is unloaded or dead
     *
     * @return True if cart is unloaded or dead
     */
    public boolean isUnloadedOrDead() {
        return this.member.isUnloaded() || this.member.getEntity().isDead();
    }

    /**
     * Updates to perform every tick before the attachment positions are updated
     */
    public void syncPrePositionUpdate() {
        // Cleanup seat hints (avoid memory leaks)
        if (!this.seatHints.isEmpty()) {
            Iterator<SeatHint> iter = this.seatHints.values().iterator();
            while (iter.hasNext()) {
                if (iter.next().isExpired()) {
                    iter.remove();
                }
            }
        }

        // Make sure not dead/unloaded
        if (this.isUnloadedOrDead()) {
            return;
        }

        // Make sure a model is initialized
        this.getRootAttachment();

        // Update animation delta time, used during attachment updates
        if (TCConfig.animationsUseTickTime) {
            this.animationDeltaTime = 1.0 / 20.0;
        } else {
            long time_now = System.currentTimeMillis();
            this.animationDeltaTime = 0.001 * (double) (time_now - this.animationCurrentTime);
            this.animationCurrentTime = time_now;
        }
    }

    /**
     * Updates to perform every tick after the attachment positions are updated
     */
    public void syncPostPositionUpdate() {
        // Make sure not dead/unloaded
        if (this.isUnloadedOrDead()) {
            return;
        }

        HelperMethods.perform_onTick(this.rootAttachment);
    }

    @SuppressWarnings("deprecation")
    public void syncMovement(boolean absolute) {
        // Make sure not dead/unloaded
        if (this.isUnloadedOrDead()) {
            return;
        }

        // Check network controller is still set for the cart
        // If not, fix that up
        if (absolute && !(((Object) this.member.getEntity().getNetworkController())
                instanceof com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork)
        ) {
            this.networkInvalid.set();
        }

        // Reset
        this.member.getEntity().setPositionChanged(false);
        this.member.getEntity().setVelocityChanged(false);

        // Perform actual movement, which sends movement update packets
        if (this.rootAttachment != null) {
            HelperMethods.perform_onMove(this.rootAttachment, absolute);
        }
    }

    /**
     * Gets the 4x4 transformation matrix for the central position the
     * cart has. All attachments are positioned relative to this.
     *
     * @return live position transformation matrix
     */
    public Matrix4x4 getLiveTransform() {
        // Combine translation and rotation information into a 4x4 matrix
        Matrix4x4 transform = new Matrix4x4();
        transform.translate(this.member.getWheels().getPosition());
        transform.rotate(this.member.getOrientation());
        transform.rotateZ(this.member.getRoll());
        return transform;
    }

    private void discoverSeats(Attachment attachment) {
        if (attachment instanceof CartAttachmentSeat) {
            this.seatAttachments.add((CartAttachmentSeat) attachment);
        }
        for (Attachment child : attachment.getChildren()) {
            discoverSeats(child);
        }
    }

    /**
     * Gets the number of available seat attachments a passenger can enter
     * 
     * @param passenger
     * @return available seat count
     */
    public int getAvailableSeatCount(Entity passenger) {
        int count = 0;
        for (CartAttachmentSeat seat : this.seatAttachments) {
            if (seat.canEnter(passenger)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public World getWorld() {
        return this.member.getWorld();
    }

    @Override
    public synchronized void onModelChanged(AttachmentModel model) {
        // Store the positions of the players in the previous seats
        // This is used later to re-assign the passengers to seats when the model is changed
        Map<Entity, Vector> oldSeatPositions = new HashMap<Entity, Vector>();
        for (CartAttachmentSeat seat : this.seatAttachments) {
            Entity oldEntity = seat.getEntity();
            if (oldEntity != null) {
                oldSeatPositions.put(oldEntity, seat.getTransform().toVector());
            }
        }

        //TODO: Detect when only a single element is changed, and only update that element
        // This allows for a cleaner update when repositioning/etc.

        // Detach old attachments - after this viewers see nothing anymore
        if (this.rootAttachment != null) {
            for (Player oldViewer : this.getViewers()) {
                HelperMethods.makeHiddenRecursive(this.rootAttachment, true, oldViewer);
            }
            HelperMethods.perform_onDetached(this.rootAttachment);
            this.rootAttachment = null;
        }

        // Attach new attachments - after this viewers see everything but passengers are not 'in'
        this.rootAttachment = this.createAttachment(model.getConfig());
        HelperMethods.perform_onAttached(this.rootAttachment);
        TrainCarts.plugin.getTrainUpdateController().computeAttachmentTransform(
                this.rootAttachment, this.getLiveTransform());

        this.seatAttachments.clear();
        this.discoverSeats(this.rootAttachment);

        for (Player viewer : this.getViewers()) {
            HelperMethods.makeVisibleRecursive(this.rootAttachment, true, viewer);
        }

        // Let all passengers re-enter us
        // For this, we must find suitable Seat attachments in the tree
        List<Entity> remainingPassengers = new ArrayList<Entity>(this.member.getEntity().getPassengers());
        while (!remainingPassengers.isEmpty()) {
            Entity entity = remainingPassengers.get(0);
            Vector position = oldSeatPositions.get(entity);
            if (position == null) {
                position = entity.getLocation().toVector();
            }
            boolean foundSeat = false;
            List<CartAttachmentSeat> seats = this.getSeatsClosestToPosition(position);
            for (CartAttachmentSeat seat : seats) {
                if (seat.getEntity() == null) {
                    seat.setEntity(entity);
                    remainingPassengers.remove(0);
                    foundSeat = true;
                    break;
                }
            }
            if (!foundSeat) {
                break;
            }
        }

        // It can happen passengers have no seat now. Eject them.
        //TODO!
        
        //model.log();
    }

    @Override
    public synchronized void onModelNodeChanged(AttachmentModel model, int[] targetPath, ConfigurationNode config) {
        // Find the child. If not found, just refresh the entire model.
        Attachment attachment = this.getRootAttachment().findChild(targetPath);
        if (attachment == null) {
            this.onModelChanged(model);
            return;
        }

        // Figure out the attachment type from configuration
        // If it is not the same as what it already is, refresh the entire model
        AttachmentType oldAttachmentType = this.getTypeRegistry().fromConfig(attachment.getConfig());
        AttachmentType newAttachmentType = this.getTypeRegistry().fromConfig(config);
        if (newAttachmentType == null || oldAttachmentType != newAttachmentType) {
            this.onModelChanged(model);
            return;
        }

        // Reload the configuration of just this one attachment
        attachment.getInternalState().onLoad(this.getClass(), newAttachmentType, config);
        attachment.onLoad(config);

        // TODO: Maybe only update from the changed attachment onwards?
        TrainCarts.plugin.getTrainUpdateController().computeAttachmentTransform(
                this.rootAttachment, this.getLiveTransform());
    }

    // Information stored when a player interacts with a seat trying to enter it
    // Seats in order of importance are stored
    private static class SeatHint {
        public final List<CartAttachmentSeat> seats;
        public final int expire;

        public SeatHint(List<CartAttachmentSeat> seats) {
            this.seats = seats;
            this.expire = CommonUtil.getServerTicks() + 2;
        }

        public boolean isExpired() {
            return CommonUtil.getServerTicks() >= this.expire;
        }
    }
}
