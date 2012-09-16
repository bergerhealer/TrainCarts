package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberDeadException;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

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
import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.Vec3D;
import net.minecraft.server.World;
import net.minecraft.server.EntityMinecart;

public class NativeMinecartMember extends EntityMinecart {
	/*
	 * Values taken over from source to use in the m_ function, see attached source links
	 */
	public int fuel;
	private int fuelCheckCounter = 0;
	public boolean isOnMinecartTrack = true;
	public boolean wasOnMinecartTrack = true;
	private static final Vector[][] matrix = new Vector[][] { 
		{ new Vector(0, 0, -1), new Vector(0, 0, 1) }, { new Vector(-1, 0, 0), new Vector(1, 0, 0) },
		{ new Vector(-1, -1, 0), new Vector(1, 0, 0) }, { new Vector(-1, 0, 0), new Vector(1, -1, 0) },
		{ new Vector(0, 0, -1), new Vector(0, -1, 1) }, { new Vector(0, -1, -1), new Vector(0, 0, 1) },
		{ new Vector(0, 0, 1), new Vector(1, 0, 0) }, { new Vector(0, 0, 1), new Vector(-1, 0, 0) },
		{ new Vector(0, 0, -1), new Vector(-1, 0, 0) }, { new Vector(0, 0, -1), new Vector(1, 0, 0) } };

	public static final int FUEL_PER_COAL = 3600;

	public void validate() throws MemberDeadException {
		if (this.dead) {
			this.die();
			throw new MemberDeadException();
		}
	}

	public double getForceSquared() {
		return MathUtil.lengthSquared(this.motX, this.motZ);
	}
	public double getForce() {
		return MathUtil.length(this.motX, this.motZ);
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
		return new Location(getWorld(), getX(), getY(), getZ(), this.yaw, this.pitch);
	}

	public boolean isMoving() {
		return Math.abs(this.motX) > 0.001 || Math.abs(this.motZ) > 0.001;
	}
	public boolean hasFuel() {
		return this.fuel > 0;
	}

	private boolean ignoreForces() {
		return group().isVelocityAction();
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
	}

	/*
	 * Replaced standard droppings based on TrainCarts settings. For source, see:
	 * https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/EntityMinecart.java
	 */
	@Override
	public boolean damageEntity(DamageSource damagesource, int i)
	{
		try {
			if(!this.dead)
			{
				// CraftBukkit start
				Vehicle vehicle = (Vehicle) this.getBukkitEntity();
				org.bukkit.entity.Entity passenger = (damagesource.getEntity() == null) ? null : damagesource.getEntity().getBukkitEntity();

				VehicleDamageEvent event = new VehicleDamageEvent(vehicle, passenger, i);

				if (CommonUtil.callEvent(event).isCancelled()) {
					return true;
				}

				i = event.getDamage();
				// CraftBukkit end

	            this.i(-this.k());
	            this.h(10);
	            this.K();
				setDamage(getDamage() + i * 10);
				if ((damagesource.getEntity() instanceof EntityHuman) && ((EntityHuman)damagesource.getEntity()).abilities.canInstantlyBuild) {
					setDamage(100);
				}
				if(getDamage() > 40) {
					// CraftBukkit start
					List<org.bukkit.inventory.ItemStack> drops = new ArrayList<org.bukkit.inventory.ItemStack>();
					if (TrainCarts.spawnItemDrops) {
						if (TrainCarts.breakCombinedCarts) {
							drops.add(new CraftItemStack(Item.MINECART.id, 1));
							if (type == 1) {
								drops.add(new CraftItemStack(Block.CHEST.id, 1));
							} else if(type == 2) {
								drops.add(new CraftItemStack(Block.FURNACE.id, 1));
							}
						} else {
							switch (this.type) {
								case 0:
									drops.add(new CraftItemStack(Item.MINECART.id, 1));
									break;
								case 1:
									drops.add(new CraftItemStack(Item.STORAGE_MINECART.id, 1));
									break;
								case 2:
									drops.add(new CraftItemStack(Item.POWERED_MINECART.id, 1));
									break;
							}
						}
					}

					VehicleDestroyEvent destroyEvent = new VehicleDestroyEvent(vehicle, passenger);
					if(CommonUtil.callEvent(destroyEvent).isCancelled()) {
						setDamage(40);
						return true;
					}
					// CraftBukkit end

					if(this.passenger != null) {
						this.passenger.mount(this);
					}

					for (org.bukkit.inventory.ItemStack stack : drops) {
						this.a(CraftItemStack.createNMSItemStack(stack), 0.0F);
					}
					
					this.die();
				}
			}
			return true;
		} catch (Throwable t) {
			TrainCarts.plugin.handle(t);
			return false;
		}
	}
	
