package com.bergerkiller.bukkit.tc.controller.components;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.actions.BlockActionSetLevers;
import com.bergerkiller.bukkit.tc.actions.MemberAction;
import com.bergerkiller.bukkit.tc.actions.MovementAction;
import com.bergerkiller.bukkit.tc.actions.TrackedSignActionSetOutput;
import com.bergerkiller.bukkit.tc.actions.WaitAction;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatus;
import com.bergerkiller.bukkit.tc.controller.status.TrainStatusProvider;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.bukkit.block.Block;

/**
 * Stores actions and updates them per tick
 */
public abstract class ActionTracker implements TrainStatusProvider {
    private final Queue<Action> actions = new LinkedList<>();

    public boolean hasAction() {
        return !this.actions.isEmpty();
    }

    /**
     * Gets the Owner of this action tracker. This owner must be part of TrainCarts.
     *
     * @return owner
     */
    public abstract TrainCarts.Provider getOwner();

    /**
     * Clears all actions scheduled for the owner of this Action Tracker.
     * For groups, this also clears all the actions scheduled for individual members.
     */
    public void clear() {
        for (Action a : actions) {
            a.cancel();
        }
        this.actions.clear();
    }

    /**
     * Removes all Actions running for a specific minecart
     *
     * @param forMember to remove the actions for
     */
    public void removeActions(MinecartMember<?> forMember) {
        Iterator<Action> iter = this.actions.iterator();
        while (iter.hasNext()) {
            Action action = iter.next();
            if (action instanceof MemberAction && ((MemberAction) action).getMember() == forMember) {
                action.cancel();
                iter.remove();
            }
        }
    }

    /**
     * Removes and returns the current action
     *
     * @return action removed, or null if there was none
     */
    public Action removeAction() {
        Action a = this.actions.remove();
        if (a != null) {
            a.cancel();
        }
        return a;
    }

    /**
     * Adds a new action to be scheduled for this member or group
     *
     * @param action to be executed
     * @return the action that was added
     */
    public <T extends Action> T addAction(T action) {
        this.actions.offer(action);
        action.bind();
        return action;
    }

    /**
     * Adds an action that toggles the levers attached to a Block.
     * 
     * @param block Block to which levers are attached
     * @param down Whether the levers should be down (true) or up (false)
     * @return the added action
     */
    public BlockActionSetLevers addActionSetLevers(Block block, boolean down) {
        return addAction(new BlockActionSetLevers(getOwner().getTrainCarts(), block, down));
    }

    /**
     * Adds an action that toggles the output state of a Sign. For real signs, this
     * toggles levers around the block the sign is attached to, similar to
     * {@link #addActionSetLevers(Block, boolean)}. Unlike that method, this method
     * also supports fake signs which handle outputs in their own way.
     *
     * @param sign TrackedSign whose output to change
     * @param output New output state
     * @return the added action
     */
    public TrackedSignActionSetOutput addActionSetSignOutput(TrackedSign sign, boolean output) {
        return addAction(new TrackedSignActionSetOutput(getOwner().getTrainCarts(), sign, output));
    }

    /**
     * Gets whether an action is controlling this train.
     * When this is True, no physics should be applied.
     *
     * @return True if movement is controlled by an action, False if not
     */
    public boolean isMovementControlled() {
        final Action a = this.getCurrentAction();
        return a instanceof MovementAction && ((MovementAction) a).isMovementSuppressed();
    }

    public boolean isWaitAction() {
        return this.getCurrentAction() instanceof WaitAction;
    }

    public boolean isCurrentActionTag(String tag) {
        return !this.actions.isEmpty() && this.actions.peek().hasTag(tag);
    }

    public Action getCurrentAction() {
        return this.actions.peek();
    }

    public void doTick() {
        Action action;
        while ((action = this.actions.peek()) != null && action.doTick()) {
            // Action completed, but as a side-effect it may have altered the actions queue
            // Only if the action at the head equals what we just ticked do we remove it
            if (action == this.actions.peek()) {
                this.actions.remove();
            }
        }
    }

    @Override
    public List<TrainStatus> getStatusInfo() {
        Action currentAction = this.getCurrentAction();
        if (currentAction == null) {
            return Collections.emptyList();
        } else {
            return currentAction.getStatusInfo();
        }
    }
}
