package com.bergerkiller.bukkit.tc;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeDown;

/**
 * A collision mode between a Minecart and another Entity
 */
public enum CollisionMode {
	DEFAULT("is stopped by"), PUSH("pushes"), CANCEL("ignores"), KILL("kills"), 
	KILLNODROPS("kills without drops"), ENTER("takes in"), LINK("forms a group with");

	private final String operationName;

	private CollisionMode(String operationName) {
		this.operationName = operationName;
	}

	/**
	 * Executes this collision mode
	 * 
	 * @param member collided
	 * @param entity collided with
	 * @return True if collision is allowed, False if not
	 */
	public boolean execute(MinecartMember<?> member, Entity entity) {
		final CommonMinecart<?> minecart = member.getEntity();
		final MinecartMember<?> other = MinecartMemberStore.get(entity);
		// Some default exception rules
		if (!member.isInteractable() || entity.isDead() || member.isCollisionIgnored(entity)) {
			return false;
		}
		// Ignore passengers
		if (entity.isInsideVehicle() && entity.getVehicle() instanceof Minecart) {
			return false;
		}
		// Exception rules for other Minecarts
		if (other != null) {
			if (!other.isInteractable()) {
				return false;
			}
			// Ignore collisions with same group
			if (member.getGroup() == other.getGroup()) {
				return false;
			}
			// Check if both minecarts are on the same vertical column
			RailLogic logic1 = member.getRailLogic();
			if (logic1 instanceof RailLogicVerticalSlopeDown) {
				RailLogic logic2 = other.getRailLogic();
				if (logic2 instanceof RailLogicVerticalSlopeDown) {
					Block b1 = member.getBlock(logic1.getDirection());
					Block b2 = other.getBlock(logic2.getDirection());
					if (BlockUtil.equals(b1, b2)) {
						return false;
					}
				}
			}
		} else if (member.isMovementControlled()) {
			// For other entity types - ignore collision
			return false;
		}
		switch (this) {
			case ENTER :
				if (!minecart.hasPassenger() && minecart.isVehicle() && Util.canBePassenger(entity) && member.canCollisionEnter()) {
					minecart.setPassenger(entity);
				}
				return false;
			case PUSH :
				if (entity instanceof Minecart) {
					// Push the minecart (only when moving towards it)
					if (member.isHeadingTo(entity)) {
						double force;
						// Keeping distance
						force = TrainCarts.cartDistance - member.getEntity().loc.distanceSquared(entity);
						force *= TrainCarts.cartDistanceForcer;
						// Difference in velocity
						force += member.getForce() - entity.getVelocity().length();
						// Apply
						if (force > 0.0) {
							member.push(entity, force);
						}
					}
				} else {
					member.pushSideways(entity);
				}
				return false;
			case CANCEL :
				return false;
			case KILLNODROPS :
			case KILL :
				if (member.isMoving() && member.isHeadingTo(entity)) {
					if (this == KILLNODROPS) {
						TCListener.cancelNextDrops = true;
					}
					if (entity instanceof LivingEntity) {
						boolean old = EntityUtil.isInvulnerable(entity);
						EntityUtil.setInvulnerable(entity, false);
						((LivingEntity) entity).damage(Short.MAX_VALUE, member.getEntity().getEntity());
						EntityUtil.setInvulnerable(entity, old);
					} else {
						EntityUtil.damage(entity, DamageCause.CUSTOM, (double) Short.MAX_VALUE);
						entity.remove();
					}
					if (this == KILLNODROPS) {
						TCListener.cancelNextDrops = false;
					}
				}
				return false;
			case LINK :
				if (other != null) {
					// Perform default linking logic
					return !MinecartGroupStore.link(member, other);
				}
				return true;
			default :
				if (other != null) {
					// Perform default logic: Stop this train
					if (member.isHeadingTo(entity)) {
						member.getGroup().stop();
					}
					return false;
				}
				return true;
		}
	}

	/**
	 * Gets the text for what this Collision Mode performs
	 * 
	 * @return collision operation name
	 */
	public String getOperationName() {
		return this.operationName;
	}

	/**
	 * Parses a Collision Mode from a String
	 * 
	 * @param text to parse
	 * @return Collision Mode, or null if not parsed
	 */
	public static CollisionMode parse(String text) {
		text = text.toLowerCase();
		if (text.startsWith("allow") || text.startsWith("enable")) {
			return DEFAULT;
		} else if (text.startsWith("deny") || text.startsWith("denied") || text.startsWith("disable")) {
			return CANCEL;
		}
		return ParseUtil.parseEnum(CollisionMode.class, text, null);
	}

	/**
	 * Gets the Collision Mode as being a linking state
	 * 
	 * @param state of linking
	 * @return LINK OR DEFAULT
	 */
	public static CollisionMode fromLinking(boolean state) {
		return state ? LINK : DEFAULT;
	}
	
	/**
	 * Gets the Collision Mode as being a pushing state
	 * 
	 * @param state of pushing
	 * @return PUSH or DEFAULT
	 */
	public static CollisionMode fromPushing(boolean state) {
		return state ? PUSH : DEFAULT;
	}

	/**
	 * Gets the Collision Mode as being an entering state
	 * 
	 * @param state of entering
	 * @return ENTER or DEFAULT
	 */
	public static CollisionMode fromEntering(boolean state) {
		return state ? ENTER : DEFAULT;
	}
}
