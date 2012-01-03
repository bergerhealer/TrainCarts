package com.bergerkiller.bukkit.tc.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.block.Block;

public class BlockMap <T> {
	private final Map<BlockLocation, T> map = new HashMap<BlockLocation, T>();
	
	@SuppressWarnings({"unused", "unchecked"})
	private class BlockLocation {
		public BlockLocation(Block block) {
			this(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
		}
		public BlockLocation(final UUID world, final int x, final int y, final int z) {
			this.world = world;
			this.x = x;
			this.y = y;
			this.z = z;
		}
		public final int x, y, z;
		public final UUID world;
		public boolean equals(Object object) {
			BlockLocation bloc = (BlockLocation) object;
			return bloc.x == this.x && bloc.y == this.y && bloc.z == this.z && bloc.world.equals(this.world);
		}
		public int hashcode() {
	        int hash = 3;
	        hash = 53 * hash + this.world.hashCode();
	        hash = 53 * hash + (this.x ^ (this.x >> 16));
	        hash = 53 * hash + (this.y ^ (this.y >> 16));
	        hash = 53 * hash + (this.z ^ (this.z >> 16));
	        return hash;
		}
	}
	
	public boolean containsKey(Block block) {
		return this.map.containsKey(new BlockLocation(block));
	}
	public boolean containsValue(T value) {
		return this.map.containsValue(value);
	}
	public T get(Block block) {
		return this.map.get(new BlockLocation(block));
	}
	public T put(Block block, T value) {
		return this.map.put(new BlockLocation(block), value);
	}
	public T remove(Block block) {
		return this.map.remove(new BlockLocation(block));
	}
	public boolean isEmpty() {
		return this.map.isEmpty();
	}
	public int size() {
		return this.map.size();
	}
	public Collection<T> values() {
		return this.map.values();
	}

}