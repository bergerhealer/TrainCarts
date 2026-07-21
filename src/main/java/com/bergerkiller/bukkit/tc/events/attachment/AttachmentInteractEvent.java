package com.bergerkiller.bukkit.tc.events.attachment;

import com.bergerkiller.bukkit.common.wrappers.HumanHand;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.util.Vector;

/**
 * Details provided to attachments when players left (attack) or right (interact) click on them.
 * Attachments are selected when the player clicks on an entity id that an attachment says it
 * {@link Attachment#containsEntityId(int) contains}.
 */
public class AttachmentInteractEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Attachment attachment;
    private final boolean isAttack;
    private final HumanHand hand;
    private final Boolean sneaking; // Null if not known, then it uses player.isSneaking()
    private final int interactedEntityId;
    private final Vector atPosition;
    private boolean cancelled = false;
    private boolean isPreventDefault = false;

    public AttachmentInteractEvent(
            final Player who,
            final boolean isAttack,
            final HumanHand hand,
            final Boolean isSneaking, /* Nullable if not known */
            final int interactedEntityId,
            final Attachment attachment,
            final Vector atPosition
    ) {
        super(who);
        this.attachment = attachment;
        this.isAttack = isAttack;
        this.hand = hand;
        this.sneaking = isSneaking;
        this.interactedEntityId = interactedEntityId;
        this.atPosition = atPosition;
    }

    /**
     * Gets the Attachment that was interacted with
     *
     * @return Attachment
     */
    public Attachment getAttachment() {
        return attachment;
    }

    /**
     * Gets the Entity ID that the player clicked, that was linked with the Attachment using
     * {@link Attachment#containsEntityId(int)}. If your attachment has more than one entity
     * spawned to players that can be clicked, use this to find out which one was clicked.
     *
     * @return Entity ID that was interacted with
     */
    public int getInteractedEntityId() {
        return interactedEntityId;
    }

    /**
     * Gets whether this interaction is for attacking the entity (true) or interacting with it (false)
     *
     * @return True if this is an attack, False if this is an interaction
     */
    public boolean isAttack() {
        return isAttack;
    }

    /**
     * Gets whether this interaction is for interacting with the entity (true) or attacking it (false)
     *
     * @return True if this is an interaction, False if this is an attack
     */
    public boolean isInteract() {
        return !isAttack;
    }

    /**
     * Gets whether the player was sneaking at the time they interacted
     *
     * @return True if sneaking, False if not
     */
    public boolean isSneaking() {
        return sneaking != null ? sneaking : getPlayer().isSneaking();
    }

    /**
     * Gets what hand the player used to do this interaction
     *
     * @return Hand
     */
    public HumanHand getHand() {
        return hand;
    }

    /**
     * Gets the position relative to the entity that the player cursor was looking at when interacting. Can be null if unavailable.
     *
     * @return Interaction at-position, or <i>null</i> if unavailable
     */
    public Vector getAtPosition() {
        return atPosition;
    }

    /**
     * Prevents the default click action from happening. This disables attacks/damaging for attack actions, and disables
     * entering the vehicle for interaction actions.
     */
    public void preventDefault() {
        isPreventDefault = true;
    }

    /**
     * Allows default click action from happening again. Undoes {@link #preventDefault()}
     */
    public void allowDefault() {
        isPreventDefault = false;
    }

    /**
     * Gets whether the default behavior of the click action is prevented. For attacks, that is damaging/destroying the attachment
     * or vehicle it belongs to. And for interaction, it is for entering the vehicle.
     *
     * @return True if the default behavior is prevented from happening
     * @see #preventDefault()
     */
    public boolean isPreventDefault() {
        return isPreventDefault;
    }

    /**
     * Clones this AttachmentInteractEvent but changes what attachment it is for
     *
     * @param attachment Attachment
     * @return AttachmentInteractEvent
     */
    public AttachmentInteractEvent cloneWithDifferentAttachment(Attachment attachment) {
        AttachmentInteractEvent evt = new AttachmentInteractEvent(
                getPlayer(),
                isAttack(),
                getHand(),
                sneaking,
                getInteractedEntityId(),
                attachment,
                getAtPosition()
        );
        evt.cancelled = this.cancelled;
        evt.isPreventDefault = this.isPreventDefault;
        return evt;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        cancelled = b;
    }

    @Override
    public String toString() {
        return "PlayerInteractEvent{" +
                "player=" + getPlayer() +
                ", attachment=" + attachment +
                ", type=" + (isAttack ? "attack" : "interact") +
                ", hand=" + hand +
                ", sneaking=" + sneaking +
                ", interactedEntityId=" + interactedEntityId +
                ", atPosition=" + atPosition +
                ", cancelled=" + cancelled +
                ", isPreventDefault=" + isPreventDefault +
                '}';
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
