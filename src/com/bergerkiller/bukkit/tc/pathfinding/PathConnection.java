package com.bergerkiller.bukkit.tc.pathfinding;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.block.BlockFace;

public class PathConnection {
	
	public PathConnection(DataInputStream stream, PathNode destination) throws IOException {
		this.distance = stream.readInt();
		switch (stream.readByte()) {
		case 1 : this.direction = BlockFace.EAST; break;
		case 2 : this.direction = BlockFace.SOUTH; break;
		case 3 : this.direction = BlockFace.WEST; break;
		default : this.direction = BlockFace.NORTH; break;
		}
		this.destination = destination;
	}
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
	
	public void writeTo(DataOutputStream stream) throws IOException {
		stream.writeInt(this.destination.index);
		stream.writeInt(this.distance);
		switch (this.direction) {
		case EAST : stream.writeByte(1); break;
		case SOUTH : stream.writeByte(2); break;
		case WEST : stream.writeByte(3); break;
		default : stream.writeByte(0); break;
		}
	}
	
}
