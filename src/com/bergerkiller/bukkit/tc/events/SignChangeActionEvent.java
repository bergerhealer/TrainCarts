package com.bergerkiller.bukkit.tc.events;

import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.tc.utils.ChangingSign;

public class SignChangeActionEvent extends SignActionEvent {

	private final SignChangeEvent event;

	public SignChangeActionEvent(SignChangeEvent event) {
		super(event.getBlock(), new ChangingSign(event), null);
		this.event = event;
	}

	public Player getPlayer() {
		return this.event.getPlayer();
	}

	@Override
	public void setCancelled(boolean cancelled) {
		super.setCancelled(cancelled);
		this.event.setCancelled(cancelled);
	}
}
