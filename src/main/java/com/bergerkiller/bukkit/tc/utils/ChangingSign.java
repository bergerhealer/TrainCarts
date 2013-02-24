package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.bases.BlockStateBase;

/**
 * A sign implementation that ensured proper access to a sign while it is being placed
 */
public class ChangingSign extends BlockStateBase implements Sign {
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
	public void setLine(int index, String line) throws IndexOutOfBoundsException {
		this.event.setLine(index, line);
	}

	@Override
	public String[] getLines() {
		return this.event.getLines();
	}
}
