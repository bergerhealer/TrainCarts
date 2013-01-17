package com.bergerkiller.bukkit.tc;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * A collision mode between a Minecart and a non-minecart Entity
 */
public enum CollisionMode {
	DEFAULT, PUSH, CANCEL, KILL, KILLNODROPS, ENTER;

	/**
	 * Executes this collision mode
	 * 
	 * @param member collided
	 * @param entity collided with
	 * @return True if collision is allowed, False if not
	 */
	public boolean execute(MinecartMember member, Entity entity) {
		switch (this) {
			case ENTER : 
				if (!member.hasPassenger() && member.canBeRidden() && Util.canBePassenger(entity)) {
					member.getBukkitEntity().setPassenger(entity);
				}
				return false;
			case PUSH :
				member.pushSideways(entity);
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
						((LivingEntity) entity).damage(Short.MAX_VALUE, member.getBukkitEntity());
						EntityUtil.setInvulnerable(entity, old);
					} else {
						entity.remove();
					}
					if (this == KILLNODROPS) {
						TCListener.cancelNextDrops = false;
					}
				}
				return false;
			default :
				return true;
		}
	}

	/**
	 * Gets the text for what this Collision Mode performs
	 * 
	 * @return collision operation name
	 */
	public String getOperationName() {
		switch (this) {
			case PUSH :
				return "pushes";
			case CANCEL :
				return "ignores";
			case KILL :
				return "kills";
			case KILLNODROPS :
				return "kills without drops";
			case ENTER :
				return "takes in";
			default :
				return "is stopped by";
		}
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
	 * Gets the Collision Mode as being a pushing state
	 * 
	 * @param state of pushing
	 * @return PUSH or NONE
	 */
	public static CollisionMode fromPushing(boolean state) {
		return state ? PUSH : DEFAULT;
	}

	/**
	 * Gets the Collision Mode as being an entering state
	 * 
	 * @param state of entering
	 * @return ENTER or NONE
	 */
	public static CollisionMode fromEntering(boolean state) {
		return state ? ENTER : DEFAULT;
	}
}
