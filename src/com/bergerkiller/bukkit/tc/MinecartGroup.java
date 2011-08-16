package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import net.minecraft.server.EntityMinecart;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;



public class MinecartGroup {
	/*
	 * STATIC REGION
	 */
	private static HashMap<Minecart, MinecartGroup> mgroups = new HashMap<Minecart, MinecartGroup>();
	private static HashSet<MinecartGroup> groups = new HashSet<MinecartGroup>();
	
	public static void cleanGroups() {
		//Remove dead carts and lonely groups caused by this
		ArrayList<Minecart> removecarts = new ArrayList<Minecart>();
		for (Minecart m : mgroups.keySet()){
			if (!validateCart(m)) removecarts.add(m);
		}
		for (Minecart m : removecarts) {
			remove(m);
			MinecartFixer.removeReplacedCart(m);
		}
	}
	public static void updateGroups() {
		//generate groups
		for (World w : Bukkit.getServer().getWorlds()) {
			ArrayList<Minecart> minecarts = new ArrayList<Minecart>();
			for (Entity e : w.getEntities()) {
				if (e instanceof Minecart && validateCart((Minecart) e)) {
					minecarts.add((Minecart) e);
				}
			}
		    for (Minecart m1 : minecarts) {
	    	    for (Minecart m2 : minecarts) {
	    	    	if (m1 != m2 && m1.getLocation().getWorld() == m2.getLocation().getWorld()) {
	    	    		double distance = m1.getLocation().distance(m2.getLocation());
	    	    		if (distance <= TrainCarts.linkRadius) {
	    	    			link(m1, m2);
	    	    		}
	    	    	}
	    	    }
		    }
		}
	}
	public static void updateMembers() {
		for (MinecartGroup g : groups) g.update();
	}
	public static boolean isMember(Minecart m) {
		return mgroups.containsKey(m);
	}
			
	public static void remove(Minecart m) {
		MinecartGroup g = get(m);
		if (g != null) g.removeCart(m);
	}
	public static MinecartGroup get(EntityMinecart m) {
		return get(m.getBukkitEntity());
	}
	public static MinecartGroup get(Entity e) {
		if (e instanceof Minecart) return get((Minecart) e);
		return null;
	}
	public static MinecartGroup get(Minecart m) {
		return mgroups.get(m);
	}
	
	public static void shareForce(MinecartGroup group) {
		double f = 0;
		for (MinecartMember m : group.mc) {
			f += m.getForwardForce();
		}
		f /= group.mc.size();
		group.updateReverse();
		for (MinecartMember m : group.mc) {
			m.setForwardForce(f);
		}
	}
	public static boolean link(Minecart m1, Minecart m2) {
		MinecartGroup g1 = get(m1);
		MinecartGroup g2 = get(m2);
		if (g1 != g2 || g1 == null) {
    		if (MinecartMember.isSharingRails(m1, m2)) {
    			return link(m1, g1, m2, g2);
    		}
		}
		return false;
	}
	public static boolean link(Minecart m1, MinecartGroup g1, Minecart m2, MinecartGroup g2) {
		if (!validateCart(m1)) return false;
		if (!validateCart(m2)) return false;
		if (g1 == g2 && g1 != null) return false;
		if (g1 == null && g2 == null) {
			m1 = MinecartFixer.replace(m1);
			m2 = MinecartFixer.replace(m2);
			MinecartMember mm1 = new MinecartMember(m1);
			MinecartMember mm2 = new MinecartMember(m2);
			MinecartGroup g = new MinecartGroup();
			g.mc.add(mm1);
			g.mc.add(mm2);
			groups.add(g);
			mgroups.put(m1, g);
			mgroups.put(m2, g);
			//g.update(true);
			shareForce(g);
			playLinkEffect(m1);
			return true;
		} else if (g1 == null && g2 != null) {
			//add cart 1 to group 2
			m1 = g2.connect(m2, m1);
			if (m1 != null) {
				mgroups.put(m1, g2);
				//g2.update(true);
				shareForce(g2);
				playLinkEffect(m1);
				return true;
			} else {
				return false;	
			}
		} else if (g2 == null && g1 != null) {
			//add cart 2 to group 1
			m2 = g1.connect(m1, m2);
			if (m2 != null) {
				mgroups.put(m2, g1);
				//g1.update(true);
				shareForce(g1);
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
				g2.mc.addAll(0, g1.mc);
				broadcast("MODE 1");
			} else if (m1index == 0 && m2index == g2.mc.size() - 1) {
				g1.reverse();
				g2.reverse();
				g2.mc.addAll(g1.mc);
				broadcast("MODE 2");
			} else if (m1index == g1.mc.size() - 1 && m2index == 0) {
				g2.mc.addAll(0, g1.mc);
				broadcast("MODE 3");
			} else if (m1index == g1.mc.size() - 1 && m2index == g2.mc.size() - 1) {
				g2.reverse();
				g2.mc.addAll(g1.mc);
				broadcast("MODE 4");
			} else {
				return false;
			}
			groups.remove(g1);
			for (MinecartMember m : g2.mc) {
				mgroups.put(m.getMinecart(), g2);
			}
			shareForce(g2);
			//g2.update(true);
			playLinkEffect(m2);
			return true;
		}
		return false;
	}
	
