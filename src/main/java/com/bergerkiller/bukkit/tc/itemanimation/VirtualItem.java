package com.bergerkiller.bukkit.tc.itemanimation;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.reflection.classes.EntityRef;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

/**
 * A dummy Item class that basically does nothing :)
 */
public class VirtualItem {
	public final Item item;
	private final Object handle;
	private final ItemStack itemStack;

	public VirtualItem(Location location, ItemStack itemstack) {
		this.item = EntityRef.createEntityItem(location.getWorld(), location.getX(), location.getY(), location.getZ());
		this.item.setItemStack(itemstack);
		this.handle = Conversion.toEntityHandle.convert(this.item);
		this.itemStack = itemstack;
		refresh();
		WorldUtil.getTracker(item.getWorld()).startTracking(item);
		EntityUtil.setMotY(item, EntityUtil.getMotY(item) + 0.1);
	}

	public void update(Vector dir) {
		// Update velocity
		EntityUtil.setMotX(item, dir.getX() + Math.random() * 0.02 - 0.01);
		EntityUtil.setMotY(item, MathUtil.useOld(EntityUtil.getMotY(item), dir.getY(), 0.1));
		EntityUtil.setMotZ(item, dir.getZ() + Math.random() * 0.02 - 0.01);
		// Update position using motion
		final double locX = EntityUtil.getLocX(item);
		final double locY = EntityUtil.getLocY(item);
		final double locZ = EntityUtil.getLocZ(item);
		EntityUtil.setLastX(item, locX);
		EntityUtil.setLastY(item, locY);
		EntityUtil.setLastZ(item, locZ);
		EntityUtil.setLocX(item, locX + EntityUtil.getMotX(item));
		EntityUtil.setLocY(item, locY + EntityUtil.getMotY(item));
		EntityUtil.setLocZ(item, locZ + EntityUtil.getMotZ(item));
		refresh();
	}

	public void refresh() {
		EntityRef.positionChanged.set(handle, true);
		EntityRef.velocityChanged.set(handle, true);
		EntityUtil.setChunkX(item, MathUtil.toChunk(EntityUtil.getLocX(item)));
		EntityUtil.setChunkY(item, MathUtil.toChunk(EntityUtil.getLocY(item)));
		EntityUtil.setChunkZ(item, MathUtil.toChunk(EntityUtil.getLocZ(item)));
	}

	public void die() {
		item.remove();
		WorldUtil.getTracker(item.getWorld()).stopTracking(item);
	}

	public ItemStack getItemStack() {
		return itemStack;
	}

	public Location getLocation() {
		return item.getLocation();
	}
}
