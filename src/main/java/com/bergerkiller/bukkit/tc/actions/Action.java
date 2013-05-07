package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.ToggledState;

public class Action {
	private final ToggledState started = new ToggledState();

	public boolean doTick() {
		if (this.started.set()) {
			this.start();
		}
		return this.update();
	}

	public boolean update() {
		return true;
	}

	/**
	 * Called right after this Action is bound to a group or member
	 */
	public void bind() {
	}

	public void start() {
		// Default implementation does nothing here
	}
}
