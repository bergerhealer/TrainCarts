package com.bergerkiller.bukkit.tc.controller.components;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.actions.MemberAction;
import com.bergerkiller.bukkit.tc.actions.MovementAction;
import com.bergerkiller.bukkit.tc.actions.WaitAction;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Stores actions and updates them per tick
 */
public class ActionTracker {
	private final Queue<Action> actions = new LinkedList<Action>();

	public boolean hasAction() {
		return this.actions.size() > 0;
	}

	/**
	 * Clears all actions scheduled for the owner of this Action Tracker.
	 * For groups, this also clears all the actions scheduled for individual members.
	 */
	public void clear() {
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
		return this.actions.remove();
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

	public Action getCurrentAction() {
		return this.actions.peek();
	}

	public void doTick() {
		if (this.hasAction() && this.actions.peek().doTick()) {
			this.actions.remove();
		}
	}
}
