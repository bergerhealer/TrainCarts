package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import net.minecraft.server.EntityMinecart;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;

import com.bergerkiller.bukkit.tc.API.ForceUpdateEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent;
import com.bergerkiller.bukkit.tc.API.SignActionEvent.ActionType;
import com.bergerkiller.bukkit.tc.Listeners.CustomEvents;
import com.bergerkiller.bukkit.tc.Utils.EntityUtil;
import com.bergerkiller.bukkit.tc.Utils.FaceUtil;

public class MinecartGroup extends ArrayList<MinecartMember> {
	private static final long serialVersionUID = -1843478291169071830L;
	/*
	 * STATIC REGION
	 */
	private static HashSet<MinecartGroup> groups = new HashSet<MinecartGroup>();
	public static boolean isDisabled = false;
		
	public static void updateGroups() {				
		for (MinecartGroup mg : getGroups()) {
			//Remove dead carts and lonely groups caused by this
			if (mg.isValid()) {
				for (MinecartMember mm : mg.toArray()) {
					if (mm.dead || (TrainCarts.removeDerailedCarts && mm.isDerailed())) {
						mm.remove();
					}
				}
			}
			if (!mg.isValid()) {
				mg.remove();
				continue;
			}
			//Unloaded chunk handling
			SimpleChunk[] unloaded = mg.getNearChunks(false, true);
			if (unloaded.length > 0) {
				if (TrainCarts.keepChunksLoaded) {
					for (SimpleChunk c : unloaded) c.load();
				} else {
					GroupManager.hideGroup(mg);
				}
			}
		}
	}
		
	public static void unload(MinecartGroup group) {
		if (group == null) return;
		group.stop();
		for (MinecartMember mm : group) {
			MinecartMember.undoReplacement(mm);
		}
	    group.clear();
		groups.remove(group);
	}
	public static MinecartGroup create() {
		return new MinecartGroup();
	}
	public static MinecartGroup create(Entity... members) {
		return create(MinecartMember.convertAll(members));
	}
	public static MinecartGroup create(MinecartMember... members) {
		MinecartGroup g = new MinecartGroup();
		for (MinecartMember member : members) {
			if (!member.dead) {
				g.add(member);
				member.preUpdate();
			}
		}
		//handle events
		for (MinecartMember mm : g) {
			mm.updateActiveSign();
		}
		return g;
	}
	
