package com.bergerkiller.bukkit.tc;

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
	public int fuel;
	public boolean isOnMinecartTrack = true;
	public boolean wasOnMinecartTrack = true;
	private static final Vector[][] matrix = new Vector[][] { 
		{ new Vector(0, 0, -1), new Vector(0, 0, 1) }, { new Vector(-1, 0, 0), new Vector(1, 0, 0) },
		{ new Vector(-1, -1, 0), new Vector(1, 0, 0) }, { new Vector(-1, 0, 0), new Vector(1, -1, 0) },
		{ new Vector(0, 0, -1), new Vector(0, -1, 1) }, { new Vector(0, -1, -1), new Vector(0, 0, 1) },
		{ new Vector(0, 0, 1), new Vector(1, 0, 0) }, { new Vector(0, 0, 1), new Vector(-1, 0, 0) },
		{ new Vector(0, 0, -1), new Vector(-1, 0, 0) }, { new Vector(0, 0, -1), new Vector(1, 0, 0) } };

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
		if (this instanceof MinecartMember) {
			MinecartMember mm = (MinecartMember) this;
			return new Location(getWorld(), getX(), getY(), getZ(), mm.getYaw(), mm.getPitch());
		} else {
			return new Location(getWorld(), getX(), getY(), getZ(), this.yaw, this.pitch);
		}
	}

	public boolean isMoving() {
		return Math.abs(this.motX) > 0.001 || Math.abs(this.motZ) > 0.001;
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
		if(!world.isStatic && !dead)
		{
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

			e(-n());
			d(10);
			aW();
			setDamage(getDamage() + i * 10);
			if(getDamage() > 40)
			{
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
				world.getServer().getPluginManager().callEvent(destroyEvent);
				if(destroyEvent.isCancelled()) {
					setDamage(40);
					return true;
				}
				// CraftBukkit end

				if(this.passenger != null) {
					this.passenger.mount(this);
				}

				this.die();

				for (org.bukkit.inventory.ItemStack stack : drops) {
					this.a(CraftItemStack.createNMSItemStack(stack), 0.0F);
				}

				if(type == 1)
				{
					EntityMinecart entityminecart = this;
					for(int j = 0; j < entityminecart.getSize(); j++)
					{
						net.minecraft.server.ItemStack itemstack = entityminecart.getItem(j);
						if(itemstack != null)
						{
							float f = random.nextFloat() * 0.8F + 0.1F;
							float f1 = random.nextFloat() * 0.8F + 0.1F;
							float f2 = random.nextFloat() * 0.8F + 0.1F;
							while(itemstack.count > 0) 
							{
								int k = random.nextInt(21) + 10;
								if(k > itemstack.count)
									k = itemstack.count;
								itemstack.count -= k;
								EntityItem entityitem = new EntityItem(world, locX + (double)f, locY + (double)f1, locZ + (double)f2, new net.minecraft.server.ItemStack(itemstack.id, k, itemstack.getData(), itemstack.getEnchantments()));
								float f3 = 0.05F;
								entityitem.motX = (float)random.nextGaussian() * f3;
								entityitem.motY = (float)random.nextGaussian() * f3 + 0.2F;
								entityitem.motZ = (float)random.nextGaussian() * f3;
								world.addEntity(entityitem);
							}
						}
					}
				}
			}
			return true;
		} else
		{
			return true;
		}
	}

	/*
	 * Stores physics information (since functions are now pretty much scattered around)
	 */
	private MoveInfo moveinfo = new MoveInfo();
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

		if (this.m() > 0) {
			this.d(this.m() - 1);
		}

		if (this.getDamage() > 0) {
			this.setDamage(this.getDamage() - 1);
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
			moveinfo.vec3d = this.h(this.locX, this.locY, this.locZ);
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
				if (((BlockMinecartTrack) Block.byId[railtype]).i()) {
					moveinfo.raildata &= 7; //clear sloped state for non-slopable rails
				}
				if (moveinfo.raildata >= 2 && moveinfo.raildata <= 5) {
					this.locY = (double) (moveinfo.blockY + 1);
					//TrainNote: Used to move a minecart up or down sloped tracks
					if (this.group().getProperties().slowDown) {
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
		if (this.moveinfo == null) return; //pre-update is needed
		double motX = MathUtil.fixNaN(this.motX);
		double motZ = MathUtil.fixNaN(this.motZ);

		speedFactor = MathUtil.fixNaN(speedFactor, 1);
		if (speedFactor > 10) speedFactor = 10; //>10 is ridiculous!

		motX = MathUtil.limit(motX, this.maxSpeed);
		motZ = MathUtil.limit(motZ, this.maxSpeed);
		motX *= speedFactor;
		motZ *= speedFactor;
		this.move(motX, 0.0D, motZ);

		if (moveinfo.isRailed) {
			if (moveinfo.moveMatrix[0].getY() != 0 && MathHelper.floor(this.locX) - moveinfo.blockX == moveinfo.moveMatrix[0].getX() && MathHelper.floor(this.locZ) - moveinfo.blockZ == moveinfo.moveMatrix[0].getZ()) {
				this.setPosition(this.locX, this.locY + (double) moveinfo.moveMatrix[0].getY(), this.locZ);
			} else if (moveinfo.moveMatrix[1].getY() != 0 && MathHelper.floor(this.locX) - moveinfo.blockX == moveinfo.moveMatrix[1].getX() && MathHelper.floor(this.locZ) - moveinfo.blockZ == moveinfo.moveMatrix[1].getZ()) {
				this.setPosition(this.locX, this.locY + (double) moveinfo.moveMatrix[1].getY(), this.locZ);
			}

			// CraftBukkit
			//==================TrainCarts edited==============
			if (this.type == 2 && !ignoreForces()) {
				double fuelPower = MathUtil.length(this.b, this.c);
				if (fuelPower > 0.01) {
					this.b /= fuelPower;
					this.c /= fuelPower;
					double boost = 0.04 + TrainCarts.poweredCartBoost;

					this.motX *= 0.8;
					this.motY *= 0.0;
					this.motZ *= 0.8;
					this.motX += this.b * boost;
					this.motZ += this.c * boost;
				} else {
					if (this.group().getProperties().slowDown) {
						this.motX *= 0.9;
						this.motY *= 0.0;
						this.motZ *= 0.9;
					}
				}
			}
			if (this.group().getProperties().slowDown) {
				if (this.passenger != null || !this.slowWhenEmpty || !TrainCarts.slowDownEmptyCarts) {
					this.motX *= 0.997;
					this.motY *= 0.0;
					this.motZ *= 0.997;
				} else {
					this.motX *= 0.96;
					this.motY *= 0.0;
					this.motZ *= 0.96;
				}
			}
			//==================================================

			Vec3D vec3d1 = this.h(this.locX, this.locY, this.locZ);

			double motLength;

			//x and z motion slowed down on slopes
			if (vec3d1 != null && moveinfo.vec3d != null) {
				if (this.group().getProperties().slowDown) {
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
			if (this.type == 2) {
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
					if (this.world.e(moveinfo.blockX - 1, moveinfo.blockY, moveinfo.blockZ)) {
						this.motX = 0.02;
					} else if (this.world.e(moveinfo.blockX + 1, moveinfo.blockY, moveinfo.blockZ)) {
						this.motX = -0.02;
					}
				} else if (moveinfo.raildata == 0) {
					//launch at z-axis
					if (this.world.e(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ - 1)) {
						this.motZ = 0.02;
					} else if (this.world.e(moveinfo.blockX, moveinfo.blockY, moveinfo.blockZ + 1)) {
						this.motZ = -0.02;
					}
				}
			}
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
		this.c(this.yaw, this.pitch);

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
				if (entity != this.passenger && entity.e_() && entity instanceof EntityMinecart) {
					entity.collide(this);
				}
			}
		}

		if (this.passenger != null && this.passenger.dead) {
			this.passenger.vehicle = null; // CraftBukkit
			this.passenger = null;
		}

		if (this.fuel > 0 && this.type == 2) {
			if (--this.fuel == 0) {
				//TrainCarts - Actions to be done when empty
				if (this.onCoalUsed()) {
					this.fuel += 3600; //Refill
				} else {
					this.b = this.c = 0.0D;
				}
			}
		}
		this.a(this.fuel > 0);
	}

	/*
	 * Overridden function used to let players interact with this minecart
	 * Changes: use my own fuel and changes direction of all attached carts
	 */
	@Override
	public boolean b(EntityHuman entityhuman) {
		if (this.type == 2) {
			ItemStack itemstack = entityhuman.inventory.getItemInHand();
			if (itemstack != null && itemstack.id == Item.COAL.id) {
				if (--itemstack.count == 0) {
					entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, (ItemStack) null);
				}
				this.fuel += 3600;
			}
			this.b = this.locX - entityhuman.locX;
			this.c = this.locZ - entityhuman.locZ;	
			BlockFace dir = FaceUtil.getDirection(this.b, this.c, false);
			if (dir == this.member().getDirection().getOppositeFace()) {
				this.group().reverse();
			}
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
	@SuppressWarnings("unchecked")
	public void move(double d0, double d1, double d2)
	{
		if(bQ)
		{
			boundingBox.d(d0, d1, d2);
			locX = (boundingBox.a + boundingBox.d) / 2D;
			locY = (boundingBox.b + (double)height) - (double)bO;
			locZ = (boundingBox.c + boundingBox.f) / 2D;
		} else
		{
			bO *= 0.4F;
			double d3 = locX;
			double d4 = locZ;
			if(bC)
			{
				bC = false;
				d0 *= 0.25D;
				d1 *= 0.05000000074505806D;
				d2 *= 0.25D;
				motX = 0.0D;
				motY = 0.0D;
				motZ = 0.0D;
			}
			double d5 = d0;
			double d6 = d1;
			double d7 = d2;
			AxisAlignedBB axisalignedbb = boundingBox.clone();
			List list = world.getCubes(this, boundingBox.a(d0, d1, d2));
			//================================================
					filterCollisionList(list);
			//================================================

					for(int i = 0; i < list.size(); i++)
						d1 = ((AxisAlignedBB)list.get(i)).b(boundingBox, d1);

					boundingBox.d(0.0D, d1, 0.0D);
					if(!bD && d6 != d1)
					{
						d2 = 0.0D;
						d1 = 0.0D;
						d0 = 0.0D;
					}
					boolean flag1 = onGround || d6 != d1 && d6 < 0.0D;
					for(int j = 0; j < list.size(); j++)
						d0 = ((AxisAlignedBB)list.get(j)).a(boundingBox, d0);

					boundingBox.d(d0, 0.0D, 0.0D);
					if(!bD && d5 != d0)
					{
						d2 = 0.0D;
						d1 = 0.0D;
						d0 = 0.0D;
					}
					for(int j = 0; j < list.size(); j++)
						d2 = ((AxisAlignedBB)list.get(j)).c(boundingBox, d2);

					boundingBox.d(0.0D, 0.0D, d2);
					if(!bD && d7 != d2)
					{
						d2 = 0.0D;
						d1 = 0.0D;
						d0 = 0.0D;
					}
					double d9;
					double d10;
					int k;
					if(bP > 0.0F && flag1 && bO < 0.05F && (d5 != d0 || d7 != d2))
					{
						d9 = d0;
						d10 = d1;
						double d11 = d2;
						d0 = d5;
						d1 = bP;
						d2 = d7;
						AxisAlignedBB axisalignedbb1 = boundingBox.clone();
						boundingBox.b(axisalignedbb);
						list = world.getCubes(this, boundingBox.a(d5, d1, d7));

						//================================================
								filterCollisionList(list);
						//================================================

								for(k = 0; k < list.size(); k++)
									d1 = ((AxisAlignedBB)list.get(k)).b(boundingBox, d1);

						boundingBox.d(0.0D, d1, 0.0D);
						if(!bD && d6 != d1)
						{
							d2 = 0.0D;
							d1 = 0.0D;
							d0 = 0.0D;
						}
						for(k = 0; k < list.size(); k++)
							d0 = ((AxisAlignedBB)list.get(k)).a(boundingBox, d0);

						boundingBox.d(d0, 0.0D, 0.0D);
						if(!bD && d5 != d0)
						{
							d2 = 0.0D;
							d1 = 0.0D;
							d0 = 0.0D;
						}
						for(k = 0; k < list.size(); k++)
							d2 = ((AxisAlignedBB)list.get(k)).c(boundingBox, d2);

						boundingBox.d(0.0D, 0.0D, d2);
						if(!bD && d7 != d2)
						{
							d2 = 0.0D;
							d1 = 0.0D;
							d0 = 0.0D;
						}
						if(!bD && d6 != d1)
						{
							d2 = 0.0D;
							d1 = 0.0D;
							d0 = 0.0D;
						} else
						{
							d1 = -bP;
							for(k = 0; k < list.size(); k++)
								d1 = ((AxisAlignedBB)list.get(k)).b(boundingBox, d1);

							boundingBox.d(0.0D, d1, 0.0D);
						}
						if(d9 * d9 + d11 * d11 >= d0 * d0 + d2 * d2)
						{
							d0 = d9;
							d1 = d10;
							d2 = d11;
							boundingBox.b(axisalignedbb1);
						} else
						{
							double d12 = boundingBox.b - (double)(int)boundingBox.b;
							if(d12 > 0.0D)
								bO = (float)((double)bO + d12 + 0.01D);
						}
					}
					locX = (boundingBox.a + boundingBox.d) / 2D;
					locY = (boundingBox.b + (double)height) - (double)bO;
					locZ = (boundingBox.c + boundingBox.f) / 2D;
					positionChanged = d5 != d0 || d7 != d2;
					bz = d6 != d1;
					onGround = d6 != d1 && d6 < 0.0D;
					bA = positionChanged || bz;
					a(d1, onGround);
					if(d5 != d0)
						motX = 0.0D;
					if(d6 != d1)
						motY = 0.0D;
					if(d7 != d2)
						motZ = 0.0D;
					d9 = locX - d3;
					d10 = locZ - d4;
					if(positionChanged) {
						Vehicle vehicle = (Vehicle)getBukkitEntity();
						org.bukkit.block.Block block = world.getWorld().getBlockAt(MathHelper.floor(locX), MathHelper.floor(locY - (double)height), MathHelper.floor(locZ));
						if(d5 > d0)
							block = block.getRelative(BlockFace.SOUTH);
						else
							if(d5 < d0)
								block = block.getRelative(BlockFace.NORTH);
							else
								if(d7 > d2)
									block = block.getRelative(BlockFace.WEST);
								else
									if(d7 < d2)
										block = block.getRelative(BlockFace.EAST);
						VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, block);
						world.getServer().getPluginManager().callEvent(event);
					}
					int l;
					int i1;
					int j1;
					if(g_() && this.vehicle == null)
					{
						bJ = (float)((double)bJ + (double)MathHelper.sqrt(d9 * d9 + d10 * d10) * 0.59999999999999998D);
						l = MathHelper.floor(locX);
						i1 = MathHelper.floor(locY - 0.20000000298023224D - (double)height);
						j1 = MathHelper.floor(locZ);
						k = world.getTypeId(l, i1, j1);
						if(k == 0 && world.getTypeId(l, i1 - 1, j1) == Block.FENCE.id)
							k = world.getTypeId(l, i1 - 1, j1);
						if(bJ > (float)b && k > 0)
						{
							b = (int)bJ + 1;
							a(l, i1, j1, k);
							Block.byId[k].b(world, l, i1, j1, this);
						}
					}
					l = MathHelper.floor(boundingBox.a + 0.001D);
					i1 = MathHelper.floor(boundingBox.b + 0.001D);
					j1 = MathHelper.floor(boundingBox.c + 0.001D);
					k = MathHelper.floor(boundingBox.d - 0.001D);
					int k1 = MathHelper.floor(boundingBox.e - 0.001D);
					int l1 = MathHelper.floor(boundingBox.f - 0.001D);
					if(world.a(l, i1, j1, k, k1, l1))
					{
						for(int i2 = l; i2 <= k; i2++)
						{
							for(int j2 = i1; j2 <= k1; j2++)
							{
								for(int k2 = j1; k2 <= l1; k2++)
								{
									int l2 = world.getTypeId(i2, j2, k2);
									if(l2 > 0)
										Block.byId[l2].a(world, i2, j2, k2, this);
								}

							}

						}

					}
					boolean flag2 = aT();
					if(world.d(boundingBox.shrink(0.001D, 0.001D, 0.001D)))
					{
						burn(1);
						if(!flag2)
						{
							fireTicks++;
							if(fireTicks <= 0)
							{
								EntityCombustEvent event = new EntityCombustEvent(getBukkitEntity(), 8);
								world.getServer().getPluginManager().callEvent(event);
								if(!event.isCancelled())
									setOnFire(event.getDuration());
							} else
							{
								setOnFire(8);
							}
						}
					} else
						if(fireTicks <= 0)
							fireTicks = -maxFireTicks;
					if(flag2 && fireTicks > 0)
					{
						world.makeSound(this, "random.fizz", 0.7F, 1.6F + (random.nextFloat() - random.nextFloat()) * 0.4F);
						fireTicks = -maxFireTicks;
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
			} else if (!mm1.getGroup().getProperties().trainCollision) {
				//Allows train collisions?
				return false;
			} else if (!mm2.getGroup().getProperties().trainCollision) {
				//Other train allows train collisions?
				return false;
			} else if (mm2.getGroup().isVelocityAction()) {
				//Is this train targeting?
				return false;
			}
		} else if (e instanceof EntityLiving && e.vehicle != null && e.vehicle instanceof EntityMinecart) {
			//Ignore passenger collisions
			return false;
		} else {
			//Use push-away?
			TrainProperties prop = this.group().getProperties();
			if (e instanceof EntityItem && this.member().getProperties().pickUp) {
				return false;
			} else if (prop.canPushAway(e.getBukkitEntity())) {
				this.member().pushSideways(e.getBukkitEntity());
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
			} else if (this.type == 1 && EntityUtil.isMob(e.getBukkitEntity()) && this.member().getProperties().allowMobsEnter && this.passenger == null) {
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
