package com.bergerkiller.bukkit.tc.Utils;

import java.util.UUID;

import net.minecraft.server.EntityMinecart;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.ItemStack;
import net.minecraft.server.Packet29DestroyEntity;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrackMap;

public class EntityUtil {
	
	/*
	 * Entity getters
	 */
	public static EntityMinecart getNative(Minecart m) {
		return (EntityMinecart) getNative((Entity) m);
	}
	public static net.minecraft.server.Entity getNative(Entity e) {
		return ((CraftEntity) e).getHandle();
	}
	public static EntityPlayer getNative(Player p) {
		return ((EntityPlayer) getNative((Entity) p));
	}
	public static net.minecraft.server.WorldServer getNative(World w) {
		return ((CraftWorld) w).getHandle();
	}
	public static Minecart getMinecart(World w, UUID UID) {
		Entity e = getEntity(w, UID);
		if (e != null && e instanceof Minecart) return (Minecart) e;
		return null;
	}
	public static Entity getEntity(World w, UUID UID) {
		for (Entity e : w.getEntities()) {
			if (e.getUniqueId().equals(UID)) return e;
		}
		return null;
	}
	
	/*
	 * Entity states
	 */
	public static float getMinecartYaw(Minecart minecart) {
		return getMinecartYaw(getNative(minecart));
	}
	public static float getMinecartYaw(EntityMinecart minecart) {
		if (minecart instanceof MinecartMember) return ((MinecartMember) minecart).getYaw();
		return minecart.yaw;
	}
		
	/*
	 * Entity miscellaneous
	 */
	public static boolean isSharingRails(Minecart m1, Minecart m2) {
		Block bm1 = BlockUtil.getRailsBlock(m1);
		Block bm2 = BlockUtil.getRailsBlock(m2);
		return TrackMap.connected(bm1, bm2);
	}
	public static void transferItems(EntityMinecart from, EntityMinecart to) {
		ItemStack[] items = from.getContents();
		for (int i = 0;i < items.length;i++) {
			if (items[i] != null) {
				to.setItem(i, new ItemStack(items[i].id, items[i].count, items[i].b));
			}
		}
		for (int i = 0;i < items.length;i++) from.setItem(i, null);
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
		with.e = toreplace.e;
		with.f = toreplace.f;
		with.g = toreplace.g;
		with.derailedX = toreplace.derailedX;
		with.derailedY = toreplace.derailedY;
		with.derailedZ = toreplace.derailedZ;
		with.flyingX = toreplace.flyingX;
		with.flyingY = toreplace.flyingY;
		with.flyingZ = toreplace.flyingZ;
		with.damage = toreplace.damage;
		with.fallDistance = toreplace.fallDistance;
		with.ticksLived = toreplace.ticksLived;
		with.uniqueId = toreplace.uniqueId;
		with.dead = toreplace.dead;
		toreplace.uniqueId = new UUID(0, 0);
		transferItems(toreplace, with);
		
		//This is the only 'real' remove method that seems to work...
		toreplace.dead = true;
		Packet29DestroyEntity packet = new Packet29DestroyEntity(toreplace.id);
		for (Player p : toreplace.world.getWorld().getPlayers()) {
			getNative(p).netServerHandler.sendPacket(packet);
		}
				
		with.world.addEntity(with);
		if (toreplace.passenger != null) toreplace.passenger.setPassengerOf(with);
	}	
		
	/* 
	 * States
	 */
	public static boolean isMob(Entity e) {
		if (e instanceof LivingEntity) {
			if (!(e instanceof HumanEntity)) {
				return true;
			}
		}
		return false;
	}
}
