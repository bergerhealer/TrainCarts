package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.ImplicitlySharedSet;
import com.bergerkiller.bukkit.common.conversion.type.HandleConversion;
import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.protocol.CommonPacket;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.common.wrappers.HumanHandRole;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.attachment.AttachmentInteractEvent;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundAttackPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerInputPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundInteractPacketHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundSwingPacketHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.player.PlayerHandle;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Processes incoming packets from the client and makes corrective changes to them, to make
 * interactions with trains work properly:
 * <ul>
 *     <li>Handles the clicks on attachments and rewriting them as interactions with the actual minecart entity.</li>
 *     <lI>Handles the 'sneak' toggling by clients, to differentiate vehicle-exits as player-initiated or not</lI>
 *     <li>Handles clicks with blocks that were meant for the train occupying that space</li>
 * </ul>
 */
class TCPacketListener implements PacketListener {
    public static final int ATTACK_SUPPRESS_DURATION = 250; // 250ms
    public static final PacketType[] LISTENED_TYPES = new PacketType[] {
            /** Normal entity click interaction handling / sneak handling */
            PacketType.IN_STEER_VEHICLE,
            PacketType.IN_INTERACT,
            PacketType.IN_ATTACK,
            PacketType.IN_PLAYER_COMMAND,

            /** Block optimization handling */
            PacketType.IN_USE_ITEM,
            PacketType.IN_USE_ITEM_ON,
            PacketType.IN_SWING,
            PacketType.IN_PUNCH,
            PacketType.IN_PLAYER_ACTION
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

        // If disabled, do nothing
        if (TCConfig.optimizeInteraction) {
            optimizeBlockInteraction(event);
            if (event.isCancelled()) {
                return;
            }
        }

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
                        Attachment clickedAttachment = member.getAttachments().findAttachmentByEntityId(entityId);
                        if (clickedAttachment == null) {
                            continue; // Id is not used in the model
                        }

                        // Cancel the attack and handle this ourselves.
                        handleInteractionWithAttachment(member, new AttachmentInteractEvent(
                                event.getPlayer(),
                                true,
                                HumanHand.getMainHand(event.getPlayer()),
                                null,
                                entityId,
                                clickedAttachment,
                                null
                        ));
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } else if (event.getType() == PacketType.IN_INTERACT) {
            ServerboundInteractPacketHandle packet_use = ServerboundInteractPacketHandle.createHandle(event.getPacket().getHandle());

            // Extract sneaking as true/false or null if not supported/known by packet API
            Boolean sneaking = ServerboundInteractPacketHandle.hasSecondaryActionField() ?
                    packet_use.isUsingSecondaryAction() : null;

            // Since 1.16 this packet has a sneaking property
            // If we're inside a vehicle, disable it
            if (sneaking != null && sneaking) {
                if (player.getVehicle() == null) {
                    TCSeatChangeListener.markForUnmounting(traincarts, player);
                } else if (!traincarts.handlePlayerVehicleChange(player, null)) {
                    sneaking = Boolean.FALSE;
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

            // Save
            final Location eyeLoc = event.getPlayer().getEyeLocation();

            // Handle the click using the MinecartGroup the player's own vehicle is part of, if any
            // It's better to check for this one first, as it's most likely to intercept the click, avoiding a global iteration
            MinecartMember<?> playerVehicleMember = MinecartMemberStore.getFromEntity(player.getVehicle());
            MinecartGroup playerVehicleGroup = (playerVehicleMember != null && playerVehicleMember.hasInitializedGroup())
                    ? playerVehicleMember.getGroup() : null;
            if (playerVehicleGroup != null && handleInteractionWithGroup(player, sneaking, eyeLoc, playerVehicleGroup, entityId, packet_use)) {
                event.setCancelled(true);
                return;
            }

            // Try all groups (near to player)
            try (ImplicitlySharedSet<MinecartGroup> groups = MinecartGroupStore.getGroups().clone()) {
                for (MinecartGroup group : groups) {
                    if (group != playerVehicleGroup && handleInteractionWithGroup(player, sneaking, eyeLoc, group, entityId, packet_use)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    /**
     * Handles block interaction packets and translates them into packets
     * interacting with minecarts that are in those positions.
     * This ensures players can 'click' into trains more easily by clicking on rails/blocks behind the
     * train, when that click passes through the bounding box of a train.
     *
     * @param event PacketReceiveEvent
     */
    private void optimizeBlockInteraction(PacketReceiveEvent event) {

        // Ignore all this logic while the player is sneaking, so that interacting with blocks still works
        if (event.getPlayer().isSneaking()) {
            return;
        }

        // Store last hit when USE_ITEM is called, to avoid ENTITY_ANIMATION attack() being called incorrectly
        if (event.getType() == PacketType.IN_USE_ITEM_ON) {
            suppressAttacksFor(event.getPlayer(), TCPacketListener.ATTACK_SUPPRESS_DURATION);
        }

        // Ignore IN_BLOCK_DIG packets that are uninteresting to us
        if (event.getType() == PacketType.IN_PLAYER_ACTION) {
            String status = event.getPacket().read(PacketType.IN_PLAYER_ACTION.status).toString();
            if (!status.equals("START_DESTROY_BLOCK")) {
                return;
            }
        }

        // For BLOCK_DIG and arm swings we check if clicking on a player, handling the entity attack() event
        // When we cancel a BLOCK_DIG event, be sure to send a block update of the block being dug
        // For arm animation, make sure it is that of the player's off hand.
        boolean isAttackClick = false;
        if (event.getType() == PacketType.IN_PLAYER_ACTION) {
            isAttackClick = true;
        } else if (event.getType() == PacketType.IN_PUNCH) {
            if (isAttackSuppressed(event.getPlayer())) {
                event.setCancelled(true);
                return;
            }
            isAttackClick = true;
        } else if (event.getType() == PacketType.IN_SWING) {
            ServerboundSwingPacketHandle packet = ServerboundSwingPacketHandle.createHandle(event.getPacket().getHandle());
            if (packet.getHandRole() == HumanHandRole.OFF) {
                if (isAttackSuppressed(event.getPlayer())) {
                    event.setCancelled(true);
                    return;
                }
                isAttackClick = true;
            }
        }
        if (isAttackClick) {
            MinecartMember<?> member = MinecartMemberStore.getFromHitTest(Util.getRealEyeLocation(event.getPlayer()));
            if (member != null) {
                event.setCancelled(true);
                simulateDefaultAttack(member, event.getPlayer());

                // When packet is IN_BLOCK_DIG, send a block change to correct the changed block
                if (event.getType() == PacketType.IN_PLAYER_ACTION) {
                    IntVector3 pos = event.getPacket().read(PacketType.IN_PLAYER_ACTION.position);
                    cancelBlockChanges(event.getPlayer(), pos);
                }
            }
            return;
        }

        // IN_BLOCK_PLACE occurs when the player right-clicks the air while holding an item
        // USE_ITEM occurs when the player right-clicks a block
        // In both cases, when clicking on a cart, cancel the event and perform interaction
        if (event.getType() == PacketType.IN_USE_ITEM || event.getType() == PacketType.IN_USE_ITEM_ON) {
            MinecartMember<?> member = MinecartMemberStore.getFromHitTest(event.getPlayer().getEyeLocation());
            if (member != null) {
                HumanHand hand;
                if (event.getType() == PacketType.IN_USE_ITEM) {
                    hand = PacketType.IN_USE_ITEM.getHand(event.getPacket(), event.getPlayer());
                } else {
                    hand = PacketType.IN_USE_ITEM_ON.getHand(event.getPacket(), event.getPlayer());

                    // Cancel any block changes by re-sending the block clicked (and the block at the face)
                    IntVector3 pos = event.getPacket().read(PacketType.IN_USE_ITEM_ON.position);
                    BlockFace dir =  event.getPacket().read(PacketType.IN_USE_ITEM_ON.direction);
                    cancelBlockChanges(event.getPlayer(), pos);
                    cancelBlockChanges(event.getPlayer(), pos.add(dir));
                }

                event.setCancelled(true);
                this.simulateDefaultInteract(member, event.getPlayer(), hand, member.getEntity().getLocation().toVector());
            }

            return;
        }
    }

    private void cancelBlockChanges(Player player, IntVector3 pos) {
        // Check chunk is loaded in this area to protect against rogue client data
        if (WorldUtil.isLoaded(player.getWorld(), pos.x, pos.y, pos.z)) {
            CommonPacket bcPacket = PacketType.OUT_BLOCK_CHANGE.newInstance();
            bcPacket.write(PacketType.OUT_BLOCK_CHANGE.position, pos);
            bcPacket.write(PacketType.OUT_BLOCK_CHANGE.blockData, WorldUtil.getBlockData(player.getWorld(), pos));
            PacketUtil.sendPacket(player, bcPacket);
        }
    }

    /**
     * Handles an interaction with a particular MinecartGroup, if the entityId is part of it. If so, the interaction is handled and true is returned.
     *
     * @param player Player that interacted
     * @param sneaking Tri-state sneaking state of the player during the click (null if none supplied)
     * @param eyeLoc Player Eye-Location at the time of the click
     * @param group MinecartGroup to probe
     * @param entityId Entity ID the player clicked
     * @param packet_use Original interact packet
     * @return True if the interaction was handled (intercepted), and server-side handling should be skipped
     */
    private boolean handleInteractionWithGroup(
            final Player player,
            final Boolean sneaking,
            final Location eyeLoc,
            final MinecartGroup group,
            final int entityId,
            final ServerboundInteractPacketHandle packet_use
    ) {
        if (group.getWorld() != eyeLoc.getWorld()) {
            return false;
        }

        for (MinecartMember<?> member : group) {
            if (!member.getAttachments().isViewer(player)) {
                continue; // If not visible, don't loop through the model to check this
            }
            Attachment clickedAttachment = member.getAttachments().findAttachmentByEntityId(entityId);
            if (clickedAttachment == null) {
                continue; // Id is not used in the model
            }

            // Interaction with position fires for all entities including Armorstands
            // Before 26.1, it only fires without position for interactable entities, like Minecarts
            // Since it fires with position also for Minecarts, it is easier to ignore the one without
            // and do all handling with the one that has position.
            if (!packet_use.hasInteractAtPosition()) {
                return true; // handled
            }

            // Cancel the interaction and handle this ourselves.
            handleInteractionWithAttachment(member, new AttachmentInteractEvent(
                    player,
                    false,
                    packet_use.getHand(player),
                    sneaking,
                    entityId,
                    clickedAttachment,
                    packet_use.getInteractAtPosition()
            ));
            return true; // Handled
        }

        return false;
    }

    private void applySneaking(final AttachmentInteractEvent event) {
        if (event.getPlayer().isSneaking() != event.isSneaking()) {
            event.getPlayer().setSneaking(event.isSneaking());
        }
    }

    public void handleInteractionWithAttachment(final MinecartMember<?> member, final AttachmentInteractEvent event) {
        // Fix cross-thread access / sanity checks / sneaking handling
        if (CommonUtil.isMainThread()) {
            applySneaking(event);
        } else {
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    applySneaking(event);

                    // Avoid errors down below when we check attachments...
                    if (member.isUnloaded()) {
                        return;
                    }

                    // Validate that the clicked attachment is still there and is the one that was clicked
                    // Some weird asynchronous access could break this.
                    AttachmentInteractEvent eventMut = event;
                    if (!event.getAttachment().isAttached() || !event.getAttachment().containsEntityId(event.getInteractedEntityId())) {
                        Attachment attachment = member.getAttachments().findAttachmentByEntityId(event.getInteractedEntityId());
                        if (attachment == null || attachment == event.getAttachment()) {
                            return; // Bugged?
                        } else {
                            eventMut = event.cloneWithDifferentAttachment(attachment);
                        }
                    }

                    handleInteractionWithAttachment(member, eventMut);
                }
            });
            return;
        }

        // If member or player is unloaded or was despawned during this time, it's no longer valid
        if (member.isUnloaded() || !event.getPlayer().isValid()) {
            return;
        }

        // Fire Bukkit event first, if not cancelled, also pass it by the actual attachment implementation
        CommonUtil.callEvent(event);
        if (!event.isCancelled()) {
            event.getAttachment().onInteract(event);
        }

        // If not cancelled / default prevented, simulate default behavior
        if (!event.isCancelled() && !event.isPreventDefault()) {
            if (event.isAttack()) {
                simulateDefaultAttack(member, event.getPlayer());
            }
            if (event.isInteract()) {
                simulateDefaultInteract(member, event.getPlayer(), event.getHand(), event.getAtPosition());
            }
        }
    }

    /**
     * Simulates the Player attacking a Minecart, handling default behavior for that
     *
     * @param member MinecartMember
     * @param player Plaeyr that attacked
     */
    public void simulateDefaultAttack(final MinecartMember<?> member, final Player player) {
        // Fix cross-thread access / sanity checks / sneaking handling
        if (!CommonUtil.isMainThread()) {
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    simulateDefaultAttack(member, player);
                }
            });
            return;
        }

        if (member.isUnloaded() || !player.isValid()) {
            return;
        }

        Object playerHandleRaw = HandleConversion.toEntityHandle(player);
        PlayerHandle.createHandle(playerHandleRaw).attack(member.getEntity().getEntity());
    }

