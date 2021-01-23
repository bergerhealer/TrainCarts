package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.properties.standard.type.SlowdownMode;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Handles movement of a minecart that is flying through the air
 */
public class RailLogicAir extends RailLogic {
    public static final RailLogicAir INSTANCE = new RailLogicAir();

    private RailLogicAir() {
        super(BlockFace.SELF);
    }

    @Override
    public double getGravityMultiplier(MinecartMember<?> member) {
        return MinecartMember.GRAVITY_MULTIPLIER;
    }

    @Override
    public void onSpacingUpdate(MinecartMember<?> member, Vector velocity, Vector factor) {
        double motLen = velocity.length();
        if (motLen > 0.01) {
            double f = motLen / member.getEntity().getMaxSpeed();
            velocity.setX(velocity.getX() + f * factor.getX() * TCConfig.cartDistanceForcer);
            velocity.setZ(velocity.getZ() + f * factor.getZ() * TCConfig.cartDistanceForcer);

            // To prevent glitchy air jumps, only do Y-level adjustment when moving very vertical
            if (member.isMovingVerticalOnly() || (member != member.getGroup().head() && member != member.getGroup().head())) {
                velocity.setY(velocity.getY() + f * factor.getY() * TCConfig.cartDistanceForcer);
            }
        }
    }

    @Override
    public void onUpdateOrientation(MinecartMember<?> member, Quaternion orientation) {
        CommonMinecart<?> entity = member.getEntity();
        Vector forward = new Vector(entity.getMovedX(), entity.getMovedY(), entity.getMovedZ());
        if (member.getGroup().size() > 1) {
            boolean has_delta = false;
            double dx = 0.0, dy = 0.0, dz = 0.0;
            if (member != member.getGroup().head()) {
                // Add difference between this cart and the cart before
                MinecartMember<?> m = member.getNeighbour(-1);
                if (m.isDerailed()) {
                    dx += m.getEntity().loc.getX() - member.getEntity().loc.getX();
                    dy += m.getEntity().loc.getY() - member.getEntity().loc.getY();
                    dz += m.getEntity().loc.getZ() - member.getEntity().loc.getZ();
                    has_delta = true;
                }
            }
            if (member != member.getGroup().tail()) {
                // Add difference between this cart and the cart after
                MinecartMember<?> m = member.getNeighbour(1);
                if (m.isDerailed()) {
                    dx += member.getEntity().loc.getX() - m.getEntity().loc.getX();
                    dy += member.getEntity().loc.getY() - m.getEntity().loc.getY();
                    dz += member.getEntity().loc.getZ() - m.getEntity().loc.getZ();
                    has_delta = true;
                }
            }
            if (has_delta) {
                forward.setX(dx);
                forward.setY(dy);
                forward.setZ(dz);
            }
        } else if (!member.getGroup().getProperties().isSlowingDown(SlowdownMode.GRAVITY)) {
            // When gravity is disabled, keep the original orientation of the Minecart.
            // This allows other plugins to freely control the 3d position and orientation
            // of the Minecart without movement changes causing disruptions.
            forward.multiply(0.0);
        }

        if (forward.lengthSquared() <= 1e-8) {
            // Forward vector is useless, just use the old quaternion
            member.setOrientation(orientation);
        } else {
            // Forward vector is useful
            if (forward.dot(orientation.forwardVector()) < 0.0) {
                forward.multiply(-1.0);
            }
            member.setOrientation(Quaternion.fromLookDirection(forward, orientation.upVector()));
        }
    }

