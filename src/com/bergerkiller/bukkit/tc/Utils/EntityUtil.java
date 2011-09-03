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
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.MinecartMember;
import com.bergerkiller.bukkit.tc.TrackMap;
import com.bergerkiller.bukkit.tc.TrainCarts;

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
		return minecart.yaw + 90;
	}
	
	/*
	 * Entity miscellaneous
	 */
	public static boolean isSharingRails(Minecart m1, Minecart m2) {
		Block bm1 = BlockUtil.getRailsBlock(m1);
		Block bm2 = BlockUtil.getRailsBlock(m2);
		return TrackMap.connected(bm1, bm2);
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
		with.uniqueId = toreplace.uniqueId;
		toreplace.uniqueId = new UUID(0, 0);
		ItemStack[] items = toreplace.getContents();
		for (int i = 0;i < items.length;i++) {
			if (items[i] != null) {
				with.setItem(i, new ItemStack(items[i].id, items[i].count, items[i].damage));
			}
		}
		for (int i = 0;i < items.length;i++) toreplace.setItem(i, null);
		
		//This is the only 'real' remove method that seems to work...
		toreplace.dead = true;
		Packet29DestroyEntity packet = new Packet29DestroyEntity(toreplace.id);
		for (Player p : toreplace.world.getWorld().getPlayers()) {
			getNative(p).netServerHandler.sendPacket(packet);
		}
		
		with.world.addEntity(with);
		if (toreplace.passenger != null) toreplace.passenger.setPassengerOf(with);
	}	
	public static boolean pushAway(EntityMinecart vehicle, Entity topush) {
		if (topush instanceof Player) {
			if (!TrainCarts.pushAwayPlayers) return false;
		} else if (topush instanceof Creature) {
			if (TrainCarts.pushAwayMobs) {
				//Enter if possible
				if (vehicle.passenger == null) {
					getNative(topush).setPassengerOf(vehicle);
					return true;
				}
			} else {
				return false;
			}
		} else {
			if (!TrainCarts.pushAwayMisc) {
				return false;
			} else {
				if (topush instanceof Minecart) {
					return false;
				}
			}
		}
		//===============================Entity check end======================
		double speed = Util.length(vehicle.motX, vehicle.motY, vehicle.motZ);
		if (speed > TrainCarts.pushAwayAtVelocity) {
			//push that bastard away!
			float yaw = getMinecartYaw(vehicle);
			//which do we choose, -90 or 90?
			while (yaw < 0) yaw += 360;
			while (yaw >= 360) yaw -= 360;
			float lookat = Util.getLookAtYaw(vehicle.getBukkitEntity(), topush) - yaw;
			if (Util.getAngleDifference(lookat, 180) < 90) return false; //pushing
			while (lookat > 180) lookat -= 360;
			while (lookat < -180) lookat += 360;
			yaw -= 90;
			if (lookat > 0) {
				yaw -= 90;
			} else {
				yaw += 90;
			}
			//push the obstacle awaayyy :d
			Vector vel = Util.getDirection(yaw, 0).multiply(TrainCarts.pushAwayForce);
			topush.setVelocity(vel);
			return true;
		} else {
			return false;
		}
	}
	public static boolean pushAway(Minecart vehicle, Entity topush) {
		return pushAway(getNative(vehicle), topush);
	}
	
}
