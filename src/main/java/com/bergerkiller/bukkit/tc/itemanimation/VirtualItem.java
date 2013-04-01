package com.bergerkiller.bukkit.tc.itemanimation;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.controller.DefaultEntityNetworkController;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonItem;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * A dummy Item class that basically does nothing :)
 */
public class VirtualItem {
	public final CommonItem item;
	private final ItemStack itemStack;

	public VirtualItem(Location location, ItemStack itemstack) {
		item = (CommonItem) CommonEntity.create(EntityType.DROPPED_ITEM);
		item.last.set(this.item.loc.set(location));
		item.vel.y.add(0.1);
		item.setItemStack(itemstack);
		itemStack = itemstack;
		refresh();
		item.setNetworkController(new DefaultEntityNetworkController());
	}

	public void update(Vector dir) {
		// Update velocity
		item.vel.setX(dir.getX() + Math.random() * 0.02 - 0.01);
		item.vel.setY(MathUtil.useOld(item.vel.getY(), dir.getY(), 0.1));
		item.vel.setZ(dir.getZ() + Math.random() * 0.02 - 0.01);
		// Update position using motion
		item.last.set(item.loc);
		item.loc.add(item.vel);
		refresh();
	}

	public void refresh() {
		item.setPositionChanged(true);
		item.setVelocityChanged(true);
		item.setChunkX(item.loc.x.chunk());
		item.setChunkY(item.loc.y.chunk());
		item.setChunkZ(item.loc.z.chunk());
	}

	public void die() {
		item.remove();
		item.setNetworkController(null);
	}

	public ItemStack getItemStack() {
		return itemStack;
	}

	public Location getLocation() {
		return item.getLocation();
	}
}
