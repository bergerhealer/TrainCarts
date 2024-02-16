package com.bergerkiller.bukkit.tc.events.seat;

import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Event fired when a passenger of a MinecartMember wants to exit the seat
 * its in, and enter another seat. This combines the {@link MemberBeforeSeatExitEvent}
 * and {@link MemberBeforeSeatEnterEvent} into a single event. If this event is not
 * cancelled, a MemberBeforeSeatEnterEvent is also fired for the seat to be entered.<br>
 * <br>
 * This event extends the {@link MemberBeforeSeatExitEvent}, so it can be cast to while
 * handling that event. Use {@link MemberBeforeSeatExitEvent#isSeatChange()} to check
 * whether this is a seat changing event.<br>
 * <br>
 * After this event, a vehicle exit/enter event or Paper entity mount/dismount event can
 * also be fired, and cancelled. If actual changes need to be monitored, without the
 * option to cancel the event, listen for {@link MemberSeatChangeEvent} instead.
 */
public class MemberBeforeSeatChangeEvent extends MemberBeforeSeatExitEvent {
    private CartAttachmentSeat newSeat;

    public MemberBeforeSeatChangeEvent(CartAttachmentSeat oldSeat, CartAttachmentSeat newSeat, Entity entity, boolean playerInitiated) {
        super(oldSeat, entity, oldSeat.getPosition(entity), newSeat.getPosition(entity), true, playerInitiated);
        this.newSeat = newSeat;
    }

    @Override
    public boolean isSeatChange() {
        return true;
    }

    @Override
    public boolean isMemberVehicleChange() {
        return this.getMember() != this.getEnteredMember();
    }

    /**
     * Gets the passenger Entity that is about to change seat
     *
     * @return passenger entity
     */
    @Override
    public Entity getEntity() {
        return super.getEntity();
    }

    /**
     * Gets whether the Entity changing the seat is a Player
     *
     * @return True if the passenger is a Player
     */
    @Override
    public boolean isPlayer() {
        return super.isPlayer();
    }

    /**
     * Gets the seat the entity that wants to change seat is currently occupying
     *
     * @return current seat
     */
    @Override
    public CartAttachmentSeat getSeat() {
        return super.getSeat();
    }

    /**
     * Gets the new seat that will be entered if this event is not cancelled
     *
     * @return new seat
     */
    public CartAttachmentSeat getEnteredSeat() {
        return this.newSeat;
    }

    /**
     * Gets the new MinecartMember train cart that the entity wants to enter.
     *
     * @return new member
     */
    public MinecartMember<?> getEnteredMember() {
        return this.newSeat.getMember();
    }

    /**
     * Sets the new seat to enter. The new seat must not be occupied by an Entity
     * already, or an IllegalArgumentException is thrown.<br>
     * <br>
     * If the same seat as the current seat of the passenger is specified, the
     * seat change is effectively cancelled as well.
     *
     * @param seat New seat to enter, can not be null or occupied
     */
    public void setEnteredSeat(CartAttachmentSeat seat) {
        if (this.newSeat != seat) {
            if (seat == null) {
                throw new IllegalArgumentException("Seat can not be null");
            } else if (seat.getEntity() != null && seat.getEntity() != getEntity()) {
                throw new IllegalArgumentException("The specified seat is already occupied");
            }
        }
        this.newSeat = seat;
    }

    @Override
    public String toString() {
        return "MemberBeforeSeatChangeEvent{member=" + this.getMember() + ", passenger=" + this.getEntity() +
                ", playerInitiated=" + this.isPlayerInitiated() +
                ", vehicleChange=" + this.isMemberVehicleChange() + ", newMember=" + this.getEnteredMember() +
                ", cancelled=" + this.isCancelled() + "}";
    }
}
