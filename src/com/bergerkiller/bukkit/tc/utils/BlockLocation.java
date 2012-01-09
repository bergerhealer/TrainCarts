package com.bergerkiller.bukkit.tc.utils;

import org.bukkit.block.Block;

public class BlockLocation {
	public BlockLocation(Block block) {
		this(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
	}
	public BlockLocation(final String world, final int x, final int y, final int z) {
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public final int x, y, z;
	public final String world;
	public boolean equals(Object object) {
		if (object == this) return true;
		if (object instanceof BlockLocation) {
			BlockLocation bloc = (BlockLocation) object;
			return bloc.x == this.x && bloc.y == this.y && bloc.z == this.z && bloc.world.equals(this.world);
		} else {
			return false;
		}
	}
	public int hashCode() {
        int hash = 3;
        hash = 53 * hash + this.world.hashCode();
        hash = 53 * hash + (this.x ^ (this.x >> 16));
        hash = 53 * hash + (this.y ^ (this.y >> 16));
        hash = 53 * hash + (this.z ^ (this.z >> 16));
        return hash;
	}
}
