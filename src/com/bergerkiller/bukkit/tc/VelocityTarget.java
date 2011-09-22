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
	public Task afterTask = null;
	private final double minVelocity = 0.1;
	
	public MinecartMember getFrom() {
		return this.from;
	}
	public Location getTarget() {
		return this.target;
	}
	
	public long getDelay() {
		if (startTime == 0) {
			return this.delay;
		} else {
			long delay = startTime - System.currentTimeMillis();
			if (delay < 0) return 0;
			return delay;
		}
	}
	public void setDelay(long delay) {
		this.delay = delay;
	}
	
	public boolean started() {
		if (this.delay == 0) return true;
		return this.startTime <= System.currentTimeMillis();
	}
	
	public boolean update() {
		//Update time
		if (this.startTime == 0) {
			this.startTime = System.currentTimeMillis() + this.delay;
			if (this.delay > 0) return false;
		} else if (!started()) {
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
		
		//Ensure we can even target, too high values is never nice!
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
			//Stop to stop MM from ruining it all...
			if (TrainCarts.MinecartManiaEnabled) {
				from.getGroup().stop();
			}
			//set motion using the angle
			from.setForce(targetvel, target);
		}
		
		//Stop if dest. vel. was 0
		if (reached && this.goalVelocity == 0) {
			from.getGroup().stop();
		}
		
		if (reached && afterTask != null) {
			afterTask.run();
		}
		return reached;
	}
		
}
