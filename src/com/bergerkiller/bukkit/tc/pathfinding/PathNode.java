package com.bergerkiller.bukkit.tc.pathfinding;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.tc.utils.BlockLocation;

public class PathNode {
	private static Map<String, PathNode> nodes = new HashMap<String, PathNode>();
	public static PathNode getNode(String name) {
		PathNode node = nodes.get(name);
		if (node == null) {
			node = new PathNode(name);
			nodes.put(name, node);
		}
		return node;
	}
	
	private PathNode(final String name) {
		this.name = name;
	}
	
	public final String name;
	public BlockLocation location;
	public final Map<PathNode, PathConnection> neighbours = new HashMap<PathNode, PathConnection>(2);
	private final Map<PathNode, PathConnection> connections = new HashMap<PathNode, PathConnection>();
	public PathConnection getConnection(PathNode node) {
		return connections.get(node);
	}
	public PathConnection createConnection(final PathNode to, final double distance, final BlockFace direction) {
		PathConnection conn = this.getConnection(to);
		if (conn == null || conn.distance > distance) {
			conn = new PathConnection(distance, direction, to);
			this.connections.put(to, conn);
			//make sure all neighbours get informed of this connection
			for (Map.Entry<PathNode, PathConnection> entry : this.neighbours.entrySet()) {
				double newdistance = entry.getValue().distance + distance + 1;
				entry.getKey().createConnection(to, newdistance, entry.getValue().direction);
			}
		}
		return conn;
	}

}
