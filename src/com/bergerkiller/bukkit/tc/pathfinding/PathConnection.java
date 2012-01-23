package com.bergerkiller.bukkit.tc.pathfinding;

import org.bukkit.block.BlockFace;

public class PathConnection {
	
	public PathConnection(double distance, BlockFace direction, PathNode destination) {
		this.distance = distance;
		this.direction = direction;
		this.destination = destination;
	}
	
	public final double distance;
	public final BlockFace direction;
	public final PathNode destination;
	
}
