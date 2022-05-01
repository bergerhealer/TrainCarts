package com.bergerkiller.bukkit.tc.events.seat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Event fired when a passenger of a MinecartMember exited one seat it was in,
 * and entered another seat. This combines the {@link MemberSeatExitEvent}
 * and {@link MemberSeatEnterEvent} into a single event. A MemberSeatEnterEvent is
 * also fired for the seat that was entered.<br>
 * <br>
 * This event extends the {@link MemberSeatExitEvent}, so it can be cast to while
 * handling that event. Use {@link MemberSeatExitEvent#isSeatChange()} to check
 * whether this is a seat changing event.<br>
 * <br>
 * This event can not be cancelled and is meant for monitoring seat
 * changes. To cancel seat changes, handle {@link MemberBeforeSeatChangeEvent}
 * instead.<br>
 * <br>
 * During the handling of this event, the seat change has already completed.
 * As such, the entity will be in the new seat of the vehicle.
 */
public class MemberSeatChangeEvent extends MemberSeatExitEvent {
    private CartAttachmentSeat newSeat;

    public MemberSeatChangeEvent(CartAttachmentSeat oldSeat, CartAttachmentSeat newSeat, Entity entity, boolean playerInitiated) {
        this(oldSeat, newSeat, entity, oldSeat.getPosition(entity), newSeat.getPosition(entity), playerInitiated);
    }

    public MemberSeatChangeEvent(CartAttachmentSeat oldSeat, CartAttachmentSeat newSeat, Entity entity,
            Location seatPosition, Location exitPosition, boolean playerInitiated
    ) {
        super(oldSeat, entity, seatPosition, exitPosition, playerInitiated);
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
     * Gets the passenger Entity that changed seat
     *
     * @return passenger entity
     */
    @Override
    public Entity getEntity() {
        return super.getEntity();
    }

    /**
     * Gets whether the Entity that changed the seat is a Player
     *
     * @return True if the passenger is a Player
     */
    @Override
    public boolean isPlayer() {
        return super.isPlayer();
    }

    /**
     * Gets the seat the entity that changed seat is currently occupying
     *
     * @return current seat
     */
    @Override
    public CartAttachmentSeat getSeat() {
        return super.getSeat();
    }

    /**
     * Gets the new seat that will be entered
     *
     * @return new seat
     */
    public CartAttachmentSeat getEnteredSeat() {
        return this.newSeat;
    }

    /**
     * Gets the new MinecartMember train cart that the entity will enter
     *
     * @return new member
     */
    public MinecartMember<?> getEnteredMember() {
        return this.newSeat.getMember();
    }

    @Override
    public String toString() {
        return "MemberSeatChangeEvent{member=" + this.getMember() + ", passenger=" + this.getEntity() +
                ", playerInitiated=" + this.isPlayerInitiated() +
                ", vehicleChange=" + this.isMemberVehicleChange() + ", newMember=" + this.getEnteredMember() + "}";
    }
}
