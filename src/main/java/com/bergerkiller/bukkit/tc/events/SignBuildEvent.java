package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.common.events.SignEditTextEvent;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.utils.FakeSign;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
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
        // Note: this constructor is called by TC-Coasters!
        super(player, sign, interactive);
        this.action = SignAction.getSignAction(this);
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
     * Constructs a new SignBuildEvent detecting the registered sign action based on the
     * sign lines
     *
     * @param event SignChangeEvent describing the building of the new sign
     * @param interactive Whether this is an interactive change. If true, then a
     *                    sign build message is displayed. If not, the building is
     *                    silent unless a permission-related issue occurs.
     */
    @Deprecated
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

    // For use with BKCLSignEditBuildEvent. No other use.
    protected SignBuildEvent(Cancellable event, Player player, RailLookup.TrackedSign sign, boolean interactive) {
        super(event, player, sign, interactive);
        this.action = SignAction.getSignAction(this);;
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

    /**
     * Used for handling a BKCommonLib {@link SignEditTextEvent} as a SignBuildEvent.
     */
    public static class BKCLSignEditBuildEvent extends SignBuildEvent {

        public static SignBuildEvent create(SignEditTextEvent event, boolean interactive) {
            return new BKCLSignEditBuildEvent(event, interactive);
        }

        private BKCLSignEditBuildEvent(SignEditTextEvent event, boolean interactive) {
            super(event, event.getPlayer(), new TrackedEditedSign(event), interactive);
        }

        // Same as TrackedChangingSign, will replace it eventually
        private static class TrackedEditedSign extends RailLookup.TrackedRealSign {
            private final SignEditTextEvent event;
            private final boolean front;

            public TrackedEditedSign(SignEditTextEvent event) {
                super(FakeSign.create(event.getBlock()), event.getBlock(), RailPiece.NONE);
                this.front = event.getSide().isFront();
                ((FakeSign) sign).setHandler(new FakeSign.HandlerSignFallback(signBlock) {
                    @Override
                    public String getFrontLine(int index) {
                        return front ? event.getLine(index) : super.getFrontLine(index);
                    }

                    @Override
                    public void setFrontLine(int index, String text) {
                        if (front) {
                            event.setLine(index, text);
                        } else {
                            super.setFrontLine(index, text);
                        }
                    }

                    @Override
                    public String getBackLine(int index) {
                        return front ? super.getBackLine(index) : event.getLine(index);
                    }

                    @Override
                    public void setBackLine(int index, String text) {
                        if (front) {
                            super.setBackLine(index, text);
                        } else {
                            event.setLine(index, text);
                        }
                    }
                });
                this.rail = null;
                this.event = event;
            }

            @Override
            public boolean isFrontText() {
                return front;
            }

            @Override
            public boolean verify() {
                return false;
            }

            @Override
            public boolean isRemoved() {
                return !MaterialUtil.ISSIGN.get(event.getBlock());
            }

            @Override
            public BlockFace getFacing() {
                return BlockUtil.getFacing(event.getBlock());
            }

            @Override
            public Block getAttachedBlock() {
                return BlockUtil.getAttachedBlock(event.getBlock());
            }

            @Override
            public String[] getExtraLines() {
                return new String[0]; //TODO: important?
            }

            @Override
            public PowerState getPower(BlockFace from) {
                return PowerState.get(this.signBlock, from, (getAction() != null)
                        ? PowerState.Options.SIGN_CONNECT_WIRE
                        : PowerState.Options.SIGN);
            }

            @Override
            public String getLine(int index) throws IndexOutOfBoundsException {
                return event.getLine(index);
            }

            @Override
            public void setLine(int index, String line) throws IndexOutOfBoundsException {
                event.setLine(index, line);
            }

            @Override
            public Object getUniqueKey() {
                return this;
            }
        }
    }
}
