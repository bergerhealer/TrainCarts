package com.bergerkiller.bukkit.tc.attachments.old;

import java.util.Collection;
import java.util.Collections;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import com.bergerkiller.generated.net.minecraft.server.EntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.server.PacketPlayOutEntityMetadataHandle;

/**
 * Controls the position and properties of a seat in a train, where a player or other entity can sit in
 */
public class CartSeat {
    private VirtualEntity fakeMount = null; // this is a fake first-person-only mount to change player camera position
    private FakePlayer fakePlayer = null; // this is a fake player entity shown mounted inside the minecart
    private Entity passenger = null;
    private MinecartMemberNetwork network;
    
    public CartSeat(MinecartMemberNetwork network) {
        this.network = network;
    }

    /**
     * Gets the Entity Id of the Entity that is mounted inside the cart directly.
     * Returns -1 if no entity is mounted as part of this seat.
     * 
     * @param viewer
     * @return mounted entity id
     */
    public int getMountedEntityId(Player viewer) {
        if (this.passenger == null) {
            return -1;
        }
        /*
        if ((network.wasUpsideDown || (network.useVirtualCamera && passenger == viewer)) && passenger instanceof Player && this.fakePlayer != null) {
            return this.fakePlayer.entityId;
        } else {
            return this.passenger.getEntityId();
        }
        */
        return -1;
    }

    public void makeFakePlayerVisible(Player viewer) {
        // Make fake player visible if needed
        if (this.fakePlayer != null && this.fakePlayer.wasInvisible) {
            this.fakePlayer.wasInvisible = false;

            DataWatcher metaTmp = new DataWatcher();
            metaTmp.watch(EntityHandle.DATA_FLAGS, (byte) 0);
            FakePlayer.setMetaVisibility(metaTmp, true);
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(this.fakePlayer.entityId, metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);
        }
    }
    
    public boolean destroyFakeMount(Player viewer) {
        if (this.fakeMount != null) {
            this.fakeMount.destroy(viewer);
            this.fakeMount = null;
            return true;
        }
        return false;
    }

    public void updatePosition(MinecartMemberNetwork network, boolean absolute) {
        if (this.fakeMount == null) {
            return;
        }
        
        // Refresh camera position. Mostly useless since it's a constant, but that may change!
        for (Entity passenger : network.getSynchedPassengers()) {
            if (passenger instanceof Player) {
                this.fakeMount.setPosition(network.getMember().getPassengerPosition(passenger));
                break;
            }
        }

        // Move the fake mount with this Minecart
        this.fakeMount.updatePosition(network.getTransform());

        Collection<Entity> passengers = network.getSynchedPassengers();
        for (Player viewer : network.getViewers()) {
            if (passengers.contains(viewer)) {
                this.fakeMount.syncPosition(Collections.singleton(viewer), absolute);
                break;
            }
        }
    }
    
    public void syncRealPlayer(MinecartMemberNetwork network, boolean wasUpsideDown, boolean useVirtualCamera) {
        // Synchronize the head and body rotation of the passenger(s) to the fake player
        // This makes the fake player look where the player/entity is looking
        Player fakeRealPlayer = null;
        if (this.fakePlayer != null && (wasUpsideDown || useVirtualCamera)) {
            for (Entity passenger : network.getSynchedPassengers()) {
                if (passenger instanceof Player && (wasUpsideDown || network.getViewers().contains(passenger))) {
                    fakeRealPlayer = (Player) passenger;
                    break;
                }
            }
        }

        if (fakeRealPlayer != null) {
            EntityHandle realPlayer = EntityHandle.fromBukkit(fakeRealPlayer);

            float yaw = realPlayer.getYaw();
            float pitch = realPlayer.getPitch();
            float headRot = realPlayer.getHeadRotation();

            // Reverse the values and correct head yaw, because the player is upside-down
            if (wasUpsideDown) {
                pitch = -pitch;
                headRot = -headRot + 2.0f * yaw;
            }

            // Only send to self-player when not upside-down
            Collection<Player> viewers;
            if (wasUpsideDown) {
                viewers = network.getViewers();
            } else {
                viewers = Collections.singleton(fakeRealPlayer);
            }
            this.fakePlayer.syncRotation(viewers, yaw, pitch, headRot);
        }
    }
    
