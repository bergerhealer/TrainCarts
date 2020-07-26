package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.Timings;
import com.bergerkiller.bukkit.common.bases.mutable.VectorAbstract;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.EntityTracker;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TCTimings;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModelOwner;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> implements AttachmentModelOwner, AttachmentManager {
    public static final int ABSOLUTE_UPDATE_INTERVAL = 200;
    public static final double VELOCITY_SOUND_RADIUS = 16;
    public static final double VELOCITY_SOUND_RADIUS_SQUARED = VELOCITY_SOUND_RADIUS * VELOCITY_SOUND_RADIUS;

    private MinecartMember<?> member = null;
    private final Set<Player> velocityUpdateReceivers = new HashSet<>();

    private Attachment rootAttachment;
    private List<CartAttachmentSeat> seatAttachments = new ArrayList<CartAttachmentSeat>();
    private Map<Player, SeatHint> seatHints = new HashMap<Player, SeatHint>();

    private long animationCurrentTime = 0;
    private double animationDeltaTime = 0.0;

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
    public org.bukkit.World getWorld() {
        return this.getEntity().getWorld();
    }

    /**
     * Gets the root attachment, representing the (attachments) based model
     * 
     * @return root attachment
     */
    public Attachment getRootAttachment() {
        // Set attachment to a fallback if for whatever reason it is null
        if (this.rootAttachment == null) {
            this.onModelChanged(AttachmentModel.getDefaultModel(getMember().getEntity().getType()));
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
    public CartAttachmentSeat findSeat(Entity passenger) {
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

    @Override
    public void onAttached() {
        super.onAttached();

        this.animationCurrentTime = System.currentTimeMillis();
        this.animationDeltaTime = 0.0;
        this.getMember().getProperties().getModel().addOwner(this);
    }

    @Override
    public void onDetached() {
        super.onDetached();

        if (this.rootAttachment != null) {
            HelperMethods.perform_onDetached(this.rootAttachment);
        }
        if (this.member != null) {
            this.member.getProperties().getModel().removeOwner(this);
        }
    }

    public void setMember(MinecartMember<?> member) {
        this.member = member;
    }

    public MinecartMember<?> getMember() {
        if (this.entity == null) {
            this.member = null;
        } else if (this.member == null) {
            this.member = this.entity.getController(MinecartMember.class);
        }
        return this.member;
    }

    private double convertVelocity(double velocity) {
        return isSoundEnabled() ? MathUtil.clamp(velocity, getEntity().getMaxSpeed()) : 0.0;
    }

    private boolean isSoundEnabled() {
        MinecartMember<?> member = this.getMember();
        if (member == null || member.isUnloaded()) {
            return false;
        }
        MinecartGroup group = member.getGroup();
        if (group == null) {
            return false;
        }
        return group.getProperties().isSoundEnabled();
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

        // If the entity backing this controller does not exist,
        // remove this tracker entry from the server.
        // It is not clear why this happens sometimes.
        // Do this in another tick to avoid concurrent modification exceptions.
        if (this.getMember() == null || !this.getMember().getEntity().isSpawned()) {
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

        HelperMethods.makeVisibleRecursive(this.getRootAttachment(), true, viewer);

        this.velocityUpdateReceivers.add(viewer);
        this.updateVelocity(viewer);
    }

    @Override
    public void makeHidden(Player viewer, boolean instant) {
        //super.makeHidden(viewer, instant);

        if (this.rootAttachment != null) {
            HelperMethods.makeHiddenRecursive(this.rootAttachment, true, viewer);
        }

        this.velocityUpdateReceivers.remove(viewer);
    }

    @Override
    public void onTick() {
        try {
            // Cleanup seat hints (avoid memory leaks)
            if (!this.seatHints.isEmpty()) {
                Iterator<SeatHint> iter = this.seatHints.values().iterator();
                while (iter.hasNext()) {
                    if (iter.next().isExpired()) {
                        iter.remove();
                    }
                }
            }

            // If dead or detached, do nothing. This is probably a bug?
            if (entity.isDead() || this.getMember() == null) {
                return;
            }

            // If this minecart is unloaded, simply sync self only without any movement updates
            if (this.getMember().isUnloaded()) {
                this.syncSelf(false);
                return;
            }

            // When synchronizing the first member of the train, sync the entire train
            MinecartGroup group = this.getMember().getGroup();
            if (this.getMember() != group.head()) {
                return;
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
                    networkControllers[i].syncSelf(true);
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
                    // Perform actual updates
                    for (i = 0; i < count; i++) {
                        networkControllers[i].syncSelf(false);
                    }
                }
            }
        } catch (Throwable t) {
            TrainCarts.plugin.log(Level.SEVERE, "Failed to synchronize a network controller:");
            TrainCarts.plugin.handle(t);
        }
    }

    /**
     * Gets whether an Entity Id specified is used by an attachment
     * 
     * @param entityId
     * @return True if an attachment uses this Entity Id
     */
    public boolean isAttachment(int entityId) {
        return HelperMethods.findAttachmentWithEntityId(this.rootAttachment, entityId) != null;
    }

    /**
     * Checks what seat a player is looking at, and stores that seat for later entering operations.
     * The information is stored for at most 2 ticks before it is invalidated.
     * 
     * @param player
     */
    public void storeSeatHint(Player player) {
        this.seatHints.put(player, new SeatHint(player, this.getSeatsClosestToHitTest(player.getEyeLocation())));
    }

    public void tickSelf() {
        this.getRootAttachment();

        if (TCConfig.animationsUseTickTime) {
            this.animationDeltaTime = 1.0 / 20.0;
        } else {
            long time_now = System.currentTimeMillis();
            this.animationDeltaTime = 0.001 * (double) (time_now - this.animationCurrentTime);
            this.animationCurrentTime = time_now;
        }

        try (Timings t = TCTimings.NETWORK_UPDATE_POSITIONS.start()) {
            HelperMethods.updatePositions(this.rootAttachment, getLiveTransform());
        }
        try (Timings t = TCTimings.NETWORK_PERFORM_TICK.start()) {
            HelperMethods.perform_onTick(this.rootAttachment);
        }
    }

    public void syncSelf(boolean absolute) {
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
            HelperMethods.perform_onMove(this.rootAttachment, absolute);
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
    public void onModelChanged(AttachmentModel model) {
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
        HelperMethods.updatePositions(this.rootAttachment, this.getLiveTransform());

        this.seatAttachments.clear();
        this.discoverSeats(this.rootAttachment);

        for (Player viewer : this.getViewers()) {
            HelperMethods.makeVisibleRecursive(this.rootAttachment, true, viewer);
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
    public void onModelNodeChanged(AttachmentModel model, int[] targetPath, ConfigurationNode config) {
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
        HelperMethods.updatePositions(this.rootAttachment, this.getLiveTransform());
    }

    // Information stored when a player interacts with a seat trying to enter it
    // Seats in order of importance are stored
    private static class SeatHint {
        public final Player player;
        public final List<CartAttachmentSeat> seats;
        public final int expire;

        public SeatHint(Player player, List<CartAttachmentSeat> seats) {
            this.player = player;
            this.seats = seats;
            this.expire = CommonUtil.getServerTicks() + 2;
        }

        public boolean isExpired() {
            return CommonUtil.getServerTicks() >= this.expire;
        }
    }
}
