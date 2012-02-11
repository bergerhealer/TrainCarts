package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;

import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.Packet22Collect;
import net.minecraft.server.WorldServer;

import com.bergerkiller.bukkit.common.SafeField;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.API.MinecartSwapEvent;

public class Util {
	
	/*
	 * Entity states
	 */
	public static float getMinecartYaw(Minecart minecart) {
		return getMinecartYaw(EntityUtil.getNative(minecart));
	}
	public static float getMinecartYaw(EntityMinecart minecart) {
		if (minecart instanceof MinecartMember) return ((MinecartMember) minecart).getYaw();
		return minecart.yaw;
	}
	
	public static void setItemMaxSize(Material material, int maxstacksize) {
		setItemMaxSize(net.minecraft.server.Item.byId[material.getId()], maxstacksize);
	}
	public static void setItemMaxSize(net.minecraft.server.Item item, int maxstacksize) {
		SafeField.set(item, "maxStackSize", maxstacksize);
	}
	
	
	public static boolean hasSignsAttached(Block block) {
		Block b;
		for (BlockFace dir : FaceUtil.attachedFaces) {
			b = block.getRelative(dir);
			if (BlockUtil.isSign(b) && BlockUtil.getAttachedFace(b) == dir.getOppositeFace()) {
				return true;
			}
		}
		return false;
	}
	public static Block getRailsFromSign(final Block signblock) {
		int type = signblock.getTypeId();
		if (type == Material.SIGN_POST.getId()) {
			Block rails = signblock.getRelative(BlockFace.UP);
			if (isRails(rails)) return rails;
		} else if (type == Material.WALL_SIGN.getId()) {
			Block rails = BlockUtil.getAttachedBlock(signblock);	
			do {
				rails = rails.getRelative(BlockFace.UP);
				if (isRails(rails)) return rails;
			} while (hasSignsAttached(rails));
		}
		return null;
	}
	private static List<Block> blockbuff = new ArrayList<Block>();
	public static List<Block> getSignsFromRails(Block railsblock) {
		return getSignsFromRails(blockbuff, railsblock);
	}
	public static List<Block> getSignsFromRails(List<Block> rval, Block railsblock) {
		rval.clear();
		Block b;
		boolean added;
		do {
			added = false;
			railsblock = railsblock.getRelative(BlockFace.DOWN);
			if (railsblock.getTypeId() == Material.SIGN_POST.getId()) {
				rval.add(railsblock);
				break;
			} else {
				//check signs attached
				for (BlockFace face : FaceUtil.axis) {
					b = railsblock.getRelative(face);
					if (b.getTypeId() == Material.WALL_SIGN.getId()) {
						if (BlockUtil.getAttachedFace(b) == face.getOppositeFace()) {
							rval.add(b);
							added = true;
						}
					}
				}
			}
		} while (added);
		return rval;
	}
			
	/*
	 * Entity miscellaneous
	 */
	public static void pickUpAnimation(Item item, Entity by) {
		pickUpAnimation(EntityUtil.getNative(item), EntityUtil.getNative(by));
	}
	public static void pickUpAnimation(EntityItem item, net.minecraft.server.Entity by) {
		((WorldServer) item.world).tracker.a(by, new Packet22Collect(item.id, by.id));
	}
	
	public static void replaceMinecarts(EntityMinecart toreplace, EntityMinecart with) {
		with.yaw = toreplace.yaw;
		with.pitch = toreplace.pitch;
		with.locX = toreplace.locX;
		with.locY = toreplace.locY;
		with.locZ = toreplace.locZ;
		with.motX = toreplace.motX;
		with.motY = toreplace.motY;
		with.motZ = toreplace.motZ;
		with.b = toreplace.b;
		with.c = toreplace.c;
		with.fallDistance = toreplace.fallDistance;
		with.ticksLived = toreplace.ticksLived;
		with.uniqueId = toreplace.uniqueId;
		with.setDamage(toreplace.getDamage());
		ItemUtil.transfer(toreplace, with);
		with.dead = false;
		toreplace.dead = true;
		
		with.setDerailedVelocityMod(toreplace.getDerailedVelocityMod());
		with.setFlyingVelocityMod(toreplace.getFlyingVelocityMod());
		
		//longer public in 1.0.0... :-(
		//with.e = toreplace.e;
		
		//swap
		MinecartSwapEvent.call(toreplace, with);
		((WorldServer) toreplace.world).tracker.untrackEntity(toreplace);
		toreplace.world.removeEntity(toreplace);
		with.world.addEntity(with);
		if (toreplace.passenger != null) toreplace.passenger.setPassengerOf(with);
	}
		
	public static boolean isRails(Block block) {
		return block != null && isRails(block.getTypeId());
	}
	public static boolean isRails(Material type) {
		return isRails(type.getId());
	}
	public static boolean isRails(int id) {
		return BlockUtil.isRails(id) || isPressurePlate(id);
	}
	
	public static boolean isPressurePlate(int id) {
		return BlockUtil.isType(id, Material.WOOD_PLATE.getId(), Material.STONE_PLATE.getId());
	}
	
	public static Block getRailsBlock(Block from) {
		if (isRails(from)) {
			return from;
		} else {
			from = from.getRelative(BlockFace.DOWN);
			return isRails(from) ? from : null;
		}
	}
	public static boolean isRails(Block block, BlockFace direction) {
		return getRailsBlock(block.getRelative(direction)) != null;
	}
	
	/*
	 * This will return:
	 * South or west if it's a straight piece
	 * Self if it is a cross-intersection
	 */
	public static BlockFace getPlateDirection(Block plate) {
		boolean s = isRails(plate, BlockFace.NORTH) || isRails(plate, BlockFace.SOUTH);
		boolean w = isRails(plate, BlockFace.EAST) || isRails(plate, BlockFace.WEST);
		if (s && w) {
			return BlockFace.SELF;
		} else if (w) {
			return BlockFace.WEST;
		} else if (s) {
			return BlockFace.SOUTH;
		} else {
			return BlockFace.SELF;
		}
	}
	
}
