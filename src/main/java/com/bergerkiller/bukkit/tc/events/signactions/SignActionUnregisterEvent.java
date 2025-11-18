package com.bergerkiller.bukkit.tc.events.signactions;

import com.bergerkiller.bukkit.tc.signactions.SignAction;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event creates when a SignAction is un-registered from the sign action registry
 */
public class SignActionUnregisterEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final SignAction action;

    public SignActionUnregisterEvent(SignAction action) {
        this.action = action;
    }

    /**
     * Gets the sign action instance that was registered
     *
     * @return SignAction
     */
    public SignAction getSignAction() {
        return action;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
