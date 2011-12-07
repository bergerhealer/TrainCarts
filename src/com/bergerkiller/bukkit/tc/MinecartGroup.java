package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;

import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent.ActionType;
import com.bergerkiller.bukkit.tc.Listeners.CustomEvents;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;

public class MinecartGroup extends ArrayList<MinecartMember> {
	private static final long serialVersionUID = 2;
	/*
	 * STATIC REGION
	 */
	private static HashSet<MinecartGroup> groups = new HashSet<MinecartGroup>();
	
	public static void unload(MinecartGroup group) {
		if (group == null) return;
		group.stop();
		for (MinecartMember mm : group) {
			MinecartMember.undoReplacement(mm);
		}
		group.clear();
		groups.remove(group);
	}
	public static MinecartGroup create(Entity... members) {
		return create(MinecartMember.convertAll(members));
	}
	public static MinecartGroup create(MinecartMember... members) {
		MinecartGroup g = new MinecartGroup();
		for (MinecartMember member : members) {
			if (member != null && member.isValidMember()) {
				g.add(member);
				//member.preUpdate();
			}
		}
		if (!g.isValid()) return null;
		groups.add(g);
		return g;
	}
	
	public static MinecartGroup spawn(Location[] at, int[] types, double forwardforce) {
		if (at.length != types.length || at.length == 0) return null;
		MinecartGroup g = new MinecartGroup();
		for (int i = 0; i < types.length; i++) {
			g.add(MinecartMember.spawn(at[i], types[i], forwardforce));
		}
		groups.add(g);
		return g;
	}
	public static MinecartGroup spawn(Block startblock, BlockFace direction, int[] types, double forwardforce) {
		ArrayList<Integer> typelist = new ArrayList<Integer>(types.length);
		for (int i : types) typelist.add(i);
		return spawn(startblock, direction, typelist, forwardforce);
	}
	public static MinecartGroup spawn(Block startblock, BlockFace direction, List<Integer> types, double forwardforce) {
		Location[] destinations = TrackMap.walk(startblock, direction, types.size(), TrainCarts.cartDistance);
		if (types.size() != destinations.length || destinations.length == 0) return null;
		MinecartGroup g = new MinecartGroup();
		for (int i = 0; i < destinations.length; i++) {
			g.add(MinecartMember.spawn(destinations[destinations.length - i - 1], types.get(i), forwardforce));
		}
		groups.add(g);
		return g;
	}
	
	public static MinecartGroup[] getGroups() {
		return groups.toArray(new MinecartGroup[0]);
	}
	public static MinecartGroup get(Entity e) {
		MinecartMember mm = MinecartMember.get(e);
		if (mm == null) return null;
		return mm.getGroup();
	}
	public static MinecartGroup get(String name) {
		for (MinecartGroup group : groups) {
			if (group.name == null) continue;
			if (group.name.equalsIgnoreCase(name)) return group;
		}
		return null;
	}
	
	public static boolean isInSameGroup(Object... minecarts) {
		return isInSameGroup(MinecartMember.getAll(minecarts));
	}
	public static boolean isInSameGroup(MinecartMember... minecarts) {
		for (int i = 0;i < minecarts.length - 1; i++) {
			if (minecarts[i] == null) return false;
			if (minecarts[i + 1] == null) return false;
			if (minecarts[i].getGroup() != minecarts[i + 1].getGroup()) return false;
		}
		return true;
	}
	
