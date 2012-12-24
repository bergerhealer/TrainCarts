package com.bergerkiller.bukkit.tc.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.inventory.MergedInventory;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.*;
import com.bergerkiller.bukkit.tc.detector.DetectorRegion;
import com.bergerkiller.bukkit.tc.events.GroupCreateEvent;
import com.bergerkiller.bukkit.tc.events.GroupRemoveEvent;
import com.bergerkiller.bukkit.tc.events.GroupUnloadEvent;
import com.bergerkiller.bukkit.tc.events.MemberAddEvent;
import com.bergerkiller.bukkit.tc.events.MemberRemoveEvent;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.TrackWalkIterator;

public class MinecartGroup extends MinecartGroupStore {
	private static final long serialVersionUID = 3;

	private Map<IntVector3, MinecartMember> memberBlockSpace = new HashMap<IntVector3, MinecartMember>();
	private final Set<Block> activeSigns = new LinkedHashSet<Block>();
	private final Queue<Action> actions = new LinkedList<Action>();
	private static Set<DetectorRegion> tmpRegions = new HashSet<DetectorRegion>();
	private TrainProperties prop = null;
	private boolean breakPhysics = false;
	private boolean needsUpdate = false;
	private boolean needsSignRefresh = true;
	protected long lastSync = Long.MIN_VALUE;

	protected MinecartGroup() {}

	/*
	 * Properties
	 */
	public TrainProperties getProperties() {
		if (this.prop == null) {
			this.prop = TrainPropertiesStore.create();
			for (MinecartMember member : this) {
				this.prop.add(member);
			}
		}
		return this.prop;
	}

	public void setProperties(TrainProperties properties) {
		if (properties == null) {
			throw new IllegalArgumentException("Can not set properties to null");
		}
		if (this.prop != null) {
			TrainPropertiesStore.remove(this.prop.getTrainName());
		}
		this.prop = properties;
	}

	/*
	 * Actions
	 */
	public boolean hasAction() {
		return this.actions.size() > 0;
	}
	public void clearActions() {
		this.actions.clear();
	}
	public Action removeAction() {
		return this.actions.remove();
	}
	public <T extends Action> T addAction(T action) {
		this.actions.offer(action);
		return action;
	}
	public Action getCurrentAction() {
		if (this.hasAction()) {
			return this.actions.peek();
		} else {
			return null;
		}
	}
	private void updateAction() {
		if (!this.hasAction()) return;
		if (this.actions.peek().doTick()) {
			this.actions.remove();
			this.updateAction();
		}
	}
	public GroupActionWait addActionWait(long delay) {
		return this.addAction(new GroupActionWait(this, delay));
	}
	public GroupActionWaitTill addActionWaitTill(long time) {
		return this.addAction(new GroupActionWaitTill(this, time));
	}
	public GroupActionWaitTicks addActionWaitTicks(int ticks) {
		return this.addAction(new GroupActionWaitTicks(this, ticks));
	}
	public GroupActionWaitForever addActionWaitForever() {
		return this.addAction(new GroupActionWaitForever(this));
	}
	public GroupActionWaitState addActionWaitState() {
		return this.addAction(new GroupActionWaitState(this));
	}
	public GroupActionSizzle addActionSizzle() {
		return this.addAction(new GroupActionSizzle(this));
	}
	public GroupActionRefill addActionRefill() {
		return this.addAction(new GroupActionRefill(this));
	}
	public boolean isWaitAction() {
		Action a = this.actions.peek();
		return a == null ? false : a instanceof WaitAction;
	}
	public boolean isVelocityAction() {
		Action a = this.actions.peek();
		return a == null ? false : a instanceof VelocityAction && ((VelocityAction) a).isVelocityChangesSuppressed();
	}