	/*
	 * Stores physics information (since functions are now pretty much scattered around)
	 */
	private final MoveInfo moveinfo = new MoveInfo();
	private class MoveInfo {
		public boolean isRailed; //sets what post-velocity update to run
		public double prevX;
		public double prevY;
		public double prevZ;
		public float prevYaw;
		public float prevPitch;
		public Vector[] moveMatrix;
		public int blockX;
		public int blockY;
		public int blockZ;
		public int raildata;
		public Vec3D vec3d;
		public boolean isLaunching;
		public Location getPrevLoc(World world) {
			return new Location(world.getWorld(), prevX, prevY, prevZ, prevYaw, prevPitch);
		}
	}

	public void addFuel(int fuel) {
		this.fuel += fuel;
		if (this.fuel <= 0) {
			this.fuel = 0;
			this.b = this.c = 0.0;
		} else if (this.b == 0.0 && this.c == 0.0) {
			this.b = this.motX;
			this.c = this.motZ;
		}
	}

	/*
	 * Executes the pre-velocity and location updates
	 * Returns whether or not any velocity updates were done. (if the cart is NOT static)
	 */
	public boolean preUpdate(int stepcount) {
		//Some fixed
		if (this.dead) return false;
		this.motX = MathUtil.fixNaN(this.motX);
		this.motY = MathUtil.fixNaN(this.motY);
		this.motZ = MathUtil.fixNaN(this.motZ);

		// CraftBukkit start
		moveinfo.prevX = this.locX;
		moveinfo.prevY = this.locY;
		moveinfo.prevZ = this.locZ;
		moveinfo.prevYaw = this.yaw;
		moveinfo.prevPitch = this.pitch;
		// CraftBukkit end

		//fire ticks decrease
        if (this.j() > 0) {
            this.h(this.j() - 1);
        }

        //health regenerate
        if (this.getDamage() > 0) {
            this.setDamage(this.getDamage() - 1);
        }

        // Kill entity if falling into the void
        if (this.locY < -64.0D) {
            this.C();
        }

		//put coal into cart if needed
		if (this.isPoweredCart()) {
			if (this.fuel <= 0) {
				if (fuelCheckCounter++ % 20 == 0 && TrainCarts.useCoalFromStorageCart && this.member().getCoalFromNeighbours()) {
					this.addFuel(FUEL_PER_COAL);
				}
			} else {
				this.fuelCheckCounter = 0;
				if (MathUtil.lengthSquared(this.b, this.c) < 0.001) {
					this.b = this.motX;
					this.c = this.motZ;
				}
			}
		}

		this.lastX = this.locX;
		this.lastY = this.locY;
		this.lastZ = this.locZ;
		this.wasOnMinecartTrack = this.isOnMinecartTrack;
		this.motY -= 0.04 * (1 / stepcount);
		moveinfo.blockX = MathHelper.floor(this.locX);
		moveinfo.blockY = MathHelper.floor(this.locY);
		moveinfo.blockZ = MathHelper.floor(this.locZ);

		//get the type of rails below
		int railtype = this.world.getTypeId(moveinfo.blockX, moveinfo.blockY - 1, moveinfo.blockZ);
		if (Util.isRails(railtype)) {
			moveinfo.isRailed = true;
			--moveinfo.blockY;
		} else {
			railtype = this.world.getTypeId(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ);
			moveinfo.isRailed = Util.isRails(railtype);
		}
		this.isOnMinecartTrack = moveinfo.isRailed ? BlockUtil.isRails(railtype) : false;

		moveinfo.isLaunching = false;

		//TrainCarts - prevent sloped movement if forces are ignored
		double slopedMotion = this.ignoreForces() ? 0 : 0.0078125; //forward movement on slopes

		if (moveinfo.isRailed) {
			moveinfo.vec3d = this.a(this.locX, this.locY, this.locZ);
			moveinfo.raildata = this.world.getData(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ);

			this.locY = (double) moveinfo.blockY;
			moveinfo.isLaunching = false;
			boolean isBraking = false;

			// TrainNote: Used to boost a Minecart on powered tracks
			if (railtype == Block.GOLDEN_RAIL.id && !ignoreForces()) {
				moveinfo.isLaunching = (moveinfo.raildata & 8) != 0;
				isBraking = !moveinfo.isLaunching;
			}
			//TrainNote end

			if (!this.isOnMinecartTrack) {
				//driving on top of powered plate: extra calculation needed
				org.bukkit.block.Block curr = this.getWorld().getBlockAt(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ);
				BlockFace dir = Util.getPlateDirection(curr);
				if (dir == BlockFace.SELF) {
					//set track direction based on direction of this cart (0 or 1)
					if (Math.abs(this.motX) > Math.abs(this.motZ)) {
						moveinfo.raildata = 1;
					} else {
						moveinfo.raildata = 0;
					}
				} else if (dir == BlockFace.SOUTH) {
					moveinfo.raildata = 1;
				} else if (dir == BlockFace.WEST) {
					moveinfo.raildata = 0;
				}
			} else {
				if (((BlockMinecartTrack) Block.byId[railtype]).n()) {
					moveinfo.raildata &= 7; //clear sloped state for non-slopable rails
				}
				if (moveinfo.raildata >= 2 && moveinfo.raildata <= 5) {
					this.locY = (double) (moveinfo.blockY + 1);
					//TrainNote: Used to move a minecart up or down sloped tracks
					if (this.group().getProperties().isSlowingDown()) {
						switch (moveinfo.raildata) {
							case 2 : this.motX -= slopedMotion; break;
							case 3 : this.motX += slopedMotion; break;
							case 4 : this.motZ += slopedMotion; break;
							case 5 : this.motZ -= slopedMotion; break;	
						}
					}
					//TrainNote end
				}
			}

			moveinfo.moveMatrix = matrix[moveinfo.raildata];

			//rail motion is calculated from the rails
			double railMotionX = moveinfo.moveMatrix[1].getX() - moveinfo.moveMatrix[0].getX();
			double railMotionZ = moveinfo.moveMatrix[1].getZ() - moveinfo.moveMatrix[0].getZ();		
			//reverse motion if needed
			if ((this.motX * railMotionX + this.motZ * railMotionZ) < 0.0) {
				railMotionX = -railMotionX;
				railMotionZ = -railMotionZ;
			}

			//rail motion is applied (railFactor is used to normalize the rail motion to current motion)
			double railFactor = MathUtil.normalize(railMotionX, railMotionZ, this.motX, this.motZ);
			this.motX = railFactor * railMotionX;
			this.motZ = railFactor * railMotionZ;

			//slows down minecarts on unpowered powered rails
			if (isBraking) {
				if (this.getForceSquared() < 0.0009D) {
					this.motX = 0;
					this.motY = 0;
					this.motZ = 0;
				} else {
					this.motX /= 2;
					this.motY = 0;
					this.motZ /= 2;
				}
			}

			//location is updated to follow the tracks
			double oldRailX = (double) moveinfo.blockX + 0.5 + moveinfo.moveMatrix[0].getX() * 0.5;
			double oldRailZ = (double) moveinfo.blockZ + 0.5 + moveinfo.moveMatrix[0].getZ() * 0.5;
			double newRailX = (double) moveinfo.blockX + 0.5 + moveinfo.moveMatrix[1].getX() * 0.5;
			double newRailZ = (double) moveinfo.blockZ + 0.5 + moveinfo.moveMatrix[1].getZ() * 0.5;

			railMotionX = newRailX - oldRailX;
			railMotionZ = newRailZ - oldRailZ;
			if (railMotionX == 0) {
				railMotionZ *= this.locZ - moveinfo.blockZ;
			} else if (railMotionZ == 0) {
				railMotionX *= this.locX - moveinfo.blockX;
			} else {
				double factor = railMotionX * (this.locX - oldRailX) + railMotionZ * (this.locZ - oldRailZ);
				factor *= 2;
				railMotionX *= factor;
				railMotionZ *= factor;
			}
			this.locX = oldRailX + railMotionX;
			this.locZ = oldRailZ + railMotionZ;

			//finally update the position
			this.setPosition(this.locX, this.locY + (double) this.height, this.locZ);
		} else {
			Vector der;
			if (this.onGround) {
				der = this.getDerailedVelocityMod();
			} else {
				der = this.getFlyingVelocityMod();
			}
			this.motX *= der.getX();
			this.motY *= der.getY();
			this.motZ *= der.getZ();
		}
		return true;
	}

