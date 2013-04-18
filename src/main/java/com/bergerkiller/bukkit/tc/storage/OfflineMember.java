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

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;

/**
 * Contains the information to get and restore a Minecart
 */
public class OfflineMember {
	public double motX, motZ;
	public UUID entityUID;
	public int cx, cz;
	public OfflineGroup group;

	public OfflineMember() {}
	public OfflineMember(OfflineGroup group, MinecartMember<?> instance) {
		CommonEntity<?> entity = instance.getEntity();
		this.motX = entity.vel.getX();
		this.motZ = entity.vel.getZ();
		this.entityUID = entity.getUniqueId();
		this.cx = entity.loc.x.chunk();
		this.cz = entity.loc.z.chunk();
		this.group = group;
	}

	public boolean isMoving() {
		return Math.abs(motX) >= CommonEntity.MIN_MOVE_SPEED || Math.abs(motZ) >= CommonEntity.MIN_MOVE_SPEED;
	}

	public void setVelocity(double velocity) {
		Vector vel = new Vector(this.motX, 0.0, this.motZ).normalize().multiply(velocity);
		this.motX = vel.getX();
		this.motZ = vel.getZ();
	}

	public MinecartMember<?> create(World world) {
		MinecartMember<?> mm = null;
		// first try to find it in the chunk
		Chunk c = world.getChunkAt(cx, cz);
		for (Entity e : WorldUtil.getEntities(c)) {
			if (e instanceof Minecart && e.getUniqueId().equals(this.entityUID)) {
				mm = MinecartMemberStore.convert((Minecart) e);
				break;
			}
		}
		// Try to find it in the world
		if (mm == null) {
			// Load a 5x5 chunk area around this Minecart so it can properly be found
			WorldUtil.loadChunks(world, this.cx, this.cz, 2);
			// Try to find it
			Entity e = EntityUtil.getEntity(world, this.entityUID);
			if (e instanceof Minecart) {
				mm = MinecartMemberStore.convert((Minecart) e);
			}
		}
		// Restore velocity
		if (mm != null) {
			mm.getEntity().vel.xz.set(this.motX, this.motZ);
		}
		return mm;
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