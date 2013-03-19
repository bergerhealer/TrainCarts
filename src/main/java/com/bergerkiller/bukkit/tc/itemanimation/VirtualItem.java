package com.bergerkiller.bukkit.tc.itemanimation;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.entity.CommonItem;
import com.bergerkiller.bukkit.common.reflection.classes.EntityRef;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

/**
 * A dummy Item class that basically does nothing :)
 */
public class VirtualItem {
	public final CommonItem item;
	private final ItemStack itemStack;

	public VirtualItem(Location location, ItemStack itemstack) {
		this.item = new CommonItem(EntityRef.createEntityItem(location.getWorld(), location.getX(), location.getY(), location.getZ()));
		this.item.setItemStack(itemstack);
		this.itemStack = itemstack;
		refresh();
		WorldUtil.getTracker(item.getWorld()).startTracking(item.getEntity());
		item.addMotY(0.1);
	}

	public void update(Vector dir) {
		// Update velocity
		item.setMotX(dir.getX() + Math.random() * 0.02 - 0.01);
		item.setMotY(MathUtil.useOld(item.getMotY(), dir.getY(), 0.1));
		item.setMotZ(dir.getZ() + Math.random() * 0.02 - 0.01);
		// Update position using motion
		final double locX = item.getLocX();
		final double locY = item.getLocY();
		final double locZ = item.getLocZ();
		item.setLastX(locX);
		item.setLastY(locY);
		item.setLastZ(locZ);
		item.setLocX(locX + item.getMotX());
		item.setLocY(locX + item.getMotY());
		item.setLocZ(locX + item.getMotZ());
		refresh();
	}

	public void refresh() {
		item.setPositionChanged(true);
		item.setVelocityChanged(true);
		item.setChunkX(item.getLocChunkX());
		item.setChunkY(item.getLocChunkY());
		item.setChunkZ(item.getLocChunkZ());
	}

	public void die() {
		item.remove();
		WorldUtil.getTracker(item.getWorld()).stopTracking(item.getEntity());
	}

	public ItemStack getItemStack() {
		return itemStack;
	}

	public Location getLocation() {
		return item.getLocation();
	}
}