    // Old updateOrientation logic - breaks vertical - air rails
    public void onUpdateOrientation_old(MinecartMember<?> member, Quaternion orientation) {
        CommonMinecart<?> entity = member.getEntity();
        boolean upsideDown = MathUtil.getAngleDifference(entity.loc.getPitch(), 180.0f) < 89.0f;
        float newYaw = member.getEntity().loc.getYaw();
        float newPitch = member.getEntity().loc.getPitch();
        if (member.getGroup().size() <= 1) {
            //Update yaw and pitch based on motion
            final double movedX = entity.getMovedX();
            final double movedY = entity.getMovedY();
            final double movedZ = entity.getMovedZ();
            final boolean movedXZ = Math.abs(movedX) > 0.001 || Math.abs(movedZ) > 0.001;

            // Update yaw
            if (Math.abs(movedX) > 0.01 || Math.abs(movedZ) > 0.01) {
                newYaw = MathUtil.getLookAtYaw(movedX, movedZ);
            }

            // Update pitch
            if (movedXZ && Math.abs(movedY) > 0.001) {
                // Use movement for pitch (but only when moving horizontally)
                newPitch = MathUtil.clamp(-MathUtil.getLookAtPitch(-movedX, -movedY, -movedZ), 89.9f);
                if (upsideDown) {
                    newPitch += 180.0f;
                }
            }
        } else {
            // Find our displayed angle based on the relative position of this Minecart to the neighbours
            int n = 0;
            double dx = 0.0, dy = 0.0, dz = 0.0;
            if (member != member.getGroup().head()) {
                // Add difference between this cart and the cart before
                MinecartMember<?> m = member.getNeighbour(-1);
                dx += m.getEntity().loc.getX() - member.getEntity().loc.getX();
                dy += m.getEntity().loc.getY() - member.getEntity().loc.getY();
                dz += m.getEntity().loc.getZ() - member.getEntity().loc.getZ();
                n++;
            }
            if (member != member.getGroup().tail()) {
                // Add difference between this cart and the cart after
                MinecartMember<?> m = member.getNeighbour(1);
                dx += member.getEntity().loc.getX() - m.getEntity().loc.getX();
                dy += member.getEntity().loc.getY() - m.getEntity().loc.getY();
                dz += member.getEntity().loc.getZ() - m.getEntity().loc.getZ();
                n++;
            }
            dx /= n;
            dy /= n;
            dz /= n;

            // Calculate yaw and pitch from dx/dy/dz
            if (MathUtil.lengthSquared(dx, dz) < 0.0001) {
                // Only has vertical motion. Stick to a 90-degree angle
                if (MathUtil.getAngleDifference(newPitch, 90.0f) < MathUtil.getAngleDifference(newPitch, -90.0f)) {
                    newPitch = 90.0f;
                } else {
                    newPitch = -90.0f;
                }
            } else {
                newYaw = MathUtil.getLookAtYaw(dx, dz);
                newPitch = MathUtil.getLookAtPitch(dx, dy, dz);
            }

            // Preserve upside-down state
            if (upsideDown) {
                newPitch += 180.0f;
            }
        }

        member.setRotationWrap(newYaw, newPitch);
    }

    @Override
    public BlockFace getMovementDirection(BlockFace endDirection) {
        return endDirection;
    }

    @Override
    public double getForwardVelocity(MinecartMember<?> member) {
        final CommonEntity<?> e = member.getEntity();

        if (member.getEntity().vel.xz.lengthSquared() == 0.0) {
            double dot = e.vel.getY() * member.getDirection().getModY();
            return MathUtil.invert(e.vel.length(), dot < 0.0);
        } else {
            return e.vel.length();
        }
    }

    @Override
    public void setForwardVelocity(MinecartMember<?> member, double force) {
        if (member.isMovementControlled()) {
            // Be sure to use the direction, we are being controlled!
            super.setForwardVelocity(member, force);
        } else if (member.getEntity().vel.xz.lengthSquared() == 0.0) {
            // Moving only vertically; control speed in order to maintain a vertical stack
            Vector vel = member.getEntity().vel.vector();
            MathUtil.setVectorLength(vel, force);
            member.getEntity().vel.set(vel);
        } else {
            // Simply set vector length
            // Setting speed while in the air causes pretty awful breakage, unfortunately
            // Free-falling looks more natural
            Vector vel = member.getEntity().vel.vector();
            MathUtil.setVectorLength(vel, force);
            member.getEntity().vel.set(vel);
        }
    }

    @Override
    public boolean hasVerticalMovement() {
        return true;
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        CommonMinecart<?> entity = member.getEntity();

        // Apply flying friction
        if (!member.isMovementControlled() && member.getGroup().getProperties().isSlowingDown(SlowdownMode.FRICTION)) {
            Vector flyingMod = entity.getFlyingVelocityMod();
            if (member.getGroup().getUpdateStepCount() > 1) {
                entity.vel.x.multiply(Math.pow(flyingMod.getX(), member.getGroup().getUpdateSpeedFactor()));
                entity.vel.y.multiply(Math.pow(flyingMod.getY(), member.getGroup().getUpdateSpeedFactor()));
                entity.vel.z.multiply(Math.pow(flyingMod.getZ(), member.getGroup().getUpdateSpeedFactor()));
            } else {
                entity.vel.multiply(flyingMod);
            }
        }
    }
}
