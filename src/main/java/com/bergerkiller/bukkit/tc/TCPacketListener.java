package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.conversion.type.HandleConversion;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundAttackPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerInputPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundInteractPacketHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.player.PlayerHandle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Temporary (???) packet listener to handle and cancel player SHIFT presses to cancel vehicle exit
 */
class TCPacketListener implements PacketListener {
    public static final int ATTACK_SUPPRESS_DURATION = 250; // 250ms
    public static final PacketType[] LISTENED_TYPES = new PacketType[] {
            PacketType.IN_STEER_VEHICLE, PacketType.IN_INTERACT, PacketType.IN_ATTACK, PacketType.IN_PLAYER_COMMAND
    };

    private final TrainCarts traincarts;
    private final Map<Player, Long> lastHitTime = new HashMap<Player, Long>();

    public TCPacketListener(TrainCarts traincarts) {
        this.traincarts = traincarts;
    }

    public void suppressAttacksFor(Player player, int durationMillis) {
        synchronized (lastHitTime) {
            if (lastHitTime.isEmpty()) {
                new HitTimeCleanTask(traincarts).start(1, 1);
            }
            lastHitTime.put(player, System.currentTimeMillis() + durationMillis);
        }
    }

    public boolean isAttackSuppressed(Player player) {
        synchronized (lastHitTime) {
            return lastHitTime.containsKey(player);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Thread.dumpStack();
    }

    private final Consumer<PacketReceiveEvent> steerHandler = Common.hasCapability("Common:PacketListener:SetPacket") ?
            this::handleSteerNew : this::handleSteerLegacy;

    private void handleSteerLegacy(PacketReceiveEvent event) {
        CommonPacket packet = event.getPacket();
        if (packet.read(PacketType.IN_STEER_VEHICLE.unmount)) {
            Player player = event.getPlayer();

            // Handle vehicle exit cancelling
            if (player.getVehicle() == null) {
                TCSeatChangeListener.markForUnmounting(traincarts, player);
            } else if (!traincarts.handlePlayerVehicleChange(player, null)) {
                packet.write(PacketType.IN_STEER_VEHICLE.unmount, false);
            }
        }
    }

    private void handleSteerNew(PacketReceiveEvent event) {
        ServerboundPlayerInputPacketHandle steerPacket = ServerboundPlayerInputPacketHandle.createHandle(event.getPacket().getHandle());
        if (steerPacket.isUnmount()) {
            // Handle vehicle exit cancelling
            Player player = event.getPlayer();
            if (player.getVehicle() == null) {
                TCSeatChangeListener.markForUnmounting(traincarts, player);
            } else if (!traincarts.handlePlayerVehicleChange(player, null)) {
                //packet.write(PacketType.IN_STEER_VEHICLE.unmount, false);
                event.setPacket(ServerboundPlayerInputPacketHandle.createNew(
                        steerPacket.isLeft(),
                        steerPacket.isRight(),
                        steerPacket.isForward(),
                        steerPacket.isBackward(),
                        steerPacket.isJump(),
                        false,
                        steerPacket.isSprint()
                ));
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        CommonPacket packet = event.getPacket();
        Player player = event.getPlayer();

        // Note: sometimes an unmount packet is sent before the player is actually inside a vehicle.
        // However, this could also be because of a virtual invisible entity being controlled by the player.
        // We will allow the unmount, but if it spawns a vehicle exit event later on, we must cancel that
        // event. This is a compromise so that other plugins can still freely eject the player, without
        // the player exit property blocking that behavior.
        if (event.getType() == PacketType.IN_PLAYER_COMMAND) {
            String action = ((Enum<?>) packet.read(PacketType.IN_PLAYER_COMMAND.action)).name();
            if (action.equals("START_SNEAKING") || action.equals("PRESS_SHIFT_KEY")) {
                // Player wants to exit, if inside a vehicle
                if (player.getVehicle() == null) {
                    TCSeatChangeListener.markForUnmounting(traincarts, player);
                } else if (!traincarts.handlePlayerVehicleChange(player, null)) {
                    // Cancel it!
                    event.setCancelled(true);
                }
            }
        }

        if (event.getType() == PacketType.IN_STEER_VEHICLE) {
            steerHandler.accept(event);
            return;
        }

        if (event.getType() == PacketType.IN_ATTACK) {
            ServerboundAttackPacketHandle packet_attack = ServerboundAttackPacketHandle.createHandle(event.getPacket().getHandle());

            // When a player interacts with a virtual attachment, the main entity should receive the interaction
            int entityId = packet_attack.getEntityId();
            if (WorldUtil.getEntityById(event.getPlayer().getWorld(), entityId) != null) {
                return; // Is a valid Entity. Ignore it.
            }

            // Don't know how to deal with this one
            if (event.getPlayer().getGameMode().name().equals("SPECTATOR")) {
                return;
            }

            // Find all Minecart entities that are nearby the player
            Location eyeLoc = event.getPlayer().getEyeLocation();
            try (ImplicitlySharedSet<MinecartGroup> groups = MinecartGroupStore.getGroups().clone()) {
                for (MinecartGroup group : groups) {
                    if (group.getWorld() != eyeLoc.getWorld()) {
                        continue;
                    }

                    for (MinecartMember<?> member : group) {
                        if (!member.getAttachments().isViewer(event.getPlayer())) {
                            continue; // If not visible, don't loop through the model to check this
                        }
                        if (!member.getAttachments().isAttachment(entityId)) {
                            continue; // Id is not used in the model
                        }

                        // If nearby the player, allow standard interaction. Otherwise, do all of this ourselves.
                        // Minecraft enforces a 3 block radius when not having line of sight, assume this limit.
                        if (member.getEntity().loc.distanceSquared(eyeLoc) < (3.0 * 3.0)) {
                            // Rewrite the packet
                            event.setPacket(ServerboundAttackPacketHandle.createNew(member.getEntity().getEntityId()));
                            return; // Allow
                        }

                        // Cancel the attack and handle this ourselves.
                        fakeAttack(member, event.getPlayer());
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } else if (event.getType() == PacketType.IN_INTERACT) {
            ServerboundInteractPacketHandle packet_use = ServerboundInteractPacketHandle.createHandle(event.getPacket().getHandle());

            // Since 1.16 this packet has a sneaking property
            // If we're inside a vehicle, disable it
            if (packet_use.isUsingSecondaryAction()) {
                if (player.getVehicle() == null) {
                    TCSeatChangeListener.markForUnmounting(traincarts, player);
                } else if (!traincarts.handlePlayerVehicleChange(player, null)) {
                    packet_use = ServerboundInteractPacketHandle.withUsingSecondaryAction(packet_use, false);
                    event.setPacket(packet_use);
                }
            }

            // When a player interacts with a virtual attachment, the main entity should receive the interaction
            int entityId = packet_use.getUsedEntityId();
            if (WorldUtil.getEntityById(event.getPlayer().getWorld(), entityId) != null) {
                return; // Is a valid Entity. Ignore it.
            }

            // Don't know how to deal with this one
            if (event.getPlayer().getGameMode().name().equals("SPECTATOR")) {
                return;
            }

            // Find all Minecart entities that are nearby the player
            Location eyeLoc = event.getPlayer().getEyeLocation();
            try (ImplicitlySharedSet<MinecartGroup> groups = MinecartGroupStore.getGroups().clone()) {
                for (MinecartGroup group : groups) {
                    if (group.getWorld() != eyeLoc.getWorld()) {
                        continue;
                    }

                    for (MinecartMember<?> member : group) {
                        if (!member.getAttachments().isViewer(event.getPlayer())) {
                            continue; // If not visible, don't loop through the model to check this
                        }
                        if (!member.getAttachments().isAttachment(entityId)) {
                            continue; // Id is not used in the model
                        }

                        // Interaction with position fires for all entities including Armorstands
                        // Before 26.1, it only fires without position for interactable entities, like Minecarts
                        // Since it fires with position also for Minecarts, it is easier to ignore the one without
                        // and do all handling with the one that has position.
                        if (!packet_use.hasInteractAtPosition()) {
                            event.setCancelled(true);
                            return;
                        }

                        // If nearby the player, allow standard interaction. Otherwise, do all of this ourselves.
                        // Minecraft enforces a 3 block radius when not having line of sight, assume this limit.
                        if (member.getEntity().loc.distanceSquared(eyeLoc) < (3.0 * 3.0)) {
                            // Must track this to cancel superfluous LEFT clicks that happen later
                            this.suppressAttacksFor(event.getPlayer(), ATTACK_SUPPRESS_DURATION);

                            // Rewrite the packet
                            packet_use = ServerboundInteractPacketHandle.withUsedEntityId(packet_use, member.getEntity().getEntityId());
                            event.setPacket(packet_use);
                            return; // Allow
                        }

                        // Cancel the interaction and handle this ourselves.
                        HumanHand hand = packet_use.getHand(event.getPlayer());
                        fakeInteraction(member, event.getPlayer(), hand, packet_use.getInteractAtPosition());
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    public static void fakeAttack(final MinecartMember<?> member, final Player player) {
        // Fix cross-thread access
        if (!CommonUtil.isMainThread()) {
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    fakeAttack(member, player);
                }
            });
            return;
        }

        // If member is unloaded or was despawned during this time, it's no longer valid
        if (member == null || member.isUnloaded() || player == null || !player.isValid()) {
            return;
        }

        Object playerHandleRaw = HandleConversion.toEntityHandle(player);
        PlayerHandle.createHandle(playerHandleRaw).attack(member.getEntity().getEntity());
    }

    public void fakeInteraction(final MinecartMember<?> member, final Player player, final HumanHand hand, final Vector atPosition) {
        this.suppressAttacksFor(player, ATTACK_SUPPRESS_DURATION);

        // Fix cross-thread access
        if (!CommonUtil.isMainThread()) {
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    fakeInteraction(member, player, hand, atPosition);
                }
            });
            return;
        }

        // If member is unloaded or was despawned during this time, it's no longer valid
        if (member == null || member.isUnloaded() || player == null || !player.isValid()) {
            return;
        }

        if (CommonCapabilities.PLAYER_OFF_HAND) {
            HumanHand mainHand = HumanHand.getMainHand(player);

            // Fire a Bukkit event first, as defined in PlayerConnection PacketPlayInUseEntity handler
            EquipmentSlot slot = EquipmentSlot.HAND;
            if (hand != mainHand) {
                // Needed in case it errors out for no reason on MC 1.8 or somesuch
                try {
                    slot = EquipmentSlot.OFF_HAND;
                } catch (Throwable t) {}
            }

            // Post-1.9: EquipmentSlot parameter
            PlayerInteractAtEntityEvent interactAtEvent = new PlayerInteractAtEntityEvent(player, member.getEntity().getEntity(), atPosition, slot);
            if (CommonUtil.callEvent(interactAtEvent).isCancelled()) {
                return;
            }
        } else {
            // Pre-1.9
            PlayerInteractEntityEvent interactEvent;
            if (atPosition != null) {
                interactEvent = new PlayerInteractAtEntityEvent(player, member.getEntity().getEntity(), atPosition);
            } else {
                interactEvent = new PlayerInteractEntityEvent(player, member.getEntity().getEntity());
            }
            if (CommonUtil.callEvent(interactEvent).isCancelled()) {
                return;
            }
        }

        // Interact
        member.onInteractBy(player, hand);
    }

    private final class HitTimeCleanTask extends Task {

        public HitTimeCleanTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            synchronized (lastHitTime) {
                long timeout = System.currentTimeMillis();
                Iterator<Long> iter = lastHitTime.values().iterator();
                while (iter.hasNext()) {
                    if (timeout >= iter.next().longValue()) {
                        iter.remove();
                    }
                }
                if (lastHitTime.isEmpty()) {
                    this.stop();
                }
            }
        }
    }
}
