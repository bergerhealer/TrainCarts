package com.bergerkiller.bukkit.tc.actions;

public class Action {
	
	private boolean started = false;
	public boolean doTick() {
		if (!this.started) {
			this.started = true;
			this.start();
		}
		return this.update();
	}
	
	public boolean update() {return true;}
	public void start() {}

}