    public void sendUpsideDownUnmount(Player viewer, Entity entity) {
        // Destroy fake first-person mount
        if (viewer == entity && this.fakeMount != null) {
            this.fakeMount.destroy(viewer);
        }

        // Destroy a fake player entity - if displayed
        destroyFakeEntity(viewer, entity);
    }

    public void destroyFakeEntity(Player viewer, Entity entity) {
        // Make entity visible again and reset potential nametags by re-sending all metadata
        DataWatcher metaTmp = EntityHandle.fromBukkit(entity).getDataWatcher();
        PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(entity.getEntityId(), metaTmp, true);
        PacketUtil.sendPacket(viewer, metaPacket);

        // Destroy a fake player entity - if displayed
        if (entity instanceof Player && this.fakePlayer != null) {
            this.fakePlayer.destroy(viewer, (Player) entity);
        }
    }

    public void handlePassengerMount(Player viewer, Entity passenger) {
        if (passenger instanceof Player) {
            Player player = (Player) passenger;
            
            // Create a new entity Id for a fake player, if needed
            if (this.fakePlayer == null) {
                this.fakePlayer = new FakePlayer();
            }

            // Refresh name
            /*
            this.fakePlayer.setMode(viewer, player, network.wasUpsideDown ? 
                    FakePlayer.DisplayMode.UPSIDEDOWN : FakePlayer.DisplayMode.NORMAL);
*/
            
            // Make original entity invisible using a metadata change
            DataWatcher metaTmp = new DataWatcher();
            metaTmp.set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(passenger.getEntityId(), metaTmp, true);
            PacketUtil.sendPacket(viewer, metaPacket);

            // Spawn the fake entity
            this.fakePlayer.spawn(viewer, player);
        } else {
            // Apply the upside-down nametag to the entity to turn him upside-down
            // Only do this when upside-down; we don't want to ever show the nametag
            /*
            if (network.wasUpsideDown) {
                DataWatcher metaTmp = new DataWatcher();
                metaTmp.set(EntityHandle.DATA_CUSTOM_NAME, FakePlayer.DisplayMode.UPSIDEDOWN.getPlayerName());
                metaTmp.set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, false);
                PacketPlayOutEntityMetadataHandle metaPacket = PacketPlayOutEntityMetadataHandle.createNew(passenger.getEntityId(), metaTmp, true);
                PacketUtil.sendPacket(viewer, metaPacket);
            }
            */
        }
    }

    public void initMount(MinecartMemberNetwork network, MinecartMember<?> member, Player viewer, Entity passenger) {
        if (this.fakeMount == null) {
            this.fakeMount = new VirtualEntity();
            this.fakeMount.setPosition(member.getPassengerPosition(passenger));

            // When synchronizing passenger to himself, we put him on a fake mount to alter where the camera is at
            this.fakeMount.updatePosition(network.getTransform(false));
            this.fakeMount.syncPosition(Collections.emptyList(), true);
            this.fakeMount.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) (EntityHandle.DATA_FLAG_INVISIBLE));
            this.fakeMount.getMetaData().set(EntityLivingHandle.DATA_HEALTH, 10.0F);
            this.fakeMount.setPassengers(new int[] {passenger.getEntityId()});
            //this.fakeMount.spawn(viewer, network.lastDeltaX, network.lastDeltaY, network.lastDeltaZ);
        }
    }

    public void setPassenger(Entity passenger) {
        this.passenger = passenger;
    }

    public void makeVisible(Player viewer) {
        boolean viewerIsPassenger = network.getSynchedPassengers().contains(viewer);
        /*
        if (network.wasUpsideDown || (network.useVirtualCamera && viewerIsPassenger)) {
            for (Entity passenger : network.getSynchedPassengers()) {
                handlePassengerMount(viewer, passenger);
            }
        }
        */
    }

    public void makeHidden(Player viewer) {
        // If this player has mounted this Minecart and virtual camera is used, unmount the fake mount
        /*
        boolean destroyFakeEntity = network.wasUpsideDown;
        if ((viewer == this.passenger) && network.useVirtualCamera && !network.disableVirtualCameraHandling && this.destroyFakeMount(viewer)) {
            // Also destroy the ghost player that took this person's place in the Minecart
            destroyFakeEntity = true;
        }
        */

        // When upside-down, destroy the fake entity that is displayed
        //if (destroyFakeEntity) {
        //    this.destroyFakeEntity(viewer, this.passenger);
        //}
    }
}
