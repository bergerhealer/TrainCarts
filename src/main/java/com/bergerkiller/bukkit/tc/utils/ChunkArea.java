package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.bases.IntVector2;

/**
 * Stores the chunk coordinates and a 5x5 chunk area around it
 */
public class ChunkArea {
	public static final int CHUNK_RANGE = 2;
	public static final int CHUNK_EDGE = 2 * CHUNK_RANGE + 1;
	public static final int CHUNK_AREA = CHUNK_EDGE * CHUNK_EDGE;
	private int x, z;
	private final IntVector2[] chunks = new IntVector2[CHUNK_AREA];

	public ChunkArea(ChunkArea area) {
		this.x = area.x;
		this.z = area.z;
		System.arraycopy(area.chunks, 0, this.chunks, 0, CHUNK_AREA);
	}

	public ChunkArea(int x, int z) {
		updateForced(x, z);
	}

	public int getX() {
		return x;
	}

	public int getZ() {
		return z;
	}

	public IntVector2[] getChunks() {
		return chunks;
	}

	public void update(ChunkArea area) {
		if (this.x != area.x || this.z != area.z) {
			this.x = area.x;
			this.z = area.z;
			System.arraycopy(area.chunks, 0, this.chunks, 0, CHUNK_AREA);
		}
	}

	public void update(int x, int z) {
		if (this.x != x || this.z != z) {
			updateForced(x, z);
		}
	}

	private void updateForced(int x, int z) {
		this.x = x;
		this.z = z;
		int cx, cz;
		int i = 0;
		for (cx = -CHUNK_RANGE; cx <= CHUNK_RANGE; cx++) {
			for (cz = -CHUNK_RANGE; cz <= CHUNK_RANGE; cz++) {
				chunks[i++] = new IntVector2(x + cx, z + cz);
			}
		}
	}
}
