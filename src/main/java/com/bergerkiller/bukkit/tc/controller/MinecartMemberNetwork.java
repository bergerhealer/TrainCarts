package com.bergerkiller.bukkit.tc.controller;

import com.bergerkiller.bukkit.common.bases.mutable.VectorAbstract;
import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.generated.com.mojang.authlib.GameProfileHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityTrackerEntryHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityDestroyHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutMountHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutNamedEntitySpawnHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutScoreboardTeamHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle.EnumPlayerInfoActionHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutPlayerInfoHandle.PlayerInfoDataHandle;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class MinecartMemberNetwork extends EntityNetworkController<CommonMinecart<?>> {
    public static final float ROTATION_K = 0.55f;
    public static final int ABSOLUTE_UPDATE_INTERVAL = 200;
    public static final double VELOCITY_SOUND_RADIUS = 16;
    public static final double VELOCITY_SOUND_RADIUS_SQUARED = VELOCITY_SOUND_RADIUS * VELOCITY_SOUND_RADIUS;
    private static final Vector ZERO_VELOCITY = new Vector(0.0, 0.0, 0.0);
    private final Set<Player> velocityUpdateReceivers = new HashSet<>();
    private boolean wasUpsideDown = false;
    private int fakePlayerId = -1;
    private int fakeMountId = -1;
    private int fakePlayerLastYaw = Integer.MAX_VALUE;
    private int fakePlayerLastPitch = Integer.MAX_VALUE;
    private int fakePlayerLastHeadRot = Integer.MAX_VALUE;
    private boolean isFirstUpdate = true;

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
                velocityPacket = getVelocityPacket(velSynched.getX(), velSynched.getY(), velSynched.getZ());
            } else {
                // Clear velocity
                velocityPacket = getVelocityPacket(0.0, 0.0, 0.0);
            }
            // Send
            PacketUtil.sendPacket(player, velocityPacket);
        }
    }

    @Override
    public void makeHidden(Player player, boolean instant) {
        super.makeHidden(player, instant);
        this.velocityUpdateReceivers.remove(player);
        PacketUtil.sendPacket(player, PacketType.OUT_ENTITY_VELOCITY.newInstance(getEntity().getEntityId(), ZERO_VELOCITY));

        Entity entity = this.getUpsideDownEntity();
        if (entity != null) {
            for (Player viewer : this.getViewers()) {
                sendUpsideDownUnmount(viewer, entity);
            }
        }
    }

    @Override
    public void makeVisible(Player player) {
        super.makeVisible(player);
        this.velocityUpdateReceivers.add(player);
        this.updateVelocity(player);
    }

    @Override
    public void onSync() {
        try {
            if (entity.isDead()) {
                return;
            }
            MinecartMember<?> member = (MinecartMember<?>) entity.getController();
            if (member.isUnloaded()) {
                // Unloaded: Synchronize just this Minecart
                super.onSync();
                return;
            } else if (member.getIndex() != 0) {
                // Ignore
                return;
            }

            // Update the entire group
            int i;
            MinecartGroup group = member.getGroup();
            final int count = group.size();
            MinecartMemberNetwork[] networkControllers = new MinecartMemberNetwork[count];
            for (i = 0; i < count; i++) {
                EntityNetworkController<?> controller = group.get(i).getEntity().getNetworkController();
                if (!(controller instanceof MinecartMemberNetwork)) {
                    // This is not good, but we can fix it...but not here
                    group.networkInvalid.set();
                    return;
                }
                networkControllers[i] = (MinecartMemberNetwork) controller;
            }

            // Synchronize to the clients
            if (this.getTicksSinceLocationSync() > ABSOLUTE_UPDATE_INTERVAL) {
                // Perform absolute updates
                for (i = 0; i < count; i++) {
                    networkControllers[i].syncSelf(group.get(i), true, true, true);
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
                        networkControllers[i].syncSelf(group.get(i), moved, rotated, false);
                    }
                }
            }
        } catch (Throwable t) {
            TrainCarts.plugin.log(Level.SEVERE, "Failed to synchronize a network controller:");
            TrainCarts.plugin.handle(t);
        }
    }

    @Override
    protected void onSyncPassengers(Player viewer, List<Entity> oldPassengers, List<Entity> newPassengers) {
        if (!this.wasUpsideDown) {
            super.onSyncPassengers(viewer, oldPassengers, newPassengers);
            return;
        }

        // Mount new passengers
        for (Entity newPassenger : newPassengers) {
            if (oldPassengers.contains(newPassenger)) {
                continue;
            }

            // Send upside-down mounted player to a viewer
            sendUpsideDownMount(viewer, newPassenger);
        }

        // Unmount old passengers
        for (Entity oldPassenger : oldPassengers) {
            if (newPassengers.contains(oldPassenger)) {
                continue;
            }

            // Send upside-down unmounted player to a viewer
            sendUpsideDownUnmount(viewer, oldPassenger);
        }
    }

    public void sendUpsideDownUnmount(Player viewer, Entity entity) {

        // Make player visible again by re-sending all metadata
        DataWatcher metaTmp = EntityHandle.fromBukkit(entity).getDataWatcher();
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(entity.getEntityId(), metaTmp, true);
        PacketUtil.sendPacket(viewer, metaPacket);

        // Send entity destroy packet to hide a fake mount
        if (this.fakePlayerId != -1) {
            PacketPlayOutEntityDestroyHandle destroyPacket = PacketPlayOutEntityDestroyHandle.createNew(new int[] {this.fakePlayerId, this.fakeMountId});
            PacketUtil.sendPacket(viewer, destroyPacket);
        }

        // Clear mounted passengers
        PacketPlayOutMountHandle mount = PacketPlayOutMountHandle.createNew(this.getEntity().getEntityId(), new int[] {});
        PacketUtil.sendPacket(viewer, mount);
    }

    /**
     * Calculates the (new) position of the fake mount a player is sitting on
     * 
     * @return new fake mount position
     */
    private Location calculateFakeMountPosition() {
        Location pos = getEntity().getLocation();
        pos = pos.add(0.0, -2.2, 0.0);
        return pos;
    }

    public void sendUpsideDownMount(Player viewer, Entity entity) {
        Player player = (Player) entity;
        
        // Create a new entity Id for a fake mount, if needed
        if (this.fakePlayerId == -1) {
            this.fakePlayerId = EntityUtil.getUniqueEntityId();
            this.fakeMountId = EntityUtil.getUniqueEntityId();
        }

        // Make original entity invisible using a metadata change
        DataWatcher metaTmp = new DataWatcher();
        metaTmp.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(entity.getEntityId(), metaTmp, true);
        PacketUtil.sendPacket(viewer, metaPacket);

        // Send player information with name 'Dinnerbone' and a unique Id
        UUID fakerUUID = UUID.randomUUID();
        GameProfileHandle fakeGameProfile = GameProfileHandle.createNew(fakerUUID, "Dinnerbone");
        fakeGameProfile.setAllProperties(GameProfileHandle.getForPlayer(player));
        PacketPlayOutPlayerInfoHandle infoPacket = PacketPlayOutPlayerInfoHandle.createNew();
        infoPacket.setAction(EnumPlayerInfoActionHandle.ADD_PLAYER);
        PlayerInfoDataHandle playerInfo = PlayerInfoDataHandle.createNew(
                infoPacket,
                fakeGameProfile,
                50,
                GameMode.CREATIVE,
                ChatText.fromMessage(player.getPlayerListName())
        );
        infoPacket.getPlayers().add(playerInfo);
        PacketUtil.sendPacket(viewer, infoPacket);

        // Make this fake entity part of a scoreboard team that causes the nametag to not render
        PacketPlayOutScoreboardTeamHandle teamPacket = PacketPlayOutScoreboardTeamHandle.T.newHandleNull();
        teamPacket.setName("DizzyTCRiders");
        teamPacket.setDisplayName("DizzyTCRiders");
        teamPacket.setPrefix("");
        teamPacket.setSuffix("");
        teamPacket.setVisibility("never");
        teamPacket.setCollisionRule("never");
        teamPacket.setMode(0x0);
        teamPacket.setFriendlyFire(0x3);
        teamPacket.setPlayers(new ArrayList<String>(Arrays.asList("Dinnerbone")));
        teamPacket.setChatFormat(0);
        PacketUtil.sendPacket(viewer, teamPacket);

        // Create a named entity spawn packet
        PacketPlayOutNamedEntitySpawnHandle fakePlayerSpawnPacket = PacketPlayOutNamedEntitySpawnHandle.T.newHandleNull();
        fakePlayerSpawnPacket.setEntityId(this.fakePlayerId);
        fakePlayerSpawnPacket.setPosX(entity.getLocation().getX());
        fakePlayerSpawnPacket.setPosY(entity.getLocation().getY());
        fakePlayerSpawnPacket.setPosZ(entity.getLocation().getZ());
        fakePlayerSpawnPacket.setYaw(entity.getLocation().getYaw());
        fakePlayerSpawnPacket.setPitch(entity.getLocation().getPitch());
        fakePlayerSpawnPacket.setEntityUUID(fakerUUID);

        // Copy data watcher data from the original player
        DataWatcher data_in = EntityHandle.fromBukkit(entity).getDataWatcher();
        DataWatcher fakePlayerMetaData = new DataWatcher();
        for (DataWatcher.Item<?> item : data_in.getWatchedItems()) {
            fakePlayerMetaData.watch((DataWatcher.Key) item.getKey(), item.getValue());
        }
        //data.set(EntityHandle.DATA_CUSTOM_NAME, "berger");
        //data.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, true);
        fakePlayerSpawnPacket.setDataWatcher(fakePlayerMetaData);

        // Finally send the packet
        PacketUtil.sendPacket(viewer, fakePlayerSpawnPacket);

        if (viewer == entity) {
            // When synchronizing passenger to himself, we put him on a fake mount to offset him downwards
            PacketPlayOutMountHandle mount = PacketPlayOutMountHandle.createNew(this.getEntity().getEntityId(), new int[] {this.fakePlayerId});
            PacketUtil.sendPacket(viewer, mount);
            

            DataWatcher data = new DataWatcher();
            data.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            data.set(EntityLivingHandle.DATA_HEALTH, 10.0F);

            Location fakePos = calculateFakeMountPosition();
            CommonPacket packet = PacketType.OUT_ENTITY_SPAWN_LIVING.newInstance();
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityId, this.fakeMountId);
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityUUID, UUID.randomUUID());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.entityType, (int) EntityType.CHICKEN.getTypeId());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posX, fakePos.getX());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posY, fakePos.getY());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.posZ, fakePos.getZ());
            packet.write(PacketType.OUT_ENTITY_SPAWN_LIVING.dataWatcher, data);
            PacketUtil.sendPacket(viewer, packet);
            
            PacketPlayOutMountHandle mount2 = PacketPlayOutMountHandle.createNew(this.fakeMountId, new int[] {entity.getEntityId()});
            PacketUtil.sendPacket(viewer, mount2);
            
        } else {
            // Other players simply see the fake mount, and an invisible 'self' player
            PacketPlayOutMountHandle mount = PacketPlayOutMountHandle.createNew(this.getEntity().getEntityId(), new int[] {this.fakePlayerId, entity.getEntityId()});
            PacketUtil.sendPacket(viewer, mount);
        }
    }

    private void broadcastExcept(PacketHandle packet, Entity except) {
        for (Player viewer : this.getViewers()) {
            if (viewer != except) {
                PacketUtil.sendPacket(viewer, packet);
            }
        }
    }

    private Entity getUpsideDownEntity() {
        if (!this.wasUpsideDown) {
            return null;
        }
        List<Entity> passengers = this.getSynchedPassengers();
        if (passengers == null || passengers.isEmpty()) {
            return null;
        }
        return passengers.get(0);
    }
    
    public void syncSelf(MinecartMember<?> member, boolean moved, boolean rotated, boolean absolute) {
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

        // If the pitch angle crosses a 180-degree boundary, re-spawn the minecart
        // This prevents a really ugly 360 rotation from occurring
        if (rotated) {
            int prot_a = EntityTrackerEntryHandle.getProtocolRotation(rotPitch) & 0xFF;
            int prot_b = EntityTrackerEntryHandle.getProtocolRotation(locSynched.getPitch()) & 0xFF;
            if ((prot_a <= 127 && prot_b >= 128) || (prot_b <= 127 && prot_a >= 128)) {
                rotYaw = rotYawLive;
                rotPitch = rotPitchLive;
                absolute = false;
                rotated = false;

                // Instantly set the newly requested rotation
                locSynched.setRotation(rotYaw, rotPitch);

                // Destroy and re-spawn the minecart with the new coordinates
                for (Player viewer : this.getViewers()) {
                    super.makeHidden(viewer, true);
                    super.makeVisible(viewer);
                }
            }
        }

        isFirstUpdate = false;
        getEntity().setPositionChanged(false);

        // Absolute/relative movement updates
        if (absolute) {
            syncLocationAbsolute(posX, posY, posZ, rotYaw, rotPitch);
        } else {
            syncLocation(moved, rotated, posX, posY, posZ, rotYaw, rotPitch);
        }

        // Synchronized the player mount to the player that rides this Minecart, when upside-down
        // This moves the mount along with the player
        if (this.wasUpsideDown) {
            Entity passenger = this.getUpsideDownEntity();
            if (passenger instanceof Player) {
                Location loc = this.calculateFakeMountPosition();
                CommonPacket movePacket = PacketType.OUT_ENTITY_TELEPORT.newInstance(this.fakeMountId,
                        loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), false);
                        
                PacketUtil.sendPacket((Player) passenger, movePacket);
            }
        }

        // Velocity is used exclusively for controlling the minecart's audio level
        // When derailed, no audio should be made. Otherwise, the velocity speed controls volume.
        // Minecraft does not play minecart audio for the Y-axis. To make sound on vertical rails,
        // we instead apply the vector length to just the X-axis so that this works.
        Vector currVelocity;
        if (member.isDerailed()) {
            currVelocity = new Vector(0.0, 0.0, 0.0);
        } else {
            currVelocity = new Vector(velLive.length(), 0.0, 0.0);
        }
        boolean velocityChanged = (velSynched.distanceSquared(currVelocity) > (MIN_RELATIVE_VELOCITY * MIN_RELATIVE_VELOCITY)) ||
                (velSynched.lengthSquared() > 0.0 && currVelocity.lengthSquared() == 0.0);

        // Synchronize velocity
        if (absolute || getEntity().isVelocityChanged() || velocityChanged) {
            // Reset dirty velocity
            getEntity().setVelocityChanged(false);

            // Send packets to recipients
            velSynched.set(currVelocity);

            CommonPacket velocityPacket = getVelocityPacket(currVelocity.getX(), currVelocity.getY(), currVelocity.getZ());
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

        // Passengers
        boolean isUpsideDown = MathUtil.getAngleDifference(locLive.getPitch(), 180.0f) < 89.0f;
        if (isUpsideDown != wasUpsideDown) {            
            // First remove all old passengers
            List<Entity> old_passengers = this.getSynchedPassengers();
            for (Player viewer : this.getViewers()) {
                onSyncPassengers(viewer, old_passengers, new ArrayList<Entity>(0));
            }

            // Change mode
            wasUpsideDown = isUpsideDown;

            // Add all passengers again
            for (Player viewer : this.getViewers()) {
                onSyncPassengers(viewer, new ArrayList<Entity>(0), old_passengers);
            }
        }
        this.syncPassengers();

        // Synchronize the head and body rotation of the passenger(s) to the fake mount
        // This makes the fake mount look where the player is looking
        // Position is not important, because the fake mount is mounted (and can not move)
        if (this.wasUpsideDown) {
            Entity passenger = this.getUpsideDownEntity();
            if (passenger != null) {
                EntityHandle realPlayer = EntityHandle.fromBukkit(passenger);

                float yaw = realPlayer.getYaw();
                float pitch = realPlayer.getPitch();
                float headRot = realPlayer.getHeadRotation();

                // Reverse the values and correct head yaw, because the player is upside-down
                pitch = -pitch;
                headRot = -headRot + 2.0f * yaw;

                // Protocolify
                int protYaw = EntityTrackerEntryHandle.getProtocolRotation(yaw);
                int protPitch = EntityTrackerEntryHandle.getProtocolRotation(pitch);
                int protHeadRot = EntityTrackerEntryHandle.getProtocolRotation(headRot);

                if (protYaw != fakePlayerLastYaw || protPitch != fakePlayerLastPitch) {
                    CommonPacket lookPacket = PacketType.OUT_ENTITY_LOOK.newInstance();
                    lookPacket.write(PacketType.OUT_ENTITY_LOOK.entityId, this.fakePlayerId);
                    lookPacket.write(PacketPlayOutEntityHandle.T.dyaw_raw.toFieldAccessor(), (byte) protYaw);
                    lookPacket.write(PacketPlayOutEntityHandle.T.dpitch_raw.toFieldAccessor(), (byte) protPitch);
                    this.broadcast(lookPacket);
                    fakePlayerLastYaw = protYaw;
                    fakePlayerLastPitch = protPitch;
                }

                if (protHeadRot != fakePlayerLastHeadRot) {
                    CommonPacket headPacket = PacketType.OUT_ENTITY_HEAD_ROTATION.newInstance();
                    headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.entityId, this.fakePlayerId);
                    headPacket.write(PacketType.OUT_ENTITY_HEAD_ROTATION.headYaw, (byte) protHeadRot);
                    this.broadcast(headPacket);
                    fakePlayerLastHeadRot = protHeadRot;
                }
            }
        }
    }
}
