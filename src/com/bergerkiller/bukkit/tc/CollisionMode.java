package com.bergerkiller.bukkit.tc;

import org.bukkit.entity.Entity;

import com.bergerkiller.bukkit.common.utils.EnumUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * A collision mode between a Minecart and a non-minecart Entity
 */
public enum CollisionMode {
	DEFAULT, PUSH, CANCEL, KILL, ENTER;

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
			case KILL :
				if (member.isHeadingTo(entity)) {
					entity.remove();
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
		return EnumUtil.parse(CollisionMode.class, text, null);
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
