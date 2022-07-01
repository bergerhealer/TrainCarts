package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.utils.ChangingSign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

/**
 * A sign action event meant to represent a sign that has just been placed, or has changed<br>
 * This ensures that the sign can still properly be interacted with
 */
public class SignChangeActionEvent extends SignActionEvent {
    private final SignChangeEvent event;

    public SignChangeActionEvent(SignChangeEvent event) {
        super(event.getBlock(), new ChangingSign(event), null);
        this.event = event;
    }

    public SignChangeActionEvent(SignChangeEvent event, TrackedSign sign) {
        super(sign);
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
