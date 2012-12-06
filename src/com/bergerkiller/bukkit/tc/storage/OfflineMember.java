package com.bergerkiller.bukkit.tc.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.NativeUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * Contains the information to get and restore a Minecart
 */
public class OfflineMember {
	public OfflineMember() {}
	public OfflineMember(OfflineGroup group, MinecartMember instance) {
		this.motX = instance.motX;
		this.motZ = instance.motZ;
		this.entityUID = instance.uniqueId;
		this.cx = instance.getLiveChunkX();
		this.cz = instance.getLiveChunkZ();
		this.group = group;
	}
	public double motX, motZ;
	public UUID entityUID;
	public int cx, cz;
	public OfflineGroup group;
	public void setVelocity(double velocity) {
		Vector vel = new Vector(this.motX, 0.0, this.motZ).normalize().multiply(velocity);
		this.motX = vel.getX();
		this.motZ = vel.getZ();
	}
	public MinecartMember create(World world) {
		// first try to find it in the chunk
		Chunk c = world.getChunkAt(cx, cz);
		for (Entity e : WorldUtil.getEntities(c)) {
			if (e instanceof Minecart && e.getUniqueId().equals(entityUID)) {
				return NativeUtil.getNative(e, MinecartMember.class);
			}
		}
		// try to find it in the world
		return NativeUtil.getNative(EntityUtil.getEntity(world, entityUID), MinecartMember.class);
	}
	public void writeTo(DataOutputStream stream) throws IOException {
		stream.writeLong(entityUID.getMostSignificantBits());
		stream.writeLong(entityUID.getLeastSignificantBits());
		stream.writeDouble(motX);
		stream.writeDouble(motZ);
		stream.writeInt(cx);
		stream.writeInt(cz);
	}
	public static OfflineMember readFrom(DataInputStream stream) throws IOException {
		OfflineMember wm = new OfflineMember();
		wm.entityUID = new UUID(stream.readLong(), stream.readLong());
		wm.motX = stream.readDouble();
		wm.motZ = stream.readDouble();	
		wm.cx = stream.readInt();
		wm.cz = stream.readInt();
		return wm;
	}
}