	/*
	 * Executes the post-velocity and positioning updates
	 */
	@SuppressWarnings("unchecked")
	public void postUpdate(double speedFactor) throws MemberDeadException, GroupUnloadedException {
		this.validate();
		double motX = MathUtil.fixNaN(this.motX);
		double motZ = MathUtil.fixNaN(this.motZ);

		speedFactor = MathUtil.fixNaN(speedFactor, 1);
		if (speedFactor > 10) speedFactor = 10; //>10 is ridiculous!

		motX = MathUtil.limit(motX, this.maxSpeed);
		motZ = MathUtil.limit(motZ, this.maxSpeed);
		motX *= speedFactor;
		motZ *= speedFactor;

		if (moveinfo.isRailed) {
			this.move(motX, 0.0, motZ);
			for (Vector mov : moveinfo.moveMatrix) {
				if (mov.getY() != 0 && MathHelper.floor(this.locX) - moveinfo.blockX == mov.getX() && MathHelper.floor(this.locZ) - moveinfo.blockZ == mov.getZ()) {
					this.setPosition(this.locX, this.locY + mov.getY(), this.locZ);
					break;
				}
			}

			this.validate();

			// CraftBukkit
			//==================TrainCarts edited==============
			if (this.isPoweredCart() && !ignoreForces()) {
				double fuelPower;
				if (this.hasFuel() && (fuelPower = MathUtil.length(this.b, this.c)) > 0) {
					this.b /= fuelPower;
					this.c /= fuelPower;
					double boost = 0.04 + TrainCarts.poweredCartBoost;

					this.motX *= 0.8;
					this.motY *= 0.0;
					this.motZ *= 0.8;
					this.motX += this.b * boost;
					this.motZ += this.c * boost;
				} else {
					if (this.group().getProperties().isSlowingDown()) {
						this.motX *= 0.9;
						this.motY *= 0.0;
						this.motZ *= 0.9;
					}
				}
			}
			if (this.group().getProperties().isSlowingDown()) {
				if (this.passenger != null || !this.slowWhenEmpty || !TrainCarts.slowDownEmptyCarts) {
					this.motX *= TrainCarts.slowDownMultiplierNormal;
					this.motY *= 0.0;
					this.motZ *= TrainCarts.slowDownMultiplierNormal;
				} else {
					this.motX *= TrainCarts.slowDownMultiplierSlow;
					this.motY *= 0.0;
					this.motZ *= TrainCarts.slowDownMultiplierSlow;
				}
			}
			//==================================================

			Vec3D vec3d1 = this.a(this.locX, this.locY, this.locZ);

			double motLength;

			//x and z motion slowed down on slopes
			if (vec3d1 != null && moveinfo.vec3d != null) {
				if (this.group().getProperties().isSlowingDown()) {
					motLength = this.getForce();
					if (motLength > 0) {
						double slopeSlowDown = (moveinfo.vec3d.b - vec3d1.b) * 0.05 / motLength + 1;
						this.motX *= slopeSlowDown;
						this.motZ *= slopeSlowDown;
					}
				}
				this.setPosition(this.locX, vec3d1.b, this.locZ);
			}

			//update motion based on changed location
			int newBlockX = MathHelper.floor(this.locX);
			int newBlockZ = MathHelper.floor(this.locZ);
			if (newBlockX != moveinfo.blockX || newBlockZ != moveinfo.blockZ) {
				motLength = this.getForce();
				this.motX = motLength * (double) (newBlockX - moveinfo.blockX);
				this.motZ = motLength * (double) (newBlockZ - moveinfo.blockZ);
			}

			//PushX and PushZ updated for Powered Minecarts
			if (this.isPoweredCart()) {
				motLength = MathUtil.length(this.b, this.c);
				if (motLength > 0.01 && this.motX * this.motX + this.motZ * this.motZ > 0.001) {
					this.b /= motLength;
					this.c /= motLength;
					if (this.b * this.motX + this.c * this.motZ < 0) {
						this.b = 0;
						this.c = 0;
					} else {
						this.b = this.motX;
						this.c = this.motZ;
					}
				}
			}

			//Launch on powered rails
			if (moveinfo.isLaunching) {
				motLength = this.getForce();
				if (motLength > 0.01) {
					//simple motion boosting when already moving
					double launchFactor = 0.06D / motLength;
					this.motX += this.motX * launchFactor;
					this.motZ += this.motZ * launchFactor;
				} else if (moveinfo.raildata == 1) {
					//launch at x-axis
					if (this.world.s(moveinfo.blockX - 1, moveinfo.blockY, moveinfo.blockZ)) {
						this.motX = 0.02;
					} else if (this.world.s(moveinfo.blockX + 1, moveinfo.blockY, moveinfo.blockZ)) {
						this.motX = -0.02;
					}
				} else if (moveinfo.raildata == 0) {
					//launch at z-axis
					if (this.world.s(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ - 1)) {
						this.motZ = 0.02;
					} else if (this.world.s(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ + 1)) {
						this.motZ = -0.02;
					}
				}
			}
		} else {
			this.move(motX, this.motY, motZ);
		}

		//Update yaw and pitch
		double movedX = this.lastX - this.locX;
		double movedY = this.lastY - this.locY;
		double movedZ = this.lastZ - this.locZ;
		if (moveinfo.isRailed && (this.wasOnMinecartTrack != this.isOnMinecartTrack)) {
			this.pitch = 0.0F; //prevent weird pitch angles on pressure plates
		} else if (MathUtil.lengthSquared(movedX, movedZ) > 0.001) {
			if (this.moveinfo.isRailed && this.isOnMinecartTrack) {
				this.pitch = -0.8F * MathUtil.getLookAtPitch(movedX, movedY, movedZ);
			} else if (this.member().isFlying()) {
				this.pitch = 0.7F * MathUtil.getLookAtPitch(movedX, movedY, movedZ);
			} else {
				this.pitch = 0;
			}
			this.pitch = MathUtil.limit(this.pitch, 60F);
			float newyaw = MathUtil.getLookAtYaw(movedX, movedZ);
			if (MathUtil.getAngleDifference(this.yaw, newyaw) > 170) {
				this.yaw = MathUtil.normalAngle(newyaw + 180);
			} else {
				this.yaw = newyaw;
			}
		} else {
			if (Math.abs(this.pitch) > 0.1) {
				this.pitch *= 0.1;
			} else {
				this.pitch = 0;
			}
		}
		this.b(this.yaw, this.pitch);

		// CraftBukkit start
		Location from = moveinfo.getPrevLoc(this.world);
		Location to = this.getLocation();
		Vehicle vehicle = (Vehicle) this.getBukkitEntity();

		CommonUtil.callEvent(new VehicleUpdateEvent(vehicle));

		if (!from.equals(to)) {
			CommonUtil.callEvent(new VehicleMoveEvent(vehicle, from, to));
		}
		// CraftBukkit end

		List<Entity> list = this.world.getEntities(this, this.boundingBox.grow(0.2, 0, 0.2));
		if (list != null && !list.isEmpty()) {
			for (Entity entity : list) {
				if (entity != this.passenger && entity.M() && entity instanceof EntityMinecart && MinecartMemberStore.validateMinecart(entity)) {
					entity.collide(this);
				}
			}
		}

		if (this.passenger != null && this.passenger.dead) {
			this.passenger.vehicle = null; // CraftBukkit
			this.passenger = null;
		}

		if (this.fuel > 0 && this.isPoweredCart()) {
			this.fuel--;
			if (this.fuel == 0) {
				//TrainCarts - Actions to be done when empty
				if (this.onCoalUsed()) {
					this.addFuel(FUEL_PER_COAL); //Refill
				}
			}
		}
		if (this.fuel <= 0) {
			this.fuel = 0;
			this.b = this.c = 0.0;
		}

		this.d(this.hasFuel());
	}

