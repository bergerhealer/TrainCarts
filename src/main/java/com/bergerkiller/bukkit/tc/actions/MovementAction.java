package com.bergerkiller.bukkit.tc.actions;

/**
 * Represents an action that controls train movement
 */
public interface MovementAction {

	/**
	 * Tells the train whether movement control should be suppressed.
	 * If this action controls velocity/position changes, this should return True.
	 * 
	 * @return True if movement is suppressed, False if not
	 */
	public boolean isMovementSuppressed();
}
