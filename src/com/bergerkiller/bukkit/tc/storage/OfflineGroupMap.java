package com.bergerkiller.bukkit.tc.storage;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.craftbukkit.util.LongHashtable;

import com.bergerkiller.bukkit.common.utils.MathUtil;

public class OfflineGroupMap implements Iterable<OfflineGroup> {
	
	private Set<OfflineGroup> groups = new HashSet<OfflineGroup>();
	private LongHashtable<HashSet<OfflineGroup>> groupmap = new LongHashtable<HashSet<OfflineGroup>>();
	
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
			getOrCreate(chunk).add(group);
		}
	}
	
	public void remove(OfflineGroup group) {
		remove(group, false);
	}
		
	public void remove(OfflineGroup group, boolean onlyFromMap) {
		if (!onlyFromMap) {
			this.groups.remove(group);
		}
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
		return remove(MathUtil.toLong(x, z));
	}
	
	public Set<OfflineGroup> remove(long chunk) {
		Set<OfflineGroup> rval = get(chunk);
		if (rval != null) {
			this.groupmap.remove(chunk);
		}
		return rval;
	}
	
	public Set<OfflineGroup> get(Chunk chunk) {
		return get(chunk.getX(), chunk.getZ());
	}
	
	public Set<OfflineGroup> get(int x, int z) {
		return this.get(MathUtil.toLong(x, z));
	}
	
	public Set<OfflineGroup> get(long chunk) {
		return this.groupmap.get(chunk);
	}
	
	public Set<OfflineGroup> getOrCreate(long chunk) {
		HashSet<OfflineGroup> rval = this.groupmap.get(chunk);
		if (rval == null) {
			rval = new HashSet<OfflineGroup>();
			this.groupmap.put(chunk, rval);
		}
		return rval;
	}
	
	public Set<OfflineGroup> values() {
		return this.groups;
	}

}
