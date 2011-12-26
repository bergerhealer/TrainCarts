package com.bergerkiller.bukkit.tc;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.utils.EntityUtil;

import net.minecraft.server.AxisAlignedBB;
import net.minecraft.server.Block;
import net.minecraft.server.BlockMinecartTrack;
import net.minecraft.server.DamageSource;
import net.minecraft.server.Entity;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.MathHelper;
import net.minecraft.server.Vec3D;
import net.minecraft.server.World;
import net.minecraft.server.EntityMinecart;

@SuppressWarnings("rawtypes")
public class NativeMinecartMember extends EntityMinecart {
	/*
	 * Values taken over from source to use in the m_ function, see attached source links
	 */
	public int e; //warning! NOT OVERRIDEN FROM BASE CLASS!
	private boolean f;
	private static final int[][][] matrix = new int[][][] { 
		{ { 0, 0, -1 }, { 0, 0, 1 } }, { { -1, 0, 0 }, { 1, 0, 0 } },
		{ { -1, -1, 0 }, { 1, 0, 0 } }, { { -1, 0, 0 }, { 1, -1, 0 } },
		{ { 0, 0, -1 }, { 0, -1, 1 } }, { { 0, -1, -1 }, { 0, 0, 1 } },
		{ { 0, 0, 1 }, { 1, 0, 0 } }, { { 0, 0, 1 }, { -1, 0, 0 } },
		{ { 0, 0, -1 }, { -1, 0, 0 } }, { { 0, 0, -1 }, { 1, 0, 0 } } };

	private int h;
	private double i;
	private double j;
	private double k;
	private double l;
	private double m;

	public int getChunkX() {
		return Util.locToChunk(this.locX);
	}
	public int getChunkZ() {
		return Util.locToChunk(this.locZ);
	}
	public double getX() {
		return this.locX;
	}
	public double getY() {
		return this.locY;
	}
	public double getZ() {
		return this.locZ;
	}
	public int getBlockX() {
		return MathHelper.floor(this.getX());
	}
	public int getBlockY() {
		return MathHelper.floor(this.getY());
	}
	public int getBlockZ() {
		return MathHelper.floor(this.getZ());
	}
	public org.bukkit.World getWorld() {
		return world.getWorld();
	}
	public Location getLocation() {
		if (this instanceof MinecartMember) {
			MinecartMember mm = (MinecartMember) this;
			return new Location(getWorld(), getX(), getY(), getZ(), mm.getYaw(), mm.getPitch());
		} else {
			return new Location(getWorld(), getX(), getY(), getZ(), this.yaw, this.pitch);
		}
	}

	private boolean ignoreForces() {
		return group().isActionWait();
	}

	private MinecartMember member() {
		return (MinecartMember) this;
	}
	private MinecartGroup group() {
		return member().getGroup();
	}

	public NativeMinecartMember(World world, double d0, double d1, double d2, int i) {
		super(world, d0, d1, d2, i);
		this.locX = this.lastX;
		this.locY = this.lastY;
		this.locZ = this.lastZ;
		Location loc = new Location(this.world.getWorld(), this.locX, this.locY, this.locZ);
		this.yaw = BlockUtil.getRailsYaw(BlockUtil.getRails(loc));
	}

