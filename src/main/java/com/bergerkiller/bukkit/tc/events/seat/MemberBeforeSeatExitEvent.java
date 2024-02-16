package com.bergerkiller.bukkit.tc.events.seat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.events.MemberEvent;

/**
 * Event fired when a passenger is about to be removed from a MinecartMember.
 * Similar to the VehicleExitEvent, but more reliable cross-version and
 * filters out just the events for Traincarts carts.<br>
 * <br>
 * Sometimes players exit one seat to enter another one. In that case, a
 * {@link MemberBeforeSeatChangeEvent} is fired instead, which is a sub-class of
 * this event. You can use {@link #isSeatChange()} to check for this.<br>
 * <br>
 * After this event, a vehicle exit event or Paper entity dismount event can
 * also be fired, and cancelled. If actual changes need to be monitored, without the
 * option to cancel the event, listen for {@link MemberSeatExitEvent} instead.
 */
public class MemberBeforeSeatExitEvent extends MemberEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Entity entity;
    private final boolean playerInitiated;
    private final CartAttachmentSeat seat;
    private final Location seatPosition;
    private Location exitPosition;
    private boolean exitPreservePlayerRotation;
    private boolean cancelled;

    public MemberBeforeSeatExitEvent(CartAttachmentSeat seat, Entity entity, Location seatPosition, Location exitPosition, boolean exitPreservePlayerRotation, boolean playerInitiated) {
        super(seat.getMember());
        this.seat = seat;
        this.entity = entity;
        this.seatPosition = seatPosition;
        this.exitPosition = exitPosition;
        this.exitPreservePlayerRotation = exitPreservePlayerRotation;
        this.playerInitiated = playerInitiated;
    }

    /**
     * Get the entity that wants to exit a seat of the MinecartMember
     *
     * @return The entity that exited
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * Gets whether the Entity exiting the seat is a Player
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
     * Gets whether the player rotation is preserved when exiting the seat. If true, then
     * the yaw and pitch of {@link #getExitPosition()} are ignored.
     *
     * @return True if the player rotation is preserved
     */
    public boolean isExitPlayerRotationPreserved() {
        return exitPreservePlayerRotation;
    }

    /**
     * Sets a new exit position to use instead of the default one. Players will
     * be teleported here when exiting from the vehicle, or when entering the new
     * seat fails.
     *
     * @param position New exit position
     * @see #getExitPosition()
     */
    public void setExitPosition(Location position) {
        this.exitPosition = position;
        this.exitPreservePlayerRotation = false;
    }

    /**
     * Sets a new exit position to use instead of the default one. Players will
     * be teleported here when exiting from the vehicle, or when entering the new
     * seat fails.
     *
     * @param position New exit position
     * @param preservePlayerRotation Whether to preserve the player rotation when
     *                               exiting. If true, yaw and pitch are ignored.
     * @see #getExitPosition()
     */
    public void setExitPosition(Location position, boolean preservePlayerRotation) {
        this.exitPosition = position;
        this.exitPreservePlayerRotation = preservePlayerRotation;
    }

    /**
     * Gets the seat the entity that wants to exit is currently occupying
     *
     * @return current seat inside the member of the exiting entity
     */
    public CartAttachmentSeat getSeat() {
        return this.seat;
    }

    /**
     * Gets whether the passenger isn't just exiting a seat, it is exiting
     * it to enter another one. If true, this is a {@link MemberBeforeSeatChangeEvent}
     * and the new seat to be entered can also be gotten/configured.
     *
     * @return True if this is a seat change event
     */
    public boolean isSeatChange() {
        return false;
    }

    /**
     * Gets whether the original seat's member is going to be exited by the
     * passenger. If true, then not just the seat will change, the passenger
     * will also change the vehicle it is in. Also returns true when not entering
     * a new (minecart) vehicle.
     *
     * @return True if the member vehicle entity is going to be exited by
     *         the passenger, and a new vehicle entity is going to be entered.
     */
    public boolean isMemberVehicleChange() {
        return true;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String toString() {
        return "MemberBeforeSeatExitEvent{member=" + this.getMember() + ", passenger=" + this.getEntity() +
                ", playerInitiated=" + this.playerInitiated + ", cancelled=" + this.isCancelled() + "}";
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
