package com.bergerkiller.bukkit.tc;

import java.util.logging.Level;

import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicVerticalSlopeNormalA;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

/**
 * A collision mode between a Minecart and another Entity
 */
public enum CollisionMode {
    DEFAULT("is stopped by"), PUSH("pushes"), CANCEL("ignores"), KILL("kills"),
    KILLNODROPS("kills without drops"), ENTER("takes in"), LINK("forms a group with"),
    DAMAGE("damages"), DAMAGENODROPS("damages without drops"), SKIP("do not process");

    private final String operationName;

    CollisionMode(String operationName) {
        this.operationName = operationName;
    }

    /**
     * Parses a Collision Mode from a String
     *
     * @param text to parse
     * @return Collision Mode, or null if not parsed
     */
    public static CollisionMode parse(String text) {
        CollisionMode tf = ParseUtil.isBool(text) ? (ParseUtil.parseBool(text) ? DEFAULT : CANCEL) : null;
        return ParseUtil.parseEnum(CollisionMode.class, text, tf);
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
     * Executes this collision mode
     *
     * @param member collided
     * @param entity collided with
     * @return True if collision is allowed, False if not
     */
    public boolean execute(MinecartMember<?> member, Entity entity) {
        final CommonMinecart<?> minecart = member.getEntity();
        final MinecartMember<?> other = MinecartMemberStore.getFromEntity(entity);
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
            if (logic1 instanceof RailLogicVerticalSlopeNormalA) {
                RailLogic logic2 = other.getRailLogic();
                if (logic2 instanceof RailLogicVerticalSlopeNormalA) {
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

        // Determine if the player is pushing the train or getting run over by it
        if (this != CANCEL) {
            if (entity instanceof Player && this != SKIP) {
                // Get train's X and Z velocity
                double trainX = member.getLimitedVelocity().getX();
                double trainZ = member.getLimitedVelocity().getZ();

                // Get player's X and Z velocity
                double playerSpeed = ((Player) entity).getWalkSpeed();
                Vector playerVelocity = ((Player) entity).getEyeLocation().getDirection();
                playerVelocity.multiply(playerSpeed);
                double playerX = playerVelocity.getX();
                double playerZ = playerVelocity.getZ();

            /*
             * Make sure the player is moving before comparing speeds and directions.
             * If the player is not moving, then the train is running over the player,
             * and fall through to the regular logic below.
             */
                if (Math.abs(playerX) + Math.abs(playerZ) > 0.03) {
                    if (Math.abs(trainX) + Math.abs(trainZ) < 0.03) {
                        // Train isn't moving (much if at all). Return true to let player push train.
                        return true;
                    }
                    if (Math.abs(trainX) > Math.abs(trainZ) && playerX * trainX > 0 && Math.abs(playerX) > Math.abs(trainX)) {
                        // Player moving in same direction as train, faster than train. Return true to push train.
                        return true;
                    }
                    if (Math.abs(trainX) <= Math.abs(trainZ) && playerZ * trainZ >= 0 && Math.abs(playerZ) > Math.abs(trainZ)) {
                        // Player moving in same direction as train, faster than train. Return true to push train.
                        return true;
                    }
                }
            }
        }
        switch (this) {
            case ENTER:
                if (member.getAvailableSeatCount(entity) > 0 && Util.canBePassenger(entity) && member.canCollisionEnter()) {
                    minecart.addPassenger(entity);
                }
                return false;
            case PUSH:
                push(member, entity);
                return false;
            case CANCEL:
                return false;
            case DAMAGE:
            case DAMAGENODROPS:
                if (member.isMoving() && member.isHeadingTo(entity)) {
                    if (this == DAMAGENODROPS) {
                        TCListener.cancelNextDrops = true;
                    }
                    double minecartEnergy = member.getEntity().vel.lengthSquared() * member.getProperties().getTrainProperties().getCollisionDamage();
                    damage(member, entity, minecartEnergy);
                    push(member, entity);
                    if (this == DAMAGENODROPS) {
                        TCListener.cancelNextDrops = false;
                    }
                }
                return false;
            case KILLNODROPS:
            case KILL:
                if (member.isMoving() && member.isHeadingTo(entity)) {
                    if (this == KILLNODROPS) {
                        TCListener.cancelNextDrops = true;
                    }

                    MinecartMember<?> oldKilledByMember = TCListener.killedByMember;
                    try {
                        TCListener.killedByMember = member;
                        damage(member, entity, (double) Short.MAX_VALUE);
                    } finally {
                        TCListener.killedByMember = oldKilledByMember;
                    }

                    if (this == KILLNODROPS) {
                        TCListener.cancelNextDrops = false;
                    }
                }
                return false;
            case LINK:
                if (other != null) {
                    // Perform default linking logic
                    return !MinecartGroupStore.link(member, other);
                }
                return true;
            case SKIP:
                // Should not ever be called. If it is, do nothing.
                TrainCarts.plugin.log(Level.WARNING, "Collision mode SKIP should not be called");
                return false;
            default:
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

    /*
     * Push something after a minecart hits it
     */
    private void push(MinecartMember<?> member, Entity entity) {
        if (entity instanceof Minecart) {
            // Push the minecart (only when moving towards it)
            if (member.isHeadingTo(entity)) {
                double force;
                // Keeping distance
                //TODO: Needs to take cart size into account
                force = TCConfig.cartDistanceGap + 1.0 - member.getEntity().loc.distanceSquared(entity);
                force *= TCConfig.cartDistanceForcer;
                // Difference in velocity
                force += member.getRealSpeed() - entity.getVelocity().length();
                // Apply
                if (force > 0.0) {
                    member.push(entity, force);
                }
            }
        } else {
            member.pushSideways(entity);
        }
    }

    /*
     * Impart damage to an entity that a minecart hits
     */
    private void damage(MinecartMember<?> member, Entity entity, double damageAmount) {
        if (entity instanceof LivingEntity) {
            boolean old = EntityUtil.isInvulnerable(entity);
            EntityUtil.setInvulnerable(entity, false);
            ((LivingEntity) entity).damage(damageAmount, member.getEntity().getEntity());
            EntityUtil.setInvulnerable(entity, old);
        } else {
            EntityUtil.damage(entity, DamageCause.CUSTOM, (double) Short.MAX_VALUE);
            entity.remove();
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
}