	/*
	 * Signs underneath this group
	 */
	public boolean isActiveSign(Block signblock) {
		if (signblock == null) return false;
		return this.activeSigns.contains(signblock);
	}
	public boolean setActiveSign(SignActionEvent signblock, boolean active) {
		Block b = signblock.getBlock();
		if (b == null) return false;
		if (active) {
			if (this.activeSigns.add(b)) {
				SignAction.executeAll(signblock, SignActionType.GROUP_ENTER);
				return true;
			}
		} else {
			if (this.activeSigns.remove(b)) {
				SignAction.executeAll(signblock, SignActionType.GROUP_LEAVE);
				return true;
			}
		}
		return false;
	}
	public boolean setActiveSign(Block signblock, boolean active) {
		if (signblock == null) return false;
		return setActiveSign(new SignActionEvent(signblock, null, this), active);
	}
	public void updateActiveSigns() {
		this.needsSignRefresh = false;
		World world = this.getWorld();
		for (Map.Entry<IntVector3, MinecartMember> entry : this.memberBlockSpace.entrySet()) {
			IntVector3 p = entry.getKey();
			Block block = world.getBlockAt(p.x, p.y, p.z);
			if (Util.ISTCRAIL.get(block)) {
				for (Block sign : Util.getSignsFromRails(block)) {
					entry.getValue().addActiveSign(sign);
				}
			}
		}
	}

	/**
	 * Clears all the signs this group is currently in<br>
	 * <b>This includes the signs individual members are in</b>
	 */
	public void clearActiveSigns() {
		clearActiveSigns(true);
	}

	/**
	 * Clears all the signs this group is currently in<br>
	 * 
	 * @param clearMembers state, True to clear member signs, False to keep them
	 */
	public void clearActiveSigns(boolean clearMembers) {
		for (Block signblock : this.activeSigns) {
			SignAction.executeAll(new SignActionEvent(signblock, null, this), SignActionType.GROUP_LEAVE);
		}
		this.activeSigns.clear();
		if (clearMembers) {
			for (MinecartMember mm : this) {
				mm.clearActiveSigns();
			}
		}
	}

	public MinecartMember head(int index) {
		return this.get(index);
	}
	public MinecartMember head() {
		return this.head(0);
	}
	public MinecartMember tail(int index) {
		return this.get(this.size() - 1 - index);
	}
	public MinecartMember tail() {
		return this.tail(0);
	}
	public MinecartMember middle() {
		return this.get((int) Math.floor((double) size() / 2));
	}

	public MinecartMember[] toArray() {
		return super.toArray(new MinecartMember[0]);
	}

	public boolean connect(MinecartMember contained, MinecartMember with) {
		if (this.size() <= 1) {
			this.add(with);
		} else if (this.head() == contained && this.canConnect(with, 0)) {
			this.add(0, with);
		} else if (this.tail() == contained && this.canConnect(with, this.size() - 1)) {
			this.add(with);
		} else {
			return false;
		}
		return true;
	}

	public int indexOf(Object object) {
		MinecartMember mm = MinecartMember.get(object);
		if (mm == null) return -1;
		return super.indexOf(mm);
	}

	private void addMember(MinecartMember member) {
		member.setGroup(this);
		for (Block sign : member.getActiveSigns()) {
			this.setActiveSign(sign, true);
		}
		this.updateBlockSpace();
		this.getProperties().add(member);
	}
	public void add(int index, MinecartMember member) {
		super.add(index, member);
		MemberAddEvent.call(member, this);
		this.addMember(member);
	}
	public boolean add(MinecartMember member) {
		super.add(member);
		MemberAddEvent.call(member, this);
		this.addMember(member);
		return true;
	}
	public boolean addAll(int index, Collection<? extends MinecartMember> members) {
		super.addAll(index, members);
		MinecartMember[] memberArr = members.toArray(new MinecartMember[0]);
		for (MinecartMember m : memberArr) {
			MemberAddEvent.call(m, this);
		}
		for (MinecartMember member : memberArr) {
			this.addMember(member);
		}
		return true;
	}
	public boolean addAll(Collection<? extends MinecartMember> members) {
		super.addAll(members);
		MinecartMember[] memberArr = members.toArray(new MinecartMember[0]);
		for (MinecartMember m : memberArr) {
			MemberAddEvent.call(m, this);
		}
		for (MinecartMember member : memberArr) {
			this.addMember(member);
		}
		return true;
	}

