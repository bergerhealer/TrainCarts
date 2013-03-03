package com.bergerkiller.bukkit.tc.pathfinding;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;

public class PathConnection {
	public final int distance;
	public final BlockFace direction;
	public final PathNode destination;

	public PathConnection(PathNode destination, DataInputStream stream) throws IOException {
		this.destination = destination;
		this.distance = stream.readInt();
		this.direction = FaceUtil.notchToFace((int) stream.readByte() << 1);
	}
	public PathConnection(PathNode destination, int distance, BlockFace direction) {
		this.destination = destination;
		this.distance = distance;
		this.direction = direction;
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
