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

public class MinecartGroup {
	/*
	 * STATIC REGION
	 */
	private static HashSet<MinecartGroup> groups = new HashSet<MinecartGroup>();
	public static boolean isDisabled = false;
		
	public static void updateGroups() {				
		for (MinecartGroup mg : getGroups()) {
			//Remove dead carts and lonely groups caused by this
			if (mg.size() == 0) {
				groups.remove(mg);
			} else if (mg.size() != 1) {
				for (MinecartMember mm : mg.getMembers()) {
					if (mm.dead || (TrainCarts.removeDerailedCarts && mm.isDerailed())) {
						mm.remove();
					}
				}
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
		for (MinecartMember mm : group.mc) {
			MinecartMember.undoReplacement(mm);
		}
	    group.mc.clear();
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
			g.addMember(member);
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
	
	public static boolean isInSameGroup(Minecart... minecarts) {
		MinecartMember[] members = MinecartMember.getAll(minecarts);
		for (int i = 0;i < minecarts.length - 1; i++) {
			if (members[i] == null) return false;
			if (members[i + 1] == null) return false;
			if (members[i].getGroup() == null) return false;
			if (members[i].getGroup() != members[i + 1].getGroup()) return false;
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
    			if (!prop1.sharesOwner(prop2)) {
    				return false;
    			} else if (!prop1.allowLinking || !prop2.allowLinking) {
    				return false;
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
					g2.addMember(0, g1.mc);
				} else if (m1index == 0 && m2index == g2.size() - 1) {
					g2.addMember(g1.mc);
				} else if (m1index == g1.size() - 1 && m2index == 0) {
					g2.addMember(0, g1.mc);
				} else {
					return false;
				}
				
				//Clear targets and active signs
				g2.clearTargets();
				
				//Get the freshly added signs
				g2.activeSigns.addAll(g1.activeSigns);
				//Re-activate the signs underneath the train
				for (MinecartMember mm : g2.mc) {
					Block s = mm.getActiveSign();
					if (s != null) {
						g2.activeSigns.add(s.getLocation());
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
	private ArrayList<MinecartMember> mc = new ArrayList<MinecartMember>();
	private HashSet<Location> activeSigns = new HashSet<Location>();
	private Queue<VelocityTarget> targets = new LinkedList<VelocityTarget>();
	public boolean ignorePushes = false;
	public boolean ignoreForces = false;
	private String name;
	
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
		return TrainProperties.get(this.getName());
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
		return mc.get(index);
	}
	public MinecartMember head() {
		return head(0);
	}
	public MinecartMember tail(int index) {
		return mc.get(mc.size() - 1 - index);
	}
	public MinecartMember tail() {
		return tail(0);
	}
	public MinecartMember middle() {
		return getMember((int) Math.floor((double) size() / 2));
	}
	
	public boolean connect(MinecartMember contained, MinecartMember toadd) {
		if (this.size() <= 1) return false;
		if (head().getMinecart() == contained) {
			//Validate
			double d1 = toadd.distance(head(0));
			double d2 = toadd.distance(head(1));
			if (d1 >= d2) return false;
			this.addMember(0, toadd);
		} else if (tail().getMinecart() == contained) {
			//Validate
			double d1 = toadd.distance(tail(0));
			double d2 = toadd.distance(tail(1));
			if (d1 >= d2) return false;
			this.addMember(toadd);
		}
		return true;
	}
	
	public MinecartMember[] sortOnIndex(Minecart... mm) {
		HashMap<Integer, MinecartMember> rval = new HashMap<Integer, MinecartMember>(mm.length);
		for (int i = 0;i < mm.length;i++) {
			int index = indexOf(mm[i]);
			rval.put(index, mc.get(index));
		}
		return rval.values().toArray(new MinecartMember[0]);
	}
	
	public int indexOf(Entity instance) {
		for (int i = 0;i < mc.size();i++) {
			if (mc.get(i).getMinecart() == instance) return i;
		}
		return -1;
	}
	public int indexOf(MinecartMember instance) {
		return mc.indexOf(instance);
	}
	
	public void addMember(int index, MinecartMember member) {
		this.mc.add(index, member);
		member.setGroup(this);
	}
	public void addMember(MinecartMember member) {
		this.mc.add(member);
		member.setGroup(this);
	}
	public void addMember(int index, Collection<MinecartMember> members) {
		this.mc.addAll(index, members);
		for (MinecartMember m : members) {
			m.setGroup(this);
		}
	}
	public void addMember(Collection<MinecartMember> members) {
		this.mc.addAll(members);
		for (MinecartMember m : members) {
			m.setGroup(this);
		}
	}
	
	public MinecartMember getMember(int index) {
		return mc.get(index);
	}
	public MinecartMember getMember(Entity instance) {
		int index = indexOf(instance);
		if (index == -1) return null;
		return mc.get(index);
	}
	public MinecartMember getMember(EntityMinecart instance) {
		return getMember(instance.getBukkitEntity());
	}
	public MinecartMember[] getMembers() {
		return mc.toArray(new MinecartMember[0]);
	}
	public World getWorld() {
		if (this.mc.size() == 0) return null;
		return mc.get(0).getWorld();
	}
	
	public int size() {
		return mc.size();
	}
	public double length() {
		return TrainCarts.cartDistance * (this.size() - 1);
	}
	
	public boolean removeCart(MinecartMember mm) {
		int index = indexOf(mm);
		if (index == -1) return false;
		removeCart(index);
		return true;
	}
	public boolean removeCart(Minecart m) {
		int index = indexOf(m);
		if (index == -1) return false;
		removeCart(index);
		return true;
	}
	public void removeCart(int index) {
		
		playLinkEffect(mc.get(index).getMinecart());
		
		//remove cart from global info
		if (mc.get(index).getGroup() == this) {
			mc.get(index).setGroup(null);
		}
		
		//Set the deactivated signs that need updating
		HashSet<Location> deactivatedSigns = new HashSet<Location>();
		for (Location loc : this.activeSigns) {
			deactivatedSigns.add(loc);
		}
		
		//split the train at the index
		MinecartGroup gnew = new MinecartGroup();
		gnew.ignorePushes = this.ignorePushes;
		for (int i = 0;i < index;i++) {
			MinecartMember mm = mc.get(i);
			gnew.addMember(mm);
		}
		
		//Add the group
		groups.add(gnew);
		
		//Set the new active signs
		for (MinecartMember mm : gnew.mc) {
			Block b = mm.getActiveSign();
			if (b != null) {
				gnew.activeSigns.add(b.getLocation());
				deactivatedSigns.remove(b.getLocation());
			}
		}
		
		//Remove if empty
		if (gnew.size() == 0) {
			gnew.remove();
		}

		//remove transferred carts
		for (int i = 0;i <= index;i++) {
			this.mc.remove(0);
		}
		
		//Set the new active signs
		this.activeSigns.clear();
		for (MinecartMember mm : this.mc) {
			Block b = mm.getActiveSign();
			if (b != null) {
				this.activeSigns.add(b.getLocation());
				deactivatedSigns.remove(b.getLocation());
			}
		}
		
		//Remove if empty
		if (this.size() == 0) {
			this.remove();
		}
		
		//Remove deactived signs
		for (Location location : deactivatedSigns) {
			CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_LEAVE, location.getBlock(), this));
		}
	}
	
	public void remove() {
		//Trigger all signs currently active
		if (this.size() > 0) {
			for (Location location : this.activeSigns) {
				CustomEvents.onSign(new SignActionEvent(ActionType.GROUP_LEAVE, location.getBlock(), this));
			}
		}
		activeSigns.clear();
		for (MinecartMember mm : this.mc) {
			if (mm.getGroup() == this) {
				mm.setGroup(null);
			}
		}
		this.mc.clear();
		groups.remove(this);
		if (this.name != null) {
			TrainProperties.get(this.name).remove();
		}
	}
	public void destroy() {
		for (MinecartMember mm : getMembers()) {
			mm.destroy();
		}
		this.mc.clear();
		groups.remove(this);
		TrainProperties.get(this.name).remove();
	}	
	public void stop() {
		for (MinecartMember m : mc) {
			m.stop();
		}
	}
	public void limitSpeed() {
		for (MinecartMember mm : mc) {
			mm.limitSpeed();
		}
	}
	public void shareForce() {
		double f = this.getAverageForce();
		for (MinecartMember m : mc) {
			m.setForwardForce(f);
		}
	}
	public void reverseOrder() {
		Collections.reverse(this.mc);
	}
	public void updateYaw() {
		tail().setYawTo(tail(1));
		for (int i = mc.size() - 2;i >= 0;i--) {
			mc.get(i).setYawFrom(mc.get(i + 1));
		}
	}
	
	public double getAverageForce() {
		if (size() == 0) return 0;
		if (size() == 1) return mc.get(0).getForce();
		
		updateYaw();
		
		//Get the average forwarding force of all carts
		double force = 0;
		double fforce = 0;
		for (MinecartMember m : mc) {
			double f = m.getForwardForce();
			fforce += f;
			if (f < 0) {
				force -= m.getForce();
			} else {
				force += m.getForce();
			}
		}
		force /= mc.size();
		
		//Reverse
		if (fforce < 0) {
			reverseOrder();
		}
		return force;
	}
	public double getMaxSpeed() {
		return head().maxSpeed;
	}
	public void setMaxSpeed(double maxspeed) {
		for (MinecartMember mm : mc) {
			mm.maxSpeed = maxspeed;
		}
	}
	
	public SimpleChunk[] getNearChunks(boolean addloaded, boolean addunloaded) {
		ArrayList<SimpleChunk> rval = new ArrayList<SimpleChunk>();
		for (MinecartMember mm : mc) {
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
				this.removeCart(i);
				this.update();
				return;
			}
		}
		
		//Get the average forwarding force of all carts
		double force = this.getAverageForce();
				
		tail().addForceFactor(0, 0); //last cart max speed

		//Apply force factors to carts from last cart
		for (int i = size() - 2;i >= 0;i--) {
			double distance = mc.get(i).distanceXZ(mc.get(i + 1));
			double threshold = 0;
			double forcer = 1;
			if (mc.get(i).getYawDifference(mc.get(i + 1).getYaw()) > 10 || mc.get(i).getPitchDifference(mc.get(i + 1)) > 10) {
				threshold = TrainCarts.turnedCartDistance;
				forcer = TrainCarts.turnedCartDistanceForcer;
			} else {
				threshold = TrainCarts.cartDistance;
				forcer = TrainCarts.cartDistanceForcer;
			}
			if (distance < threshold) forcer *= TrainCarts.nearCartDistanceFactor;
			mc.get(i).addForceFactor(forcer, threshold - distance);
		}
		
		//Bring the force through the listener
		force = ForceUpdateEvent.call(this, force);

		//Set max speed of head to tails automatically
		setMaxSpeed(getMaxSpeed());
		
		//update all carts
		for (MinecartMember m : mc) {
			m.setForwardForce(force);
		}
	}
	
}