	public static boolean link(Minecart m1, Minecart m2) {
		if (m1 == m2) return false;
		if (m1.isDead()) return false;
		if (m2.isDead()) return false;
		return link(MinecartMember.convert(m1), MinecartMember.convert(m2));
	}
	public static boolean link(MinecartMember m1, MinecartMember m2) {
		if (m1 == null || m2 == null || m1 == m2) return false;
		MinecartGroup g1 = m1.getGroup();
		MinecartGroup g2 = m2.getGroup();
		if (m1.dead || m2.dead) return false;
		//max links per update
		if (g1 != g2) {
    		if (EntityUtil.isSharingRails(m1.getMinecart(), m2.getMinecart())) {
    			if (m1.isDerailed() || m2.isDerailed()) return false;
    			if (GroupManager.wasInGroup(m1.uniqueId)) return false;
    			if (GroupManager.wasInGroup(m2.uniqueId)) return false;
    			//Can the two groups bind?
    			TrainProperties prop1 = g1.getProperties();
    			TrainProperties prop2 = g2.getProperties();
    			if (!prop1.sharesOwner(prop2) || !prop1.allowLinking || !prop2.allowLinking) {
    				return false;
    			}
    			
    			//Is a powered minecart required?
    			if (prop1.requirePoweredMinecart || prop2.requirePoweredMinecart) {
    				if (g1.size(Material.POWERED_MINECART) == 0 && g2.size(Material.POWERED_MINECART) == 0) {
    					return false;
    				}
    			}
    			
				//append group1 before or after group2?
				int m1index = g1.indexOf(m1);
				int m2index = g2.indexOf(m2);
				
				//Validate
				if (!g2.canConnect(m1, m2index) || !g1.canConnect(m2, m1index)) {
					return false;
				}
    			
				//Validated, prepare for train merge
				
    			//Share owners and add to g2
    			ArrayList<String> newOwners = new ArrayList<String>();
    			for (String owner : prop1.owners) {
    				boolean allow = true;
    				for (String owner2 : prop2.owners) {
    					if (owner2.equalsIgnoreCase(owner)) {
    						allow = false;
    						break;
    					}
    				}
    				if (allow) newOwners.add(owner);
    			}
    		    			
    			//Transfer properties
    			if (g1.size() > g2.size()) {
    				g2.getProperties().load(g1.getProperties());
    			}
    			g2.getProperties().owners = newOwners;
    			
				//Finally link
				if (m1index == 0 && m2index == 0) {					
					g1.reverseOrder();
					g2.addAll(0, g1);
				} else if (m1index == 0 && m2index == g2.size() - 1) {
					g2.addAll(g1);
				} else if (m1index == g1.size() - 1 && m2index == 0) {
					g2.addAll(0, g1);
				} else {
					return false;
				}
				
				//Clear targets and active signs
				g1.clearActiveSigns();
				g2.clearActiveSigns();
				g2.clearTargets();
				
				//Re-activate the signs underneath the train
				for (MinecartMember mm : g2) {
					g2.setActiveSign(mm.getSignBlock(), true);
				}
				
				g1.remove();
				m2.playLinkEffect();
				return true;
    		}
		}
		return false;
	}

	public static void rename(String oldtrainname, String newtrainname) {
		for (MinecartGroup group : groups) {
			if (group.getName().equals(oldtrainname)) {
				group.setName(newtrainname);
				return;
			}
		}
		TrainProperties.get(oldtrainname).rename(newtrainname);
	}
	
    /*
     * NON-STATIC REGION
     */
	private HashSet<Block> activeSigns = new HashSet<Block>();
	private Queue<VelocityTarget> targets = new LinkedList<VelocityTarget>();
	private String name;
	private TrainProperties prop = null;
	
	private MinecartGroup() {}
		
	/*
	 * Name
	 */
	public String getName() {
		if (this.name == null) {
			for (int i = groups.size(); i < Integer.MAX_VALUE; i++) {
				if (!TrainProperties.exists("train" + i)) {
					this.name = "train" + i;
					break;
				}
			}
		}
		return this.name;
	}
	public void setName(String name) {
		if (name != null) {
			if (this.name == null) {
				this.prop = TrainProperties.get(name);
			} else if (!this.name.equals(name)) {
				this.prop = TrainProperties.get(this.name).rename(name);
			}
		} else if (this.prop != null) {
			this.prop.remove();
			this.prop = null;
		}
		this.name = name;
	}
	
	/*
	 * Properties
	 */
	public TrainProperties getProperties() {
		if (this.prop == null) {
			this.prop = TrainProperties.get(this.getName());
		}
		return this.prop;
	}
	public void setProperties(TrainProperties properties) {
		if (this.prop != null) {
			this.prop.remove();
		}
		this.prop = properties;
		if (this.prop != null) {
			this.name = this.prop.getTrainName();
			this.prop.add();
		} else {
			this.name = null;
		}
	}
	public void reinitProperties() {
		this.prop = null;
	}
	
