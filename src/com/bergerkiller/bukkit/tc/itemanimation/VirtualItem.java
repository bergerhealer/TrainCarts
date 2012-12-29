package com.bergerkiller.bukkit.tc.itemanimation;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.reflection.classes.EntityRef;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.NativeUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

import net.minecraft.server.v1_4_6.EntityItem;

/**
 * A dummy class that basically does nothing :)
 */
public class VirtualItem extends EntityItem {

	public VirtualItem(Location location, ItemStack itemstack) {
		super(NativeUtil.getNative(location.getWorld()), location.getX(), location.getY(), location.getZ(), NativeUtil.getNative(itemstack));
		refresh();
		WorldUtil.getTracker(world).track(this);
	}

	@Override
	public void j_() {
		this.lastX = this.locX;
		this.lastY = this.locY;
		this.lastZ = this.locZ;
		this.locX += this.motX;
		this.locY += this.motY;
		this.locZ += this.motZ;
		refresh();
	};

	public void refresh() {
		this.al = true;
		EntityRef.chunkX.set(this, MathUtil.toChunk(this.locX));
		EntityRef.chunkY.set(this, MathUtil.toChunk(this.locY));
		EntityRef.chunkZ.set(this, MathUtil.toChunk(this.locZ));
	}

	public void die() {
		super.die();
		WorldUtil.getTracker(world).untrackEntity(this);
	}
}
