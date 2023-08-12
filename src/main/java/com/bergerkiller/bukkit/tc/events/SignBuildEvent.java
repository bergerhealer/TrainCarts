package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.SignChangeEvent;

/**
 * Event fired when a sign is built by a Player. Can be cancelled to prevent the
 * building of the sign, in which case the sign is not added or blanked out.
 * This event is fired before any actual building logic is executed.<br>
 * <br>
 * Use {@link #getRegisteredAction()} to see what {@link SignAction} is going to
 * handle this sign. If wanting to prevent the building of a particular type of
 * sign, you can use <code>instanceof</code> to check for the sign type here.
 * This returns null if the sign doesn't match any registered sign, which will
 * be the case when players build non-traincarts signs.
 */
public class SignBuildEvent extends SignChangeActionEvent {
    private static final HandlerList handlers = new HandlerList();
    private final SignAction action;

    /**
     * Constructs a new SignBuildEvent detecting the registered sign action based on the
     * sign lines
     *
     * @param player Player that built the sign
     * @param sign Sign that was built
     * @param interactive Whether this is an interactive change. If true, then a
     *                    sign build message is displayed. If not, the building is
     *                    silent unless a permission-related issue occurs.
     */
    public SignBuildEvent(Player player, RailLookup.TrackedSign sign, boolean interactive) {
        super(player, sign, interactive);
        this.action = SignAction.getSignAction(this);
    }

    /**
     * Constructs a new SignBuildEvent detecting the registered sign action based on the
     * sign lines
     *
     * @param event SignChangeEvent describing the building of the new sign
     * @param interactive Whether this is an interactive change. If true, then a
     *                    sign build message is displayed. If not, the building is
     *                    silent unless a permission-related issue occurs.
     */
    public SignBuildEvent(SignChangeEvent event, boolean interactive) {
        super(event, interactive);
        this.action = SignAction.getSignAction(this);
    }

    /**
     * @deprecated Just here to keep an old deprecated handleBuild() API functional
     */
    @Deprecated
    public SignBuildEvent(SignChangeActionEvent event) {
        super(event);
        this.action = SignAction.getSignAction(event);
    }

    /**
     * Constructs a new SignBuildEvent with a specified SignAction. Can be used if the SignAction
     * is already known to eliminate an unneeded lookup.
     *
     * @param player Player that built the sign
     * @param sign Sign that was built
     * @param interactive Whether this is an interactive change. If true, then a
     *                    sign build message is displayed. If not, the building is
     *                    silent unless a permission-related issue occurs.
     * @param action Registered SignAction that manages execution of this sign
     */
    public SignBuildEvent(Player player, RailLookup.TrackedSign sign, boolean interactive, SignAction action) {
        super(player, sign, interactive);
        this.action = action;
    }

    /**
     * Constructs a new SignBuildEvent with a specified SignAction. Can be used if the SignAction
     * is already known to eliminate an unneeded lookup.
     *
     * @param event SignChangeEvent describing the building of the new sign
     * @param interactive Whether this is an interactive change. If true, then a
     *                    sign build message is displayed. If not, the building is
     *                    silent unless a permission-related issue occurs.
     * @param action Registered SignAction that manages execution of this sign
     */
    public SignBuildEvent(SignChangeEvent event, boolean interactive, SignAction action) {
        super(event, interactive);
        this.action = action;
    }

    /**
     * Gets whether a {@link SignAction} is registered to handle the building and further
     * execution of this type of sign
     *
     * @return True if a SignAction is registered matching syntax of this sign
     * @see #getRegisteredAction()
     */
    public boolean hasRegisteredAction() {
        return action != null;
    }

    /**
     * Gets the registered {@link SignAction} that matches this sign, which will handle the
     * further building and execution of the sign. Returns <i>null</i> if none matches.
     *
     * @return Registered SignAction
     */
    public SignAction getRegisteredAction() {
        return action;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
