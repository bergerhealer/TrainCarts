package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.craftbukkit.block.CraftSign;
import org.bukkit.event.block.SignChangeEvent;

public class ChangingSign extends CraftSign {

	private final SignChangeEvent event;

	public ChangingSign(SignChangeEvent event) {
		super(event.getBlock());
		this.event = event;
	}

	@Override
	public String getLine(int index) throws IndexOutOfBoundsException {
		return this.event.getLine(index);
	}

	@Override
	public void setLine(int index, String line) {
		this.event.setLine(index, line);
	}

	@Override
	public String[] getLines() {
		return this.event.getLines();
	}
}