	/*
	 * Targets
	 */
	public boolean hasTarget() {
		return this.targets.size() > 0;
	}
	public void clearTargets() {
		this.targets.clear();
	}
	public VelocityTarget addTarget(MinecartMember from, Location to,  double toVelocity, long delayMS) {
		return this.addTarget(new VelocityTarget(from, to, toVelocity, delayMS));
	}
	public VelocityTarget addTarget(VelocityTarget target) {
		this.targets.offer(target);
		return target;
	}
	public VelocityTarget getTarget() {
		if (hasTarget()) {
			return this.targets.peek();
		} else {
			return null;
		}
	}
	public VelocityTarget[] getTargets() {
		return this.targets.toArray(new VelocityTarget[0]);
	}
	public void setTargets(VelocityTarget... targets) {
		this.clearTargets();
		for (VelocityTarget target: targets) {
			this.targets.offer(target);
		}
	}
	public void updateTarget() {
		if (this.hasTarget() && this.getTarget().update()) {
			this.targets.remove();
		}
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
				CustomEvents.onSign(signblock, ActionType.GROUP_ENTER);
				return true;
			}
		} else {
			if (this.activeSigns.remove(b)) {
				CustomEvents.onSign(signblock, ActionType.GROUP_LEAVE);
				return true;
			}
		}
		return false;
	}
	public boolean setActiveSign(Block signblock, boolean active) {
		if (signblock == null) return false;
		return setActiveSign(new SignActionEvent(signblock, this), active);
	}
	public void clearActiveSigns() {
		for (Block signblock : this.activeSigns) {
			CustomEvents.onSign(new SignActionEvent(signblock, this), ActionType.GROUP_LEAVE);
		}
		this.activeSigns.clear();
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
	
	public MinecartMember[] sortOnIndex(Minecart... mm) {
		HashMap<Integer, MinecartMember> rval = new HashMap<Integer, MinecartMember>(mm.length);
		for (int i = 0;i < mm.length;i++) {
			int index = indexOf(mm[i]);
			rval.put(index, get(index));
		}
		return rval.values().toArray(new MinecartMember[0]);
	}
	
	public int indexOf(Object object) {
		MinecartMember mm = MinecartMember.get(object);
		if (mm == null) return -1;
		return super.indexOf(mm);
	}
	
	public void add(int index, MinecartMember member) {
		member.group = this;
		super.add(index, member);
	}
	public boolean add(MinecartMember member) {
		member.group = this;
		return super.add(member);
	}
	public boolean addAll(int index, Collection<? extends MinecartMember> members) {
		for (MinecartMember m : members) {
			m.group = this;
		}
		return super.addAll(index, members);
	}
	public boolean addAll(Collection<? extends MinecartMember> members) {
		for (MinecartMember m : members) {
			m.group = this;
		}
		return super.addAll(members);
	}
	
	public boolean contains(Object o) {
	    return super.contains(MinecartMember.get(o));
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
	
	public boolean remove(Object o) {
		int index = this.indexOf(o);
		if (index == -1) return false;
		return this.remove(index) != null;
	}
	public MinecartMember remove(int index) {
		MinecartMember removed = this.get(index);
		if (this.size() == 1) {
			//Simplified - remove cart and remove this group
			this.remove();
		} else {
			removed.playLinkEffect();
										
			//remove cart from group info
			this.setActiveSign(removed.getSignBlock(), false);
			removed.group = null;
			
			//transfer the new removed carts
			MinecartGroup gnew = new MinecartGroup();
			for (int i = 0;i < index;i++) {
				MinecartMember mm = super.remove(0);
				this.setActiveSign(mm.getSignBlock(), false);
				gnew.add(mm);
			}
			super.remove(0);
				
			//Remove if empty or not allowed, else add
			if (gnew.isValid()) {
				//Add the group
				groups.add(gnew);
								
				//Set the new active signs
				for (MinecartMember mm : gnew) {
					this.setActiveSign(mm.getSignBlock(), false);
					gnew.setActiveSign(mm.getSignBlock(), true);
				}
				
				//Set the new group properties
				gnew.getProperties().load(this.getProperties());
			} else {
				gnew.remove();
			}
						
			//Remove if empty
			if (!this.isValid()) {
				this.remove();
			}
		}
		return removed;
	}
	
	public void clear() {
		this.clearActiveSigns();
		for (MinecartMember mm : this) {
			if (mm.group == this) {
				mm.group = null;
				if (!mm.dead) {
					mm.getGroup().getProperties().load(this.getProperties());
				}
			}
		}
		super.clear();
	}
	
	public void remove() {
		this.clear();
		if (this.prop != null) {
			this.prop.remove();
			this.prop = null;
		}
		groups.remove(this);
	}
	public void destroy() {
		for (MinecartMember mm : this) mm.destroy(false);
		this.remove();
	}	
	public void stop() {
		for (MinecartMember m : this) {
			m.stop();
		}
	}
	public void limitSpeed() {
		for (MinecartMember mm : this) {
			mm.limitSpeed();
		}
	}
	public void setSpeedFactor(double factor) {
		for (MinecartMember mm : this) {
			mm.setForceFactor(factor);
		}
	}
	public void shareForce() {
		double f = this.getAverageForce();
		for (MinecartMember m : this) {
			m.setForwardForce(f);
		}
	}
	public void reverseOrder() {
		Collections.reverse(this);
	}
	public void reverse() {
		for (MinecartMember mm : this) {
			mm.reverse();
		}
		this.reverseOrder();
	}
	public void updateYaw() {
		if (this.size() == 1) {
			head().updateYaw(head().getDirection());
		} else if (this.size() > 1) {
			tail().updateYawTo(tail(1));
			for (int i = size() - 2;i >= 0;i--) {
				get(i).updateYawFrom(get(i + 1));
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
	
	public double getAverageForce() {
		if (size() == 0) return 0;
		if (size() == 1) return get(0).getForce();
		
		updateYaw();
		
		//Get the average forwarding force of all carts
		double force = 0;
		double fforce = 0;
		for (MinecartMember m : this) {
			double f = m.getForwardForce();
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
			reverseOrder();
		}
		return force;
	}
	public List<Integer> getTypes() {
		ArrayList<Integer> types = new ArrayList<Integer>(this.size());
		for (MinecartMember mm : this) types.add(mm.type);
		return types;
	}
	
	public boolean isMoving() {
		if (this.size() == 0) return false;
		return this.head().isMoving();
	}
	public boolean canUnload() {
		if (TrainCarts.keepChunksLoadedOnlyWhenMoving && !this.isMoving()) {
			return true;
		}
		return !this.getProperties().keepChunksLoaded;
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
	
	public void doPhysics() {
		try {
			double totalforce = this.getAverageForce();
			double speedlimit = this.getProperties().speedLimit;
			if (totalforce > 0.4 && speedlimit > 0.4) {
				int bits = (int) Math.ceil(speedlimit / 0.4);
				for (MinecartMember mm : this) {
					mm.motX /= (double) bits;
					mm.motY /= (double) bits;
					mm.motZ /= (double) bits;
				}
				for (int i = 0; i < bits; i++) {
					this.doPhysics(bits);
				}
				for (MinecartMember mm : this) {
					mm.motX *= (double) bits;
					mm.motY *= (double) bits;
					mm.motZ *= (double) bits;
					mm.maxSpeed = this.getProperties().speedLimit;
				}
			} else {
				this.doPhysics(1);
			}
		} catch (GroupUnloadedException ex) {
			//this group is gone
		} catch (Exception ex) {
			Util.log(Level.SEVERE, "Failed to perform physics on train '" + this.name + "':");
			ex.printStackTrace();
		}
	}
	private void doPhysics(int stepcount) throws GroupUnloadedException {
		//set max speed
		for (MinecartMember mm : this) mm.maxSpeed = this.getProperties().speedLimit / (double) stepcount;
		
		//Prevent index exceptions: remove if not a train
		if (this.size() == 1) {
			MinecartMember mm = this.head();
			//Validate this single member
			if (mm.isValidMember()) {
				this.updateTarget();
				mm.preUpdate();
				mm.updateYaw(mm.getDirection()); //Update the yaw (IS important!)
				mm.postUpdate(1);
				return;
			} else {
				this.remove();
				throw new GroupUnloadedException();
			}
		} else if (this.size() == 0) {
			this.remove();
			throw new GroupUnloadedException();
		}

		//Validate members
		for (MinecartMember mm : this) {
			if (!mm.isValidMember()) {
				if (this.remove(mm)) {
					this.doPhysics(stepcount);
				}
				return;
			}
		}	
		//Positions in the group
		for (int i = 0; i < this.size() - 1; i++) {
			if (!head(i).isNearOf(head(i + 1))) {
				//Ow no! this is bad!
				this.remove(i);
				this.doPhysics(stepcount);
				return;
			}
		}

		//pre-update
		this.updateTarget();
		for (MinecartMember m : this) {
			m.preUpdate();
		}
		//Get the average forwarding force of all carts
		double force = this.getAverageForce();

		//update force
		for (MinecartMember m : this) {
			m.setForwardForce(force);
		}

		//Apply force factors to carts from last cart and perform post positional updates
		this.tail().postUpdate(1);
		for (int i = size() - 2;i >= 0;i--) {
			double distance = get(i).distanceXZ(get(i + 1));
			double threshold = 0;
			double forcer = 1;
			if (get(i).getYawDifference(get(i + 1).getYaw()) > 10 || get(i).getPitchDifference(get(i + 1)) > 10) {
				threshold = TrainCarts.turnedCartDistance;
				forcer = TrainCarts.turnedCartDistanceForcer;
			} else {
				threshold = TrainCarts.cartDistance;
				forcer = TrainCarts.cartDistanceForcer;
			}
			if (distance < threshold) forcer *= TrainCarts.nearCartDistanceFactor;
			this.get(i).postUpdate(1 + (forcer * (threshold - distance)));
		}
	}
		
}
