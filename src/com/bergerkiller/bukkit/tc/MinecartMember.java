package com.bergerkiller.bukkit.tc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.server.ChunkCoordinates;
import net.minecraft.server.EntityMinecart;
import net.minecraft.server.ItemStack;
import net.minecraft.server.MathHelper;
import net.minecraft.server.World;
import net.minecraft.server.EntityItem;

import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.tc.API.MemberCoalUsedEvent;
import com.bergerkiller.bukkit.tc.API.MemberBlockChangeEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.actions.*;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.BlockUtil;
import com.bergerkiller.bukkit.tc.utils.EntityUtil;
import com.bergerkiller.bukkit.tc.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.utils.ItemUtil;

public class MinecartMember extends NativeMinecartMember {
	private static Set<MinecartMember> replacedCarts = new HashSet<MinecartMember>();
	private static Map<String, MinecartMember> editing = new HashMap<String, MinecartMember>();
	private static boolean denyConversion = false;
	public static boolean canConvert(Entity entity) {
		return !denyConversion && get(entity) == null;
	}
	
	private static EntityMinecart findByID(UUID uuid) {
		EntityMinecart e;
		for (World world : Util.getWorlds()) {
			for (Object o : world.entityList) {
				if (o instanceof EntityMinecart) {
					e = (EntityMinecart) o;
					if (e.uniqueId.equals(uuid)) {
						return e;
					}
				}
			}
		}
		return null;
	}
	public static MinecartMember get(Object o) {
		if (o == null) return null;
		if (o instanceof UUID) {
			o = findByID((UUID) o);
			if (o == null) return null;
		}
		if (o instanceof Minecart) {
			o = EntityUtil.getNative((Minecart) o);
		}
		if (o instanceof MinecartMember) {
			return (MinecartMember) o;
		} else {
			return null;
		}
	}
	public static MinecartMember[] getAll(Object... objects) {
		MinecartMember[] rval = new MinecartMember[objects.length];
		for (int i = 0; i < rval.length; i++) {
			rval[i] = get(objects[i]);
		}
		return rval;
	}
	public static MinecartMember convert(Object o) {
		if (o == null) return null;
		if (o instanceof UUID) {
			o = findByID((UUID) o);
			if (o == null) return null;
		}
		EntityMinecart em;
		if (o instanceof EntityMinecart) {
			em = (EntityMinecart) o;
		} else if (o instanceof Minecart) {
			em = EntityUtil.getNative((Minecart) o);
		} else {
			return null;
		}
		if (em instanceof MinecartMember) return (MinecartMember) em;
		if (em.dead) return null; //prevent conversion of dead entities 
		//not found, conversion allowed?
		if (denyConversion) return null;
		//convert
		MinecartMember mm = new MinecartMember(em.world, em.lastX, em.lastY, em.lastZ, em.type);
		EntityUtil.replaceMinecarts(em, mm);
		replacedCarts.add(mm);
		return mm;
	}
	public static MinecartMember[] convertAll(Entity... entities) {
		MinecartMember[] rval = new MinecartMember[entities.length];
		for (int i = 0; i < rval.length; i++) {
			rval[i] = convert(entities[i]);
		}
		return rval;
	}
	
	public static MinecartMember spawn(Location at, int type) {
		MinecartMember mm = new MinecartMember(EntityUtil.getNative(at.getWorld()), at.getX(), at.getY(), at.getZ(), type);
		mm.yaw = at.getYaw();
		mm.pitch = at.getPitch();
		mm.world.addEntity(mm);
		return mm;
	}
	
