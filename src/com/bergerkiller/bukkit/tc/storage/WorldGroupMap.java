package com.bergerkiller.bukkit.tc.storage;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.craftbukkit.util.LongHashtable;

import com.bergerkiller.bukkit.common.utils.MathUtil;

public class WorldGroupMap implements Iterable<WorldGroup> {
	
	private Set<WorldGroup> groups = new HashSet<WorldGroup>();
	private LongHashtable<HashSet<WorldGroup>> groupmap = new LongHashtable<HashSet<WorldGroup>>();
	
	@Override
	public Iterator<WorldGroup> iterator() {
		return this.groups.iterator();
	}
	
	public int size() {
		return this.groups.size();
	}
	
	public boolean isEmpty() {
		return this.groups.isEmpty();
	}
	
	public void add(WorldGroup group) {
		this.groups.add(group);
		for (long chunk : group.chunks) {
			getOrCreate(chunk).add(group);
		}
	}
	
	public void remove(WorldGroup group) {
		remove(group, false);
	}
		
	public void remove(WorldGroup group, boolean onlyFromMap) {
		if (!onlyFromMap) {
			this.groups.remove(group);
		}
		for (long chunk : group.chunks) {
			Set<WorldGroup> groups = get(chunk);
			if (groups != null) {
				groups.remove(group);
				if (groups.isEmpty()) {
					this.groupmap.remove(chunk);
				}
			}
		}
	}
	
	public Set<WorldGroup> remove(Chunk chunk) {
		return remove(chunk.getX(), chunk.getZ());
	}
	
	public Set<WorldGroup> remove(int x, int z) {
		return remove(MathUtil.toLong(x, z));
	}
	
	public Set<WorldGroup> remove(long chunk) {
		Set<WorldGroup> rval = get(chunk);
		if (rval != null) {
			this.groupmap.remove(chunk);
		}
		return rval;
	}
	
	public Set<WorldGroup> get(Chunk chunk) {
		return get(chunk.getX(), chunk.getZ());
	}
	
	public Set<WorldGroup> get(int x, int z) {
		return this.get(MathUtil.toLong(x, z));
	}
	
	public Set<WorldGroup> get(long chunk) {
		return this.groupmap.get(chunk);
	}
	
	public Set<WorldGroup> getOrCreate(long chunk) {
		HashSet<WorldGroup> rval = this.groupmap.get(chunk);
		if (rval == null) {
			rval = new HashSet<WorldGroup>();
			this.groupmap.put(chunk, rval);
		}
		return rval;
	}
	
	public Set<WorldGroup> values() {
		return this.groups;
	}

}