	public static MinecartGroup[] getGroups() {
		MinecartGroup[] rval = new MinecartGroup[groups.size()];
		int i = 0;
		for (MinecartGroup group : groups) {
			rval[i] = group;
			i++;
		}
		return rval;
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
		if (isDisabled) return false;
		if (m1.isDead()) return false;
		if (m2.isDead()) return false;
		return link(MinecartMember.convert(m1), MinecartMember.convert(m2));
	}
	public static boolean link(MinecartMember m1, MinecartMember m2) {
		if (isDisabled) return false;
		if (m1 == null || m2 == null) return false;
		MinecartGroup g1 = m1.getGroup();
		MinecartGroup g2 = m2.getGroup();
		if (g1 != g2) {
    		if (EntityUtil.isSharingRails(m1.getMinecart(), m2.getMinecart())) {
    			if (m1.dead || m1.isDerailed()) return false;
    			if (m2.dead || m2.isDerailed()) return false;
    			if (GroupManager.wasInGroup(m1.getMinecart())) return false;
    			if (GroupManager.wasInGroup(m2.getMinecart())) return false;		
    		    		
    			//Can the two groups bind?
    			TrainProperties prop1 = g1.getProperties();
    			TrainProperties prop2 = g2.getProperties();
    			if (!prop1.sharesOwner(prop2) || !prop1.allowLinking || !prop2.allowLinking) {
    				return false;
    			}
    			
    			//Is a powered minecart required?
    			if (prop1.requirePoweredMinecart || prop2.requirePoweredMinecart) {
    				if (g1.getCartCount(Material.POWERED_MINECART) == 0 && g2.getCartCount(Material.POWERED_MINECART) == 0) {
    					return false;
    				}
    			}
    			
    			//Share owners and add to g2
    			for (String owner : prop1.owners) {
    				boolean allow = true;
    				for (String owner2 : prop2.owners) {
    					if (owner2.equalsIgnoreCase(owner)) {
    						allow = false;
    						break;
    					}
    				}
    				if (allow) prop2.owners.add(owner);
    			}
    			    			
				//append group1 before or after group2?
				int m1index = g1.indexOf(m1);
				int m2index = g2.indexOf(m2);	
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
				g1.activeSigns.clear();
				g2.activeSigns.clear();
				g2.clearTargets();
				
				//Re-activate the signs underneath the train
				for (MinecartMember mm : g2) {
					Block s = mm.getActiveSign();
					if (s != null) {
						g2.activeSigns.add(s.getLocation());
						CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_LEAVE, s, g2));
						CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_ENTER, s, g2));
					}
				}
				
				g1.remove();

				g2.update();
				playLinkEffect(m2.getMinecart());
				return true;
    			
    		}
		}
		return false;
	}
	public static void playLinkEffect(Minecart at) {
		Location loc = at.getLocation();
		loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
		loc.getWorld().playEffect(loc, Effect.EXTINGUISH, 0);
	}
		
	public static void rename(String oldtrainname, String newtrainname) {
		boolean renamed = false;
		for (MinecartGroup group : groups) {
			if (group.getName().equals(oldtrainname)) {
				group.setName(newtrainname);
				renamed = true;
			}
		}
		if (!renamed) {
			TrainProperties.get(oldtrainname).rename(newtrainname);
		}
	}
	
    /*
     * NON-STATIC REGION
     */
	private HashSet<Location> activeSigns = new HashSet<Location>();
	private Queue<VelocityTarget> targets = new LinkedList<VelocityTarget>();
	public boolean ignoreForces = false;
	private String name;
	private TrainProperties prop = null;
	
	private MinecartGroup() {
		groups.add(this);
	}
		
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
				TrainProperties.get(name);
			} else if (!this.name.equals(name)) {
				TrainProperties.get(this.name).rename(name);
			}
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
	
	/*
	 * Signs underneath this group
	 */
	public boolean getSignActive(Block signblock) {
		return this.activeSigns.contains(signblock.getLocation());
	}
	public void setSignActive(Block signblock, boolean active) {
		if (active) {
			this.activeSigns.add(signblock.getLocation());
		} else {
			this.activeSigns.remove(signblock.getLocation());
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
		return this.toArray(new MinecartMember[0]);
	}
	
	public boolean connect(MinecartMember contained, MinecartMember toadd) {
		if (this.size() <= 1) return false;
		if (head().getMinecart() == contained) {
			//Validate
			double d1 = toadd.distance(head(0));
			double d2 = toadd.distance(head(1));
			if (d1 >= d2) return false;
			this.add(0, toadd);
		} else if (tail().getMinecart() == contained) {
			//Validate
			double d1 = toadd.distance(tail(0));
			double d2 = toadd.distance(tail(1));
			if (d1 >= d2) return false;
			this.add(toadd);
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
		super.add(index, member);
		member.setGroup(this);
	}
	public boolean add(MinecartMember member) {
		super.add(member);
		member.setGroup(this);
		return true;
	}
	public boolean addAll(int index, Collection<? extends MinecartMember> members) {
		super.addAll(index, members);
		for (MinecartMember m : members) {
			m.setGroup(this);
		}
		return true;
	}
	public boolean addAll(Collection<? extends MinecartMember> members) {
		super.addAll(members);
		for (MinecartMember m : members) {
			m.setGroup(this);
		}
		return true;
	}
	
	public boolean contains(Object o) {
	    return super.contains(MinecartMember.get(o));
	}
	
	public World getWorld() {
		if (this.size() == 0) return null;
		return get(0).getWorld();
	}
	
	public double length() {
		return TrainCarts.cartDistance * (this.size() - 1);
	}
	public int getCartCount(Material type) {
		int typeid = 0;
		if (type == Material.STORAGE_MINECART) {
			typeid = 1;
		} else if (type == Material.POWERED_MINECART) {
			typeid = 2;
		}
		return getCartCount(typeid);
	}
	public int getCartCount(int type) {
		int rval = 0;
		for (MinecartMember mm : this) {
			if (mm.type == type) rval++;
		}
		return rval;
	}
	
	public boolean isValid() {
		if (this.size() == 0) return false;
		if (this.size() == 1) return true;
		if (this.getProperties().requirePoweredMinecart) {
			return this.getCartCount(Material.POWERED_MINECART) > 0;
		} else {
			return true;
		}
	}
	
	public boolean remove(Object o) {
		int index = this.indexOf(o);
		if (index == -1) return false;
		this.remove(index);
		return true;
	}
	public MinecartMember remove(int index) {
		MinecartMember removed = this.get(index);
		if (this.size() == 1) {
			//Simplified
			this.remove();
		} else {
			playLinkEffect(get(index).getMinecart());
			
			//remove cart from global info
			if (removed.getGroup() == this) {
				removed.setGroup(null);
			}
			
			//Set the deactivated signs that need updating
			HashSet<Location> deactivatedSigns = new HashSet<Location>();
			for (Location loc : this.activeSigns) {
				deactivatedSigns.add(loc);
			}
			
			//split the train at the index
			MinecartGroup gnew = new MinecartGroup();
			for (int i = 0;i < index;i++) {
				MinecartMember mm = get(i);
				gnew.add(mm);
			}
				
			//Remove if empty or not allowed, else add
			if (gnew.isValid()) {
				//Add the group
				groups.add(gnew);
				
				//Set the new active signs
				for (MinecartMember mm : gnew) {
					Block b = mm.getActiveSign();
					if (b != null) {
						gnew.activeSigns.add(b.getLocation());
						deactivatedSigns.remove(b.getLocation());
					}
				}
				
				//Set the new group properties
				gnew.getProperties().load(this.getProperties());
			}

			//remove transferred carts
			for (int i = 0;i <= index;i++) {
				super.remove(0);
			}
			
			//Set the new active signs
			this.activeSigns.clear();
			for (MinecartMember mm : this) {
				Block b = mm.getActiveSign();
				if (b != null) {
					this.activeSigns.add(b.getLocation());
					deactivatedSigns.remove(b.getLocation());
				}
			}
			
			//Remove if empty
			if (!this.isValid()) {
				this.remove();
			}
			
			//Remove deactived signs
			for (Location location : deactivatedSigns) {
				CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_LEAVE, location.getBlock(), this));
			}
		}
		

		return removed;
	}
	
	public void remove() {
		//Trigger all signs currently active
		if (this.size() > 0) {
			for (Location location : this.activeSigns) {
				CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_LEAVE, location.getBlock(), this));
			}
		}
		activeSigns.clear();
		for (MinecartMember mm : this) {
			if (mm.getGroup() == this) {
				mm.setGroup(null);
			}
		}
		if (this.size() > 0) {
			//set props
			for (MinecartMember mm : this) {
				mm.getGroup().getProperties().load(this.getProperties());
			}
		}
		this.clear();
		groups.remove(this);
		if (this.prop != null) {
			this.prop.remove();
		}
	}
	public void destroy() {
		for (MinecartMember mm : this.toArray()) {
			mm.destroy();
		}
		this.clear();
		groups.remove(this);
		if (this.prop != null) {
			this.prop.remove();
		}
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
	public void updateYaw() {
		tail().setYawTo(tail(1));
		for (int i = size() - 2;i >= 0;i--) {
			get(i).setYawFrom(get(i + 1));
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
	
	public SimpleChunk[] getNearChunks(boolean addloaded, boolean addunloaded) {
		ArrayList<SimpleChunk> rval = new ArrayList<SimpleChunk>();
		for (MinecartMember mm : this) {
			mm.addNearChunks(rval, addloaded, addunloaded);
		}
		return rval.toArray(new SimpleChunk[0]);
	}
	
	public void updateTarget() {
		if (this.hasTarget() && this.getTarget().update()) {
			this.targets.remove();
		}
	}	
	public void update() {
		//Prevent index exceptions: remove if not a train
		if (this.size() == 1) {
			//Set the yaw (IS important!)
			head().setYaw(FaceUtil.faceToYaw(head().getDirection()));
			return;
		} else if (size() == 0) {
			this.remove();
			return;
		}
		
		//Validation time :D
		for (int i = this.size() - 1;i > 1;i--) {
			double d1 = head(i).distance(head(i - 1));
			double d2 = head(i).distance(head(i - 2));
			if (d1 >= d2 || (d1 > TrainCarts.maxCartDistance && !head(i).isDerailed())) {
				//Ow no! this is bad! :(
				this.remove(i);
				this.update();
				return;
			}
		}
		
		//Get the average forwarding force of all carts
		double force = this.getAverageForce();
						
		tail().addForceFactor(0, 0); //last cart max speed

		//Apply force factors to carts from last cart
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
			get(i).addForceFactor(forcer, threshold - distance);
		}
		
		//Bring the force through the listener
		force = ForceUpdateEvent.call(this, force);

		//update all carts
		for (MinecartMember m : this) {
			m.setForwardForce(force);
		}
	}
	
}