	public static MinecartMember getAt(Block railblock) {
		return getAt(railblock.getWorld(), BlockUtil.getCoordinates(railblock));
	}
	public static MinecartMember getAt(org.bukkit.World world, ChunkCoordinates coord) {
		int cx = coord.x >> 4;
		int cz = coord.z >> 4;
		if (world.isChunkLoaded(cx, cz)) {
			MinecartMember mm;
			MinecartMember result = null;
			for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
				mm = get(e);
				if (mm == null) continue;
				if (mm.blockx != coord.x) continue;
				if (mm.blocky != coord.y) continue;
				if (mm.blockz != coord.z) continue;
				result = mm;
				if (result.isHeadingTo(coord)) return result;
			}
			return result;
		}
		return null;
	}
	public static MinecartMember getAt(Location at) {
		return getAt(at, null);
	}
	public static MinecartMember getAt(Location at, MinecartGroup in) {
		return getAt(at, in, 1);
	}
	public static MinecartMember getAt(Location at, MinecartGroup in, double searchRadius) {
		searchRadius *= searchRadius;
		MinecartMember result = null;
		for (Entity e : at.getBlock().getChunk().getEntities()) {
			if (e instanceof Minecart) {
				MinecartMember mm = get(e);
				if (mm == null) continue;
				if (in != null && mm.getGroup() != in) continue;
				if (mm.distanceSquared(at) > searchRadius) continue;
				result = mm;
				if (mm.isHeadingTo(at)) return result;
			}
		}
		return result;
	}
	public static EntityMinecart undoReplacement(MinecartMember mm) {
		replacedCarts.remove(mm);
		if (!mm.dead) {
			denyConversion = true;
			EntityMinecart em = new EntityMinecart(mm.world, mm.lastX, mm.lastY, mm.lastZ, mm.type);
			EntityUtil.replaceMinecarts(mm, em);
			denyConversion = false;
			return em;
		}
		return null;
	}
	public static void undoReplacement() {
		for (MinecartMember m : replacedCarts.toArray(new MinecartMember[0])) {
			undoReplacement(m);
		}
	}
	public static void cleanUpDeadCarts() {
		Iterator<MinecartMember> iter = replacedCarts.iterator();
		MinecartMember mm;
		while (iter.hasNext()) {
			mm = iter.next();
			if (mm.dead) {
				iter.remove();
				mm.die();
				Util.broadcast("AUTO REMO");
			}
		}
	}
	
	/*
	 * Editing
	 */
	public static MinecartMember getEditing(Player player) {
		return getEditing(player.getName());
	}
	public static MinecartMember getEditing(String playername) {
		MinecartMember mm = editing.get(playername.toLowerCase());
		if (mm == null || mm.getGroup() == null) {
			editing.remove(playername.toLowerCase());
			return null;
		} else {
			return mm;
		}
	}
	public void setEditing(Player player) {
		this.setEditing(player.getName());
	}
	public void setEditing(String playername) {
		editing.put(playername.toLowerCase(), this);
	}

	private float customYaw = 0;
	MinecartGroup group;
	private int blockx, blocky, blockz;
	private boolean railsloped = false;
	private boolean isDerailed = false;
	private boolean isFlying = false;
	private CartProperties properties;
	private Map<UUID, AtomicInteger> collisionIgnoreTimes = new HashMap<UUID, AtomicInteger>();
	private HashSet<Block> activeSigns = new HashSet<Block>();
	
	private MinecartMember(World world, double x, double y, double z, int type) {
		super(world, x, y, z, type);
		this.customYaw = this.yaw;
		try {
			this.updateBlock(true);
		} catch (MemberDeadException ex) {
		} catch (GroupUnloadedException ex) {}
	}
	
	/*
	 * Overridden Minecart functions
	 */
	@Override
	public void w_() {
		MinecartGroup g = this.getGroup();
		if (g == null) return;
		if (this.dead) {
			//remove self
			g.remove(this);
		} else if (g.size() == 0) {
			g.remove();
			super.w_();
		} else if (g.tail() == this) {
			g.doPhysics();
		}
	}
	
	public boolean preUpdate() {
		//subtract times
		Iterator<AtomicInteger> times = collisionIgnoreTimes.values().iterator();
		while (times.hasNext()) {			
			if (times.next().decrementAndGet() <= 0) times.remove();
		}
		return super.preUpdate();
	}
	
	public void postUpdate(double speedFactor) throws MemberDeadException, GroupUnloadedException {
		super.postUpdate(speedFactor);
		this.validate();
		if (this.getProperties().pickUp && this.isStorageMinecart()) {
			Inventory inv = this.getInventory();
			org.bukkit.inventory.ItemStack stack;
			Item item;
			for (net.minecraft.server.Entity e : this.getNearbyEntities(2)) {
				if (e instanceof EntityItem) {
					item = (Item) e.getBukkitEntity();
					if (ItemUtil.isIgnoredItem(item)) continue;
					stack = item.getItemStack();
					double distance = this.distance(e);
					if (ItemUtil.canTransfer(stack, inv)) {
						if (distance < 0.7) {
							ItemUtil.transfer(stack, inv, Integer.MAX_VALUE);
							//this.world.playNote
							this.world.getWorld().playEffect(this.getLocation(),  Effect.CLICK1, 0);
							if (stack.getAmount() == 0) {
								e.dead = true;
								continue;
							}
						} else {
							final double factor;
							if (distance > 1) {
								factor = 0.8;
							} else if (distance > 0.75) {
								factor = 0.5;
							} else {
								factor = 0.25;
							}
							this.push(e.getBukkitEntity(), -factor / distance);
							continue;
						}
					}
					this.push(e.getBukkitEntity(), 1 / distance);
				}
			}
		}
		this.updateBlock(false);
	}
	
	@Override
	public boolean onCoalUsed() {
		MemberCoalUsedEvent event = MemberCoalUsedEvent.call(this);
		if (event.useCoal()) {
			for (MinecartMember mm : this.getNeightbours()) {
				//Is it a storage minecart?
				if (mm.type == 1) {
					//has coal?
					for (int i = 0; i < mm.getSize(); i++) {
						if (mm.getItem(i) != null && mm.getItem(i).id == Material.COAL.getId()) {
							 mm.getItem(i).count--;
							 return true;
						}
					}
				}
			}
		}
		return event.refill();
	}
	
	private void updateBlock(boolean forced) throws MemberDeadException, GroupUnloadedException {
		this.validate();
		Block from = forced ? null : this.getBlock();
		int x = this.getBlockX();
		int y = this.getBlockY();
		int z = this.getBlockZ();
		if (forced || x != this.blockx || z != this.blockz || y != (this.railsloped ? this.blocky : this.blocky + 1)) {
			this.blockx = x;
			this.blocky = y;
			this.blockz = z;
			//is it in loaded chunks?
			if (!this.world.areChunksLoaded(this.blockx, this.blocky, this.blockz, 32)) {
			    if (this.getGroup().canUnload()) {
			    	//this train has to be unloaded
			    	GroupManager.hideGroup(this.getGroup());
			    	throw new GroupUnloadedException();
			    } else {
			    	//load nearby chunks
					int xmid = this.getChunkX();
					int zmid = this.getChunkZ();
					for (int cx = xmid - 2; cx <= xmid + 2; cx++) {
						for (int cz = zmid - 2; cz <= zmid + 2; cz++) {
							this.getWorld().getChunkAt(cx, cz);
						}
					}
			    }
			}
			//find the correct Y-value
			this.railsloped = false;
			this.isDerailed = false;
			this.isFlying = false;
			int r = this.world.getTypeId(this.blockx, this.blocky - 1, this.blockz);
			if (BlockUtil.isRails(r)) {
				--this.blocky;
			} else {		
				r = this.world.getTypeId(this.blockx, this.blocky, this.blockz);
				if (BlockUtil.isRails(r)) {
					this.railsloped = true;
				} else {
					this.isDerailed = true;
					if (r == 0) this.isFlying = true;
				}
			}
			//Update from value if it was not set
			if (from == null) from = this.getBlock();
			
			//update active signs
			this.clearActiveSigns();
			for (Block sign : BlockUtil.getSignsAttached(this.getBlock())) {
				this.addActiveSign(sign);
			}
			
			//destroy blocks
			Block left = this.getBlockRelative(BlockFace.WEST);
			Block right = this.getBlockRelative(BlockFace.EAST);
			if (this.getProperties().canBreak(left)) BlockUtil.breakBlock(left);
			if (this.getProperties().canBreak(right)) BlockUtil.breakBlock(right);
			
			//event
			MemberBlockChangeEvent.call(this, from, this.getBlock());
		} else if (!this.activeSigns.isEmpty()) {
			//move
			SignActionEvent info;
			for (Block sign : this.activeSigns) {
				info = new SignActionEvent(sign, this);
				SignAction.executeAll(info, SignActionType.MEMBER_MOVE);
			}
		}
	}
		
	public void validate() throws MemberDeadException {
		if (this.dead) {
			this.die();
			throw new MemberDeadException();
		}
	}
	
	/*
	 * General getters and setters
	 */
 	public CartProperties getProperties() {
 		if (this.properties == null) {
 			this.properties = CartProperties.get(this);
 		}
 		return this.properties;
 	}
	public Minecart getMinecart() {
		return (Minecart) this.getBukkitEntity();
	}
 	public MinecartGroup getGroup() {
 		if (this.group == null) {
 			this.group = MinecartGroup.create(this);
 		}
 		return this.group;
 	}
 	public int getIndex() {
 	    if (this.group == null) {
 	    	return this.dead ? -1 : 0;
 	    } else {
 	    	return this.group.indexOf(this);
 	    }
 	}
 	public MinecartMember getNeighbour(int offset) {
 		int index = this.getIndex();
 		if (index == -1) return null;
 		index += offset;
 		if (this.getGroup().containsIndex(index)) return this.getGroup().get(index);
 		return null;
 	}
 	public MinecartMember[] getNeightbours() {
		if (this.getGroup() == null) return new MinecartMember[0];
		int index = this.getIndex();
		if (index == -1) return new MinecartMember[0];
		if (index > 0) {
			if (index < this.getGroup().size() - 1) {
				return new MinecartMember[] {
						this.getGroup().get(index - 1), 
						this.getGroup().get(index + 1)};
			} else {
				return new MinecartMember[] {this.getGroup().get(index - 1)};
			}
		} else if (index < this.getGroup().size() - 1) {
			return new MinecartMember[] {this.getGroup().get(index + 1)};
		} else {
			return new MinecartMember[0];
		}
	}
	
 	/*
 	 * Active signs
 	 */
	public boolean isActiveSign(Block signblock) {
		if (signblock == null) return false;
		return this.activeSigns.contains(signblock);
	}
	public boolean addActiveSign(Block signblock) throws MemberDeadException {
		if (this.activeSigns.add(signblock)) {
			this.validate();
			SignActionEvent info = new SignActionEvent(signblock, this);
			SignAction.executeAll(info, SignActionType.MEMBER_ENTER);
			this.validate();
			MinecartGroup g = this.getGroup();
			if (g.size() == 1 || g.tail() != this) {
				this.getGroup().setActiveSign(info, true);
			}
			return true;
		} else {
			return false;
		}
	}
	public void clearActiveSigns() {
		boolean found;
		for (Block signblock : this.activeSigns) {
			SignAction.executeAll(new SignActionEvent(signblock, this), SignActionType.MEMBER_LEAVE);
			if (this.dead) return; 
			//this sign is not present in other members of the group?
			found = false;
			for (MinecartMember mm : this.getGroup()) {
				if (mm != this && mm.isActiveSign(signblock)) {
					found = true;
					break;
				}
			}
			if (found) continue;
			if (this.getGroup().size() == 1 || this.getGroup().tail() == this) {
				this.getGroup().setActiveSign(signblock, false);
			}
		}
		this.activeSigns.clear();
	}
	public Set<Block> getActiveSigns() {
		return this.activeSigns;
	}
	public boolean hasSign() {
		return !this.activeSigns.isEmpty();
	}
 	
 	/*
 	 * Block functions
 	 */
 	public Block getBlock(int dx, int dy, int dz) {
 		return this.world.getWorld().getBlockAt(this.blockx + dx, this.blocky + dy, this.blockz + dz);
 	}
	public Block getBlock(BlockFace face) {
		return this.getBlock(face.getModX(), face.getModY(), face.getModZ());
	}
	public Block getBlock() {
		return this.getBlock(0, 0, 0);
	}
	public Block getBlockRelative(BlockFace direction) {
		return this.getBlock(FaceUtil.offset(direction, this.getDirection()));
	}
 	public Block getRailsBlock() {
		if (this.isDerailed) return null;
		Block b = this.getBlock();
		if (BlockUtil.isRails(b)) {
			return b;
		} else {
			this.isDerailed = true;
			return null;
		}
	}
	public BlockFace getRailDirection() {
		Rails r = BlockUtil.getRails(this.getRailsBlock());
		if (r == null) return BlockFace.NORTH;
		return r.getDirection();
	}
	public Block getGroundBlock() {
		return this.getBlock(0, -1, 0);
	}
	
	/*
	 * Actions
	 */
	public <T extends Action> T addAction(T action) {
		return this.getGroup().addAction(action);
	}
	public MemberActionWaitDistance addActionWaitDistance(double distance) {
		return this.addAction(new MemberActionWaitDistance(this, distance));
	}
	public MemberActionWaitLocation addActionWaitLocation(Location location) {
		return this.addAction(new MemberActionWaitLocation(this, location));
	}
	public MemberActionWaitLocation addActionWaitLocation(Location location, double radius) {
		return this.addAction(new MemberActionWaitLocation(this, location, radius));
	}
	public MemberActionLaunch addActionLaunch(double distance, double targetvelocity) {
		return this.addAction(new MemberActionLaunch(this, distance, targetvelocity));
	}
	public MemberActionLaunchLocation addActionLaunch(Location destination, double targetvelocity) {
		return this.addAction(new MemberActionLaunchLocation(this, destination, targetvelocity));
	}
	public MemberActionLaunchLocation addActionLaunch(Vector offset, double targetvelocity) {
		return this.addActionLaunch(this.getLocation().add(offset), targetvelocity);
	}
	public MemberActionLaunchLocation addActionLaunch(BlockFace direction, double distance, double targetvelocity) {
		return this.addActionLaunch(FaceUtil.faceToVector(direction, distance), targetvelocity);
	}
	
	/*
	 * Velocity functions
	 */
	public double getForce() {
		return Util.length(motX, motZ);
	}
	public double getForwardForce() {
		float yaw = this.getYaw() * Util.DEGTORAD;
        return -MathHelper.sin(yaw) * this.motZ - MathHelper.cos(yaw) * this.motX;
	}
	public void setForceFactor(final double factor) {
		this.motX *= factor;
		this.motY *= factor;
		this.motZ *= factor;
	}
	public void setForce(double force, float yaw) {
		if (this.railsloped) {
			//calculate upwards or downwards force
			BlockFace raildir = this.getRailDirection();
			BlockFace dir = this.getDirection();
			final float factor = 0.7071F;
			if (dir == raildir) {
				this.motY = factor * force;
			} else if (dir == raildir.getOppositeFace()) {
				this.motY = -factor * force;
			}
		}
		if (isTurned()) {
			double l = this.getForce();
			if (l > 0.001) {
				double factor = force / l;
				this.motX *= factor;
				this.motZ *= factor;
				return;
			}
		}
		yaw *= Util.DEGTORAD;
		this.motX = -MathHelper.cos(yaw) * force;
		this.motZ = -MathHelper.sin(yaw) * force;
	}
	public void setForce(double force, Location to) {
		setForce(force, Util.getLookAtYaw(this.getLocation(), to));
	}
	public void setForce(double force, BlockFace direction) {
		setForce(force, FaceUtil.faceToYaw(direction));
	}
	public void setForwardForce(double force) {
		setForce(force, this.getYaw());
	}
	public void limitSpeed() {
		//Limits the velocity to the maximum
		double currvel = getForce();
		if (currvel > this.maxSpeed && currvel > 0.01) {
			double factor = this.maxSpeed / currvel;
			this.motX *= factor;
			this.motZ *= factor;
		}
	}
	public void setVelocity(Vector velocity) {
		this.motX = velocity.getX();
		this.motZ = velocity.getZ();
		this.motY = velocity.getY();
	}
	public Vector getVelocity() {
		return new Vector(this.motX, this.motY, this.motZ);
	}
	public BlockFace getDirection() {
		float yaw;
		if (this.isMoving()) {
			yaw = Util.getLookAtYaw(this.getVelocity());
		} else {
			yaw = this.getYaw();
		}
		return FaceUtil.yawToFace(yaw, false);
	}
	public TrackMap makeTrackMap(int size) {
		return new TrackMap(BlockUtil.getRailsBlock(this.getLocation()), this.getDirection(), size);
	}
	
	/*
	 * Location functions
	 */
	public void teleport(Location to) {
		getMinecart().teleport(to);
	}
	public double getSubX() {
		double x = getX() + 0.5;
		return x - (int) x;
	}	
	public double getSubZ() {
		double z = getZ() + 0.5;
		return z - (int) z;
	}
	public int getChunkX() {
		return this.blockx >> 4;
	}
	public int getChunkZ() {
		return this.blockz >> 4;
	}
	public double getMovedX() {
		return this.locX - this.lastX;
	}
	public double getMovedY() {
		return this.locY - this.lastY;
	}
	public double getMovedZ() {
		return this.locZ - this.lastZ;
	}
	public double getMovedDistanceXZ() {
		return Util.length(this.getMovedX(), this.getMovedZ());
	}
	public double getMovedDistance() {
		return Util.length(this.getMovedX(), this.getMovedY(), this.getMovedZ());
	}
	public double distance(net.minecraft.server.Entity e) {
		return Util.distance(this.getX(), this.getY(), this.getZ(), e.locX, e.locY, e.locZ);
	}
	public double distance(Location l) {
		return Util.distance(this.getX(), this.getY(), this.getZ(), l.getX(), l.getY(), l.getZ());
	}
	public double distanceXZ(net.minecraft.server.Entity e) {
		return Util.distance(this.getX(), this.getZ(), e.locX, e.locZ);
	}
	public double distanceXZ(Location l) {
		return Util.distance(this.getX(), this.getZ(), l.getX(), l.getZ());
	}
	public double distanceSquared(net.minecraft.server.Entity e) {
		return Util.distanceSquared(this.getX(), this.getY(), this.getZ(), e.locX, e.locY, e.locZ);
	}
	public double distanceSquared(Location l) {
		return Util.distanceSquared(this.getX(), this.getY(), this.getZ(), l.getX(), l.getY(), l.getZ());
	}
	public double distanceXZSquared(net.minecraft.server.Entity e) {
		return Util.distanceSquared(this.getX(), this.getZ(), e.locX, e.locZ);
	}
	public double distanceXZSquared(Location l) {
		return Util.distanceSquared(this.getX(), this.getZ(), l.getX(), l.getZ());
	}
	public boolean isNearOf(MinecartMember member) {
		double max = TrainCarts.maxCartDistance * TrainCarts.maxCartDistance;
		if (this.distanceXZSquared(member) > max) return false;
		if (this.isDerailed() || member.isDerailed()) {
			return Math.abs(this.getY() - member.getY()) <= max;
		}
		return true;
	}
	public List<net.minecraft.server.Entity> getNearbyEntities(double radius) {
		return this.getNearbyEntities(radius, radius, radius);
	}
	@SuppressWarnings("unchecked")
	public List<net.minecraft.server.Entity> getNearbyEntities(double x, double y, double z) {
		return this.world.b(this, this.boundingBox.b(x, y, z));
	}
	public Vector getOffset(ChunkCoordinates to) {
		return new Vector(to.x - this.getX(), to.y - this.getY(), to.z - this.getZ());
	}
	public Vector getOffset(Entity to) {
		return getOffset(to.getLocation());
	}
	public Vector getOffset(Location to) {
		return new Vector(to.getX() - this.getX(), to.getY() - this.getY(), to.getZ() - this.getZ());
	}
	
	/*
	 * Pitch functions
	 */
	public float getPitch() {
		return this.pitch;
	}
	public float getPitchDifference(MinecartMember comparer) {
		return getPitchDifference(comparer.getPitch());
	}
	public float getPitchDifference(float pitchcomparer) {
		return Util.getAngleDifference(getPitch(), pitchcomparer);
	}
		
	/*
	 * Yaw functions
	 */
	public float getYaw() {
		return this.customYaw;
	}
	public float getYawDifference(float yawcomparer) {
		return Util.getAngleDifference(customYaw, yawcomparer);
	}	
	public void updateYaw(float yawcomparer) {
		double x = this.getSubX();
		double z = this.getSubZ();
		if (x == 0 && z != 0 && Math.abs(motX) < 0.001) {
			//cart is driving along the x-axis
			this.customYaw = -90;
		} else if (z == 0 && x != 0 && Math.abs(motZ) < 0.001) {
			//cart is driving along the z-axis
			this.customYaw = -180;
		} else {
			//try to get the yaw from the rails
			this.customYaw = FaceUtil.getRailsYaw(this.getRailDirection());
		}
		//Fine tuning
		if (getYawDifference(yawcomparer) > 90) this.customYaw += 180;
	}
	public void updateYaw(BlockFace yawdirection) {
		this.updateYaw(FaceUtil.faceToYaw(yawdirection));
	}
	public void updateYaw() {
		this.updateYaw(this.yaw);
	}
	public void updateYawTo(MinecartMember head) {
		this.updateYaw(Util.getLookAtYaw(this, head));
	}
	public void updateYawFrom(MinecartMember tail) {
		this.updateYaw(Util.getLookAtYaw(tail, this));
	}
	
	/*
	 * States
	 */
 	public boolean hasMoved() {
 		return Math.abs(this.getMovedX()) > 0.001 || Math.abs(this.getMovedZ()) > 0.001;
 	}
	public boolean isMoving() {
 		return Math.abs(this.motX) > 0.001 || Math.abs(this.motZ) > 0.001;
	}
	public boolean isTurned() {
		float yaw = Math.abs(this.getYaw());
		return yaw == 45 || yaw == 135 || yaw == 225 || yaw == 315;
	}
	public boolean isDerailed() {
		return this.isDerailed;
	}
	public boolean isFlying() {
		return this.isFlying;
	}
	public boolean isHeadingTo(net.minecraft.server.Entity entity) {
		return this.isHeadingTo(entity.getBukkitEntity());
	}
	public boolean isHeadingTo(Entity entity) {
		return this.isHeadingTo(entity.getLocation());
	}
	public boolean isHeadingTo(ChunkCoordinates location) {
		return Util.isHeadingTo(this.getOffset(location), this.getVelocity());
		
	}
	public boolean isHeadingTo(Location target) {
		return Util.isHeadingTo(this.getLocation(), target, this.getVelocity());
	}
	public boolean isHeadingTo(BlockFace direction) {
		return Util.isHeadingTo(direction, this.getVelocity());
	}
	public boolean isHeadingToTrack(Block track) {
		return this.isHeadingToTrack(track, 0);
	}
	public boolean isHeadingToTrack(Block track, int maxstepcount) {
		Block from = this.getRailsBlock();
		if (BlockUtil.equals(from, track)) return true;
		if (maxstepcount == 0) maxstepcount = 1 + 2 * BlockUtil.getBlockSteps(from, track, false);
		TrackMap map = new TrackMap(from, this.getDirection());
		Block next;
		for (;maxstepcount > 0; --maxstepcount) {
			next = map.next();
			if (next == null) return false;
			if (BlockUtil.equals(next, track)) return true;
		}
		return false;
	}
	public boolean isFollowingOnTrack(MinecartMember member) {
		//checks if this member is able to follow the specified member on the tracks
		if (this.isDerailed || member.isDerailed) return true; //if derailed keep train alive
		if (!this.isNearOf(member)) return false;
		if (this.isMoving()) {
			Block memberrail = member.getRailsBlock();
			if (TrackMap.isConnected(this.getRailsBlock(), memberrail, true)) return true;
			return this.isHeadingToTrack(memberrail);
		} else {
			return TrackMap.isConnected(this.getRailsBlock(), member.getRailsBlock(), false);
		}
	}	
	public static boolean isTrackConnected(MinecartMember m1, MinecartMember m2) {
		//Can the minecart reach the other?
		boolean m1moving = m1.isMoving();
		boolean m2moving = m2.isMoving();
		if (m1moving && m2moving) {
			if (!m1.isFollowingOnTrack(m2) && !m2.isFollowingOnTrack(m1)) return false;
		} else if (m1moving) {
			if (!m1.isFollowingOnTrack(m2)) return false;
		} else if (m2moving) {
			if (!m2.isFollowingOnTrack(m1)) return false;
		} else {
			if (!m1.isNearOf(m2)) return false;
			if (!TrackMap.isConnected(m1.getRailsBlock(), m2.getRailsBlock(), false)) return false;
		}
		return true;
	}

	public boolean isOnSlope() {
		return this.railsloped;
	}
	public boolean isInChunk(Chunk chunk) {
		return this.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
	}
	public boolean isInChunk(org.bukkit.World world, int cx, int cz) {
		if (world != this.getWorld()) return false;
		if (Math.abs(cx - this.getChunkX()) > 2) return false;
		if (Math.abs(cz - this.getChunkZ()) > 2) return false;
	    return true;
	}
	public boolean isPoweredMinecart() {
		return this.type == 2;
	}
	public boolean isStorageMinecart() {
		return this.type == 1;
	}
	public boolean isRegularMinecart() {
		return this.type == 0;
	}
	public boolean hasPassenger() {
		return this.passenger != null;
	}
	public Entity getPassenger() {
		return this.passenger == null ? null : this.passenger.getBukkitEntity();
	}
	public boolean hasFuel() {
		return this.e > 0;
	}	
	public Inventory getInventory() {
		return new CraftInventory(this);
	}
	public boolean hasItem(ItemParser item) {
		if (item == null) return false;
		if (item.hasData()) {
			return this.hasItem(item.getTypeId(), item.getData());
		} else {
			return this.hasItem(item.getTypeId());
		}
	}
	public boolean hasItem(Material type, int data) {
		return this.hasItem(type.getId(), data);
	}
	public boolean hasItem(Material type) {
		return this.hasItem(type.getId());
	}
	public boolean hasItem(int typeid) {
		if (!this.isStorageMinecart()) return false;
		for (ItemStack stack : this.getContents()) {
			if (stack != null) {
				if (stack.id == typeid) {
					return true;
				}
			}
		}
		return false;
	}
	public boolean hasItem(int typeid, int data) {
		if (!this.isStorageMinecart()) return false;
		for (ItemStack stack : this.getContents()) {
			if (stack != null) {
				if (stack.id == typeid && stack.getData() == data) {
					return true;
				}
			}
		}
		return false;
	}
	public boolean hasItems() {
		if (!this.isStorageMinecart()) return false;
		for (ItemStack stack : this.getContents()) {
			if (stack != null) return true;
		}
		return false;
	}
		
	/*
	 * Actions
	 */
	public void pushSideways(Entity entity) {
		this.pushSideways(entity, TrainCarts.pushAwayForce);
	}
	public void pushSideways(Entity entity, double force) {
		float yaw = this.getYaw();
		float lookat = Util.getLookAtYaw(this.getBukkitEntity(), entity) - yaw;
		lookat = Util.normalAngle(lookat);
		if (lookat > 0) {
			yaw -= 180;
		}
		Vector vel = Util.getDirection(yaw, 0).multiply(force);
		entity.setVelocity(vel);
	}
	public void push(Entity entity, double force) {
		Vector offset = this.getOffset(entity);
		Util.setVectorLength(offset, force);
		entity.setVelocity(entity.getVelocity().add(offset));
	}
	public void playLinkEffect() {
		this.playLinkEffect(true);
	}
	public void playLinkEffect(boolean showSmoke) {
		Location loc = this.getLocation();
		if (showSmoke) loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
		loc.getWorld().playEffect(loc, Effect.EXTINGUISH, 0);
	}

	public boolean isCollisionIgnored(Entity entity) {
		return isCollisionIgnored(EntityUtil.getNative(entity));
	}
	public boolean isCollisionIgnored(net.minecraft.server.Entity entity) {
		if (entity instanceof MinecartMember) {
			return this.isCollisionIgnored((MinecartMember) entity);
		}
		return collisionIgnoreTimes.containsKey(entity.uniqueId);
	}
	public boolean isCollisionIgnored(MinecartMember member) {
		return this.collisionIgnoreTimes.containsKey(member.uniqueId) || 
				member.collisionIgnoreTimes.containsKey(this.uniqueId);
	}
	public void ignoreCollision(Entity entity, int time) {
		ignoreCollision(EntityUtil.getNative(entity), time);
	}
	public void ignoreCollision(net.minecraft.server.Entity entity, int ticktime) {
		collisionIgnoreTimes.put(entity.uniqueId, new AtomicInteger(ticktime));
	}
	public void eject() {
		this.getMinecart().eject();
	}
	public void eject(Vector offset) {
		if (this.passenger != null) {
			Entity passenger = this.passenger.getBukkitEntity();
			this.passenger.setPassengerOf(null);
			Task t = new Task(TrainCarts.plugin, passenger, offset) {
				public void run() {
					Entity e = (Entity) getArg(0);
					Vector offset = (Vector) getArg(1);
					e.teleport(e.getLocation().add(offset.getX(), offset.getY(), offset.getZ()));
				}
			};
			t.startDelayed(0);
		}
	}
	public boolean connect(MinecartMember with) {
		return this.getGroup().connect(this, with);
	}
	public void stop() {
		this.motX = 0;
		this.motY = 0;
		this.motZ = 0;
	}
	public void reverse() {
		this.motX *= -1;
		this.motY *= -1;
		this.motZ *= -1;
		this.b *= -1;
		this.c *= -1;
		this.customYaw = Util.normalAngle(this.customYaw + 180);
	}
	public void die() {
		super.die();
		replacedCarts.remove(this);
		this.clearActiveSigns();
		if (this.passenger != null) this.passenger.setPassengerOf(null);
		if (this.group != null) this.group.remove(this);
		if (this.properties != null) this.properties.remove();
	}

}