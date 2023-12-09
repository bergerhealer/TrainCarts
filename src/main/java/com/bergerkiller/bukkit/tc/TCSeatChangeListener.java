package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.bergerkiller.bukkit.common.controller.EntityNetworkController;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatChangeEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatEnterEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberBeforeSeatExitEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatEnterEvent;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatExitEvent;
import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.tickets.TicketStore;

/**
 * Handles seat enter/exit/change and vehicle enter/exit events for MinecartMember entity
 * enter and exit logic handling. It is here that logic, like the player enter/exit permissions,
 * are checked.
 */
public class TCSeatChangeListener implements Listener {
    public static boolean suppressSeatChangeEvents = false; // When true, seat enter/exit/change events are not fired during vehicle enter/exit
    public static List<Entity> exemptFromEjectOffset = new ArrayList<Entity>();
    private static Map<Player, Integer> markedForUnmounting = new HashMap<Player, Integer>();

    /*
     * Fires a BeforeSeatEnter event when handling a BeforeSeatChangeEvent.
     * This way cancelling either will show up in monitor stage correctly.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMemberSeatChangeFireEnterEvent(MemberBeforeSeatChangeEvent event) {
        // Fire an enter event as well
        MemberBeforeSeatEnterEvent enterEvent = new MemberBeforeSeatEnterEvent(event.getEnteredSeat(),
                event.getEntity(), event.isPlayerInitiated(),
                true, /* was seat change */
                event.isMemberVehicleChange() /* is a vehicle change */);
        CommonUtil.callEvent(enterEvent);
        event.setCancelled(enterEvent.isCancelled());
        event.setEnteredSeat(enterEvent.getSeat());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMemberSeatExit(MemberBeforeSeatExitEvent event) {
        if (!event.isSeatChange()) {
            handleVehicleChange(event.getMember(), null, event.getEntity(), event.isPlayerInitiated());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMemberSeatChange(MemberBeforeSeatChangeEvent event) {
        event.setCancelled(!handleVehicleChange(event.getMember(), event.getEnteredMember(),
                event.getEntity(), event.isPlayerInitiated()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMemberSeatEnter(MemberBeforeSeatEnterEvent event) {
        if (event.wasSeatChange()) {
            return; // Already handled by MemberSeatChangeEvent
        }
        event.setCancelled(!handleVehicleChange(null, event.getMember(),
                event.getEntity(), event.isPlayerInitiated()));
    }

    private boolean handleVehicleChange(MinecartMember<?> old_member, MinecartMember<?> new_member, Entity passenger, boolean playerInitiated) {
        // Don't allow entering a cart that is in a frozen state
        if (new_member != null && !new_member.isInteractable()) {
            return false;
        }

        // Player enter/exit property and tickets are checked when players initiate the change of seat
        if (playerInitiated && passenger instanceof Player) {
            // Note: only seat changes have reliable playerInitated property. Seat exit events
            // cannot properly know whether the exit was because of holding shift, because the
            // server does not disclose this information.
            if (old_member != null && new_member != null && !old_member.getProperties().getPlayersExit()) {
                return false;
            }

            if (new_member != null) {
                CartProperties prop = new_member.getProperties();
                if (!prop.getPlayersEnter()) {
                    return false;
                }
                if (prop.getCanOnlyOwnersEnter() && !prop.hasOwnership((Player) passenger)) {
                    return false;
                }
                if (old_member == null || old_member.getGroup() != new_member.getGroup()) {
                    // Handle use of tickets
                    if (!TicketStore.handleTickets((Player) passenger, new_member.getGroup().getProperties())) {
                        return false;
                    }
                }
            }
        }

        return true; // Permitted
    }

    /*
     * ======================================================================================================
     * ==================================== Seat change monitoring code =====================================
     * ======================================================================================================
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberSeatExitHandleEjectOffset(MemberSeatExitEvent event) {
        if (!event.isSeatChange() && !exemptFromEjectOffset.contains(event.getEntity())) {
            final Entity e = event.getEntity();
            if (e.isDead() || e.getVehicle() != null || event.getMember().getEntity() == null) {
                return;
            }

            final Location old_entity_location = event.getMember().getEntity().getLocation();
            final Location old_seat_location = event.getSeatPosition();
            final Location loc = event.getExitPosition();

            // Teleport to the exit position a tick later
            // Edited: no longer has to be next tick, the seat exit event is guaranteed to occur AFTER
            //   CommonUtil.nextTick(new Runnable() {});

            // Do not teleport if the player changed position dramatically after exiting
            // This is the case when teleporting (/tp)
            // The default vanilla exit position is going to be at most 1 block away in all axis
            // Check both seat and entity location. Players can sync their perceived seat
            // location which the server accepts as an actual position.
            Location new_location = e.getLocation();
            if (!isPossibleExit(new_location, old_entity_location)
                    && !isPossibleExit(new_location, old_seat_location))
            {
                return;
            }

            Util.correctTeleportPosition(loc);
            e.teleport(loc);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberSeatExitMonitor(MemberSeatExitEvent event) {
        // Don't do this when not interactable (unloaded or dead) as that breaks things
        if (!event.getMember().isInteractable()) {
            return;
        }

        // Avoid rapid re-entering due to enter collision rule
        if (event.isMemberVehicleChange()) {
            event.getMember().resetCollisionEnter();
        }

        // Ensure a properties changed notification occurs soon
        // This sets a flag for signs, they're notified later (after the entering has concluded)
        event.getMember().onPropertiesChanged();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMemberSeatEnterMonitor(MemberSeatEnterEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            CartProperties cprop = event.getMember().getProperties();
            cprop.getTrainCarts().getPlayer(player).editCart(cprop);
            if (event.wasMemberVehicleChange()) {
                cprop.showEnterMessage(player);
            }
        }

        // Ensure a properties changed notification occurs soon
        // This sets a flag for signs, they're notified later (after the entering has concluded)
        event.getMember().onPropertiesChanged();
    }

    /*
     * ======================================================================================================
     * ====================== Translates unexpected vehicle enter/exit into seat events =====================
     * ======================================================================================================
     */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        // Fire a member enter event for other plugins to handle, if not suppressed
        // We know for certain at this point the entity isn't inside another vehicle/member,
        // because a VehicleExitEvent is fired beforehand (ejecting the entity)
        MinecartMember<?> member;
        if (!suppressSeatChangeEvents && (member = MinecartMemberStore.getFromEntity(event.getVehicle())) != null) {
            final CartAttachmentSeat seat = member.getAttachments().findNewSeatForEntity(event.getEntered());
            if (seat != null) {
                MemberBeforeSeatEnterEvent memberEnterEvent = new MemberBeforeSeatEnterEvent(seat,
                        event.getEntered(),
                        false, /* player initiated */
                        false, /* was seat change */
                        true /* is a vehicle change */);
                if (CommonUtil.callEvent(memberEnterEvent).isCancelled()) {
                    event.setCancelled(true);
                    return;
                }
                if (memberEnterEvent.getSeat() != seat) {
                    // Seat was changed. We got to store a new seat hint, if it's still the same member.
                    // If it's not the same member, then we got to abort this entire event and try again.
                    // No seat enter event is fired, then
                    memberEnterEvent.getSeat().getController().storeSeatHint(event.getEntered(), memberEnterEvent.getSeat());
                    if (memberEnterEvent.getSeat().getMember() != member) {
                        // This event is now garbage. Don't enter this vehicle please.
                        event.setCancelled(true);

                        // Enter the new member instead, which will fire another VehicleEnterEvent
                        // We suppress the handling of that event
                        try {
                            suppressSeatChangeEvents = true;

                            // Probably not needed, but make sure the entity isn't inside a vehicle
                            event.getEntered().eject();
                            if (event.getEntered().getVehicle() == null) {
                                if (!memberEnterEvent.getSeat().getMember().getEntity().addPassenger(event.getEntered())) {
                                    return; // Cancelled adding of new passenger
                                }
                            }
                        } finally {
                            suppressSeatChangeEvents = false;
                        }
                    }
                }

                // Next tick, if passenger is still in the member, fire a seat enter event
                final Entity vehicle = event.getVehicle();
                final Entity passenger = event.getEntered();
                CommonUtil.nextTick(() -> {
                    if (passenger.getVehicle() == vehicle) {
                        CommonUtil.callEvent(new MemberSeatEnterEvent(seat, passenger,
                                false, /* player initiated */
                                false, /* previously exited another seat (cant track) */
                                true /* player changed vehicle */
                        ));
                    }
                });

            } else {
                // Weird? No seat we could enter. Abort it.
                event.setCancelled(true);
                return;
            }
        }
    }

    /*
     * Bukkit now sends a VehicleExitEvent after a cancelled VehicleEnterEvent event.
     * To prevent the player teleporting into the Minecart, make him exempt here.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVehicleEnterCheck(VehicleEnterEvent event) {
        if (event.isCancelled()) {
            final Entity entered = event.getEntered();
            exemptFromEjectOffset.add(entered);
            CommonUtil.nextTick(new Runnable() {
                @Override
                public void run() {
                    exemptFromEjectOffset.remove(entered);
                }
            });
        }
    }

    /**
     * Tells the listener that a player decided, for itself, to exit the Minecart, but that
     * it is not known yet what vehicle the player is inside of.
     *
     * @param traincarts TrainCarts main plugin instance
     * @param player
     */
    public static void markForUnmounting(TrainCarts traincarts, Player player) {
        synchronized (markedForUnmounting) {
            if (markedForUnmounting.isEmpty()) {
                new Task(traincarts) {
                    @Override
                    public void run() {
                        synchronized (markedForUnmounting) {
                            int curr_ticks = CommonUtil.getServerTicks();
                            Iterator<Map.Entry<Player, Integer>> iter = markedForUnmounting.entrySet().iterator();
                            while (iter.hasNext()) {
                                Map.Entry<Player, Integer> e = iter.next();
                                if (e.getKey().isSneaking() && e.getKey().getVehicle() == null) {
                                    e.setValue(Integer.valueOf(curr_ticks));
                                } else if ((curr_ticks - e.getValue().intValue()) >= 2) {
                                    iter.remove();
                                }
                            }
                            if (markedForUnmounting.isEmpty()) {
                                stop();
                            }
                        }
                    }
                }.start(1, 1);
            }
            markedForUnmounting.put(player, CommonUtil.getServerTicks());
        }
    }

    /*
     * We must handle vehicle exit for when an unmount packet is received before
     * the player is actually seated inside a vehicle. Player exit is normally
     * handled inside the packet listener instead of here.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleExitCheck(VehicleExitEvent event) {
        // Only do this check when marked for unmounting by the packet listener
        synchronized (markedForUnmounting) {
            if (!markedForUnmounting.containsKey(event.getExited())) {
                return;
            }
        }

        MinecartMember<?> mm = MinecartMemberStore.getFromEntity(event.getVehicle());
        if (mm != null && !mm.getProperties().getPlayersExit()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        final MinecartMember<?> member;
        if (!suppressSeatChangeEvents && (member = MinecartMemberStore.getFromEntity(event.getVehicle())) != null) {
            final CartAttachmentSeat seat = member.getAttachments().findSeat(event.getExited());
            if (seat != null) {
                // Note: this isn't very reliable. If player sneaks and a plugin ejects the player, it
                // will still return true incorrectly. There is no clean workaround for this sadly.
                final boolean playerInitiated = event.getExited() instanceof Player && ((Player) event.getExited()).isSneaking();

                // Fire cancellable before-exit event
                final Location seatPosition = seat.getPosition(event.getExited());
                final Location exitPosition;
                {
                    MemberBeforeSeatExitEvent memberExitEvent = new MemberBeforeSeatExitEvent(seat, event.getExited(),
                            seatPosition, seat.getEjectPosition(event.getExited()), playerInitiated);
                    if (CommonUtil.callEvent(memberExitEvent).isCancelled()) {
                        event.setCancelled(true);
                        return;
                    }
                    exitPosition = memberExitEvent.getExitPosition();
                }

                // Next tick, if passenger is indeed no longer in this seat, fire a
                // post-seat-exit event.
                final Entity vehicle = event.getVehicle();
                final Entity passenger = event.getExited();
                CommonUtil.nextTick(() -> {
                    // If member is dead, ignore
                    if (member.isUnloaded()) {
                        return;
                    }

                    // Before resuming ensure that the network controller has updated
                    // This releases the entity from the seat, so that the entity can be properly teleported
                    // afterwards. It might be the Minecart got deleted after a tick so be careful here.
                    if (!member.getEntity().isRemoved()) {
                        EntityNetworkController<?> controller = member.getEntity().getNetworkController();
                        if (controller != null) {
                            controller.syncPassengers();
                        }
                    }

                    if (passenger.getVehicle() != vehicle) {
                        CommonUtil.callEvent(new MemberSeatExitEvent(seat, passenger, seatPosition, exitPosition, playerInitiated));
                    }
                });
            }
        }
    }

    /**
     * Minecraft client 'predicts' a stable exit for the player around
     * the seat being exited. This method checks whether the current player
     * position is within range that this could be.
     *
     * @param a Position A
     * @param b Position B
     * @return True if a is a possible exit of b (or vice-versa)
     */
    private static boolean isPossibleExit(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && Math.abs(a.getBlockX() - b.getBlockX()) <= 2
                && Math.abs(a.getBlockY() - b.getBlockY()) <= 5
                && Math.abs(a.getBlockZ() - b.getBlockZ()) <= 2;
    }
}
