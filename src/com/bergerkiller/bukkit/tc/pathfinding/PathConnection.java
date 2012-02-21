package com.bergerkiller.bukkit.tc.pathfinding;

import org.bukkit.block.BlockFace;

public class PathConnection {
	
	public PathConnection(int distance, BlockFace direction, PathNode destination) {
		this.distance = distance;
		this.direction = direction;
		this.destination = destination;
	}
	
	public final int distance;
	public final BlockFace direction;
	public final PathNode destination;
	
	public String toString() {
		return "to " + destination.toString() + " going " + this.direction.toString() + " distance " + this.distance;
	}
	
}
