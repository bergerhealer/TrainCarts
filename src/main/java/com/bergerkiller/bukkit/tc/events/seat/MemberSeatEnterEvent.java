package com.bergerkiller.bukkit.tc.events.seat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.events.MemberEvent;

/**
 * Event fired after entities (or players) enter a MinecartMember seat.
 * This event is similar to the VehicleEnterEvent, but filters
 * only the enter events for Traincarts Minecarts.<br>
 * <br>
 * This event can not be cancelled and is meant for monitoring seat
 * changes. To cancel seat entering, handle {@link MemberBeforeSeatEnterEvent}
 * instead.<br>
 * <br>
 * During the handling of this event, the entering has already completed.
 * As such, the entity will be a passenger of the member vehicle.
 */
public class MemberSeatEnterEvent extends MemberEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Entity entity;
    private final boolean wasSeatChange;
    private final boolean wasVehicleChange;
    private final boolean playerInitiated;
    private CartAttachmentSeat seat;

    public MemberSeatEnterEvent(CartAttachmentSeat seat, Entity entity,
            boolean playerInitiated, boolean wasSeatChange, boolean wasVehicleChange
    ) {
        super(seat.getMember());
        this.seat = seat;
        this.entity = entity;
        this.wasSeatChange = wasSeatChange;
        this.wasVehicleChange = wasVehicleChange;
        this.playerInitiated = playerInitiated;
    }

    /**
     * Gets the Entity that is about to enter the member
     *
     * @return the Entity that is about to enter the member
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * Gets whether the Entity that entered the seat is a Player
     *
     * @return True if the passenger is a Player
     */
    public boolean isPlayer() {
        return entity instanceof Player;
    }

    /**
     * Gets whether this event is the result of a Player clicking onto a MinecartMember
     * to enter it.
     *
     * @return True if this enter was player-initiated
     */
    public boolean isPlayerInitiated() {
        return this.playerInitiated;
    }

    /**
     * Gets whether the passenger exited a previous seat to enter this one. If this
     * is the case, then if the plugin is already handling the {@link MemberBeforeSeatChangeEvent}
     * it should ignore this event. This event will be fired during the HIGHEST priority
     * level of the MemberSeatChangeEvent.
     *
     * @return True if this was after a seat change event
     */
    public boolean wasSeatChange() {
        return this.wasSeatChange;
    }

    /**
     * Gets whether the passenger entered the member, but it wasn't a passenger before.
     * If true, then not just the seat changed, the passenger also changed the
     * vehicle it was in.
     *
     * @return True if the passenger entered the vehicle, and wasn't in the vehicle before
     */
    public boolean wasMemberVehicleChange() {
        return this.wasVehicleChange;
    }

    /**
     * Gets the seat that will be used to put the entity inside of when the enter
     * is allowed to occur.
     *
     * @return Seat to be entered
     */
    public CartAttachmentSeat getSeat() {
        return this.seat;
    }

    /**
     * Sets the seat the entity will enter.<br>
     * <br>
     * It is allowed to enter a seat that is of a different
     * MinecartMember, in which case {@link #getMember()} and {@link #getGroup()}
     * are updated accordingly and a new pair of VehicleExit/Enter events are fired.
     *
     * @param seat Seat to enter (can not be null or already occupied)
     */
    public void setSeat(CartAttachmentSeat seat) {
        if (seat == null) {
            throw new IllegalArgumentException("Seat can not be null");
        } else if (seat.getEntity() != null && seat.getEntity() != getEntity()) {
            throw new IllegalArgumentException("The specified seat is already occupied");
        }
        this.seat = seat;
        this.member = seat.getMember();
    }

    @Override
    public String toString() {
        return "MemberSeatEnterEvent{member=" + this.getMember() + ", passenger=" + this.getEntity() +
                ", playerInitiated=" + this.playerInitiated + ", seatChange=" + this.wasSeatChange +
                ", vehicleChange=" + this.wasVehicleChange + "}";
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
