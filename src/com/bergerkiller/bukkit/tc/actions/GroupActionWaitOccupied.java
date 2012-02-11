package com.bergerkiller.bukkit.tc.actions;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.TrackIterator;

public class GroupActionWaitOccupied extends GroupActionWaitForever {

	private BlockFace direction;
	private Block start;
	private final int maxsize;
	private double launchforce;
	private boolean wasoccupied = true;
	private int counter = 20;
	private boolean breakCode = false;
	public GroupActionWaitOccupied(final MinecartGroup group, final int maxsize) {
		super(group);
		this.maxsize = maxsize;
	}
	
	@Override
	public void start() {
		this.direction = this.getGroup().head().getDirection();
		this.start = this.getGroup().head().getRailsBlock();
		if (this.isOccupied()) {
			this.launchforce = this.getGroup().getAverageForce();
			this.getGroup().stop(true);
		} else {
			breakCode = true;
		}
	}
	
	public boolean isOccupied() {
	    return isOccupied(this.start, this.direction, this.getGroup(), this.maxsize);
	}
	public static boolean isOccupied(Block start, BlockFace direction, MinecartGroup ignore, int maxdistance) {
		TrackIterator iter = new TrackIterator(start, direction);
		while (iter.hasNext() && --maxdistance >= 0) {
			MinecartMember mm = MinecartMember.getAt(iter.next(), true);
			if (mm != null && mm.getGroup() != ignore) {
				return true;
			}
		}
		return false;
	}
		
	@Override
	public boolean update() {
		if (breakCode) return true;
		if (counter++ >= 20) {
			if (!this.wasoccupied) {
				//launch
				this.getGroup().head().addActionLaunch(this.direction, 2, this.launchforce);
				return true;
			} else {
				this.wasoccupied = this.isOccupied();
			}
			counter = 0;
		}
		return false;
	}

}
