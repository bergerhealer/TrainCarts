package com.bergerkiller.bukkit.tc.pathfinding;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;

public class PathConnection {
	public int distance;
	public BlockFace direction;
	public final PathNode destination;

	public PathConnection(DataInputStream stream, PathNode destination) throws IOException {
		this.distance = stream.readInt();
		this.direction = FaceUtil.notchToFace((int) stream.readByte() << 1);
		this.destination = destination;
	}
	public PathConnection(int distance, BlockFace direction, PathNode destination) {
		this.distance = distance;
		this.direction = direction;
		this.destination = destination;
	}

	@Override
	public String toString() {
		return "to " + destination.toString() + " going " + this.direction.toString() + " distance " + this.distance;
	}

	public void writeTo(DataOutputStream stream) throws IOException {
		stream.writeInt(this.destination.index);
		stream.writeInt(this.distance);
		stream.writeByte(FaceUtil.faceToNotch(this.direction) >> 1);
	}
}
