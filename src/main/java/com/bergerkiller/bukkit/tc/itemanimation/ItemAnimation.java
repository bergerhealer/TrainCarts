package com.bergerkiller.bukkit.tc.itemanimation;

import java.util.ArrayList;
import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.GroundItemsInventory;

public class ItemAnimation {
	private static final ArrayList<ItemAnimation> runningAnimations = new ArrayList<ItemAnimation>();
	private static Task task;

	public static void start(Object from, Object to, org.bukkit.inventory.ItemStack data) {
		if (from == null || to == null || LogicUtil.nullOrEmpty(data)) {
			return;
		}
		data = data.clone();
		//try to stack the item to a nearby location first
		Location l1 = getLocation(fixObject(from));
		for (ItemAnimation anim : runningAnimations) {
			Location l2 = getLocation(fixObject(anim.item));
			if (l2 != null && l1.getWorld() == l2.getWorld()) {
				if (l1.distanceSquared(l2) < 4.0) {
					org.bukkit.inventory.ItemStack thisdata = anim.item.getItemStack();
					if (thisdata.getAmount() == 0) {
						continue;
					}
					ItemUtil.transfer(data, thisdata, Integer.MAX_VALUE);
					if (data.getAmount() == 0) {
						return;
					}
				}
			}
		}
		runningAnimations.add(new ItemAnimation(from, to, data));
		// Start the updating task if needed
		if (task == null) {
			task = new Task(TrainCarts.plugin) {
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
	public int ticksToFinish = 10;

	private ItemAnimation(Object from, Object to, org.bukkit.inventory.ItemStack data) {
		this.from = fixObject(from);
		this.to = fixObject(to);
		Location f = this.getFrom();
		Location t = this.getTo();
		if (f.getWorld() != t.getWorld()) {
			throw new IllegalArgumentException("Locations are on different worlds!");
		}
		this.item = new VirtualItem(f, data);
	}

	/**
	 * Fixes the location to be either a Location or an Entity
	 * 
	 * @param object to fix
	 * @return fixed object
	 */
	private static Object fixObject(Object object) {
		if (object instanceof GroundItemsInventory) {
			return ((GroundItemsInventory) object).getLocation();
		}
		if (object instanceof BlockState) {
			object = ((BlockState) object).getBlock();
		}
		if (object instanceof DoubleChest) {
			return ((DoubleChest) object).getLocation();
		}
		if (object instanceof Block) {
			return ((Block) object).getLocation().add(0.5, 0.5, 0.5);
		}
		if (object instanceof MinecartMember) {
			return ((MinecartMember<?>) object).getEntity().getEntity();
		}
		if (object instanceof VirtualItem) {
			object = ((VirtualItem) object).item.getEntity();
		}
		return object;
	}

	/**
	 * Tries to find out the location for a given Object
	 * 
	 * @param object to find a location for
	 * @return object location
	 */
	private static Location getLocation(Object object) {
		if (object instanceof Entity) {
			return ((Entity) object).getLocation();
		}
		if (object instanceof Location) {
			return (Location) object;
		}
		throw new IllegalArgumentException("Unable to find the location of " + object.getClass().getName());
	}

	public Location getTo() {
		return getLocation(this.to);
	}
	public Location getFrom() {
		return getLocation(this.from);
	}

	public boolean update() {
		if (--this.ticksToFinish > 0) {
			Vector dir = this.item.item.loc.offsetTo(this.getTo());
			double distancePerTick = dir.length();
			distancePerTick /= (double) this.ticksToFinish;
			dir.normalize().multiply(distancePerTick);
			this.item.update(dir);
		} else {
			return true;
		}
		return false;
	}
}
