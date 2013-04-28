package com.bergerkiller.bukkit.tc.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Chunk;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.wrappers.LongHashMap;

/**
 * Maps all the Offline Groups to chunk coordinates, allowing faster chunk access for restoring trains
 */
public class OfflineGroupMap implements Iterable<OfflineGroup> {
	private Set<OfflineGroup> groups = new HashSet<OfflineGroup>();
	private LongHashMap<HashSet<OfflineGroup>> groupmap = new LongHashMap<HashSet<OfflineGroup>>();

	@Override
	public Iterator<OfflineGroup> iterator() {
		return this.groups.iterator();
	}

	public int size() {
		return this.groups.size();
	}

	public boolean isEmpty() {
		return this.groups.isEmpty();
	}

	public void add(OfflineGroup group) {
		this.groups.add(group);
		for (long chunk : group.chunks) {
			if (!group.loadedChunks.contains(chunk)) {
				getOrCreate(chunk).add(group);
			}
		}
	}

	public boolean removeCart(UUID memberUUID) {
		for (OfflineGroup group : groups) {
			for (OfflineMember member : group.members) {
				if (member.entityUID.equals(memberUUID)) {
					// Undo previous registration
					remove(group);
					// Remove this member from the group
					ArrayList<OfflineMember> members = new ArrayList<OfflineMember>();
					for (OfflineMember m : group.members) {
						if (!m.entityUID.equals(memberUUID)) {
							members.add(m);
						}
					}
					if (!members.isEmpty()) {
						group.members = members.toArray(new OfflineMember[0]);
						group.genChunks();
						add(group);
					}
					// Finished
					return true;
				}
			}
		}
		return false;
	}

	public OfflineGroup remove(String groupName) {
		for (OfflineGroup group : groups) {
			if (group.name.equals(groupName)) {
				remove(group);
				return group;
			}
		}
		return null;
	}

	public void remove(OfflineGroup group) {
		this.groups.remove(group);
		for (long chunk : group.chunks) {
			Set<OfflineGroup> groups = get(chunk);
			if (groups != null) {
				groups.remove(group);
				if (groups.isEmpty()) {
					this.groupmap.remove(chunk);
				}
			}
		}
	}

	public Set<OfflineGroup> remove(Chunk chunk) {
		return remove(chunk.getX(), chunk.getZ());
	}

	public Set<OfflineGroup> remove(int x, int z) {
		return remove(MathUtil.longHashToLong(x, z));
	}

	public Set<OfflineGroup> remove(long chunk) {
		Set<OfflineGroup> rval = this.groupmap.remove(chunk);
		if (rval != null) {
			for (OfflineGroup group : rval) {
				group.loadedChunks.add(chunk);
			}
		}
		return rval;
	}

	public Set<OfflineGroup> get(Chunk chunk) {
		return get(chunk.getX(), chunk.getZ());
	}

	public Set<OfflineGroup> get(int x, int z) {
		return this.get(MathUtil.longHashToLong(x, z));
	}

	public Set<OfflineGroup> get(long chunk) {
		return this.groupmap.get(chunk);
	}

	public Set<OfflineGroup> getOrCreate(long chunk) {
		HashSet<OfflineGroup> rval = this.groupmap.get(chunk);
		if (rval == null) {
			rval = new HashSet<OfflineGroup>(1);
			this.groupmap.put(chunk, rval);
		}
		return rval;
	}

	public Set<OfflineGroup> values() {
		return this.groups;
	}
}
