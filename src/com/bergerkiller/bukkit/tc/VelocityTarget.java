package com.bergerkiller.bukkit.tc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class VelocityTarget {
	
	public VelocityTarget(Location target, double startVelocity, double goalVelocity, Entity e) {
		this.distance = 0;
		this.target = target;
		this.startVelocity = startVelocity;
		this.goalVelocity = goalVelocity;
		if (startVelocity < 0.01) startVelocity = 0.01;
		this.goalDistance = target.distance(e.getLocation());
	}
	public VelocityTarget(Location target, double goalVelocity, Entity e) {
		this.distance = 0;
		this.target = target;
		this.goalVelocity = goalVelocity;
		this.startVelocity = e.getVelocity().length();
		if (startVelocity < 0.01) startVelocity = 0.01;
		this.goalDistance = target.distance(e.getLocation());
	}
	
	public void setDelay(long delayMS) {
		this.startTime = System.currentTimeMillis() + delayMS;
	}
	
	private Location target;
	public double distance;
	public double goalDistance;
	public double startVelocity;
	public double goalVelocity;
	private double prevdistance;
	public long startTime;
	private boolean prevset = false;
	
	public boolean update(Entity e) {
		if (this.startTime > System.currentTimeMillis()) return false;
		net.minecraft.server.Entity ee = Util.getNative(e);
		//Increment distance
		this.distance += Util.distance(ee.locX, ee.locY, ee.locZ, ee.lastX, ee.lastY, ee.lastZ);

		//Did not pass the goal already?
		boolean reached = this.distance >= this.goalDistance;
		double newdist = e.getLocation().distance(this.target);
		if (newdist > this.prevdistance && prevset) {
			reached = true;
		}
		this.prevdistance = newdist;
		prevset = true;
		
		//Get the velocity to set the cart to
		double targetvel = Util.stage(this.startVelocity, this.goalVelocity, this.distance / this.goalDistance);
		
		//Are we heading towards the target?
		if (reached || Util.isHeadingTo(e.getLocation(), this.target, e.getVelocity())) {
			//set motion using a factor
			double currvel = Util.length(ee.motX, ee.motY, ee.motZ);
			if (currvel < 0.01) {
				ee.motX = 0;
				ee.motZ = 0;
				return true;
			} else {
				double factor = targetvel / currvel;
				ee.motX *= factor;
				ee.motZ *= factor;
			}
		} else {
			//set motion using the angle
			float yaw = Util.getLookAtYaw(e.getLocation(), this.target);
			double ryaw = -yaw / 180 * Math.PI;
			ee.motX = Math.sin(ryaw) * targetvel;
			ee.motZ = Math.cos(ryaw) * targetvel;
		}
		
		return reached;
	}
		
}
