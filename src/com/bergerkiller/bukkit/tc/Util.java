package com.bergerkiller.bukkit.tc;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;

import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.Packet22Collect;
import net.minecraft.server.WorldServer;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
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
	
	public static final int[] railsTypes = new int[] {
		Material.RAILS.getId(), Material.POWERED_RAIL.getId(), Material.DETECTOR_RAIL.getId(),
		Material.WOOD_PLATE.getId(), Material.STONE_PLATE.getId()};
	
	public static boolean isRails(Block block) {
		return BlockUtil.isType(block.getTypeId(), railsTypes);
	}
	public static boolean isRails(Material type) {
		return BlockUtil.isType(type.getId(), railsTypes);
	}
	public static boolean isRails(int id) {
		return BlockUtil.isType(id, railsTypes);
	}
	
}
