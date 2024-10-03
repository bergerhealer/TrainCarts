package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.PowerState;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;

import com.bergerkiller.bukkit.tc.utils.FakeSign;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.SignChangeEvent;

/**
 * A sign action event meant to represent a sign that has just been placed, or has changed<br>
 * This ensures that the sign can still properly be interacted with
 */
public class SignChangeActionEvent extends SignActionEvent {
    private final Cancellable event;
    private final Player player;
    private final boolean interactive;

    /**
     * Constructs a new SignChangeActionEvent
     *
     * @param event Sign change event describing the sign and player involved
     * @param interactive Whether this is an interactive change. If true, then a
     *                    sign build message is displayed. If not, the building is
     *                    silent unless a permission-related issue occurs.
     */
    public SignChangeActionEvent(SignChangeEvent event, boolean interactive) {
        this(event, event.getPlayer(), new TrackedChangingSign(event), interactive);
    }

    public SignChangeActionEvent(SignChangeEvent event) {
        this(event, event.getPlayer(), new TrackedChangingSign(event), true);
    }

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
        this(new MockCancellable(), player, sign, interactive);
    }

    public SignChangeActionEvent(Player player, TrackedSign sign) {
        this(new MockCancellable(), player, sign, true);
    }

    protected SignChangeActionEvent(SignChangeActionEvent event) {
        this(event.event, event.player, event.getTrackedSign(), event.interactive);
    }

    private SignChangeActionEvent(Cancellable event, Player player, TrackedSign sign, boolean interactive) {
        super(sign);
        this.event = event;
        this.player = player;
        this.interactive = interactive;
    }

    /**
     * Gets the player that placed this sign
     *
     * @return Player
     */
    public Player getPlayer() {
        return this.player;
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

    private static class MockCancellable implements Cancellable {
        private boolean cancelled = false;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean b) {
            cancelled = b;
        }
    }

    private static class TrackedChangingSign extends RailLookup.TrackedRealSign {
        private final SignChangeEvent event;
        private final boolean front;

        public TrackedChangingSign(SignChangeEvent event) {
            super(FakeSign.create(event.getBlock()), event.getBlock(), RailPiece.NONE);
            this.front = BlockUtil.isChangingFrontLines(event);
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