	public boolean contains(Object o) {
		return super.contains(MinecartMember.get(o));
	}
	public boolean containsIndex(int index) {
		return this.isEmpty() ? false : index >= 0 && index < this.size();
	}

	public World getWorld() {
		if (this.size() == 0) return null;
		return this.get(0).getWorld();
	}

	public double length() {
		return TrainCarts.cartDistance * (this.size() - 1);
	}
	public int size(Material carttype) {
		switch (carttype) {
		case STORAGE_MINECART : return this.size(1);
		case POWERED_MINECART : return this.size(2);
		default : return this.size(0);
		}
	}
	public int size(int carttype) {
		int rval = 0;
		for (MinecartMember mm : this) {
			if (mm.type == carttype) rval++;
		}
		return rval;
	}

	public boolean isValid() {
		if (this.size() == 0) return false;
		if (this.size() == 1) return true;
		if (this.getProperties().requirePoweredMinecart) {
			return this.size(Material.POWERED_MINECART) > 0;
		} else {
			return true;
		}
	}

	/**
	 * Removes a member without splitting the train or causing link effects
	 * 
	 * @param member to remove
	 * @return True if removed, False if not
	 */
	public boolean removeSilent(MinecartMember member) {
		int index = this.indexOf(member);
		if (index == -1) {
			return false;
		}
		this.removeMember(index);
		if (this.isEmpty()) {
			this.remove();
		}
		return true;
	}
	public boolean remove(Object o) {
		int index = this.indexOf(o);
		if (index == -1) return false;
		return this.remove(index) != null;
	}
	private MinecartMember removeMember(int index) {
		MinecartMember member = super.get(index);
		MemberRemoveEvent.call(member);
		//Delete the member if dead, otherwise remove active signs from this group only
		if (member.dead) {
			//added Bukkit vehicle destroy event
			member.clearActiveSigns();
		} else {
			Set<Block> toRemove = new HashSet<Block>();
			toRemove.addAll(member.getActiveSigns());
			for (MinecartMember mm : this) {
				if (mm != member) toRemove.removeAll(mm.getActiveSigns());
			}
			for (Block sign : toRemove) {
				this.setActiveSign(sign, false);
			}
		}
		super.remove(index);
		this.getProperties().remove(member);
		this.updateBlockSpace();
		Action a;
		for (Iterator<Action> actionit = this.actions.iterator(); actionit.hasNext();) {
			a = actionit.next();
			if (a instanceof MemberAction) {
				if (((MemberAction) a).getMember() == member) {
					actionit.remove();
				}
			}
		}
		member.group = null;
		return member;
	}
	public MinecartMember remove(int index) {
		MinecartMember removed = this.removeMember(index);
		if (this.isEmpty()) {
			//Remove empty group as a result
			this.remove();
		} else {
			//Split the train at the index
			removed.playLinkEffect();
			this.split(index);
		}
		return removed;
	}

	/**
	 * Splits this train, the index is the first cart for the new group<br><br>
	 * 
	 * For example, this Group has a total cart count of 5<br>
	 * If you then split at index 2, it will result in:<br>
	 * - This group becomes a group of 2 carts<br>
	 * - A new group of 3 carts is created
	 */
	public MinecartGroup split(int at) {
		if (at <= 0) return this;
		if (at >= this.size()) return null;
		//transfer the new removed carts
		MinecartGroup gnew = new MinecartGroup();
		int count = this.size();
		for (int i = at; i < count; i++) {
			gnew.add(this.removeMember(this.size() - 1));
		}
		//Remove this train if now empty
		if (!this.isValid()) {
			this.remove();
		}
		//Remove if empty or not allowed, else add
		if (gnew.isValid()) {
			//Add the group
			groups.add(gnew);

			//Set the new active signs
			for (MinecartMember mm : gnew) {
				for (Block sign : mm.getActiveSigns()) {
					gnew.setActiveSign(sign, true);
				}
			}

			//Set the new group properties
			gnew.getProperties().load(this.getProperties());

			GroupCreateEvent.call(gnew);
			return gnew;
		} else {
			gnew.clear();
			return null;
		}
	}

