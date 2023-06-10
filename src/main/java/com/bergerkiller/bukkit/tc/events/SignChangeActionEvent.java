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

    public SignChangeActionEvent(SignChangeEvent event) {
        super(TrackedSign.forChangingSign(event));
        this.event = event;
    }

    /**
     * Gets the player that placed this sign
     *
     * @return Player
     */
    public Player getPlayer() {
        return this.event.getPlayer();
    }

    @Override
    public void setCancelled(boolean cancelled) {
        super.setCancelled(cancelled);
        this.event.setCancelled(cancelled);
    }
}
