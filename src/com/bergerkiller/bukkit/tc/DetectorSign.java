package com.bergerkiller.bukkit.tc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.bergerkiller.bukkit.tc.utils.BlockUtil;

import net.minecraft.server.ChunkCoordinates;

public class DetectorSign {
	private static final Map<ChunkCoordinates, Set<DetectorSign>> signs = new HashMap<ChunkCoordinates, Set<DetectorSign>>();
	private static Set<DetectorSign> getSigns(ChunkCoordinates at) {
		Set<DetectorSign> rval = signs.get(at);
		if (rval == null) {
			rval = new HashSet<DetectorSign>(1);
			signs.put(at, rval);
		}
		return rval;
	}
	public static DetectorSign getSign(Block at) {
		Set<DetectorSign> signset = signs.get(BlockUtil.getCoordinates(at));
		if (signset == null) return null;
		for (DetectorSign sign : signset) {
			if (sign.world.equals(at.getWorld().getUID())) return sign;
		}
		return null;
	}
	public static DetectorSign add(Block startsign, Block endsign, TrackMap rails) {
		return new DetectorSign(startsign, endsign, rails.getCoordinates());
	}
	
	private DetectorSign(Block sign1, Block sign2, final Set<ChunkCoordinates> rails) {
		this(BlockUtil.getCoordinates(sign1), BlockUtil.getCoordinates(sign2), sign1.getWorld().getUID(), rails);
	}
	private DetectorSign(final ChunkCoordinates sign1, final ChunkCoordinates sign2, final UUID world, final Set<ChunkCoordinates> rails) {
		this.sign1 = sign1;
		this.sign2 = sign2;
		this.world = world;
		this.rails = rails;
	    for (ChunkCoordinates coord : rails) {
	    	getSigns(coord).add(this);
	    }
	}
	public final Set<ChunkCoordinates> rails;
	public final ChunkCoordinates sign1;
	public final ChunkCoordinates sign2;
	public final UUID world;
	
	public Block getSign1(World world) {
		return BlockUtil.getBlock(world, this.sign1);
	}
	public Block getSign2(World world) {
		return BlockUtil.getBlock(world, this.sign2);
	}

}