	public void clear() {
		this.clearActiveSigns();
		this.clearActions();
		for (MinecartMember mm : this.toArray()) {
			this.getProperties().remove(mm);
			if (mm.dead) {
				mm.die();
			} else {
				mm.group = null;
				mm.getGroup().getProperties().load(this.getProperties());
			}
		}
		super.clear();
	}
	public void remove() {
		if (!groups.remove(this)) {
			return; // Already removed
		}
		GroupRemoveEvent.call(this);
		this.clear();
		if (this.prop != null) {
			TrainPropertiesStore.remove(this.prop.getTrainName());
			this.prop = null;
		}
	}
	public void destroy() {
		for (MinecartMember mm : this) {
			mm.dead = true;
		}
		this.remove();
	}
	public void unload() {
		GroupUnloadEvent.call(this);
		this.stop(true);
		groups.remove(this);
		for (MinecartMember member : this) {
			member.group = null;
			member.unloaded = true;
		}
	}

	/**
	 * Visually respawns this minecart to avoid teleportation smoothing
	 */
	public void respawn() {
		for (MinecartMember mm : this) {
			mm.respawn();
		}
	}
	public void playLinkEffect() {
		for (MinecartMember mm : this) {
			mm.playLinkEffect();
		}
	}
	public void stop() {
		this.stop(false);
	}
	public void stop(boolean cancelLocationChange) {
		for (MinecartMember m : this) {
			m.stop(cancelLocationChange);
		}
	}
	public void limitSpeed() {
		for (MinecartMember mm : this) {
			mm.limitSpeed();
		}
	}
	public void eject() {
		for (MinecartMember mm : this) mm.eject();
	}

	/**
	 * A simple version of teleport where the inertia of the train is maintained
	 */
	public void teleportAndGo(Block start, BlockFace direction) {
		double force = this.getAverageForce();
		this.teleport(start, direction);
		this.stop();
		this.clearActions();
		if (force > 0.01 || force < -0.01) {
			this.tail().addActionLaunch(direction, 1.0, force);
		}
	}

	public void teleport(Block start, BlockFace direction) {
		this.teleport(start, direction, TrainCarts.cartDistance);
	}
	public void teleport(Block start, BlockFace direction, double stepsize) {
		this.teleport(TrackWalkIterator.walk(start, direction, this.size(), stepsize), true);
	}
	public void teleport(Location[] locations) {
		this.teleport(locations, false);
	}
	public void teleport(Location[] locations, boolean reversed) {
		if (locations == null || locations.length == 0 || locations.length != this.size()) {
			return;
		}
		this.needsSignRefresh = true;
		boolean needAvoidSmoothing = locations[0].getWorld() == this.getWorld();
		this.clearActiveSigns();
		this.breakPhysics();
		if (reversed) {
			for (int i = 0; i < locations.length; i++) {
				this.get(i).teleport(locations[locations.length - i - 1]);
			}
		} else {
			for (int i = 0; i < locations.length; i++) {
				this.get(i).teleport(locations[i]);
			}
		}
		if (needAvoidSmoothing) {
			this.respawn();
		}
	}

	public void shareForce() {
		double f = this.getAverageForce();
		for (MinecartMember m : this) {
			m.setForwardForce(f);
		}
	}
	public void reverse() {
		for (MinecartMember mm : this) {
			mm.reverse();
		}
		Collections.reverse(this);
	}
	public void setForwardForce(double force) {
		final double currvel = this.head().getForce();
		if (currvel <= 0.01 || Math.abs(force) < 0.01) {
			for (MinecartMember mm : this) {
				mm.setForwardForce(force);
			}
		} else {
			final double f = force / currvel;
			for (MinecartMember mm : this) {
				mm.setForceFactor(f);
			}
		}

	}

