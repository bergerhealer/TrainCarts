package com.bergerkiller.bukkit.tc.utils;

import java.util.UUID;

import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Packet22Collect;
import net.minecraft.server.WorldServer;

import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.API.MinecartSwapEvent;

public class EntityUtil {
	
	/*
	 * Entity getters
	 */
	public static EntityItem getNative(Item item) {
		return (EntityItem) getNative((Entity) item);
	}
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
	public static void pickUpAnimation(Item item, Entity by) {
		pickUpAnimation(getNative(item), getNative(by));
	}
	public static void pickUpAnimation(EntityItem item, net.minecraft.server.Entity by) {
		((WorldServer) item.world).tracker.a(by, new Packet22Collect(item.id, by.id));
	}
	public static void transferItems(EntityMinecart from, EntityMinecart to) {
		net.minecraft.server.ItemStack[] items = from.getContents();
		for (int i = 0;i < items.length;i++) {
			if (items[i] != null) {
				to.setItem(i, new net.minecraft.server.ItemStack(items[i].id, items[i].count, items[i].b));
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
		with.b = toreplace.b;
		with.c = toreplace.c;
		with.fallDistance = toreplace.fallDistance;
		with.ticksLived = toreplace.ticksLived;
		with.uniqueId = toreplace.uniqueId;
		with.setDamage(toreplace.getDamage());
		transferItems(toreplace, with);
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