	/*
	 * Overridden function used to let players interact with this minecart
	 * Changes: use my own fuel and changes direction of all attached carts
	 */
	@Override
	public boolean c(EntityHuman entityhuman) {
		if (this.isPoweredCart()) {
			ItemStack itemstack = entityhuman.inventory.getItemInHand();
			if (itemstack != null && itemstack.id == Item.COAL.id) {
				if (--itemstack.count == 0) {
					entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
				}
				this.addFuel(3600);
			}
			this.b = this.locX - entityhuman.locX;
			this.c = this.locZ - entityhuman.locZ;	
			BlockFace dir = FaceUtil.getDirection(this.b, this.c, false);
			if (this.group().isMoving()) {
				if (dir == this.member().getDirectionTo().getOppositeFace()) {
					this.group().reverse();
				}
			}
			return true;
		} else {
			return super.c(entityhuman);
		}
	}

	/*
	 * Overridden to make it save properly (id was faulty)
	 */
	@Override
	public boolean c(NBTTagCompound nbttagcompound) {
		if (!this.dead) {
			nbttagcompound.setString("id", "Minecart");
			this.d(nbttagcompound);
			return true;
		} else {
			return false;
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
	@SuppressWarnings("unchecked")
	public void move(double d0, double d1, double d2) {
		if (this.X) {
			boundingBox.d(d0, d1, d2);
			locX = (boundingBox.a + boundingBox.d) / 2.0D;
			locY = (boundingBox.b + (double) this.height) - (double) this.V;
			locZ = (boundingBox.c + boundingBox.f) / 2.0D;
		} else {
			this.V *= 0.4F;
			double d3 = locX;
			double d4 = locZ;
			if(this.J) {
				this.J = false;
				d0 *= 0.25D;
				d1 *= 0.05D;
				d2 *= 0.25D;
				motX = 0.0D;
				motY = 0.0D;
				motZ = 0.0D;
			}
			double d5 = d0;
			double d6 = d1;
			double d7 = d2;
			AxisAlignedBB axisalignedbb = boundingBox.clone();
			List<AxisAlignedBB> list = world.getCubes(this, boundingBox.a(d0, d1, d2));

			//================================================
			filterCollisionList(list);
			//================================================

			// Collision testing using Y
			for (AxisAlignedBB aabb : list) {
				d1 = aabb.b(boundingBox, d1);
			}
			boundingBox.d(0.0D, d1, 0.0D);
			if(!this.K && d6 != d1) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			// Collision testing using X
			boolean flag1 = onGround || d6 != d1 && d6 < 0.0D;
			for (AxisAlignedBB aabb : list) {
				d0 = aabb.a(boundingBox, d0);
			}
			boundingBox.d(d0, 0.0D, 0.0D);
			if(!this.K && d5 != d0) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			// Collision testing using Z
			for (AxisAlignedBB aabb : list) {
				d2 = aabb.c(boundingBox, d2);
			}
			boundingBox.d(0.0D, 0.0D, d2);
			if(!this.K && d7 != d2) {
				d2 = 0.0D;
				d1 = 0.0D;
				d0 = 0.0D;
			}

			double d9;
			double d10;
			int k;
			if(this.W > 0.0F && flag1 && this.V < 0.05F && (d5 != d0 || d7 != d2)) {
				d9 = d0;
				d10 = d1;
				double d11 = d2;
				d0 = d5;
				d1 = (double) this.W;
				d2 = d7;

				AxisAlignedBB axisalignedbb1 = boundingBox.clone();
				boundingBox.c(axisalignedbb);

				list = world.getCubes(this, boundingBox.a(d5, d1, d7));

				//================================================
				filterCollisionList(list);
				//================================================

				for (AxisAlignedBB aabb : list) {
					d1 = aabb.b(boundingBox, d1);
				}
				boundingBox.d(0.0D, d1, 0.0D);
				if(!this.K && d6 != d1)
				{
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				for (AxisAlignedBB aabb : list) {
					d0 = aabb.a(boundingBox, d0);
				}
				boundingBox.d(d0, 0.0D, 0.0D);
				if(!this.K && d5 != d0)
				{
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}

				for (AxisAlignedBB aabb : list) {
					d2 = aabb.c(boundingBox, d2);
				}
				boundingBox.d(0.0D, 0.0D, d2);
				if(!this.K && d7 != d2) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				}
				
				if(!this.K && d6 != d1) {
					d2 = 0.0D;
					d1 = 0.0D;
					d0 = 0.0D;
				} else {
					d1 = (double) -this.W;
					for(k = 0; k < list.size(); k++) {
						d1 = ((AxisAlignedBB)list.get(k)).b(boundingBox, d1);
					}
					boundingBox.d(0.0D, d1, 0.0D);
				}
				if (d9 * d9 + d11 * d11 >= d0 * d0 + d2 * d2) {
					d0 = d9;
					d1 = d10;
					d2 = d11;
					boundingBox.c(axisalignedbb1);
				} else {
					double d12 = boundingBox.b - (double)(int) boundingBox.b;
					if (d12 > 0.0D) {
						this.V = (float)((double) this.V + d12 + 0.01D);
					}
				}
			}
			locX = (boundingBox.a + boundingBox.d) / 2D;
			locY = (boundingBox.b + (double) this.height) - (double) this.V;
			locZ = (boundingBox.c + boundingBox.f) / 2D;
			positionChanged = d5 != d0 || d7 != d2;
			this.G = d6 != d1;
			onGround = d6 != d1 && d6 < 0.0D;
			this.H = positionChanged || this.G;
			a(d1, onGround);

			if (d6 != d1) {
				motY = 0.0D;
			}

			//========TrainCarts edit: Math.abs check to prevent collision slowdown=====
			if (d5 != d0) {
				if (Math.abs(motX) > Math.abs(motZ)) {
					motX = 0.0D;
				}
			}
			if (d7 != d2) {
				if (Math.abs(motZ) > Math.abs(motX)) {
					motZ = 0.0D;
				}
			}
			//===========================================================================
			
			d9 = locX - d3;
			d10 = locZ - d4;
			if (positionChanged) {
				Vehicle vehicle = (Vehicle)getBukkitEntity();
				org.bukkit.block.Block block = world.getWorld().getBlockAt(MathHelper.floor(locX), MathHelper.floor(locY - (double)height), MathHelper.floor(locZ));
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
				world.getServer().getPluginManager().callEvent(event);
			}
			int l;
			int i1;
			int j1;
			if (this.e_() && this.vehicle == null) {
				this.Q = (float) ((double) this.Q + MathUtil.length(d9, d10) * 0.6D);
				l = MathHelper.floor(locX);
				i1 = MathHelper.floor(locY - 0.2D - (double) height);
				j1 = MathHelper.floor(locZ);
				k = world.getTypeId(l, i1, j1);
				if (k == 0 && world.getTypeId(l, i1 - 1, j1) == Block.FENCE.id) {
					k = world.getTypeId(l, i1 - 1, j1);
				}
				if (this.Q > (float) b && k > 0) {
					b = (int) this.Q + 1;
					a(l, i1, j1, k);
					Block.byId[k].b(world, l, i1, j1, this);
				}
			}

			this.D(); // Handle block collisions

			// Fire tick calculation (check using block collision)
			boolean flag2 = this.G();
			if (world.e(boundingBox.shrink(0.001D, 0.001D, 0.001D))) {
				this.burn(1);
				if(!flag2) {
					this.fireTicks++;
					if(this.fireTicks <= 0) {
						EntityCombustEvent event = new EntityCombustEvent(getBukkitEntity(), 8);
						this.world.getServer().getPluginManager().callEvent(event);
						if(!event.isCancelled()) {
							this.setOnFire(event.getDuration());
						}
					} else {
						this.setOnFire(8);
					}
				}
			} else if (this.fireTicks <= 0) {
				this.fireTicks = -this.maxFireTicks;
			}
			if (flag2 && this.fireTicks > 0) {
				this.world.makeSound(this, "random.fizz", 0.7F, 1.6F + (random.nextFloat() - random.nextFloat()) * 0.4F);
				this.fireTicks = -this.maxFireTicks;
			}
		}
	}

	/*
	 * Returns if this entity is allowed to collide with another entity
	 */
	private boolean canCollide(Entity e) {
		if (this.member().isCollisionIgnored(e)) return false;
		if (e.dead || this.dead) return false;
		if (this.group().isVelocityAction()) return false;
		if (e instanceof MinecartMember) {
			//colliding with a member in the group, or not?
			MinecartMember mm1 = this.member();
			MinecartMember mm2 = (MinecartMember) e;
			if (mm1.getGroup() == mm2.getGroup()) {
				//Same group, but do prevent penetration
				if (mm1.distance(mm2) > 0.5) {
					return false;
				}
			} else if (!mm1.getGroup().getProperties().getColliding()) {
				//Allows train collisions?
				return false;
			} else if (!mm2.getGroup().getProperties().getColliding()) {
				//Other train allows train collisions?
				return false;
			} else if (mm2.getGroup().isVelocityAction()) {
				//Is this train targeting?
				return false;
			}
			return false;
		} else if (e instanceof EntityLiving && e.vehicle != null && e.vehicle instanceof EntityMinecart) {
			//Ignore passenger collisions
			return false;
		} else {
			//Use push-away?
			TrainProperties prop = this.group().getProperties();
			if (e instanceof EntityItem && this.member().getProperties().canPickup()) {
				return false;
			} else if (prop.canPushAway(e.getBukkitEntity())) {
				this.member().pushSideways(e.getBukkitEntity());
				return false;
			}
			if (!prop.getColliding()) {
				//No collision is allowed? (Owners override)
				if (e instanceof EntityPlayer) {
					Player p = (Player) e.getBukkitEntity();
					if (!prop.isOwner(p)) {
						return false;
					}
				} else {
					return false;
				}
			} else if (this.passenger == null && this.canBeRidden() && EntityUtil.isMob(e.getBukkitEntity()) && this.member().getProperties().getMobsEnter()) {
				e.setPassengerOf(this);
				return false;
			}
		}
		return MinecartMemberStore.validateMinecart(e);
	}

	/*
	 * Prevents passengers and Minecarts from colliding with Minecarts
	 */
	@SuppressWarnings("unchecked")
	private void filterCollisionList(List<AxisAlignedBB> list) {		
		try {
			List<Entity> entityList = this.world.entityList;

			Iterator<AxisAlignedBB> iter = list.iterator();
			AxisAlignedBB a;
			while (iter.hasNext()) {
				a = iter.next();
				for (Entity e : entityList) {
					if (e.boundingBox == a) {
						if (!canCollide(e)) iter.remove();
						break;
					}
				}
			}
		} catch (ConcurrentModificationException ex) {
			TrainCarts.plugin.log(Level.WARNING, "Another plugin is interacting with the world entity list from another thread, please check your plugins!");
		}
	}

	public boolean canBeRidden() { return this.type == 0; }
	public boolean isStorageCart() { return this.type == 1; }
	public boolean isPoweredCart() { return this.type == 2; }
}
