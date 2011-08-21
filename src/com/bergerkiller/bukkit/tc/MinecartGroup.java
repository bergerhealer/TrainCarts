package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import net.minecraft.server.EntityMinecart;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;



public class MinecartGroup {
	/*
	 * STATIC REGION
	 */
	private static HashSet<MinecartGroup> groups = new HashSet<MinecartGroup>();
	
	public static void updateGroups() {
		for (MinecartGroup mg : getGroups()) {
			//Remove dead carts and lonely groups caused by this
			for (MinecartMember mm : mg.getMembers()) {
				if (!MinecartMember.validate(mm)) {
					remove(mm);
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
	
	public static MinecartGroup[] getGroups() {
		return groups.toArray(new MinecartGroup[0]);
	}
	public static void remove(MinecartMember mm) {
		if (mm == null) return;
		MinecartGroup g = mm.getGroup();
		if (g != null) g.removeCart(g.mc.indexOf(mm));
	}
	public static void remove(Minecart m) {
		remove(MinecartMember.get(m));
	}
	
	public static void unload(MinecartGroup group) {
		group.stop();
		for (MinecartMember mm : group.mc) {
			mm.setGroup(null);
			MinecartMember.undoReplacement(mm);
		}
	    group.mc.clear();
		groups.remove(group);
	}
	public static void load(MinecartGroup group) {
		group.groupMembers();
		groups.add(group);
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
	
	public static boolean link(Minecart m1, Minecart m2) {
		MinecartGroup g1 = get(m1);
		MinecartGroup g2 = get(m2);
		if (g1 != g2 || g1 == null) {
    		if (Util.isSharingRails(m1, m2)) {
    			if (!MinecartMember.validate(m1)) return false;
    			if (!MinecartMember.validate(m2)) return false;
    			if (GroupManager.wasInGroup(m1)) return false;
    			if (GroupManager.wasInGroup(m2)) return false;
    			if (g1 == null && g2 == null) {
    				MinecartGroup g = new MinecartGroup(m1, m2);
    				g.shareForce();
    				load(g);
    				playLinkEffect(m1);
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
    					g1.reverse();
    					//head-on collision, this will go real bad if we don't slow it down!
    					g2.mc.addAll(0, g1.mc);
    					//g2.stop();
    				} else if (m1index == 0 && m2index == g2.size() - 1) {
    					g2.mc.addAll(g1.mc);
    				} else if (m1index == g1.size() - 1 && m2index == 0) {
    					g2.mc.addAll(0, g1.mc);
    				} else {
    					return false;
    				}
    				groups.remove(g1);
    				g2.groupMembers();
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

	public MinecartGroup() {}
	public MinecartGroup(Minecart... members) {
		for (int i = 0;i < members.length;i++) {
			mc.add(MinecartMember.get(members[i], this));
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
		
	public MinecartMember connect(Minecart contained, Minecart toadd) {
		MinecartMember rval = null;;
		if (head().getMinecart() == contained) {
			rval = MinecartMember.get(toadd, this);
			mc.add(0, rval);
		} else if (tail().getMinecart() == contained) {
			rval = MinecartMember.get(toadd, this);
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
	
	public boolean removeCart(Minecart m) {
		int index = indexOf(m);
		if (index == -1) return false;
		removeCart(index);
		return true;
	}
	public void removeCart(int index) {
		playLinkEffect(mc.get(index).getMinecart());
		
		//remove cart from global info
		mc.get(index).setGroup(null);
		
		//split the train at the index
		MinecartGroup gnew = new MinecartGroup();
		for (int i = 0;i < index;i++) {
			MinecartMember mm = mc.get(i);
			gnew.mc.add(mm);
			mm.setGroup(gnew);
		}
		//Add the group
		groups.add(gnew);
		
		//Remove if empty
		if (gnew.mc.size() <= 1) gnew.remove();

		//remove transferred carts
		for (int i = 0;i <= index;i++) {
			this.mc.remove(0);
		}
		
		//Remove if empty
		if (this.mc.size() <= 1) this.remove();
	}
	public void remove() {
		for (MinecartMember mm : this.mc) {
			mm.setGroup(null);
		}
		this.mc.clear();
		groups.remove(this);
	}
	
	public boolean isGrouped() {
		return groups.contains(this);
	}
	public void stop() {
		for (MinecartMember m : mc) {
			m.stop();
		}
	}
	public void shareForce() {
		double f = 0;
		for (MinecartMember m : mc) {
			f += m.getForce();
		}
		f /= mc.size();
		updateReverse();
		for (MinecartMember m : mc) {
			m.setForwardForce(f);
		}
	}
	private void reverse() {
		Collections.reverse(mc);
	}
	
	public SimpleChunk[] getNearChunks(boolean addloaded, boolean addunloaded) {
		ArrayList<SimpleChunk> rval = new ArrayList<SimpleChunk>();
		for (MinecartMember mm : mc) {
			mm.addNearChunks(rval, addloaded, addunloaded);
		}
		return rval.toArray(new SimpleChunk[0]);
	}

	private void groupMembers() {
		for (MinecartMember mm : mc) {
			mm.setGroup(this);
		}
	}
	public void updateReverse() {
		//Sorting the carts, head at 0
		double fullforce  = 0;
		for (MinecartMember m : mc) {
			fullforce += m.getForwardForce();
		}
		if (fullforce < 0) reverse();
	}
	public void update() {
		
		//Prevent index exceptions: remove if not a train
		if (mc.size() <= 1) {
			this.remove();
			return;
		}
		
		//calculate the yaw for all carts; tail is the initial yaw to start at
		tail().setYawTo(tail(1));
		for (int i = mc.size() - 2;i >= 0;i--) {
			mc.get(i).setYawFrom(mc.get(i + 1));
		}
		
		updateReverse();
		
		//Get the average forwarding force of all carts
		double force = 0;
		for (MinecartMember m : mc) {
			force += m.getForce();
		}
		force /= mc.size();
				
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
			if (distance > TrainCarts.maxCartDistance) {
				this.removeCart(i);
				this.update();
				return;
			}
			mc.get(i).addForceFactor(forcer, threshold - distance);
		}

		//update all carts
		for (MinecartMember m : mc) {
			m.setForwardForce(force);
		}
	}
	
}
