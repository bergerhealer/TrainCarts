package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;

import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

/**
 * A sign action event meant to represent a sign that has just been placed, or has changed<br>
 * This ensures that the sign can still properly be interacted with
 */
public class SignChangeActionEvent extends SignActionEvent {
    private final SignChangeEvent event;
    private final boolean interactive;

    /**
     * Constructs a new SignChangeActionEvent
     *
     * @param player Player that changed a sign
     * @param sign Sign that was changed
     * @param interactive Whether this is an interactive change. If true, then a
     *                    sign build message is displayed. If not, the building is
     *                    silent unless a permission-related issue occurs.
     */
    public SignChangeActionEvent(Player player, TrackedSign sign, boolean interactive) {
        this(mockChangeEvent(player, sign), sign, interactive);
    }

    /**
     * Constructs a new SignChangeActionEvent
     *
     * @param event Sign change event describing the sign and player involved
     * @param interactive Whether this is an interactive change. If true, then a
     *                    sign build message is displayed. If not, the building is
     *                    silent unless a permission-related issue occurs.
     */
    public SignChangeActionEvent(SignChangeEvent event, boolean interactive) {
        this(event, TrackedSign.forChangingSign(event), interactive);
    }

    public SignChangeActionEvent(Player player, TrackedSign sign) {
        this(mockChangeEvent(player, sign), sign, true);
    }

    public SignChangeActionEvent(SignChangeEvent event) {
        this(event, TrackedSign.forChangingSign(event), true);
    }

    protected SignChangeActionEvent(SignChangeActionEvent event) {
        this(event.event, event.getTrackedSign(), event.interactive);
    }

    private SignChangeActionEvent(SignChangeEvent event, TrackedSign sign, boolean interactive) {
        super(sign);
        this.event = event;
        this.interactive = interactive;
    }

    /**
     * Gets the player that placed this sign
     *
     * @return Player
     */
    public Player getPlayer() {
        return this.event.getPlayer();
    }

    /**
     * Gets whether the sign was changed interactively. In that case messages should be sent to the
     * player indicating what sign was placed. If false, build handling should be as silent as possible.
     *
     * @return True if this is an interactive sign building event. False if this is not and messages
     *         should be kept to a minimum.
     */
    public boolean isInteractive() {
        return interactive;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        super.setCancelled(cancelled);
        this.event.setCancelled(cancelled);
    }

    private static SignChangeEvent mockChangeEvent(Player player, TrackedSign sign) {
        //Note: deprecated constructor, assumed side FRONT
        return new SignChangeEvent(sign.signBlock, player, sign.sign.getLines());
    }
}
