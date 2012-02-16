package com.bergerkiller.bukkit.tc.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import net.minecraft.server.ChunkProviderServer;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.MinecartGroup;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * A class containing an array of Minecart Members
 * Also adds functions to handle multiple members at once
 * Also adds functions to write and load from/to file
 */
class WorldGroup {
	
	public WorldGroup() {}
	public WorldGroup(MinecartGroup group) {
		this.members = new WorldMember[group.size()];
		for (int i = 0;i < members.length;i++) {
			this.members[i] = new WorldMember(group.get(i));
		}
		this.name = group.getName();
		this.genChunks();
	}
	public WorldMember[] members;
	public String name;
	public final Set<Long> chunks = new HashSet<Long>();
	public int chunkCounter;
	
	public boolean testFullyLoaded() {
		return this.chunkCounter == this.chunks.size();
	}
	public boolean updateLoadedChunks(World world) {
		ChunkProviderServer cps = WorldUtil.getNative(world).chunkProviderServer;
		this.chunkCounter = 0;
		for (long chunk : this.chunks) {
			if (cps.chunks.containsKey(chunk)) {
				this.chunkCounter++;
			}
		}
		return this.testFullyLoaded();
	}
	private void genChunks() {
		for (WorldMember wm : this.members) {
			for (int x = wm.cx - 2; x <= wm.cx + 2; x++) {
				for (int z = wm.cz - 2; z <= wm.cz + 2; z++) {
					this.chunks.add(MathUtil.toLong(x, z));
				}
			}
		}
		this.chunkCounter = this.chunks.size();
	}
	
	/**
	 * Tries to find all Minecarts based on their UID
	 * @param w
	 * @return An array of Minecarts
	 */
	public Minecart[] getMinecarts(World w) {
		ArrayList<Minecart> rval = new ArrayList<Minecart>();
		int missingNo = 0;
		for (WorldMember member : members) {
			//first try to find it in the chunk
			Chunk c = w.getChunkAt(member.cx, member.cz);
			Minecart m = null;
			for (Entity e : c.getEntities()) {
				if (e instanceof Minecart && e.getUniqueId().equals(member.entityUID)) {
					m = (Minecart) e;
					break;
				}
			}
			if (m == null) {
				m = EntityUtil.getEntity(w, member.entityUID, Minecart.class);
			}
			if (m != null) {
				m.setVelocity(new Vector(member.motX, 0, member.motZ));
				rval.add(m);
			} else {
				missingNo++;
			}
		}
		if (missingNo > 0) {
			TrainCarts.plugin.log(Level.WARNING, missingNo + " carts of group '" + this.name + "' are missing! (externally edited?)");
		}
		return rval.toArray(new Minecart[0]);
	}
		
	/*
	 * Read and write functions used internally
	 */
	public void writeTo(DataOutputStream stream) throws IOException {
		stream.writeInt(members.length);
		for (WorldMember member : members) member.writeTo(stream);
		stream.writeUTF(this.name);
	}
	public static WorldGroup readFrom(DataInputStream stream) throws IOException {
		WorldGroup wg = new WorldGroup();
		wg.members = new WorldMember[stream.readInt()];
		for (int i = 0;i < wg.members.length;i++) {
			wg.members[i] = WorldMember.readFrom(stream);
		}
		wg.name = stream.readUTF();
		wg.genChunks();
		return wg;
	}
}