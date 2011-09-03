package com.bergerkiller.bukkit.tc;

import org.bukkit.Location;


public class VelocityTarget {
	
	public VelocityTarget(MinecartMember from, Location target, double goalVelocity, long delayMS) {
		this.distance = 0;
		this.target = target.clone();
		this.startTime = 0;
		this.goalVelocity = goalVelocity;
		if (startVelocity < 0.05) startVelocity = 0.05;
		this.goalDistance = 0;
		this.delay = delayMS;
		this.from = from;
	}
		
	private MinecartMember from;
	private Location target;
	public double distance;
	public double goalDistance;
	public double startVelocity;
	public double goalVelocity;
	private long startTime;
	private long delay;
	private final double minVelocity = 0.1;
	
	public boolean update() {
		//Update time
		if (this.startTime == 0) {
			this.startTime = System.currentTimeMillis() + this.delay;
			if (this.delay > 0) return false;
		} else if (this.startTime > System.currentTimeMillis()) {
			return false;
		}
		//First start
		if (this.goalDistance == 0) {
			this.goalDistance = from.distanceXZ(target);
			this.startVelocity = from.getForce();
			if (this.startVelocity > from.maxSpeed) {
				this.startVelocity = from.maxSpeed;
			}
			if (this.startVelocity < minVelocity) this.startVelocity = minVelocity;
		}
		
		from.getGroup().limitSpeed();

		//Increment distance
		this.distance += Util.distance(from.locX, from.locZ, from.lastX, from.lastZ);

		//Did not pass the goal already?
		boolean reached = this.distance > this.goalDistance - 0.2;
		
		//Get the velocity to set the cart to
		double targetvel = Util.stage(this.startVelocity, this.goalVelocity, (this.distance - 0.6) / this.goalDistance);
		
		//Are we heading towards the target?
		if (reached || Util.isHeadingTo(from.getLocation(), this.target, from.getVelocity())) {
			//set motion using a factor
			double currvel = Util.length(from.motX, from.motZ);
			if (currvel < minVelocity) {
				currvel = minVelocity;
			}
			double factor = targetvel / currvel;
			from.motX *= factor;
			from.motZ *= factor;
		} else {
			//set motion using the angle
			from.setForce(targetvel, target);
		}
		
		//Stop if dest. vel. was 0
		if (reached && this.goalVelocity == 0) {
			if (from.grouped()) {
				from.getGroup().stop();
			} else {
				from.stop();
			}
		}
		return reached;
	}
		
}
