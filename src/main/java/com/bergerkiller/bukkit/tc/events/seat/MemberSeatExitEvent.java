package com.bergerkiller.bukkit.tc.events.seat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.events.MemberEvent;

/**
 * Event fired after a passenger was removed from a MinecartMember.
 * Similar to the VehicleExitEvent, but more reliable cross-version and
 * filters out just the events for Traincarts carts.<br>
 * <br>
 * Sometimes players exit one seat to enter another one. In that case, a
 * {@link MemberSeatChangeEvent} is fired instead, which is a sub-class of
 * this event. You can use {@link #isSeatChange()} to check for this.<br>
 * <br>
 * This event can not be cancelled and is meant for monitoring seat
 * changes. To cancel seat exiting, handle {@link MemberBeforeSeatExitEvent}
 * instead.<br>
 * <br>
 * During the handling of this event, the exiting has already completed.
 * As such, the entity will not be a passenger of the member vehicle's
 * old seat. If this is a seat change event for the same member, then
 * the entity may still be a passenger of the member.
 */
public class MemberSeatExitEvent extends MemberEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Entity entity;
    private final boolean playerInitiated;
    private final Location seatPosition;
    private final Location exitPosition;
    private final boolean exitPreserveRotation;
    private final CartAttachmentSeat seat;

    public MemberSeatExitEvent(CartAttachmentSeat seat, Entity entity, Location seatPosition, Location exitPosition, boolean exitPreserveRotation, boolean playerInitiated) {
        super(seat.getMember());
        this.seat = seat;
        this.entity = entity;
        this.seatPosition = seatPosition;
        this.exitPosition = exitPosition;
        this.exitPreserveRotation = exitPreserveRotation;
        this.playerInitiated = playerInitiated;
    }

    /**
     * Get the entity that is going to exit a seat of the MinecartMember
     *
     * @return The entity that exited
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * Gets whether the Entity that exited the seat is a Player
     *
     * @return True if the passenger is a Player
     */
    public boolean isPlayer() {
        return entity instanceof Player;
    }

    /**
     * Gets whether this event is the result of a Player ejecting with sneak or
     * by clicking on another vehicle.
     *
     * @return True if this exit was player-initiated
     */
    public boolean isPlayerInitiated() {
        return this.playerInitiated;
    }

    /**
     * Gets the Location where the Entity sat in the seat
     *
     * @return Seat position
     */
    public Location getSeatPosition() {
        return this.seatPosition;
    }

    /**
     * Gets the Location where the Entity will be after exiting the seat. When
     * changing to a new seat, this is the current location of that seat. When
     * exiting from the vehicle entirely, this is the seat-configured ejection
     * point/offset.
     *
     * @return Exit position
     */
    public Location getExitPosition() {
        return this.exitPosition;
    }

    /**
     * Gets whether the entity rotation is preserved when exiting the seat. If true, then
     * the yaw and pitch of {@link #getExitPosition()} are ignored.
     *
     * @return True if the entity rotation is preserved
     */
    public boolean isExitRotationPreserved() {
        return exitPreserveRotation;
    }

    /**
     * Gets the seat the entity is going to exit.
     *
     * @return current seat inside the member of the exiting entity
     */
    public CartAttachmentSeat getSeat() {
        return this.seat;
    }

    /**
     * Gets whether the passenger didn't just exit a seat, it is exiting
     * it to enter another one. If true, this is a {@link MemberSeatChangeEvent}
     * and the new seat being entered can also be gotten.
     *
     * @return True if this is a seat change event
     */
    public boolean isSeatChange() {
        return false;
    }

    /**
     * Gets whether the original seat's member was exited by the
     * passenger. If true, then not just the seat changed, the passenger
     * will changed the vehicle it is in. Also returns true when not entering
     * a new (minecart) vehicle.
     *
     * @return True if the member vehicle entity was exited by
     *         the passenger, and a new vehicle entity is going to be entered.
     */
    public boolean isMemberVehicleChange() {
        return true;
    }

    @Override
    public String toString() {
        return "MemberSeatExitEvent{member=" + this.getMember() + ", passenger=" + this.getEntity() +
                ", playerInitiated=" + this.playerInitiated + "}";
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