	public boolean canConnect(MinecartMember mm, int at) {
		if (this.size() == 1) return true;
		if (this.size() == 0) return false;
		if (at == 0) {
			//compare the head
			return this.head().isNearOf(mm);
		} else if (at == this.size() - 1) {
			//compare the tail
			return this.tail().isNearOf(mm);
		} else {
			return false;
		}
	}
	public void updateDirection() {
		if (this.size() == 1) {
			this.get(0).updateDirection();
		} else if (this.size() > 1) {
			//Update yaw from other cart
			tail().updateDirectionTo(tail(1));
			for (int i = size() - 2;i >= 0;i--) {
				get(i).updateDirectionFrom(get(i + 1));
			}
		}
	}
	public double getAverageForce() {
		if (this.isEmpty()) return 0;
		if (this.size() == 1) return this.get(0).getForce();
		//Get the average forwarding force of all carts
		double force = 0;
		double fforce = 0;
		double f;
		for (MinecartMember m : this) {
			f = m.getForwardForce();
			fforce += f;
			if (f < 0) {
				force -= m.getForce();
			} else {
				force += m.getForce();
			}
		}
		force /= size();
		//Reverse
		if (fforce < 0) {
			Collections.reverse(this);
			force = -force;
		}
		return force;
	}
	public List<Integer> getTypes() {
		ArrayList<Integer> types = new ArrayList<Integer>(this.size());
		for (MinecartMember mm : this) {
			types.add(mm.type);
		}
		return types;
	}

	public boolean hasPassenger() {
		for (MinecartMember mm : this) {
			if (mm.hasPassenger()) return true;
		}
		return false;
	}
	public boolean hasFuel() {
		for (MinecartMember mm : this) {
			if (mm.hasFuel()) return true;
		}
		return false;
	}
	public boolean hasItems() {
		for (MinecartMember mm : this) {
			if (mm.hasItems()) return true;
		}
		return false;
	}
	public boolean hasItem(ItemParser item) {
		for (MinecartMember mm : this) {
			if (mm.hasItem(item)) return true;
		}
		return false;
	}
	public boolean isMoving() {
		if (this.isEmpty()) {
			return false;
		} else {
			return this.head().isMoving();
		}
	}

	/**
	 * Checks if this Minecart Group can unload, or if chunks are kept loaded instead<br>
	 * The keepChunksLoaded property is read, as well the moving state if configured<br>
	 * If a player is inside the train, it will keep the chunks loaded as well
	 * 
	 * @return True if it can unload, False if it keeps chunks loaded
	 */
	public boolean canUnload() {
		if (this.getProperties().isKeepingChunksLoaded()) {
			if (!TrainCarts.keepChunksLoadedOnlyWhenMoving || this.isMoving()) {
				return false;
			}
		}
		for (MinecartMember member : this) {
			if (member.hasPlayerPassenger()) {
				return false;
			}
		}
		return true;
	}
	public boolean isRemoved() {
		return !groups.contains(this);
	}

	public Inventory getInventory() {
		//count amount of storage minecarts
		Inventory[] source = new Inventory[this.size(Material.STORAGE_MINECART)];
		int i = 0;
		for (MinecartMember mm : this) {
			if (mm.isStorageCart()) {
				source[i] = mm.getInventory();
				i++;
			}
		}
		if (source.length == 1) {
			return source[0];
		}
		return new MergedInventory(source);
	}
	public Inventory getPlayerInventory() {
		//count amount of player passengers
		int count = 0;
		for (MinecartMember mm : this) {
			if (mm.hasPlayerPassenger()) {
				count++;
			}
		}
		Inventory[] source = new Inventory[count];
		if (source.length == 1) {
			return source[0];
		}
		int i = 0;
		for (MinecartMember mm : this) {
			if (mm.hasPlayerPassenger()) {
				source[i] = mm.getPlayerInventory();
				i++;
			}
		}
		return new MergedInventory(source);
	}

	public void loadChunks() {
		for (MinecartMember mm : this) mm.loadChunks();
	}

	public boolean isInChunk(Chunk chunk) {
		return this.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
	}
	public boolean isInChunk(World world, int cx, int cz) {
		for (MinecartMember mm : this) {
			if (mm.isInChunk(world, cx, cz)) return true;
		}
		return false;
	}