    /**
     * Simulates the Player interacting with a Minecart, handling the default behavior of entering the vehicle
     * and related Bukkit events for that.
     *
     * @param member MinecartMember
     * @param player Player
     * @param hand Hand used to interact
     * @param atPosition At-position where the player clicked, or <i>null</i> if unknown
     */
    public void simulateDefaultInteract(
            final MinecartMember<?> member,
            final Player player,
            final HumanHand hand,
            final Vector atPosition
    ) {
        // Fix cross-thread access / sanity checks / sneaking handling
        if (!CommonUtil.isMainThread()) {
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    simulateDefaultInteract(member, player, hand, atPosition);
                }
            });
            return;
        }

        if (member.isUnloaded() || !player.isValid()) {
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

            // This event always fires
            {
                PlayerInteractAtEntityEvent interactAtEvent = new PlayerInteractAtEntityEvent(player, member.getEntity().getEntity(), atPosition, slot);
                if (CommonUtil.callEvent(interactAtEvent).isCancelled()) {
                    return;
                }
            }

            // Before 26.1.2, the PlayerInteractEntityEvent has a handler list separate from at, and needs to be fired also.
            if (PlayerInteractAtEntityEvent.getHandlerList() != PlayerInteractEntityEvent.getHandlerList()) {
                PlayerInteractEntityEvent interactAtEvent = new PlayerInteractEntityEvent(player, member.getEntity().getEntity(), slot);
                if (CommonUtil.callEvent(interactAtEvent).isCancelled()) {
                    return;
                }
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
        member.onInteractBy(player, hand, atPosition);
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
