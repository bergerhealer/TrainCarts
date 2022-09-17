package com.bergerkiller.bukkit.tc.controller.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

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
import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TCSeatChangeListener;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModelOwner;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.helper.AttachmentUpdateTransformHelper;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatChangeEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatEnterEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatExitEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatChangeEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatEnterEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatExitEvent;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

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
    private List<CartAttachmentSeat> seatAttachments = Collections.emptyList();
    private List<Attachment> flattenedAttachments = Collections.emptyList();
    private Map<Entity, SeatHint> seatHints = new HashMap<Entity, SeatHint>();
    private final Map<Player, AttachmentViewer> viewers = new IdentityHashMap<>();
    protected final ToggledState networkInvalid = new ToggledState();
    private boolean attached = false;
    private boolean hidden = false;

    private long animationCurrentTime = 0;
    private double animationDeltaTime = 0.0;

    public AttachmentControllerMember(MinecartMember<?> member) {
        this.member = member;
    }

    /**
     * Gets whether this attachment controller is attached. If not attached, it
     * is not loaded and no attachments exist.
     *
     * @return True if attached
     */
    public boolean isAttached() {
        return this.attached;
    }

    // Called by NetworkController
    public synchronized void onAttached() {
        this.animationCurrentTime = System.currentTimeMillis();
        this.animationDeltaTime = 0.0;
        this.attached = true;
        this.member.getProperties().getModel().addOwner(this);
    }

    // Called by NetworkController
    public synchronized void onDetached() {
        this.attached = false;
        if (this.rootAttachment != null) {
            detachRootAttachment();
        }
        this.member.getProperties().getModel().removeOwner(this);
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean hidden) {
        if (this.hidden == hidden) {
            return;
        }

        this.hidden = hidden;
        if (hidden) {
            // Despawn everything
            if (this.rootAttachment != null) {
                makeHiddenForAll();
                detachRootAttachment();
            }
        } else {
            // Spawn in for the first time. Done by retrieving the model
            if (this.rootAttachment == null) {
                onModelChanged(this.member.getProperties().getModel());
            }
        }
    }

    private void detachRootAttachment() {
        try {
            ListIterator<Attachment> iter = this.flattenedAttachments.listIterator(this.flattenedAttachments.size());
            while (iter.hasPrevious()) {
                HelperMethods.perform_onDetached_single(iter.previous());
            }
        } finally {
            this.rootAttachment = null;
            this.flattenedAttachments = Collections.emptyList();
            this.seatAttachments = Collections.emptyList();
        }
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
     * Gets the root attachment, representing the (attachments) based model.
     * Throws an {@link IllegalStateException} if not currently loaded, use
     * {@link #isAttached()} to check for this.
     * 
     * @return root attachment
     */
    public Attachment getRootAttachment() {
        // Error
        if (!this.attached) {
            throw new IllegalStateException("This member has no network presence and was probably unloaded");
        }
        if (this.hidden) {
            throw new IllegalStateException("This member's attachments are temporarily hidden");
        }
        // Set attachment to a fallback if for whatever reason it is null
        if (this.rootAttachment == null) {
            this.onModelChanged(AttachmentModel.getDefaultModel(this.member.getEntity().getType()));
        }
        // Return
        return this.rootAttachment;
    }

    /**
     * Gets a full list of all attachments currently displayed for this member.
     * If the member is unloaded, returns an empty list.
     *
     * @return flattened list of all attachments of this member
     */
    public List<Attachment> getAllAttachments() {
        return this.flattenedAttachments;
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
     * @param oldPassengers
     * @param newPassengers
     */
    public synchronized void onPassengersChanged(List<Entity> oldPassengers, List<Entity> newPassengers) {
        // Clear passengers that have ejected
        for (CartAttachmentSeat seat : this.seatAttachments) {
            Entity oldPassenger = seat.getEntity();
            if (!newPassengers.contains(oldPassenger)) {
                seat.setEntity(null);
            }
        }

        // Add passengers that have entered
        for (Entity newPassenger : newPassengers) {
            boolean isInSeat = false;
            for (CartAttachmentSeat seat : this.seatAttachments) {
                if (seat.getEntity() == newPassenger) {
                    isInSeat = true;
                    break;
                }
            }
            if (!isInSeat) {
                CartAttachmentSeat newSeat = findNewSeatForEntity(newPassenger);
                if (newSeat != null) {
                    newSeat.setEntity(newPassenger);
                }
            }
        }
    }

    /**
     * Handles switching seats (when a player clicks on a different seat while already seated
     * in the same cart). Events are fired, which could be cancelled by other plugins.<br>
     * <br>
     * The seat to enter is picked by looking where the passenger is currently looking.
     *
     * @param passenger
     * @return True if the seat was changed
     */
    public synchronized boolean changeSeatsLookingAt(Entity passenger) {
        return changeSeats(passenger, findNewSeatForEntity(passenger), true);
    }

    /**
     * Handles switching seats (when a player clicks on a different seat while already seated
     * in the same cart). Events are fired, which could be cancelled by other plugins.
     *
     * @param passenger The entity that wants to change seat
     * @param new_seat The new seat (of this controller) to enter
     * @param playerInitiated Whether this seat change was player-initiated or not
     * @return True if the seat was changed
     */
    public synchronized boolean changeSeats(Entity passenger, CartAttachmentSeat new_seat, boolean playerInitiated) {
        if (new_seat != null && new_seat.getController() != this) {
            throw new IllegalArgumentException("Cannot change seats to a seat of another member");
        }

        // See what seat attachment this passenger currently occupies
        CartAttachmentSeat old_seat = this.findSeat(passenger);
        if (old_seat == null || new_seat == null || old_seat == new_seat) {
            return false;
        }

        return handleSeatChange(passenger, old_seat, new_seat, playerInitiated);
    }

    /**
     * Handles all the logic for changing a passenger from one member (and seat) to another.
     * This will fire the appropriate member seat enter/exit/change events as required.
     *
     * @param passenger Passenger to change seats
     * @param old_seat Old seat, null to not exit a previous seat
     * @param new_seat New seat, null to not enter a new seat
     * @param isPlayerInitiated Whether this seat change was initiated by the player (clicking, sneak)
     * @return True if the seat change was successful, false if it was (partially) cancelled
     */
    public static boolean handleSeatChange(
            Entity passenger,
            CartAttachmentSeat old_seat,
            CartAttachmentSeat new_seat,
            boolean isPlayerInitiated
    ) {
        if (old_seat == new_seat) {
            return false;
        }

        Location seatPosition = null, exitPosition = null;
        if (old_seat != null && new_seat != null) {
            // Fire event for changing from one seat to another
            MemberBeforeSeatChangeEvent event = new MemberBeforeSeatChangeEvent(old_seat, new_seat, passenger, isPlayerInitiated);
            seatPosition = event.getSeatPosition();
            if (CommonUtil.callEvent(event).isCancelled()) {
                return false;
            }
            new_seat = event.getEnteredSeat();
            exitPosition = event.getExitPosition();
            if (old_seat == new_seat) {
                return false;
            }
        } else if (old_seat != null) {
            // Fire an event to exit an old seat (eject)
            seatPosition = old_seat.getPosition(passenger);
            exitPosition = old_seat.getEjectPosition(passenger);
            MemberBeforeSeatExitEvent event = new MemberBeforeSeatExitEvent(old_seat, passenger, seatPosition, exitPosition, isPlayerInitiated);
            if (CommonUtil.callEvent(event).isCancelled()) {
                return false;
            }
            exitPosition = event.getExitPosition();
        } else if (new_seat != null) {
            // Fire an event to enter a new seat
            MemberBeforeSeatEnterEvent event = new MemberBeforeSeatEnterEvent(new_seat, passenger, isPlayerInitiated,
                    false, /* was seat change */
                    true /* is a vehicle change */ );
            if (CommonUtil.callEvent(event).isCancelled()) {
                return false;
            }
            new_seat = event.getSeat();
        } else {
            return false;
        }

        // If same member, simply update the seat attachments
        // No Bukkit vehicle enter/exit events fire in that case
        if (old_seat != null && new_seat != null && old_seat.getMember() == new_seat.getMember()) {
            old_seat.setEntity(null);
            new_seat.setEntity(passenger);

            // Fire post-seat-change events
            CommonUtil.callEvent(new MemberSeatChangeEvent(old_seat, new_seat, passenger, seatPosition, exitPosition, isPlayerInitiated));
            CommonUtil.callEvent(new MemberSeatEnterEvent(new_seat, passenger, isPlayerInitiated, true, false));
            return true;
        }

        // Different member. Player will have to be removed as passenger of the
        // old member and put as passenger for the new member.
        // For this a VehicleExit and VehicleEnter event are fired.
        // We don't want a duplicate Seat Enter/Change/Exit to fire during that.

        try {
            TCSeatChangeListener.suppressSeatChangeEvents = true;

            // Remove from old cart - fires VehicleExitEvent/EntityDismountEvent which can be cancelled
            if (old_seat != null) {
                if ( !old_seat.getMember().getEntity().removePassenger(passenger) &&
                     old_seat.getMember().getEntity().isPassenger(passenger)
                ) {
                    return false;
                }
                if (old_seat.getEntity() == passenger) {
                    old_seat.setEntity(null);
                }
            }

            // Try to enter the new seat, which fires VehicleEnterEvent/EntityMountEvent
            // which can be cancelled.
            boolean enteredNewSeat = false;
            if (new_seat != null) {
                if (new_seat.getEntity() == null) {
                    // Try entering the (new) vehicle, and entering the seat
                    new_seat.getController().storeSeatHint(passenger, new_seat);
                    enteredNewSeat = new_seat.getMember().getEntity().addPassenger(passenger);
                    new_seat.getController().storeSeatHint(passenger, null);
                    enteredNewSeat &= (new_seat.getEntity() == null);
                    if (enteredNewSeat) {
                        new_seat.setEntity(passenger);
                    }
                } else if (new_seat.getEntity() == passenger) {
                    enteredNewSeat = true; // Somehow already entered. Do nothing more. (recursive?)
                } else {
                    enteredNewSeat = false; // Another passenger already took it - can't enter it.
                }
            }

            // Fire post-seat-change events depending on what seats were entered/exited
            if (old_seat != null) {
                if (enteredNewSeat) {
                    CommonUtil.callEvent(new MemberSeatChangeEvent(old_seat, new_seat, passenger, seatPosition, exitPosition, isPlayerInitiated));
                } else {
                    CommonUtil.callEvent(new MemberSeatExitEvent(old_seat, passenger, seatPosition, exitPosition, isPlayerInitiated));
                }
            }
            if (enteredNewSeat) {
                CommonUtil.callEvent(new MemberSeatEnterEvent(new_seat, passenger, isPlayerInitiated,
                        old_seat != null, /* was seat change */
                        old_seat == null || old_seat.getMember() != new_seat.getMember() /* was vehicle change */));
            }

            return true;
        } finally {
            TCSeatChangeListener.suppressSeatChangeEvents = false;
        }
    }

    /**
     * Gets whether a seat hint was set for this attachment controller. A seat hint
     * tells the attachment controller where to put players that enter the minecart.
     *
     * @param passenger
     * @return True if there is a seat hint
     */
    public boolean hasSeatHint(Entity passenger) {
        return this.seatHints.containsKey(passenger);
    }

    /**
     * Gets the new seat that would be assigned to a passenger, if that passenger
     * entered the cart. This takes into account previously stored seat hints.
     *
     * @param passenger
     * @return New seat for this passenger
     */
    public synchronized CartAttachmentSeat findNewSeatForEntity(Entity passenger) {
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

    private List<CartAttachmentSeat> getSeatsClosestToPosition(Vector position) {
        if (this.seatAttachments.size() <= 1) {
            return this.seatAttachments;
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
            return this.seatAttachments;
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
     * Finds the seat occupied by a passenger. If the passenger is not yet inside a seat
     * of this cart currently, then the best matching seat that the passenger would enter
     * is returned instead. This is important because the seat assignment occurs
     * slightly delayed.
     *
     * @param passenger
     * @return seat, null if this cart has no seats for the passenger to enter
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
        return this.findNewSeatForEntity(passenger);
    }

    // Called from NetworkController
    public synchronized void makeVisible(Player viewer) {
        AttachmentViewer attachmentViewer = asAttachmentViewer(viewer);
        viewers.put(viewer, attachmentViewer);
        if (!this.hidden) {
            HelperMethods.makeVisibleRecursive(this.getRootAttachment(), true, attachmentViewer);
        }
    }

    // Called from NetworkController
    public synchronized void makeHidden(Player viewer) {
        //super.makeHidden(viewer, instant);

        AttachmentViewer attachmentViewer = viewers.remove(viewer);
        if (attachmentViewer == null) {
            attachmentViewer = asAttachmentViewer(viewer);
        }

        if (!this.hidden && this.rootAttachment != null) {
            HelperMethods.makeHiddenRecursive(this.rootAttachment, true, attachmentViewer);
        }
    }

    public synchronized void makeHiddenForAll() {
        for (Iterator<AttachmentViewer> iter = this.viewers.values().iterator(); iter.hasNext();) {
            AttachmentViewer attachmentViewer = iter.next();
            iter.remove();
            HelperMethods.makeHiddenRecursive(this.rootAttachment, true, attachmentViewer);
        }
    }

    /**
     * Gets the Player viewers viewing this cart's attachments
     *
     * @return player viewers
     */
    @Override
    public Set<Player> getViewers() {
        return this.viewers.keySet();
    }

    /**
     * Gets the Attachment Viewers viewing this cart's attachments
     *
     * @return attachment viewers
     */
    @Override
    public Collection<AttachmentViewer> getAttachmentViewers() {
        return this.viewers.values();
    }

    @Override
    public AttachmentViewer asAttachmentViewer(Player player) {
        return this.member.getTrainCarts().getPacketQueueMap().getQueue(player);
    }

    /**
     * Gets whether a given player is viewing the attachments of this cart
     *
     * @param player Viewer
     * @return True if visible to this player
     */
    public synchronized boolean isViewer(Player player) {
        return this.viewers.containsKey(player);
    }

    /**
     * Gets whether an Entity Id specified is used by an attachment
     * 
     * @param entityId
     * @return True if an attachment uses this Entity Id
     */
    public synchronized boolean isAttachment(int entityId) {
        for (Attachment attachment : this.flattenedAttachments) {
            if (attachment.containsEntityId(entityId)) {
                return true;
            }
        }
        return false;
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
     * Sets what seat should be entered the next time the specified entity enters the cart
     *
     * @param entity Entity that enters
     * @param seat Seat to enter
     */
    public void storeSeatHint(Entity entity, CartAttachmentSeat seat) {
        if (seat == null) {
            this.seatHints.remove(entity);
        } else {
            this.seatHints.put(entity, new SeatHint(Collections.singletonList(seat)));
        }
    }

    /**
     * Called by the network controller if this MinecartMember is unloaded.
     * It should do a special kind of no-movement no-logic sync.
     */
    public void syncUnloaded() {
        this.syncMovement(false);
    }

    public synchronized void syncPrePositionUpdate(AttachmentUpdateTransformHelper updater) {
        if (isAttached()) {
            syncPrePositionUpdate();
            updater.start(getRootAttachment(), getLiveTransform());
        }
    }

    /**
     * Synchronizes all attachments by first de-spawning and then re-spawning
     * all attachments to all viewers of the member.
     */
    public synchronized void syncRespawn() {
        List<Player> oldViewers = new ArrayList<>(this.getViewers());
        this.makeHiddenForAll();
        this.member.getTrainCarts().getTrainUpdateController().syncPositions(this.member);
        for (Player viewer : oldViewers) {
            this.makeVisible(viewer);
        }
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
        if (this.rootAttachment == null || this.isUnloadedOrDead()) {
            return;
        }

        this.flattenedAttachments.forEach(Attachment::onTick);
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
            this.flattenedAttachments.forEach(a -> a.onMove(absolute));
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
        // If not attached or hidden don't do anything to prevent bad things from happening
        if (!this.attached || this.hidden) {
            return;
        }

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

        // Use a temporary set for the players, so that we can clear them after hiding it for viewers
        // This makes sure getViewers() stays in sync with what makeVisible/makeHidden does
        ArrayList<AttachmentViewer> originalViewers = new ArrayList<>(this.viewers.values());

        // Detach old attachments - after this viewers see nothing anymore
        if (this.rootAttachment != null) {
            makeHiddenForAll();
            detachRootAttachment();
        } else {
            this.viewers.clear(); // Silent
        }

        // Attach new attachments - after this viewers see everything but passengers are not 'in'
        this.rootAttachment = this.createAttachment(model.getConfig());
        this.flattenedAttachments = HelperMethods.listAllAttachments(this.rootAttachment);
        this.flattenedAttachments.forEach(HelperMethods::perform_onAttached_single);
        this.seatAttachments = this.flattenedAttachments.stream()
                .filter(attachment -> attachment instanceof CartAttachmentSeat)
                .map(attachment -> (CartAttachmentSeat) attachment)
                .collect(StreamUtil.toUnmodifiableList());
        this.member.getTrainCarts().getTrainUpdateController().computeAttachmentTransform(
                this.rootAttachment, this.getLiveTransform());

        // Re-show the attachments and repopulate the viewers set
        for (AttachmentViewer viewer : originalViewers) {
            HelperMethods.makeVisibleRecursive(this.rootAttachment, true, viewer);
            this.viewers.put(viewer.getPlayer(), viewer);
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
        // If not attached don't do anything to prevent bad things from happening
        if (!this.attached) {
            return;
        }

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
        try {
            newAttachmentType.migrateConfiguration(config);
        } catch (Throwable t) {
            this.member.getTrainCarts().getLogger().log(Level.SEVERE,
                    "Failed to migrate attachment configuration of " + newAttachmentType.getName(), t);
        }
        attachment.getInternalState().onLoad(this.getClass(), newAttachmentType, config);
        attachment.onLoad(config);

        // TODO: Maybe only update from the changed attachment onwards?
        this.member.getTrainCarts().getTrainUpdateController().computeAttachmentTransform(
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
