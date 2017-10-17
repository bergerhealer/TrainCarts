package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.bases.mutable.VectorAbstract;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.CartAttachment;
import com.bergerkiller.bukkit.tc.attachments.CartAttachmentOwner;
import com.bergerkiller.bukkit.tc.attachments.SeatAttachment;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> implements CartAttachment, CartAttachmentOwner {
    public static final float ROTATION_K = 0.55f;
    public static final int ABSOLUTE_UPDATE_INTERVAL = 200;
    public static final double VELOCITY_SOUND_RADIUS = 16;
    public static final double VELOCITY_SOUND_RADIUS_SQUARED = VELOCITY_SOUND_RADIUS * VELOCITY_SOUND_RADIUS;
    private static final Vector ZERO_VELOCITY = new Vector(0.0, 0.0, 0.0);
    private MinecartMember<?> member = null;
    private final Set<Player> velocityUpdateReceivers = new HashSet<>();
    public boolean disableMountHandling = false;
    private boolean isFirstUpdate = true;
    private double lastDeltaX = 0.0;
    private double lastDeltaY = 0.0;
    private double lastDeltaZ = 0.0;
    private boolean needsPassengerResync = true;

    private List<SeatAttachment> seats = Arrays.asList(new SeatAttachment(this));

    @Override
    public Vector getLastMovement() {
        return new Vector(this.lastDeltaX , this.lastDeltaY, this.lastDeltaZ);
    }

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
    }

    // sends a mount packet with the passengers of this Minecart directly
    private void syncDirectPassengers() {
        if (this.needsPassengerResync) {
            this.needsPassengerResync = false;

            for (Player viewer : this.getViewers()) {
                sendPassengers(viewer);
            }
        }
    }

    private void sendPassengers(Player viewer) {
        PacketPlayOutMountHandle mount = PacketPlayOutMountHandle.createNew(this.getEntity().getEntityId(), new int[0]);
        for (SeatAttachment seat : this.seats) {
            int entityId = seat.getEntityId(viewer);
            if (entityId != -1) {
                mount.addMountedEntityId(entityId);
            }
        }
        PacketUtil.sendPacket(viewer, mount);
    }

    @Override
    protected void onSyncPassengers(Player viewer, List<Entity> oldPassengers, List<Entity> newPassengers) {
        if (this.disableMountHandling) {
            return; // suppress
        }

        // Clear passengers that have ejected
        for (Entity oldPassenger : oldPassengers) {
            if (!newPassengers.contains(oldPassenger)) {
                // Remove from seat
                for (SeatAttachment seat : this.seats) {
                    if (seat.getEntity() == oldPassenger) {
                        seat.setEntity(null);
                    }
                }
            }
        }

        // Add passengers that have entered
        for (Entity newPassenger : newPassengers) {
            if (!oldPassengers.contains(newPassenger)) {
                // Find a free seat and add the player there
                for (SeatAttachment seat : this.seats) {
                    if (seat.getEntity() == null) {
                        seat.setEntity(newPassenger);
                    }
                }
            }
        }

        // Synchronize the passengers mounted to the Minecart
        this.syncDirectPassengers();
    }

    @Override
    public void onAttached() {
        super.onAttached();

        if (this.member == null) {
            this.member = this.entity.getController(MinecartMember.class);
        }

        // The network controller is attached after the minecart is already spawned.
        // As a result, makeVisible isn't called. We explicitly handle that logic here.
        for (Player viewer : this.getViewers()) {
            this.onMadeVisible(viewer);
        }
    }

    private static float getAngleKFactor(float angle1, float angle2) {
        float diff = angle1 - angle2;
        while (diff <= -180.0f) {
            diff += 360.0f;
        }
        while (diff > 180.0f) {
            diff -= 360.0f;
        }
        return (ROTATION_K * diff);
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
        super.makeVisible(viewer);
        onMadeVisible(viewer);
    }

    // fired after this Minecart was made visible (spawned) to a viewer
    public void onMadeVisible(Player viewer) {
        this.velocityUpdateReceivers.add(viewer);
        this.updateVelocity(viewer);

        for (SeatAttachment seat : this.seats) {
            seat.addViewer(viewer);
        }
    }
    
    @Override
    public void makeHidden(Player viewer, boolean instant) {
        super.makeHidden(viewer, instant);
        this.velocityUpdateReceivers.remove(viewer);
        PacketUtil.sendPacket(viewer, PacketType.OUT_ENTITY_VELOCITY.newInstance(getEntity().getEntityId(), ZERO_VELOCITY));

        for (SeatAttachment seat : this.seats) {
            seat.removeViewer(viewer);
        }
    }

    @Override
    public void onTick() {
        for (SeatAttachment seat : this.seats) {
            seat.onTick();
        }
    }

    @Override
    public void onSync() {
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
                    super.onSync();
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
                networkControllers[i].onTick();
            }

            // Synchronize to the clients
            if (this.getTicksSinceLocationSync() > ABSOLUTE_UPDATE_INTERVAL) {
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
     * Gets the position transform of this Minecart with motion prediction taken into account
     * 
     * @return transform
     */
    public Matrix4x4 getTransform() {
        return getTransform(true);
    }

    /**
     * Gets the position transform of this Minecart
     * 
     * @param motion whether motion prediction needs to be taken into account
     * @return transform
     */
    public Matrix4x4 getTransform(boolean motion) {
        Matrix4x4 transform = new Matrix4x4();

        double fx = 0.0, fy = 0.0, fz = 0.0;
        if (motion) {
            fx = this.lastDeltaX * 0.625;
            fy = this.lastDeltaY * 0.625;
            fz = this.lastDeltaZ * 0.625;
        }

        // Some factor of the movement change needs to be re-predicted
        // Otherwise things stuck to this Minecart will always move ahead
        transform.translateRotate(
                (this.locSynched.getX() - fx),
                (this.locSynched.getY() - fy),
                (this.locSynched.getZ() - fz),
                this.locSynched.getYaw(), this.locSynched.getPitch()
        );
        return transform;
    }

    public void syncSelf(boolean moved, boolean rotated, boolean absolute) {
        // Check
        if (this.getMember() == null) {
            return;
        }

        // Read live location
        double posX = locLive.getX();
        double posY = locLive.getY();
        double posZ = locLive.getZ();
        float rotYawLive = locLive.getYaw();
        float rotPitchLive = locLive.getPitch();
        float rotYaw = rotYawLive;
        float rotPitch = rotPitchLive;

        // Synchronize location
        if (rotated && !member.isDerailed() && !isFirstUpdate) {
            // Update rotation with control system function
            // This ensures that the Client animation doesn't glitch the rotation
            rotYaw += getAngleKFactor(rotYaw, locSynched.getYaw());
            rotPitch += getAngleKFactor(rotPitch, locSynched.getPitch());
        }

        // Minecraft has really shitty pitch angle calculations for Minecarts
        // For example, if the pitch angle crosses a 180-degree boundary, it bugs out!
        // But we can detect this consistent behavior, and respawn the Minecart when we detect it happening
        // This prevents a really ugly 360 rotation from occurring
        if (rotated && Util.isProtocolRotationGlitched(locSynched.getPitch(), rotPitch)) {
            rotYaw = rotYawLive;
            rotPitch = rotPitchLive;
            absolute = false;
            rotated = false;

            // Instantly set the newly requested rotation
            locSynched.setRotation(rotYaw, rotPitch);

            // Destroy and re-spawn the minecart with the new coordinates
            // Do not do any wacky passenger mounting/unmounting here
            // We only want to respawn the Minecart itself
            this.disableMountHandling = true;
            for (Player viewer : this.getViewers()) {
                super.makeHidden(viewer, true);
                super.makeVisible(viewer);
            }
            this.disableMountHandling = false;
            this.needsPassengerResync = true;
            this.syncDirectPassengers();
        }

        isFirstUpdate = false;
        getEntity().setPositionChanged(false);

        // Absolute/relative movement updates
        if (absolute) {
            syncLocationAbsolute(posX, posY, posZ, rotYaw, rotPitch);

            lastDeltaX = 0.0;
            lastDeltaY = 0.0;
            lastDeltaZ = 0.0;
        } else {
            if (moved) {
                lastDeltaX = (posX - this.locSynched.getX());
                lastDeltaY = (posY - this.locSynched.getY());
                lastDeltaZ = (posZ - this.locSynched.getZ());
            }

            syncLocation(moved, rotated, posX, posY, posZ, rotYaw, rotPitch);
        }

        // Velocity is used exclusively for controlling the minecart's audio level
        // When derailed, no audio should be made. Otherwise, the velocity speed controls volume.
        // Minecraft does not play minecart audio for the Y-axis. To make sound on vertical rails,
        // we instead apply the vector length to just the X-axis so that this works.
        double currVelocity;
        if (member.isDerailed()) {
            currVelocity = 0.0;
        } else {
            currVelocity = velLive.length();
        }
        currVelocity = Math.min(currVelocity, member.getEntity().getMaxSpeed());
        boolean velocityChanged = (MathUtil.length(velSynched.length(), currVelocity) > (MIN_RELATIVE_VELOCITY * MIN_RELATIVE_VELOCITY)) ||
                (velSynched.lengthSquared() > 0.0 && currVelocity == 0.0);

        // Synchronize velocity
        if (absolute || getEntity().isVelocityChanged() || velocityChanged) {
            // Reset dirty velocity
            getEntity().setVelocityChanged(false);

            // Send packets to recipients
            velSynched.set(currVelocity, 0.0, 0.0);

            CommonPacket velocityPacket = getVelocityPacket(currVelocity);
            for (Player player : velocityUpdateReceivers) {
                PacketUtil.sendPacket(player, velocityPacket);
            }
        }

        // Update the velocity update receivers
        if (isSoundEnabled()) {
            for (Player player : getViewers()) {
                updateVelocity(player);
            }
        }

        // Synchronize meta data
        syncMetaData();

        if (MathUtil.getAngleDifference(locLive.getPitch(), 180.0f) < 89.0f) {
            // Beyond the point where the entity should be rendered upside-down
            for (SeatAttachment seat : this.seats) {
                seat.setUpsideDown(true);
            }
        } else if (MathUtil.getAngleDifference(locLive.getPitch(), 0.0f) < 89.0f) {
            // Beyond the point where the entity should be rendered normally again
            for (SeatAttachment seat : this.seats) {
                seat.setUpsideDown(false);
            }
        }

        if ((locLive.getPitch() < -46.0f) || (locLive.getPitch() > 46.0f)) {
            for (SeatAttachment seat : this.seats) {
                seat.setUseVirtualCamera(true);
            }
        } else {
            for (SeatAttachment seat : this.seats) {
                seat.setUseVirtualCamera(false);
            }
        }

        onSyncAtt(absolute);

        this.syncPassengers();
        this.syncDirectPassengers();
    }

    @Override
    public void onAttachmentsChanged() {
        this.needsPassengerResync = true;
    }

    @Override
    public void onSyncAtt(boolean absolute) {
        for (SeatAttachment seat : this.seats) {
            seat.onSyncAtt(absolute);
        }
    }
}