	/*
	 * Replaced standard droppings based on TrainCarts settings. For source, see:
	 * https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/EntityMinecart.java
	 */
	@Override
	public boolean damageEntity(DamageSource damagesource, int i) {
		if (!this.world.isStatic && !this.dead) {
			// CraftBukkit start
			Vehicle vehicle = (Vehicle) this.getBukkitEntity();
			org.bukkit.entity.Entity passenger = (damagesource.getEntity() == null) ? null : damagesource.getEntity().getBukkitEntity();

			VehicleDamageEvent event = new VehicleDamageEvent(vehicle, passenger, i);
			this.world.getServer().getPluginManager().callEvent(event);

			if (event.isCancelled()) {
				return true;
			}

			i = event.getDamage();
			// CraftBukkit end

			this.d(-this.m());
			this.c(10);
			this.aB();
			this.setDamage(this.getDamage() + i * 10);
			if (this.getDamage() > 40) {
				if (this.passenger != null) {
					this.passenger.mount(this);
				}

				// CraftBukkit start
				VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, passenger);
				this.world.getServer().getPluginManager().callEvent(destroyEvent);

				if (destroyEvent.isCancelled()) {
					this.setDamage(40); // Maximize damage so this doesn't get triggered again right away
					return true;
				}
				// CraftBukkit end

				this.die();
				if (TrainCarts.breakCombinedCarts || this.type == 0) {
					if (TrainCarts.spawnItemDrops) this.a(Item.MINECART.id, 1, 0.0F);
				}
				if (this.type == 1) {
					EntityMinecart entityminecart = this;

					for (int j = 0; j < entityminecart.getSize(); ++j) {
						ItemStack itemstack = entityminecart.getItem(j);

						if (itemstack != null) {
							float f = this.random.nextFloat() * 0.8F + 0.1F;
							float f1 = this.random.nextFloat() * 0.8F + 0.1F;
							float f2 = this.random.nextFloat() * 0.8F + 0.1F;

							while (itemstack.count > 0) {
								int k = this.random.nextInt(21) + 10;

								if (k > itemstack.count) {
									k = itemstack.count;
								}

								itemstack.count -= k;
								EntityItem entityitem = new EntityItem(this.world, this.locX + (double) f, this.locY + (double) f1, this.locZ + (double) f2, new ItemStack(itemstack.id, k, itemstack.getData()));
								float f3 = 0.05F;

								entityitem.motX = (double) ((float) this.random.nextGaussian() * f3);
								entityitem.motY = (double) ((float) this.random.nextGaussian() * f3 + 0.2F);
								entityitem.motZ = (double) ((float) this.random.nextGaussian() * f3);
								this.world.addEntity(entityitem);
							}
						}
					}
					if (TrainCarts.breakCombinedCarts) {
						if (TrainCarts.spawnItemDrops) this.a(Block.CHEST.id, 1, 0.0F);
					} else {
						if (TrainCarts.spawnItemDrops) this.a(Material.STORAGE_MINECART.getId(), 1, 0.0F);
					}
				} else if (this.type == 2) {
					if (TrainCarts.breakCombinedCarts) {
						if (TrainCarts.spawnItemDrops) this.a(Block.FURNACE.id, 1, 0.0F);
					} else {
						if (TrainCarts.spawnItemDrops) this.a(Material.POWERED_MINECART.getId(), 1, 0.0F);
					}
				}
			}

			return true;
		} else {
			return true;
		}
	}

	/*
	 * Stores m_ information (since functions are now pretty much scattered around)
	 */
	private MoveInfo moveinfo;
	private class MoveInfo {
		public boolean isRailed; //sets what post-velocity update to run
		public double prevX;
		public double prevY;
		public double prevZ;
		public float prevYaw;
		public float prevPitch;
		public int[][] aint;
		public int i;
		public int j;
		public int k;
		public int i1;
		public Vec3D vec3d;
		public boolean flag;
		public boolean flag1;
	}
	public boolean hasDonePhysics() {
		return this.moveinfo != null;
	}
	
	/*
	 * Executes the pre-velocity and location updates
	 * Returns whether or not any velocity updates were done. (if the cart is NOT static)
	 */
	public boolean preUpdate() {
		//Some fixed
		if (this.dead) return false;
		this.motX = Util.fixNaN(this.motX);
		this.motY = Util.fixNaN(this.motY);
		this.motZ = Util.fixNaN(this.motZ);

		moveinfo = new MoveInfo();

		// CraftBukkit start
		moveinfo.prevX = this.locX;
		moveinfo.prevY = this.locY;
		moveinfo.prevZ = this.locZ;
		moveinfo.prevYaw = this.yaw;
		moveinfo.prevPitch = this.pitch;
		// CraftBukkit end

		if (this.l() > 0) {
			this.c(this.l() - 1);
		}

		if (this.getDamage() > 0) {
			this.setDamage(this.getDamage() - 1);
		}

		if (this.j() && this.random.nextInt(4) == 0) {
			this.world.a("largesmoke", this.locX, this.locY + 0.8D, this.locZ, 0.0D, 0.0D, 0.0D);
		}

		if (this.world.isStatic) {
			if (this.h > 0) {
				double d0 = this.locX + (this.i - this.locX) / (double) this.h;
				double d1 = this.locY + (this.j - this.locY) / (double) this.h;
				double d2 = this.locZ + (this.k - this.locZ) / (double) this.h;

				double d3;

				for (d3 = this.l - (double) this.yaw; d3 < -180.0D; d3 += 360.0D) {
					;
				}

				while (d3 >= 180.0D) {
					d3 -= 360.0D;
				}

				this.yaw = (float) ((double) this.yaw + d3 / (double) this.h);
				this.pitch = (float) ((double) this.pitch + (this.m - (double) this.pitch) / (double) this.h);
				--this.h;
				this.setPosition(d0, d1, d2);
				this.c(this.yaw, this.pitch);
			} else {
				this.setPosition(this.locX, this.locY, this.locZ);
				this.c(this.yaw, this.pitch);
			}
		} else {
			this.lastX = this.locX;
			this.lastY = this.locY;
			this.lastZ = this.locZ;
			this.motY -= 0.03999999910593033D;
			moveinfo.i = MathHelper.floor(this.locX);
			moveinfo.j = MathHelper.floor(this.locY);
			moveinfo.k = MathHelper.floor(this.locZ);

			if (BlockMinecartTrack.g(this.world, moveinfo.i, moveinfo.j - 1, moveinfo.k)) {
				--moveinfo.j;
			}

			// CraftBukkit
			moveinfo.flag = false;
			//double d4 = this.maxSpeed; //traincarts - removed because of usage in other function
			
			//TrainCarts - prevent sloped movement if forces are ignored
			double d5 = this.ignoreForces() ? 0 : 0.0078125D; //forward movement on slopes
			
			int l = this.world.getTypeId(moveinfo.i, moveinfo.j, moveinfo.k);

			moveinfo.isRailed = BlockMinecartTrack.d(l);
			if (moveinfo.isRailed) {
				moveinfo.vec3d = this.h(this.locX, this.locY, this.locZ);
				moveinfo.i1 = this.world.getData(moveinfo.i, moveinfo.j, moveinfo.k);

				this.locY = (double) moveinfo.j;
				moveinfo.flag = false;
				moveinfo.flag1 = false;

				// TrainNote: Used to boost a Minecart on powered tracks
				if (l == Block.GOLDEN_RAIL.id && !ignoreForces()) {
					moveinfo.flag = (moveinfo.i1 & 8) != 0;
					moveinfo.flag1 = !moveinfo.flag;
				}

				if (((BlockMinecartTrack) Block.byId[l]).h()) {
					moveinfo.i1 &= 7; //sloped?
				}
				if (moveinfo.i1 >= 2 && moveinfo.i1 <= 5) {
					this.locY = (double) (moveinfo.j + 1);
				}

				if (moveinfo.i1 == 2) {
					if (this.motX <= 0 || this.group().getProperties().slowDown) {
						this.motX -= d5;
					}
				}

				if (moveinfo.i1 == 3) {
					if (this.motX >= 0 || this.group().getProperties().slowDown) {
						this.motX += d5;
					}
				}

				if (moveinfo.i1 == 4) {
					if (this.motZ >= 0 || this.group().getProperties().slowDown) {
						this.motZ += d5;
					}
				}

				if (moveinfo.i1 == 5) {
					if (this.motZ <= 0 || this.group().getProperties().slowDown) {
						this.motZ -= d5;
					}
				}
				//TrainNote end
				moveinfo.aint = matrix[moveinfo.i1];
				double d6 = (double) (moveinfo.aint[1][0] - moveinfo.aint[0][0]);
				double d7 = (double) (moveinfo.aint[1][2] - moveinfo.aint[0][2]);
				double d8 = Math.sqrt(d6 * d6 + d7 * d7);
				double d9 = this.motX * d6 + this.motZ * d7;

				if (d9 < 0.0D) {
					d6 = -d6;
					d7 = -d7;
				}

				double d10 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);

				this.motX = d10 * d6 / d8;
				this.motZ = d10 * d7 / d8;
				double d11;
				// TrainNote: Used to slow down a Minecart
				if (moveinfo.flag1) {
					d11 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);
					if (d11 < 0.03D) {
						this.motX *= 0.0D;
						this.motY *= 0.0D;
						this.motZ *= 0.0D;
					} else {
						this.motX *= 0.5D;
						this.motY *= 0.0D;
						this.motZ *= 0.5D;
					}
				}
				// TrainNote end
				
				d11 = 0.0D;
				double d12 = (double) moveinfo.i + 0.5D + (double) moveinfo.aint[0][0] * 0.5D;
				double d13 = (double) moveinfo.k + 0.5D + (double) moveinfo.aint[0][2] * 0.5D;
				double d14 = (double) moveinfo.i + 0.5D + (double) moveinfo.aint[1][0] * 0.5D;
				double d15 = (double) moveinfo.k + 0.5D + (double) moveinfo.aint[1][2] * 0.5D;

				d6 = d14 - d12;
				d7 = d15 - d13;
				double d16;
				double d17;
				double d18;
				if (d6 == 0.0D) {
					this.locX = (double) moveinfo.i + 0.5D;
					d11 = this.locZ - (double) moveinfo.k;
				} else if (d7 == 0.0D) {
					this.locZ = (double) moveinfo.k + 0.5D;
					d11 = this.locX - (double) moveinfo.i;
				} else {
					d16 = this.locX - d12;
					d18 = this.locZ - d13;
					d17 = (d16 * d6 + d18 * d7) * 2.0D;
					d11 = d17;
				}
				
				this.locX = d12 + d6 * d11;
				this.locZ = d13 + d7 * d11;
				this.setPosition(this.locX, this.locY + (double) this.height, this.locZ);
			} else {
				if (this.onGround) {
					// CraftBukkit start
					Vector der = this.getDerailedVelocityMod();
					this.motX *= der.getX();
					this.motY *= der.getY();
					this.motZ *= der.getZ();
					// CraftBukkit start
				}
			}
			return true;
		}
		return false;
	}

	/*
	 * Executes the post-velocity and positioning updates
	 */
	public void postUpdate(double speedFactor) throws GroupUnloadedException {
		if (this.dead) return;
		if (this.moveinfo == null) return; //pre-update is needed
		double motX = this.motX;
		double motZ = this.motZ;
		//Prevent NaN (you never know!)
		motX = Util.fixNaN(motX);
		motZ = Util.fixNaN(motZ);
		speedFactor = Util.fixNaN(speedFactor, 1);
		if (speedFactor > 10) speedFactor = 10; //>10 is ridiculous!
		motX = Util.limit(motX, this.maxSpeed);
		motZ = Util.limit(motZ, this.maxSpeed);
		motX *= speedFactor;
		motZ *= speedFactor;
		if (moveinfo.isRailed) {
			this.move(motX, 0.0D, motZ);
			if (moveinfo.aint[0][1] != 0 && MathHelper.floor(this.locX) - moveinfo.i == moveinfo.aint[0][0] && MathHelper.floor(this.locZ) - moveinfo.k == moveinfo.aint[0][2]) {
				this.setPosition(this.locX, this.locY + (double) moveinfo.aint[0][1], this.locZ);
			} else if (moveinfo.aint[1][1] != 0 && MathHelper.floor(this.locX) - moveinfo.i == moveinfo.aint[1][0] && MathHelper.floor(this.locZ) - moveinfo.k == moveinfo.aint[1][2]) {
				this.setPosition(this.locX, this.locY + (double) moveinfo.aint[1][1], this.locZ);
			}

			// CraftBukkit
			//==================TrainCarts edited==============
			if (this.type == 2 && !ignoreForces()) {
				double d17 = (double) MathHelper.a(this.b * this.b + this.c * this.c);
				if (d17 > 0.01D) {
					this.b /= d17;
					this.c /= d17;
					double d19 = 0.04D;

					this.motX *= 0.800000011920929D + TrainCarts.poweredCartBoost;
					this.motY *= 0.0D;
					this.motZ *= 0.800000011920929D + TrainCarts.poweredCartBoost;
					this.motX += this.b * d19;
					this.motZ += this.c * d19;
				} else {
					if (this.group().getProperties().slowDown) {
						this.motX *= 0.8999999761581421D;
						this.motY *= 0.0D;
						this.motZ *= 0.8999999761581421D;
					}
				}
			}
			if (this.group().getProperties().slowDown) {
				this.motX *= 0.996999979019165D;
				this.motY *= 0.0D;
				this.motZ *= 0.996999979019165D;
			}
			//==================================================

			Vec3D vec3d1 = this.h(this.locX, this.locY, this.locZ);

			double d10;
			if (vec3d1 != null && moveinfo.vec3d != null) {
				if (this.group().getProperties().slowDown) {
					double d20 = (moveinfo.vec3d.b - vec3d1.b) * 0.05D;

					d10 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);
					if (d10 > 0.0D) {
						this.motX = this.motX / d10 * (d10 + d20);
						this.motZ = this.motZ / d10 * (d10 + d20);
					}
				}
				this.setPosition(this.locX, vec3d1.b, this.locZ);
			}
			int j1 = MathHelper.floor(this.locX);
			int k1 = MathHelper.floor(this.locZ);

			if (j1 != moveinfo.i || k1 != moveinfo.k) {
				d10 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);
				this.motX = d10 * (double) (j1 - moveinfo.i);
				this.motZ = d10 * (double) (k1 - moveinfo.k);
			}

			double d21;

			//TrainNote: PushX and PushZ updated for Powered Minecarts
			if (this.type == 2) {
				d21 = (double) MathHelper.a(this.b * this.b + this.c * this.c);
				if (d21 > 0.01D && this.motX * this.motX + this.motZ * this.motZ > 0.0010D) {
					this.b /= d21;
					this.c /= d21;
					if (this.b * this.motX + this.c * this.motZ < 0.0D) {
						this.b = 0.0D;
						this.c = 0.0D;
					} else {
						this.b = this.motX;
						this.c = this.motZ;
					}
				}
			}
			//TrainNote end

			if (moveinfo.flag) {
				d21 = Math.sqrt(this.motX * this.motX + this.motZ * this.motZ);
				if (d21 > 0.01D) {
					double d22 = 0.06D;
					this.motX += this.motX / d21 * d22;
					this.motZ += this.motZ / d21 * d22;
				} else if (moveinfo.i1 == 1) {
					if (this.world.e(moveinfo.i - 1, moveinfo.j, moveinfo.k)) {
						this.motX = 0.02D;
					} else if (this.world.e(moveinfo.i + 1, moveinfo.j, moveinfo.k)) {
						this.motX = -0.02D;
					}
				} else if (moveinfo.i1 == 0) {
					if (this.world.e(moveinfo.i, moveinfo.j, moveinfo.k - 1)) {
						this.motZ = 0.02D;
					} else if (this.world.e(moveinfo.i, moveinfo.j, moveinfo.k + 1)) {
						this.motZ = -0.02D;
					}
				}
			}
		} else {

			if (this.onGround) {
				// CraftBukkit start
				Vector der = this.getDerailedVelocityMod();
				this.motX *= der.getX();
				this.motY *= der.getY();
				this.motZ *= der.getZ();
				// CraftBukkit start
			}

			this.move(motX, this.motY, motZ);
			if (!this.onGround) {
				// CraftBukkit start
				Vector der = this.getFlyingVelocityMod();
				this.motX *= der.getX();
				this.motY *= der.getY();
				this.motZ *= der.getZ();
				// CraftBukkit start
			}
		}



		this.pitch = 0.0F;
		double d23 = this.lastX - this.locX;
		double d24 = this.lastZ - this.locZ;

		if (d23 * d23 + d24 * d24 > 0.0010D) {
			this.yaw = (float) (Math.atan2(d24, d23) * 180.0D / 3.141592653589793D);
			if (this.f) {
				this.yaw += 180.0F;
			}
		}

		double d25;

		for (d25 = (double) (this.yaw - this.lastYaw); d25 >= 180.0D; d25 -= 360.0D) {
			;
		}

		while (d25 < -180.0D) {
			d25 += 360.0D;
		}

		if (d25 < -170.0D || d25 >= 170.0D) {
			this.yaw += 180.0F;
			this.f = !this.f;
		}

		this.c(this.yaw, this.pitch);

		// CraftBukkit start
		org.bukkit.World bworld = this.world.getWorld();
		Location from = new Location(bworld, moveinfo.prevX, moveinfo.prevY,
				moveinfo.prevZ, moveinfo.prevYaw, moveinfo.prevPitch);
		Location to = new Location(bworld, this.locX, this.locY, this.locZ,
				this.yaw, this.pitch);
		Vehicle vehicle = (Vehicle) this.getBukkitEntity();

		this.world.getServer().getPluginManager()
		.callEvent(new VehicleUpdateEvent(vehicle));

		if (!from.equals(to)) {
			this.world.getServer().getPluginManager()
			.callEvent(new VehicleMoveEvent(vehicle, from, to));
		}
		// CraftBukkit end

		List list = this.world.b((Entity) this, this.boundingBox.b(
				0.20000000298023224D, 0.0D, 0.20000000298023224D));

		if (list != null && list.size() > 0) {
			for (int l1 = 0; l1 < list.size(); ++l1) {
				Entity entity = (Entity) list.get(l1);
				if (entity != this.passenger && entity.f_()
						&& entity instanceof EntityMinecart) {
					entity.collide(this);
				}
			}
		}

		if (this.passenger != null && this.passenger.dead) {
			this.passenger.vehicle = null; // CraftBukkit
			this.passenger = null;
		}

		if (this.e > 0) {
			--this.e;
		}
		if (this.e <= 0) {
			//TrainCarts - Actions to be done when empty
			if (this.type == 2 && this.onCoalUsed()) {
				this.e = 3600; //Refill
			} else {
				this.b = this.c = 0.0D;
			}
		}
		this.a(this.e > 0);
	}

	/*
	 * Overridden to use my own fuel value
	 */
	@Override
	public boolean b(EntityHuman entityhuman) {
		if (this.type == 2) {
            ItemStack itemstack = entityhuman.inventory.getItemInHand();
            if (itemstack != null && itemstack.id == Item.COAL.id) {
                if (--itemstack.count == 0) {
                    entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
                }
                this.e += 3600;
            }
            this.b = this.locX - entityhuman.locX;
            this.c = this.locZ - entityhuman.locZ;		
			return true;
		} else {
			return super.b(entityhuman);
		}
	}
	/*
	 * To be overridden by MinecartMember
	 * Returns if the fuel should be refilled
	 */
	public boolean onCoalUsed() {
		return false;
	}

	/*
	 * Cloned and updated to prevent collisions. For source, see:
	 * https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/Entity.java
	 */
	@Override
	public void move(double d0, double d1, double d2) {
		if (this.bN) {
			this.boundingBox.d(d0, d1, d2);
			this.locX = (this.boundingBox.a + this.boundingBox.d) / 2.0D;
			this.locY = this.boundingBox.b + (double) this.height - (double) this.bL;
			this.locZ = (this.boundingBox.c + this.boundingBox.f) / 2.0D;
		} else {
			this.bL *= 0.4F;
			double d3 = this.locX;
			double d4 = this.locZ;

			if (this.bz) {
				this.bz = false;
				d0 *= 0.25D;
				d1 *= 0.05000000074505806D;
				d2 *= 0.25D;
				this.motX = 0.0D;
				this.motY = 0.0D;
				this.motZ = 0.0D;
			}

			double d5 = d0;
			double d6 = d1;
			double d7 = d2;
			AxisAlignedBB axisalignedbb = this.boundingBox.clone();
			boolean flag = this.onGround && this.isSneaking();

			if (flag) {
				double d8;

				for (d8 = 0.05D; d0 != 0.0D && this.world.getEntities(this, this.boundingBox.c(d0, -1.0D, 0.0D)).size() == 0; d5 = d0) {
					if (d0 < d8 && d0 >= -d8) {
						d0 = 0.0D;
					} else if (d0 > 0.0D) {
						d0 -= d8;
					} else {
						d0 += d8;
					}
				}

				for (; d2 != 0.0D && this.world.getEntities(this, this.boundingBox.c(0.0D, -1.0D, d2)).size() == 0; d7 = d2) {
					if (d2 < d8 && d2 >= -d8) {
						d2 = 0.0D;
					} else if (d2 > 0.0D) {
						d2 -= d8;
					} else {
						d2 += d8;
					}
				}
			}

			List list = this.world.getEntities(this, this.boundingBox.a(d0, d1, d2));

			//=========================TrainCarts Changes Start==============================
					filterCollisionList(list);
			//=========================TrainCarts Changes End==============================

					for (int i = 0; i < list.size(); ++i) {
						d1 = ((AxisAlignedBB) list.get(i)).b(this.boundingBox, d1);
					}

			this.boundingBox.d(0.0D, d1, 0.0D);
			if (!this.bA && d6 != d1) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			boolean flag1 = this.onGround || d6 != d1 && d6 < 0.0D;

			int j;

			for (j = 0; j < list.size(); ++j) {
				d0 = ((AxisAlignedBB) list.get(j)).a(this.boundingBox, d0);
			}

			this.boundingBox.d(d0, 0.0D, 0.0D);
			if (!this.bA && d5 != d0) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			for (j = 0; j < list.size(); ++j) {
				d2 = ((AxisAlignedBB) list.get(j)).c(this.boundingBox, d2);
			}

			this.boundingBox.d(0.0D, 0.0D, d2);
			if (!this.bA && d7 != d2) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			double d9;
			double d10;
			int k;

			if (this.bM > 0.0F && flag1 && (flag || this.bL < 0.05F) && (d5 != d0 || d7 != d2)) {
				d9 = d0;
				d10 = d1;
				double d11 = d2;

				d0 = d5;
				d1 = (double) this.bM;
				d2 = d7;
				AxisAlignedBB axisalignedbb1 = this.boundingBox.clone();

				this.boundingBox.b(axisalignedbb);
				list = this.world.getEntities(this, this.boundingBox.a(d5, d1, d7));

				//=========================TrainCarts Changes Start==============================
						filterCollisionList(list);
				//=========================TrainCarts Changes End==============================

						for (k = 0; k < list.size(); ++k) {
							d1 = ((AxisAlignedBB) list.get(k)).b(this.boundingBox, d1);
						}

						this.boundingBox.d(0.0D, d1, 0.0D);
						if (!this.bA && d6 != d1) {
							d2 = 0.0D;
							d1 = 0.0D;
							d0 = 0.0D;
						}

						for (k = 0; k < list.size(); ++k) {
							d0 = ((AxisAlignedBB) list.get(k)).a(this.boundingBox, d0);
						}

						this.boundingBox.d(d0, 0.0D, 0.0D);
						if (!this.bA && d5 != d0) {
							d2 = 0.0D;
							d1 = 0.0D;
							d0 = 0.0D;
						}

						for (k = 0; k < list.size(); ++k) {
							d2 = ((AxisAlignedBB) list.get(k)).c(this.boundingBox, d2);
						}

						this.boundingBox.d(0.0D, 0.0D, d2);
						if (!this.bA && d7 != d2) {
							d2 = 0.0D;
							d1 = 0.0D;
							d0 = 0.0D;
						}

						if (!this.bA && d6 != d1) {
							d2 = 0.0D;
							d1 = 0.0D;
							d0 = 0.0D;
						} else {
							d1 = (double) (-this.bM);

							for (k = 0; k < list.size(); ++k) {
								d1 = ((AxisAlignedBB) list.get(k)).b(this.boundingBox, d1);
							}

							this.boundingBox.d(0.0D, d1, 0.0D);
						}

						if (d9 * d9 + d11 * d11 >= d0 * d0 + d2 * d2) {
							d0 = d9;
							d1 = d10;
							d2 = d11;
							this.boundingBox.b(axisalignedbb1);
						} else {
							double d12 = this.boundingBox.b - (double) ((int) this.boundingBox.b);

							if (d12 > 0.0D) {
								this.bL = (float) ((double) this.bL + d12 + 0.01D);
							}
						}
			}

			this.locX = (this.boundingBox.a + this.boundingBox.d) / 2.0D;
			this.locY = this.boundingBox.b + (double) this.height - (double) this.bL;
			this.locZ = (this.boundingBox.c + this.boundingBox.f) / 2.0D;
			this.positionChanged = d5 != d0 || d7 != d2;
			this.bw = d6 != d1;
			this.onGround = d6 != d1 && d6 < 0.0D;
			this.bx = this.positionChanged || this.bw;
			this.a(d1, this.onGround);
			if (d5 != d0) {
				this.motX = 0.0D;
			}

			if (d6 != d1) {
				this.motY = 0.0D;
			}

			if (d7 != d2) {
				this.motZ = 0.0D;
			}

			d9 = this.locX - d3;
			d10 = this.locZ - d4;
			int l;
			int i1;
			int j1;

			// CraftBukkit start
			if ((this.positionChanged) && (this.getBukkitEntity() instanceof Vehicle)) {
				Vehicle vehicle = (Vehicle) this.getBukkitEntity();
				org.bukkit.block.Block block = this.world.getWorld().getBlockAt(MathHelper.floor(this.locX), MathHelper.floor(this.locY - 0.20000000298023224D - (double) this.height), MathHelper.floor(this.locZ));

				if (d5 > d0) {
					block = block.getRelative(BlockFace.SOUTH);
				} else if (d5 < d0) {
					block = block.getRelative(BlockFace.NORTH);
				} else if (d7 > d2) {
					block = block.getRelative(BlockFace.WEST);
				} else if (d7 < d2) {
					block = block.getRelative(BlockFace.EAST);
				}

				VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, block);
				this.world.getServer().getPluginManager().callEvent(event);
			}
			// CraftBukkit end

			if (this.g_() && !flag && this.vehicle == null) {
				this.bG = (float) ((double) this.bG + (double) MathHelper.a(d9 * d9 + d10 * d10) * 0.6D);
				l = MathHelper.floor(this.locX);
				i1 = MathHelper.floor(this.locY - 0.20000000298023224D - (double) this.height);
				j1 = MathHelper.floor(this.locZ);
				k = this.world.getTypeId(l, i1, j1);
				if (k == 0 && this.world.getTypeId(l, i1 - 1, j1) == Block.FENCE.id) {
					k = this.world.getTypeId(l, i1 - 1, j1);
				}

				if (this.bG > (float) this.b && k > 0) {
					this.b = (int) this.bG + 1;
					this.a(l, i1, j1, k);
					Block.byId[k].b(this.world, l, i1, j1, this);
				}
			}

			l = MathHelper.floor(this.boundingBox.a + 0.0010D);
			i1 = MathHelper.floor(this.boundingBox.b + 0.0010D);
			j1 = MathHelper.floor(this.boundingBox.c + 0.0010D);
			k = MathHelper.floor(this.boundingBox.d - 0.0010D);
			int k1 = MathHelper.floor(this.boundingBox.e - 0.0010D);
			int l1 = MathHelper.floor(this.boundingBox.f - 0.0010D);

			if (this.world.a(l, i1, j1, k, k1, l1)) {
				for (int i2 = l; i2 <= k; ++i2) {
					for (int j2 = i1; j2 <= k1; ++j2) {
						for (int k2 = j1; k2 <= l1; ++k2) {
							int l2 = this.world.getTypeId(i2, j2, k2);

							if (l2 > 0) {
								Block.byId[l2].a(this.world, i2, j2, k2, this);
							}
						}
					}
				}
			}

			boolean flag2 = this.ay();

            if (this.world.d(this.boundingBox.shrink(0.0010D, 0.0010D, 0.0010D))) {
                this.burn(1);
                if (!flag2) {
                    ++this.fireTicks;
                    // CraftBukkit start - not on fire yet
                    if (this.fireTicks <= 0) { // only throw events on the first combust, otherwise it spams
                        EntityCombustEvent event = new EntityCombustEvent(this.getBukkitEntity(), 8);
                        this.world.getServer().getPluginManager().callEvent(event);

                        if (!event.isCancelled()) {
                            this.setOnFire(event.getDuration());
                        }
                    } else {
                        // CraftBukkit end
                        this.setOnFire(8);
                    }
                }
            } else if (this.fireTicks <= 0) {
                this.fireTicks = -this.maxFireTicks;
            }

            if (flag2 && this.fireTicks > 0) {
                this.world.makeSound(this, "random.fizz", 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                this.fireTicks = -this.maxFireTicks;
            }
		}
	}

	/*
	 * Returns if this entity is allowed to collide with another entity
	 */
	private boolean canCollide(Entity e) {
		if (this.member().isCollisionIgnored(e)) return false;
		if (e.dead) return false;
		if (this.dead) return false;
		if (this.group().isActionWait()) return false;
		if (e instanceof MinecartMember) {
			//colliding with a member in the group, or not?
			MinecartMember mm1 = this.member();
			MinecartMember mm2 = (MinecartMember) e;
			if (mm1.getGroup() == mm2.getGroup()) {
				//Same group, but do prevent penetration
				if (mm1.distance(mm2) > 0.5) {
					return false;
				}
			} else if (!mm1.getGroup().getProperties().trainCollision) {
				//Allows train collisions?
				return false;
			} else if (!mm2.getGroup().getProperties().trainCollision) {
				//Other train allows train collisions?
				return false;
			} else if (mm2.getGroup().isActionWait()) {
				//Is this train targeting?
				return false;
			}
		} else if (e instanceof EntityLiving && e.vehicle != null && e.vehicle instanceof EntityMinecart) {
			//Ignore passenger collisions
			return false;
		} else {
			//Use push-away?
			TrainProperties prop = this.group().getProperties();
			if (prop.canPushAway(e.getBukkitEntity())) {
				this.member().push(e.getBukkitEntity());
				return false;
			}
			if (!prop.trainCollision) {
				//No collision is allowed? (Owners override)
				if (e instanceof EntityPlayer) {
					Player p = (Player) e.getBukkitEntity();
					if (!prop.isOwner(p)) {
						return false;
					}
				} else {
					return false;
				}
			} else if (EntityUtil.isMob(e.getBukkitEntity()) && this.member().getProperties().allowMobsEnter && this.passenger == null) {
				e.setPassengerOf(this);
				return false;
			}
		}
		return true;
	}

	/*
	 * Prevents passengers and Minecarts from colliding with Minecarts
	 */
	@SuppressWarnings("unchecked")
	private void filterCollisionList(List list) {		
		int ri = 0;

		Entity[] entityList = (Entity[]) this.world.entityList.toArray(new Entity[0]);

		while (ri < list.size()) {
			AxisAlignedBB a = (AxisAlignedBB) list.get(ri);
			boolean remove = false;
			for (Entity e : entityList) {
				if (e.boundingBox == a) {
					remove = !canCollide(e);
				}
				if (remove) break;
			}
			if (remove) {
				list.remove(ri);
			} else {
				ri++;
			}
		}
	}

}
