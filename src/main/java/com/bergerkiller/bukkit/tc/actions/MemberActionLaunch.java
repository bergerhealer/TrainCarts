package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MemberActionLaunch extends MemberAction implements MovementAction {
	private static final double minVelocity = 0.02;
	private static final double minVelocityForLaunch = 0.004;
	private double distance;
	private double targetdistance;
	private double targetvelocity;
	private double startvelocity;

	public MemberActionLaunch(final MinecartMember member, double targetdistance, double targetvelocity) {
		super(member);
		this.distance = 0;
		this.targetdistance = targetdistance;
		this.targetvelocity = targetvelocity;
		this.distance = 0;
	}

	protected void setTargetDistance(double distance) {
		this.targetdistance = distance;
	}

	public void start() {
		this.startvelocity = MathUtil.clamp(this.getMember().getForce(), this.getMember().maxSpeed);
		if (this.startvelocity < minVelocity) this.startvelocity = minVelocity;
	}

	@Override
	public boolean isMovementSuppressed() {
		return true;
	}
	
	public double getTargetVelocity() {
		return this.targetvelocity;
	}
	public double getTargetDistance() {
		return this.targetdistance;
	}
	public double getDistance() {
		return this.distance;
	}

	@Override
	public boolean update() {	
		//Derailed?
		if (this.getMember().isDerailed()) {
			return true;
		}

		//Did any of the carts in the group stop?
		if (this.distance != 0) {
			for (MinecartMember mm : this.getGroup()) {
				if (mm.getForceSquared() < minVelocityForLaunch * minVelocityForLaunch) return true; //stopped
			}
		}
		
		//Increment distance
		this.distance += this.getMember().getMovedDistance();
		
		//Reached the target distance?
		if (this.distance > this.targetdistance - 0.2) {
			if (this.targetvelocity == 0) {
				//Stop if target velocity was 0
				this.getGroup().stop();
			} else {
				//Launch at full speed
				this.getGroup().setForwardForce(this.targetvelocity);
			}
			return true;
		} else {
			//Get the velocity to set the carts to
			double targetvel = MathUtil.clamp(this.targetvelocity, this.getMember().maxSpeed);
			if (this.targetvelocity > 0 || (this.targetdistance - this.distance) < 5) {
				targetvel = MathUtil.lerp(this.startvelocity, targetvel, this.distance / this.targetdistance);
			} else {
				targetvel = this.startvelocity;
			}
			targetvel = Math.max(targetvel, minVelocity);
			this.getGroup().setForwardForce(targetvel);
			return false;
		}
	}
}
