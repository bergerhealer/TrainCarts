package com.bergerkiller.bukkit.tc.itemanimation;

import java.util.ArrayList;
import java.util.Iterator;

import net.minecraft.server.TileEntity;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.MathUtil;

public class ItemAnimation {
	
	private static final ArrayList<ItemAnimation> runningAnimations = new ArrayList<ItemAnimation>();
	private static Task task;
	public static void start(Object from, Object to, ItemStack data) {
		runningAnimations.add(new ItemAnimation(from, to, data));
		if (task != null) return;
		task = new Task() {
			public void run() {
				Iterator<ItemAnimation> iter = runningAnimations.iterator();
				while (iter.hasNext()) {
					if (iter.next().update()) {
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
			return ((Block) object).getLocation();
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
	
	public boolean update() {
		Location to = this.getTo();
		if (to == null) return true;
		Vector dir = new Vector(to.getX() - this.item.locX, to.getY() - this.item.locY, to.getZ() - this.item.locZ);
		double len = dir.length();
		if (len <= 0.5) {
			this.item.die();
			return true;
		}
		final double factor;
		if (len > 1) {
			factor = 0.8;
		} else if (len > 0.75) {
			factor = 0.5;
		} else {
			factor = 0.25;
		}
		dir.multiply((factor / len) / len);
		
		final double rate = 0.8;
		this.item.motX = MathUtil.useOld(this.item.motX, dir.getX(), rate);
		this.item.motY = MathUtil.useOld(this.item.motY, dir.getY(), rate);
		this.item.motY += 0.01;
		this.item.motZ = MathUtil.useOld(this.item.motZ, dir.getZ(), rate);
		this.item.velocityChanged = true;
		this.item.y_();
		return false;
	}

}
