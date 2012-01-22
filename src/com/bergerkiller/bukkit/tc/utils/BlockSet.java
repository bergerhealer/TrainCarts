package com.bergerkiller.bukkit.tc.utils;

import java.util.HashSet;

import net.minecraft.server.ChunkCoordinates;

import org.bukkit.World;
import org.bukkit.block.Block;

public class BlockSet extends HashSet<BlockLocation> {
	private static final long serialVersionUID = 1L;

	public boolean contains(Block block) {
		return super.contains(new BlockLocation(block));
	}
	public boolean contains(World world, final int x, final int y, final int z) {
		return this.contains(world.getName(), x, y, z);
	}
	public boolean contains(World world, ChunkCoordinates coord) {
		return this.contains(world.getName(), coord.x, coord.y, coord.z);
	}
	public boolean contains(final String world, ChunkCoordinates coord) {
		return this.contains(world, coord.x, coord.y, coord.z);
	}
	public boolean contains(final String world, final int x, final int y, final int z) {
		return super.contains(new BlockLocation(world, x, y, z));
	}
	
	public boolean remove(Block block) {
		return super.remove(new BlockLocation(block));
	}
	public boolean remove(World world, final int x, final int y, final int z) {
		return this.remove(world.getName(), x, y, z);
	}
	public boolean remove(World world, ChunkCoordinates coord) {
		return this.remove(world.getName(), coord.x, coord.y, coord.z);
	}
	public boolean remove(final String world, ChunkCoordinates coord) {
		return this.remove(world, coord.x, coord.y, coord.z);
	}
	public boolean remove(final String world, final int x, final int y, final int z) {
		return super.remove(new BlockLocation(world, x, y, z));
	}
	
	public boolean add(Block block) {
		return super.add(new BlockLocation(block));
	}
	public boolean add(World world, final int x, final int y, final int z) {
		return this.add(world.getName(), x, y, z);
	}
	public boolean add(World world, ChunkCoordinates coord) {
		return this.add(world.getName(), coord.x, coord.y, coord.z);
	}
	public boolean add(final String world, ChunkCoordinates coord) {
		return this.add(world, coord.x, coord.y, coord.z);
	}
	public boolean add(final String world, final int x, final int y, final int z) {
		return super.add(new BlockLocation(world, x, y, z));
	}

}
