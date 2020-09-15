package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Handles the rail logic of a minecart sliding on the ground
 */
public class RailLogicGround extends RailLogic {
    public static final RailLogicGround INSTANCE = new RailLogicGround();

    private RailLogicGround() {
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
        }
    }

    @Override
    public void onGravity(MinecartMember<?> member, double gravityFactorSquared) {
        CommonMinecart<?> e = member.getEntity();
        e.vel.y.subtract(gravityFactorSquared * getGravityMultiplier(member));
    }

    @Override
    public double getForwardVelocity(MinecartMember<?> member) {
        CommonEntity<?> e = member.getEntity();
        BlockFace direction = member.getDirection();
        double vel = 0.0;
        vel += e.vel.getX() * FaceUtil.cos(direction);
        vel += e.vel.getZ() * FaceUtil.sin(direction);
        return vel;
    }

    @Override
    public void setForwardVelocity(MinecartMember<?> member, double force) {
        final CommonEntity<?> e = member.getEntity();

        if (e.vel.getY() > 0.0) {
            // Flying up from the ground, set vector length instead
            Vector vel = e.vel.vector();
            MathUtil.setVectorLength(vel, force);
            e.vel.set(vel);
        } else {
            // Sliding along the ground, apply forward vector but preserve gravity
            // This makes sure a slowdown is applied during movement behavior
            e.vel.set(force * FaceUtil.cos(member.getDirection()),
                      e.vel.getY(),
                      force * FaceUtil.sin(member.getDirection()));
        }
    }

    @Override
    public void onUpdateOrientation(MinecartMember<?> member, Quaternion orientation) {
        //Update yaw and pitch based on motion
        CommonMinecart<?> entity = member.getEntity();
        final double movedX = entity.getMovedX();
        final double movedZ = entity.getMovedZ();
        final float oldyaw = entity.loc.getYaw();
        float newyaw = oldyaw;
        float newpitch = entity.loc.getPitch();
        boolean upsideDown = (newpitch <= -91.0f || newpitch >= 91.0f);

        // Update yaw
        if (Math.abs(movedX) > 0.01 || Math.abs(movedZ) > 0.01) {
            newyaw = MathUtil.getLookAtYaw(movedX, movedZ);
        }

        // Reduce pitch over time
        if (upsideDown) {
            newpitch = MathUtil.wrapAngle(newpitch + 180.0f);
        }
        if (Math.abs(newpitch) > 0.1) {
            newpitch *= 0.1;
        } else {
            newpitch = 0;
        }
        if (upsideDown) {
            newpitch += 180.0f;
        }

        member.setRotationWrap(newyaw, newpitch);
    }

    @Override
    public BlockFace getMovementDirection(BlockFace endDirection) {
        return endDirection;
    }

    @Override
    public boolean hasVerticalMovement() {
        return true;
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        // Apply ground friction
        if (!member.isMovementControlled()) {
            member.getEntity().vel.multiply(member.getEntity().getDerailedVelocityMod());
        }
    }
}