	/**
	 * Aborts any physics routines going on in this tick
	 */
	public void breakPhysics() {
		this.breakPhysics = true;
	}

	public void update() {
		this.needsUpdate = true;
	}

	/*
	 * These two overrides ensure that sets use this MinecartGroup properly
	 * Without it, the AbstractList versions were used, which don't apply here
	 */
	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}

	@Override
	public boolean equals(Object other) {
		return other == this;
	}

	/**
	 * Gets the Member at the block position specified
	 * 
	 * @param position to get the member at
	 * @return member at the position, or null if not found
	 */
	public MinecartMember getAt(IntVector3 position) {
		return this.memberBlockSpace.get(new IntVector3(position.x, position.y, position.z));
	}

	/**
	 * Updates the member block space mapping of this group
	 */
	public void updateBlockSpace() {
		// Update block space of minecarts in this group
		this.memberBlockSpace.clear();
		if (this.size() == 1) {
			MinecartMember member = head();
			this.memberBlockSpace.put(member.getBlockPos(), member);
		} else if (this.size() > 1) {
			for (int i = 0; i < this.size() - 1; i++) {
				MinecartMember member = get(i);
				IntVector3 from = member.getBlockPos();
				IntVector3 to = get(i + 1).getBlockPos();
				this.memberBlockSpace.put(from, member);
				if (to.x > from.x + 1) {
					this.memberBlockSpace.put(new IntVector3(from.x + 1, from.y, from.z), member);
				} else if (to.x + 1 < from.x) {
					this.memberBlockSpace.put(new IntVector3(from.x - 1, from.y, from.z), member);
				}
				if (to.y > from.y + 1) {
					this.memberBlockSpace.put(new IntVector3(from.x, from.y + 1, from.z), member);
				} else if (to.y + 1 < from.y) {
					this.memberBlockSpace.put(new IntVector3(from.x, from.y - 1, from.z), member);
				}
				if (to.z > from.z + 1) {
					this.memberBlockSpace.put(new IntVector3(from.x, from.y, from.z + 1), member);
				} else if (to.z + 1 < from.z) {
					this.memberBlockSpace.put(new IntVector3(from.x, from.y, from.z - 1), member);
				}
			}
			this.memberBlockSpace.put(tail().getBlockPos(), tail());
		}
	}
	
	public void doPhysics() {
		try {
			double totalforce = this.getAverageForce();
			double speedlimit = this.getProperties().getSpeedLimit();
			if (totalforce > 0.4 && speedlimit > 0.4) {
				int bits = (int) Math.ceil(speedlimit / 0.4);
				for (MinecartMember mm : this) {
					mm.motX /= (double) bits;
					mm.motY /= (double) bits;
					mm.motZ /= (double) bits;
				}
				for (int i = 0; i < bits; i++) {
					while (!this.doPhysics(bits));
				}
				for (MinecartMember mm : this) {
					mm.motX *= (double) bits;
					mm.motY *= (double) bits;
					mm.motZ *= (double) bits;
					mm.maxSpeed = this.getProperties().getSpeedLimit();
				}
			} else {
				this.doPhysics(1);
			}
		} catch (GroupUnloadedException ex) {
			//this group is gone
		} catch (Exception ex) {
			TrainCarts.plugin.log(Level.SEVERE, "Failed to perform physics on train '" + this.getProperties().getTrainName() + "':");
			ex.printStackTrace();
		} catch (Throwable t) {
			TrainCarts.plugin.handle(t);
		}
	}
	private boolean doPhysics(int stepcount) throws GroupUnloadedException {
		this.breakPhysics = false;
		try {			
			//Prevent index exceptions: remove if not a train
			if (this.isEmpty()) {
				this.remove();
				throw new GroupUnloadedException();
			}

			//validate members and set max speed
			for (MinecartMember mm : this) {
				mm.checkMissing();
				mm.maxSpeed = this.getProperties().getSpeedLimit() / (double) stepcount;
			}

			// Update direction and executed actions prior to updates
			this.updateDirection();
			this.updateAction();

			// Perform block updates prior to doing the movement calculations
			boolean blockChanged = false;
			for (MinecartMember m : this) {
				m.onPhysicsStart();
				blockChanged |= m.hasBlockChanged();
			}
			if (blockChanged) {
				this.updateBlockSpace();
			}
			for (MinecartMember m : this) {
				m.onPhysicsBlockChange();
			}
			if (this.needsSignRefresh) {
				this.updateActiveSigns();
			}

			this.updateDirection();

			// Perform velocity updates
			for (MinecartMember m : this) {
				m.onPhysicsPreMove();
			}

			if (this.size() == 1) {
				//Simplified calculation for single carts
				this.updateDirection();
				this.head().onPhysicsPostMove(1);
				this.updateDirection();
			} else {
				//Get the average forwarding force of all carts
				double force = this.getAverageForce();
				this.updateDirection();

				//Perform forward force or not? First check if we are not messing up...
				boolean performUpdate = true;
				for (int i = 0; i < this.size() - 1; i++) {
					if (!head(i + 1).isFollowingOnTrack(head(i))) {
						performUpdate = false;
						break;
					}
				}

				if (performUpdate) {
					//update force
					for (MinecartMember m : this) {
						m.setForwardForce(force);
					}
				}

				//Apply force factors to carts from last cart and perform post positional updates
				if (this.size() < 2) return false;
				try {
					int i = 1;
					double distance, threshold, forcer;
					MinecartMember after;
					for (MinecartMember member : this) {
						after = this.get(i);
						distance = member.distance(after);
						if (member.getDirectionDifference(after) >= 45 || member.getPitchDifference(after) > 10) {
							threshold = TrainCarts.turnedCartDistance;
							forcer = TrainCarts.turnedCartDistanceForcer;
						} else {
							threshold = TrainCarts.cartDistance;
							forcer = TrainCarts.cartDistanceForcer;
						}
						if (distance < threshold) {
							forcer *= TrainCarts.nearCartDistanceFactor;
						}
						member.onPhysicsPostMove(1 + (forcer * (threshold - distance)));
						if (this.breakPhysics) return true;
						if (i++ == this.size() - 1) {
							this.tail().onPhysicsPostMove(1);
							if (this.breakPhysics) return true;
							break;
						}
					}
				} catch (ConcurrentModificationException ex) {
					return true;
				}

				//Update order after position change
				this.getAverageForce();

				//update yaw and then the positions
				this.updateDirection();

				//Validate positions in the group
				for (int i = 0; i < this.size() - 1; i++) {
					if (!head(i + 1).isFollowingOnTrack(head(i))) {
						for (int j = i + 1; j < this.size(); j++) {
							this.get(j).setForceFactor(stepcount);
						}
						MinecartGroup gnew = this.split(i + 1);
						if (gnew != null) { 
							//what time do we want to prevent them from colliding too soon?
							//needs to travel 2 blocks in the meantime
							int time = (int) MathUtil.clamp(2 / gnew.head().getForce(), 20, 40);
							for (MinecartMember mm1 : gnew) {
								for (MinecartMember mm2: this) {
									mm1.ignoreCollision(mm2, time);
								}
							}
						}
						return false;
					}
				}
			}

			//still in loaded chunks?
			boolean canunload = this.canUnload();
			for (MinecartMember mm : this) {
				mm.checkChunks(canunload);
			}

			if (this.needsUpdate) {
				this.needsUpdate = false;
				for (Block b : this.activeSigns) {
					SignAction.executeAll(new SignActionEvent(b, null, this), SignActionType.GROUP_UPDATE);
				}
				tmpRegions.clear();
				for (MinecartMember mm : this) {
					tmpRegions.addAll(mm.getActiveDetectorRegions());
				}
				for (DetectorRegion reg : tmpRegions) {
					reg.update(this);
				}
			}
			return true;
		} catch (MemberMissingException ex) {
			return false;
		}
	}

}
