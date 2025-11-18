package com.bergerkiller.bukkit.tc.events.signactions;

import com.bergerkiller.bukkit.tc.signactions.SignAction;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event creates when a new SignAction is registered in the sign action registry
 */
public class SignActionRegisterEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final SignAction action;
    private final boolean priority;

    public SignActionRegisterEvent(SignAction action, boolean priority) {
        this.action = action;
        this.priority = priority;
    }

    /**
     * Gets the sign action instance that was registered
     *
     * @return SignAction
     */
    public SignAction getSignAction() {
        return action;
    }

    /**
     * Gets whether the sign action was registered with priority or not
     *
     * @return True if registered with priority
     */
    public boolean isPriority() {
        return priority;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
