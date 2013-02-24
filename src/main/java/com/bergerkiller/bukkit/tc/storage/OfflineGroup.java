package com.bergerkiller.bukkit.tc.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.World;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.MemberActionLaunch;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * A class containing an array of Minecart Members
 * Also adds functions to handle multiple members at once
 * Also adds functions to write and load from/to file
 */
public class OfflineGroup {
	public OfflineMember[] members;
	public String name;
	public final LongHashSet chunks = new LongHashSet();
	public final LongHashSet loadedChunks = new LongHashSet();
	public UUID worldUUID;

	public OfflineGroup() {}
	public OfflineGroup(MinecartGroup group) {
		this.members = new OfflineMember[group.size()];
		for (int i = 0;i < members.length;i++) {
			this.members[i] = new OfflineMember(this, group.get(i));
		}
		this.name = group.getProperties().getTrainName();
		this.worldUUID = group.getWorld().getUID();
		if (group.getCurrentAction() instanceof MemberActionLaunch) {
			double vel = ((MemberActionLaunch) group.getCurrentAction()).getTargetVelocity();
			for (OfflineMember member : this.members) {
				member.setVelocity(vel);
			}
		}
		this.genChunks();
	}

	public boolean testFullyLoaded() {
		return this.loadedChunks.size() == this.chunks.size();
	}
	public boolean updateLoadedChunks(World world) {
		this.loadedChunks.clear();
		for (long chunk : this.chunks) {
			if (WorldUtil.isLoaded(world, MathUtil.longHashMsw(chunk), MathUtil.longHashLsw(chunk))) {
				this.loadedChunks.add(chunk);
			}
		}
		if (OfflineGroupManager.lastUnloadChunk != null) {
			this.loadedChunks.remove(OfflineGroupManager.lastUnloadChunk);
		}
		return this.testFullyLoaded();
	}
	public void genChunks() {
		this.chunks.clear();
		for (OfflineMember wm : this.members) {
			for (int x = wm.cx - 2; x <= wm.cx + 2; x++) {
				for (int z = wm.cz - 2; z <= wm.cz + 2; z++) {
					this.chunks.add(MathUtil.longHashToLong(x, z));
				}
			}
		}
	}

	/**
	 * Tries to find all Minecarts based on their UID
	 * @param w
	 * @return An array of Minecarts
	 */
	public MinecartGroup create(World w) {
		ArrayList<MinecartMember> rval = new ArrayList<MinecartMember>(this.members.length);
		int missingNo = 0;
		for (OfflineMember member : this.members) {
			MinecartMember mm = member.create(w);
			if (mm != null) {
				rval.add(mm);
			} else {
				missingNo++;
			}
		}
		if (missingNo > 0) {
			TrainCarts.plugin.log(Level.WARNING, missingNo + " carts of group '" + this.name + "' are missing! (externally edited?)");
		}
		if (rval.isEmpty()) {
			return null;
		}
		// Is a new group needed?
		return MinecartGroup.create(this.name, rval.toArray(new MinecartMember[0]));
	}

	/*
	 * Read and write functions used internally
	 */
	public void writeTo(DataOutputStream stream) throws IOException {
		stream.writeInt(members.length);
		for (OfflineMember member : members) member.writeTo(stream);
		stream.writeUTF(this.name);
	}
	public static OfflineGroup readFrom(DataInputStream stream) throws IOException {
		OfflineGroup wg = new OfflineGroup();
		wg.members = new OfflineMember[stream.readInt()];
		for (int i = 0;i < wg.members.length;i++) {
			wg.members[i] = OfflineMember.readFrom(stream);
		}
		wg.name = stream.readUTF();
		wg.genChunks();
		return wg;
	}
}