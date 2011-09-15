package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
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
			} else {
				for (MinecartMember mm : mg.getMembers()) {
					if (!MinecartMember.validate(mm)) {
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
		
	public static void setSingle(MinecartMember mm) {
		if (MinecartMember.validate(mm)) {
			MinecartGroup group = new MinecartGroup();
			group.addMember(mm);
			mm.setGroup(group);
			load(group);
		} else {
			mm.setGroup(null);
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
	public static void load(MinecartGroup group) {
		if (group != null) groups.add(group);
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
		if (e instanceof Minecart) return get((Minecart) e);
		return null;
	}
	public static MinecartGroup get(Minecart m) {
		MinecartMember mm = MinecartMember.get(m);
		if (mm == null) return null;
		return mm.getGroup();
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
		MinecartGroup g1 = get(m1);
		MinecartGroup g2 = get(m2);
		if (g1 != g2 || g1 == null) {
    		if (EntityUtil.isSharingRails(m1, m2)) {
    			if (!MinecartMember.validate(m1)) return false;
    			if (!MinecartMember.validate(m2)) return false;
    			if (GroupManager.wasInGroup(m1)) return false;
    			if (GroupManager.wasInGroup(m2)) return false;		
    			if (g1 == null && g2 == null) {
    				playLinkEffect(m1);
    				MinecartGroup g = new MinecartGroup(m1, m2);
    				g.shareForce();
    				load(g);
    				return true;
    			} else if (g1 == null && g2 != null) {
    				//add cart 1 to group 2
    				MinecartMember m = g2.connect(m2, m1);
    				if (m != null) {
    					m1 = m.getMinecart();
    					g2.shareForce();
    					playLinkEffect(m1);
    					return true;
    				} else {
    					return false;	
    				}
    			} else if (g2 == null && g1 != null) {
    				//add cart 2 to group 1
    				MinecartMember m = g1.connect(m1, m2);
    				if (m != null) {
    					m2 = m.getMinecart();
    					g1.shareForce();
    					playLinkEffect(m2);
    					return true;
    				} else {
    					return false;	
    				}
    			} else if (g1 != null && g2 != null && g1 != g2) {
    				//add group1 to group2
    				//append group1 before or after group2?
    				int m1index = g1.indexOf(m1);
    				int m2index = g2.indexOf(m2);	
    				if (m1index == 0 && m2index == 0) {
    					g1.reverseOrder();
    					g2.mc.addAll(0, g1.mc);
    				} else if (m1index == 0 && m2index == g2.size() - 1) {
    					g2.mc.addAll(g1.mc);
    				} else if (m1index == g1.size() - 1 && m2index == 0) {
    					g2.mc.addAll(0, g1.mc);
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
    				
    				groups.remove(g1);
    				for (MinecartMember mm : g2.mc) {
    					mm.setGroup(g2);
    				}
    				g2.update();
    				playLinkEffect(m2);
    				return true;
    			}
    		}
		}
		return false;
	}
	public static void playLinkEffect(Minecart at) {
		Location loc = at.getLocation();
		loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
		loc.getWorld().playEffect(loc, Effect.EXTINGUISH, 0);
	}
	
    /*
     * NON-STATIC REGION
     */
	private ArrayList<MinecartMember> mc = new ArrayList<MinecartMember>();
	private HashSet<Location> activeSigns = new HashSet<Location>();
	private Queue<VelocityTarget> targets = new LinkedList<VelocityTarget>();
	public boolean ignorePushes = false;
	public boolean ignoreForces = false;
	
	public MinecartGroup() {}
	public MinecartGroup(Minecart... members) {
		for (int i = 0;i < members.length;i++) {
			mc.add(MinecartMember.get(members[i], this));
		}
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
	public boolean grouped() {
		return groups.contains(this);
	}
	
	public MinecartMember connect(Minecart contained, Minecart toadd) {
		MinecartMember rval = null;
		if (this.size() <= 1) return null;
		if (head().getMinecart() == contained) {
			rval = MinecartMember.get(toadd, this);
			//Validate
			double d1 = rval.distance(head(0));
			double d2 = rval.distance(head(1));
			if (d1 >= d2) return null;
			mc.add(0, rval);
		} else if (tail().getMinecart() == contained) {
			rval = MinecartMember.get(toadd, this);
			//Validate
			double d1 = rval.distance(tail(0));
			double d2 = rval.distance(tail(1));
			if (d1 >= d2) return null;
			mc.add(rval);
		}
		return rval;
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
	public void addMember(MinecartMember mm) {
		this.mc.add(mm);
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
		setSingle(mc.get(index));
		
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
			gnew.mc.add(mm);
			mm.setGroup(gnew);
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
			setSingle(mm);
		}
		this.mc.clear();
		groups.remove(this);
	}
	public void destroy() {
		for (MinecartMember mm : getMembers()) {
			mm.destroy();
		}
		this.mc.clear();
		groups.remove(this);
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
		} else if (mc.size() == 0) {
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
				
		mc.get(mc.size() - 1).addForceFactor(0, 0); //last cart max speed

		//Apply force factors to carts from last cart
		for (int i = mc.size() - 2;i >= 0;i--) {
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
