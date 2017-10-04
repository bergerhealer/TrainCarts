package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

public abstract class RailLogicVerticalSlopeBase extends RailLogicSloped {

    public RailLogicVerticalSlopeBase(BlockFace direction, boolean upsideDown) {
        super(direction, upsideDown);
    }

    /**
     * Gets the ratio value between slope and vertical rail, defining where the boundary is at.
     * A value of 0 indicates exactly in the middle. A positive value indicates this is the vertical
     * rail section.
     * 
     * @param y
     * @param blockPos
     * @return slope ratio
     */
    public abstract double getSlopeRatio(double y, IntVector3 blockPos);

    /**
     * Gets whether the slope is reversed, indicating the velocity should be applied
     * in reverse.
     * 
     * @return True if reversed slope
     */
    public abstract boolean isReversedSlope();

    @Override
    public boolean hasVerticalMovement() {
        return true;
    }

    @Override
    public double getForwardVelocity(MinecartMember<?> member) {
        if (isVerticalHalf(member)) {
            return member.getEntity().vel.getY() * getVertFactor(member);            
        } else {
            return super.getForwardVelocity(member);
        }
    }

    @Override
    public void setForwardVelocity(MinecartMember<?> member, double force) {
        if (isVerticalHalf(member)) {
            member.getEntity().vel.set(0.0, force * getVertFactor(member), 0.0);
        } else {
            super.setForwardVelocity(member, force);
        }
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        final CommonMinecart<?> entity = member.getEntity();
        if (isVerticalHalf(entity.loc.getY(), member.getBlockPos())) {
            // Vertical part
            entity.vel.y.add(entity.vel.getX() * this.getDirection().getModX() +
                             entity.vel.getZ() * this.getDirection().getModZ());

            entity.vel.xz.setZero();

            // Restrain position before move
            entity.loc.set(this.getFixedPosition(entity, entity.loc.getX(), entity.loc.getY(), entity.loc.getZ(), member.getBlockPos()));
        } else {
            // Slope part
            super.onPreMove(member);
        }
    }

    @Override
    public void onPostMove(MinecartMember<?> member) {
        final CommonMinecart<?> entity = member.getEntity();
        IntVector3 railPos = member.getBlockPos();
        double slope_move_y = getSlopeRatio(entity.loc.getY(), member.getBlockPos());
        if (slope_move_y >= 0.0) {
            // Restrain vertical movement to within fixed x/z
            entity.loc.set(getFixedPosition(entity, entity.loc.getX(), entity.loc.getY(), entity.loc.getZ(), railPos));
        } else {
            // We may have moved vertically before
            // Correct the x/z position of the minecart so it follows slope logic
            if (this.isReversedSlope()) {
                if (entity.vel.getY() > 0.0) {
                    entity.loc.setX(railPos.midX() - (slope_move_y * this.getDirection().getModX()));
                    entity.loc.setZ(railPos.midZ() - (slope_move_y * this.getDirection().getModZ()));
                }
            } else {
                if (entity.vel.getY() < 0.0) {
                    entity.loc.setX(railPos.midX() + (slope_move_y * this.getDirection().getModX()));
                    entity.loc.setZ(railPos.midZ() + (slope_move_y * this.getDirection().getModZ()));
                }
            }

            // We do not use the y-velocity on this part; move it over to x/z
            entity.vel.xz.add(this.getDirection(), entity.vel.getY());
            entity.vel.y.setZero();

            // Slope part
            super.onPostMove(member);
        }
    }

    @Override
    public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
        // When dy >= 0.5 of block, move vertical, sloped logic will not apply
        if (isVerticalHalf(y, railPos)) {
            return new Vector(railPos.midX(), y, railPos.midZ());
        }

        // Execute default sloped logic
        Vector pos = super.getFixedPosition(entity, x, y, z, railPos);

        // When crossing the boundary to vertical, fix the x/z positions
        if (isVerticalHalf(pos.getY(), railPos)) {
            pos.setX(railPos.midX());
            pos.setZ(railPos.midZ());
        }
        return pos;
    }

    @Override
    public double getGravityMultiplier(MinecartMember<?> member) {
        if (member.getGroup().getProperties().isSlowingDown(SlowdownMode.GRAVITY)) {
            return TrainCarts.legacyVerticalGravity ? 
                    MinecartMember.VERTRAIL_MULTIPLIER_LEGACY : MinecartMember.SLOPE_VELOCITY_MULTIPLIER;
        }
        return 0.0;
    }

    @Override
    protected boolean checkSlopeBlockCollisions() {
        return false;
    }

    protected final boolean isVerticalHalf(MinecartMember<?> member) {
        return isVerticalHalf(member.getEntity().loc.getY(), member.getBlockPos());
    }

    protected final boolean isVerticalHalf(double y, IntVector3 blockPos) {
        return getSlopeRatio(y, blockPos) >= 0.0;
    }

    public final double getVertFactor(MinecartMember<?> member) {
        BlockFace dir = member.getDirection();
        if (FaceUtil.isVertical(dir)) {
            return dir.getModY();
        } else {
            return (dir == this.getDirection()) ? 1.0 : -1.0;
        }
    }
}
