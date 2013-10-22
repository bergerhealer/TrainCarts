package com.bergerkiller.bukkit.tc.controller.type;

import org.bukkit.Effect;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecartTNT;
import com.bergerkiller.bukkit.common.wrappers.DamageSource;
import com.bergerkiller.bukkit.tc.GroupUnloadedException;
import com.bergerkiller.bukkit.tc.MemberMissingException;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class MinecartMemberTNT extends MinecartMember<CommonMinecartTNT> {
	private boolean ignoreDamage = false;

	@Override
	public void onDamage(DamageSource damagesource, double damage) {
		if (!this.isInteractable() || ignoreDamage) {
			return;
		}
		super.onDamage(damagesource, damage);
		// If entity died and the source of the damage is 'igniting' the TNT, explode
		// Also explode if the TNT minecart is moving really fast
		if (entity.isDead() && !Util.canInstantlyBuild(damagesource.getEntity()) && 
				(damagesource.isFireDamage() || damagesource.isExplosive() || entity.isMovingFast())) {
			// Important: set dead beforehand
			ignoreDamage = true;
			entity.explode();
			// Entity explosion MAY have been cancelled, re-enable for the future
			ignoreDamage = false;
		}
	}

	@Override
	public void onActivate() {
		super.onActivate();
		if (!entity.isTNTPrimed()) {
			entity.primeTNT();
		}
	}

	@Override
	public void onPhysicsPostMove(double speedFactor) throws MemberMissingException, GroupUnloadedException {
		super.onPhysicsPostMove(speedFactor);
		int ticks = entity.getFuseTicks();
		if (ticks > 0) {
			// Update fuse ticks and show fuse effect
			entity.setFuseTicks(ticks - 1);
			entity.getWorld().playEffect(entity.getLocation().add(0.0, 0.5, 0.0), Effect.SMOKE, BlockFace.SELF);
		} else if (ticks == 0) {
			// Explode
			entity.explode();
		}
		// When hitting into a block with force - detonate
		if (entity.isMovementImpaired() && entity.isMovingFast()) {
			entity.explode();
		}
	}
}