	public static void playLinkEffect(Minecart at) {
		Location loc = at.getLocation();
		loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
		loc.getWorld().playEffect(loc, Effect.EXTINGUISH, 0);
	}
	public static boolean validateCart(Minecart m) {
		if (m.isDead()) return false;
		if (TrainCarts.removeDerailedCarts) {
			return MinecartMember.getRailsBlock(m) != null;
		} else {
			return true;
		}
	}
	public static void broadcast(String msg) {
		Bukkit.getServer().broadcastMessage(msg);
	}
    public static double round(double Rval, int Rpl) {
    	  double p = Math.pow(10,Rpl);
    	  return Math.round(Rval * p) / p;
      }
    public static double distance(double x1, double z1, double x2, double z2) {
    	return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(z1 - z2, 2));
    }
	
    /*
     * NON-STATIC REGION
     */
	private ArrayList<MinecartMember> mc = new ArrayList<MinecartMember>();
	
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
		
	public Minecart connect(Minecart contained, Minecart toadd) {
		if (head().getMinecart() == contained) {
			toadd = MinecartFixer.replace(toadd);
			mc.add(0, new MinecartMember(toadd));
		} else if (tail().getMinecart() == contained) {
			toadd = MinecartFixer.replace(toadd);
			mc.add(new MinecartMember(toadd));
		} else {
			return null;
		}
		return toadd;
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
	public boolean removeCart(Minecart m) {
		int index = indexOf(m);
		if (index == -1) return false;
		removeCart(index);
		return true;
	}
	public void removeCart(int index) {
		playLinkEffect(mc.get(index).getMinecart());
		
		//remove cart from global info
		mgroups.remove(mc.get(index).getMinecart());
		
		//split the train at the index
		MinecartGroup gnew = new MinecartGroup();
		for (int i = 0;i < index;i++) {
			MinecartMember m = mc.get(i);
			gnew.mc.add(m);
			mgroups.put(m.getMinecart(), gnew);
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
		for (MinecartMember m : this.mc) {
			mgroups.remove(m.getMinecart());
		}
		groups.remove(this);
	}
	public void stop() {
		for (MinecartMember m : mc) {
			m.stop();
		}
	}
	private void reverse() {
		Collections.reverse(mc);
	}

	public void updateReverse() {
		//Sorting the carts, head at 0
		double fullforce  = 0;
		for (MinecartMember m : mc) {
			fullforce += m.getFullForwardForce();
		}
		if (fullforce < 0) reverse();
	}
	public void update() {
		update(false);
	}
	public void update(boolean ignorecartdistance) {
		if (!TCPlayerListener.usefactor) {
			ignorecartdistance = true;
			for (MinecartMember m : mc) m.addForceFactor(0, 0);
		}
		
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
			force += m.getForwardForce();
		}	
		force /= mc.size();
				
		if (!ignorecartdistance) {
			mc.get(mc.size() - 1).addForceFactor(0, 0); //last cart max speed

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
					update(ignorecartdistance);
					return;
				}
				mc.get(i).addForceFactor(forcer, threshold - distance);
			}
		}

		//update all carts
		for (MinecartMember m : mc) {
			m.setForwardForce(force);
		}
	}
}
