package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.bases.mutable.VectorAbstract;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModelOwner;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.control.PassengerController;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> implements AttachmentModelOwner {
    public static final int ABSOLUTE_UPDATE_INTERVAL = 200;
    public static final double VELOCITY_SOUND_RADIUS = 16;
    public static final double VELOCITY_SOUND_RADIUS_SQUARED = VELOCITY_SOUND_RADIUS * VELOCITY_SOUND_RADIUS;

    private MinecartMember<?> member = null;
    private final Set<Player> velocityUpdateReceivers = new HashSet<>();
    private final Map<Player, PassengerController> passengerControllers = new HashMap<Player, PassengerController>();

    private CartAttachment rootAttachment;
    private List<CartAttachmentSeat> seatAttachments = new ArrayList<CartAttachmentSeat>();

    public MinecartMemberNetwork() {        
        final VectorAbstract velLiveBase = this.velLive;
        this.velLive = new VectorAbstract() {
            public double getX() {
                return convertVelocity(velLiveBase.getX());
            }

            public double getY() {
                return convertVelocity(velLiveBase.getY());
            }

            public double getZ() {
                return convertVelocity(velLiveBase.getZ());
            }

            public VectorAbstract setX(double x) {
                velLiveBase.setX(x);
                return this;
            }

            public VectorAbstract setY(double y) {
                velLiveBase.setY(y);
                return this;
            }

            public VectorAbstract setZ(double z) {
                velLiveBase.setZ(z);
                return this;
            }
        };

        //this.attachments.addAll(this.seats);

        // Debug: add a test attachment
        //this.attachments.add(new TestAttachment(this));
    }

    @Override
    protected void onSyncPassengers(Player viewer, List<Entity> oldPassengers, List<Entity> newPassengers) {
        // Clear passengers that have ejected
        for (CartAttachmentSeat seat : this.seatAttachments) {
            Entity oldPassenger = seat.getEntity();
            if (!newPassengers.contains(oldPassenger)) {
                seat.setEntity(null);
            }
        }

        // Add passengers that have entered
        for (Entity newPassenger : newPassengers) {
            boolean hasSeat = false;
            for (CartAttachmentSeat seat : this.seatAttachments) {
                if (seat.getEntity() == newPassenger) {
                    hasSeat = true;
                    break;
                }
            }
            if (!hasSeat) {
                // Get the LAST known position
                // We can not use current, because that is set to the location of the Minecart
                Vector position = new Vector();
                {
                    EntityHandle handle = EntityHandle.fromBukkit(newPassenger);
                    position.setX(handle.getLastX());
                    position.setY(handle.getLastY());
                    position.setZ(handle.getLastZ());
                }

                // Find a free seat and add the player there
                List<CartAttachmentSeat> sortedSeats = this.getSeatsClosestTo(position);
                for (CartAttachmentSeat seat : sortedSeats) {
                    if (seat.getEntity() == null) {
                        seat.setEntity(newPassenger);
                        break;
                    }
                }
            }
        }
    }

    private List<CartAttachmentSeat> getSeatsClosestTo(Vector position) {
        ArrayList<CartAttachmentSeat> result = new ArrayList<CartAttachmentSeat>(this.seatAttachments);
        Collections.sort(result, new Comparator<CartAttachmentSeat>() {
            @Override
            public int compare(CartAttachmentSeat o1, CartAttachmentSeat o2) {
                double d1 = o1.getPosition().distanceSquared(position);
                double d2 = o2.getPosition().distanceSquared(position);
                return Double.compare(d1, d2);
            }
        });
        return result;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        if (this.member == null) {
            this.member = this.entity.getController(MinecartMember.class);
        }

        this.member.getProperties().getModel().addOwner(this);
    }

    @Override
    public void onDetached() {
        super.onDetached();

        CartAttachment.deinitialize(this.rootAttachment);
        if (this.member != null) {
            this.member.getProperties().getModel().removeOwner(this);
        }
    }

    public void setMember(MinecartMember<?> member) {
        this.member = member;
    }

    public MinecartMember<?> getMember() {
        if (this.member == null) {
            this.member = this.entity.getController(MinecartMember.class);
        }
        return this.member;
    }

    private double convertVelocity(double velocity) {
        return isSoundEnabled() ? MathUtil.clamp(velocity, getEntity().getMaxSpeed()) : 0.0;
    }

    private boolean isSoundEnabled() {
        MinecartMember<?> member = (MinecartMember<?>) entity.getController();
        return !(member == null || member.isUnloaded()) && member.getGroup().getProperties().isSoundEnabled();
    }

    private void updateVelocity(Player player) {
        final boolean inRange = isSoundEnabled() && getEntity().loc.distanceSquared(player) <= VELOCITY_SOUND_RADIUS_SQUARED;
        if (LogicUtil.addOrRemove(velocityUpdateReceivers, player, inRange)) {
            CommonPacket velocityPacket;
            if (inRange) {
                // Send the current velocity
                velocityPacket = getVelocityPacket(velSynched.length());
            } else {
                // Clear velocity
                velocityPacket = getVelocityPacket(0.0);
            }
            // Send
            PacketUtil.sendPacket(player, velocityPacket);
        }
    }

    private CommonPacket getVelocityPacket(double velocity) {
        return getVelocityPacket(velocity, 0.0, 0.0);
    }

    @Override
    public void makeVisible(Player viewer) {
        //super.makeVisible(viewer);

        // Ensures mount packets are sent out after the spawn packets of entities involved
        this.getPassengerController(viewer).startTickDelay();

        makeVisible(this.rootAttachment, viewer);

        this.velocityUpdateReceivers.add(viewer);
        this.updateVelocity(viewer);
    }

    private static void makeVisible(CartAttachment attachment, Player viewer) {
        attachment.makeVisible(viewer);
        for (CartAttachment child : attachment.children) {
            makeVisible(child, viewer);
        }
    }

    @Override
    public void makeHidden(Player viewer, boolean instant) {
        //super.makeHidden(viewer, instant);

        makeHidden(this.rootAttachment, viewer);

        this.velocityUpdateReceivers.remove(viewer);
        this.passengerControllers.remove(viewer);
    }

    private static void makeHidden(CartAttachment attachment, Player viewer) {
        for (CartAttachment child : attachment.children) {
            makeHidden(child, viewer);
        }
        attachment.makeHidden(viewer);
    }

    @Override
    public void onTick() {
        try {
            if (entity.isDead()) {
                return;
            }

            // Retrieve group from this Minecart + addtional checks
            MinecartGroup group;
            {
                MinecartMember<?> member = (MinecartMember<?>) entity.getController();
                if (member.isUnloaded()) {
                    // Unloaded: Synchronize just this Minecart
                    super.onTick();
                    return;
                } else if (member.getIndex() != 0) {
                    // Ignore minecarts other than the first
                    return;
                } else {
                    group = member.getGroup();
                }
            }

            // Update the entire group
            int i;
            final int count = group.size();
            MinecartMemberNetwork[] networkControllers = new MinecartMemberNetwork[count];
            for (i = 0; i < count; i++) {
                MinecartMember<?> member = group.get(i);
                EntityNetworkController<?> controller = member.getEntity().getNetworkController();
                if (!(controller instanceof MinecartMemberNetwork)) {
                    // This is not good, but we can fix it...but not here
                    group.networkInvalid.set();
                    return;
                }
                networkControllers[i] = (MinecartMemberNetwork) controller;
                if (networkControllers[i].member != member) {
                    networkControllers[i].member = member;
                }
                networkControllers[i].tickSelf();
            }

            // Synchronize to the clients
            if (this.getTicksSinceLocationSync() > ABSOLUTE_UPDATE_INTERVAL) {
                EntityTrackerEntryHandle.createHandle(this.getHandle()).setTimeSinceLocationSync(0);

                // Perform absolute updates
                for (i = 0; i < count; i++) {
                    networkControllers[i].syncSelf(true, true, true);
                }
            } else {
                // Perform relative updates
                boolean needsSync = this.isUpdateTick();
                if (!needsSync) {
                    for (i = 0; i < count; i++) {
                        MinecartMemberNetwork controller = networkControllers[i];
                        if (controller.getEntity().isPositionChanged() || controller.getEntity().getDataWatcher().isChanged() || controller.isPassengersChanged()) {
                            needsSync = true;
                            break;
                        }
                    }
                }
                if (needsSync) {
                    boolean moved = false;
                    boolean rotated = false;

                    // Check whether changes are needed
                    for (i = 0; i < count; i++) {
                        MinecartMemberNetwork controller = networkControllers[i];
                        moved |= controller.isPositionChanged(MIN_RELATIVE_POS_CHANGE);
                        rotated |= controller.isRotationChanged(MIN_RELATIVE_ROT_CHANGE);
                    }

                    // Perform actual updates
                    for (i = 0; i < count; i++) {
                        networkControllers[i].syncSelf(moved, rotated, false);
                    }
                }
            }
        } catch (Throwable t) {
            TrainCarts.plugin.log(Level.SEVERE, "Failed to synchronize a network controller:");
            TrainCarts.plugin.handle(t);
        }
    }

    /**
     * Handles a player clicking on a virtual attachment part.
     * Returns true if this minecart was indeed interacted with.
     * Tracks the interaction that was performed so that it can later
     * be deduced which attachment was interacted.
     * 
     * @param entityId
     * @return True if interaction was handled
     */
    public boolean handleInteraction(int entityId) {
        CartAttachment attachment = CartAttachment.findAttachment(this.rootAttachment, entityId);
        if (attachment == null) {
            return false;
        }

        // TODO: Store this attachment for later querying
        return true;
    }

    public void tickSelf() {
        // Set attachment to a fallback if for whatever reason it is null
        if (this.rootAttachment == null) {
            this.onModelChanged(AttachmentModel.getDefaultModel(getMember().getEntity().getType()));
        }

        try (Timings t = TCTimings.NETWORK_UPDATE_POSITIONS.start()) {
            CartAttachment.updatePositions(this.rootAttachment, getLiveTransform());
        }
        try (Timings t = TCTimings.NETWORK_PERFORM_TICK.start()) {
            CartAttachment.performTick(this.rootAttachment);
        }
    }

    public void syncSelf(boolean moved, boolean rotated, boolean absolute) {
        // Check
        MinecartMember<?> member = this.getMember();
        if (member == null) {
            return;
        }

        getEntity().setPositionChanged(false);

        this.locSynched.set(this.locLive);

        // Unused, but set it to false for unknown reasons!
        getEntity().setVelocityChanged(false);

        // Perform actual movement, which sends movement update packets
        try (Timings t = TCTimings.NETWORK_PERFORM_MOVEMENT.start()) {
            CartAttachment.performMovement(this.rootAttachment, absolute);
        }

        this.syncPassengers();
    }

    public Matrix4x4 getLiveTransform() {
        // Combine translation and rotation information into a 4x4 matrix
        MinecartMember<?> member = this.getMember();
        Matrix4x4 transform = new Matrix4x4();
        transform.translate(member.getWheels().getPosition());
        transform.rotate(member.getOrientation());
        transform.rotateZ(member.getRoll());
        return transform;
    }

    public PassengerController getPassengerController(Player viewer) {
        PassengerController controller = this.passengerControllers.get(viewer);
        if (controller == null) {
            controller = new PassengerController(viewer);
            this.passengerControllers.put(viewer, controller);
        }
        return controller;
    }

    public Collection<PassengerController> getPassengerControllers() {
        return this.passengerControllers.values();
    }

    private void discoverSeats(CartAttachment attachment) {
        if (attachment instanceof CartAttachmentSeat) {
            this.seatAttachments.add((CartAttachmentSeat) attachment);
        }
        for (CartAttachment child : attachment.children) {
            discoverSeats(child);
        }
    }

    @Override
    public void onModelChanged(AttachmentModel model) {
        // Store the positions of the players in the previous seats
        // This is used later to re-assign the passengers to seats when the model is changed
        Map<Entity, Vector> oldSeatPositions = new HashMap<Entity, Vector>();
        for (CartAttachmentSeat seat : this.seatAttachments) {
            Entity oldEntity = seat.getEntity();
            if (oldEntity != null) {
                oldSeatPositions.put(oldEntity, seat.getPosition());
            }
        }

        //TODO: Detect when only a single element is changed, and only update that element
        // This allows for a cleaner update when repositioning/etc.

        // Detach old attachments - after this viewers see nothing anymore
        if (this.rootAttachment != null) {
            for (Player oldViewer : this.getViewers()) {
                makeHidden(this.rootAttachment, oldViewer);
            }
            CartAttachment.deinitialize(this.rootAttachment);
            this.rootAttachment = null;
        }

        // Clear to reset passenger controllers
        this.passengerControllers.clear();

        // Attach new attachments - after this viewers see everything but passengers are not 'in'
        this.rootAttachment = CartAttachment.initialize(this, model.getConfig());
        
        this.seatAttachments.clear();
        this.discoverSeats(this.rootAttachment);

        for (Player viewer : this.getViewers()) {
            makeVisible(this.rootAttachment, viewer);
        }

        // Let all passengers re-enter us
        // For this, we must find suitable Seat attachments in the tree
        List<Entity> remainingPassengers = new ArrayList<Entity>(this.entity.getPassengers());
        while (!remainingPassengers.isEmpty()) {
            Entity entity = remainingPassengers.get(0);
            Vector position = oldSeatPositions.get(entity);
            if (position == null) {
                position = entity.getLocation().toVector();
            }
            boolean foundSeat = false;
            List<CartAttachmentSeat> seats = this.getSeatsClosestTo(position);
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
}
