package com.bergerkiller.bukkit.tc.itemanimation;

import java.util.ArrayList;
import java.util.Iterator;

import net.minecraft.server.TileEntity;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;

public class ItemAnimation {
	
	private static final ArrayList<ItemAnimation> runningAnimations = new ArrayList<ItemAnimation>();
	private static Task task;
	public static void start(Object from, Object to, net.minecraft.server.ItemStack data) {
		if (data == null) return;
		start(from, to, new CraftItemStack(data));
	}
	public static void start(Object from, Object to, ItemStack data) {
		if (from == null || to == null || data == null) return;
		if (data.getAmount() == 0) return;
		data = data.clone();
		//try to stack the item to a nearby location first
		Location l1 = getLocation(fixObject(from));
		for (ItemAnimation anim : runningAnimations) {
			Location l2 = getLocation(fixObject(anim.item));
			if (l2 != null && l1.getWorld() == l2.getWorld()) {
				if (l1.distanceSquared(l2) < 4.0) {
					ItemStack thisdata = new CraftItemStack(anim.item.itemStack);
					if (thisdata.getAmount() == 0) continue;
					ItemUtil.transfer(data, thisdata, Integer.MAX_VALUE);
					if (data.getAmount() == 0) return;
				}
			}
		}
		if (data.getAmount() == 0) return;
		runningAnimations.add(new ItemAnimation(from, to, data));
		if (task != null) return;
		task = new Task() {
			public void run() {
				Iterator<ItemAnimation> iter = runningAnimations.iterator();
				ItemAnimation anim;
				while (iter.hasNext()) {
					anim = iter.next();
					if (anim.update()) {
						anim.item.die();
						iter.remove();
					}
				}
				if (runningAnimations.isEmpty()) {
					Task.stop(task);
					task = null;
				}
			}
		}.start(1, 1);
	}
	public static void deinit() {
		for (ItemAnimation anim : runningAnimations) {
			anim.item.die();
		}
		runningAnimations.clear();
		Task.stop(task);
		task = null;
	}
	
	private final Object from;
	private final Object to;
	private final VirtualItem item;
	private ItemAnimation(Object from, Object to, ItemStack data) {
		this.from = fixObject(from);
		this.to = fixObject(to);
		Location f = this.getFrom();
		Location t = this.getFrom();
		if (f.getWorld() != t.getWorld()) {
			throw new IllegalArgumentException("Locations are on different worlds!");
		}
		this.item = new VirtualItem(f, data);
		this.item.motY += 0.1;
	}
		
	private static Object fixObject(Object object) {
		if (object instanceof TileEntity) {
			TileEntity t = (TileEntity) object;
			return new Location(t.world.getWorld(), t.x, t.y, t.z);
		}
		if (object instanceof BlockState) {
			object = ((BlockState) object).getBlock();
		}
		if (object instanceof Block) {
			return ((Block) object).getLocation().add(0.5, 0.5, 0.5);
		}
		if (object instanceof net.minecraft.server.Entity) {
			return ((net.minecraft.server.Entity) object).getBukkitEntity();
		}
		return object;
	}
	private static Location getLocation(Object object) {
		if (object instanceof Entity) {
			Entity e = (Entity) object;
			return e.isDead() ? null : e.getLocation();
		}
		return (Location) object;
	}
	
	public Location getTo() {
		return getLocation(this.to);
	}
	public Location getFrom() {
		return getLocation(this.from);
	}
	
	public int ticksToFinish = 10;
	
	public Vector getDirection(Location to) {
		return new Vector(to.getX() - this.item.locX, to.getY() - this.item.locY, to.getZ() - this.item.locZ);
	}
	public boolean update() {
		if (--this.ticksToFinish > 0) {
			Location to = this.getTo();
			if (to == null) return true;
			Vector dir = this.getDirection(to);
			double distancePerTick = dir.length();
			distancePerTick /= (double) this.ticksToFinish;
			
			dir.normalize().multiply(distancePerTick);
			
			this.item.motX = dir.getX() + Math.random() * 0.02 - 0.01;
			this.item.motY = MathUtil.useOld(this.item.motY, dir.getY(), 0.1);
			this.item.motZ = dir.getZ() + Math.random() * 0.02 - 0.01;
			this.item.velocityChanged = true;
			this.item.G_();
		} else {
			return true;
		}
		return false;
	}

}
