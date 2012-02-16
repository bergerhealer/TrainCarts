package com.bergerkiller.bukkit.tc.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.MinecartMember;

/**
 * Contains the information to get and restore a Minecart
 */
class WorldMember {
	public WorldMember() {}
	public WorldMember(MinecartMember instance) {
		this.motX = instance.motX;
		this.motZ = instance.motZ;
		this.entityUID = instance.uniqueId;
		this.cx = MathUtil.locToChunk(instance.lastX);
		this.cz = MathUtil.locToChunk(instance.lastZ);
	}
	public double motX, motZ;
	public UUID entityUID;
	public int cx, cz;
	public void writeTo(DataOutputStream stream) throws IOException {
		stream.writeLong(entityUID.getMostSignificantBits());
		stream.writeLong(entityUID.getLeastSignificantBits());
		stream.writeDouble(motX);
		stream.writeDouble(motZ);
		stream.writeInt(cx);
		stream.writeInt(cz);
	}
	public static WorldMember readFrom(DataInputStream stream) throws IOException {
		WorldMember wm = new WorldMember();
		wm.entityUID = new UUID(stream.readLong(), stream.readLong());
		wm.motX = stream.readDouble();
		wm.motZ = stream.readDouble();	
		wm.cx = stream.readInt();
		wm.cz = stream.readInt();
		return wm;
	}
}