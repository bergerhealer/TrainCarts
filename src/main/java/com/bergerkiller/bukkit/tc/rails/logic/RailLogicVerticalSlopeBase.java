package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

public abstract class RailLogicVerticalSlopeBase extends RailLogicSloped {

    public RailLogicVerticalSlopeBase(BlockFace direction, boolean upsideDown) {
        super(direction, upsideDown);
    }

    /**
     * Gets whether a particular y-position of the Minecart is the vertical portion of this vertical-slope
     * 
     * @param y
     * @param blockPos
     * @return True if vertical half
     */
    protected abstract boolean isVerticalHalf(double y, IntVector3 blockPos);

    /**
     * Gets the y-coordinate offset relative to the middle of the block,
     * where the rail logic changes between sloped and vertical.
     * 
     * @return half offset
     */
    protected abstract double getHalfOffset();

    /**
     * Gets the vertical offset of the Minecart on the sloped rail portion
     * 
     * @return y offset
     */
    protected double getYOffset() {
        return 0.0;
    }

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
            Vector position = entity.loc.vector();
            this.getFixedPosition(position, member.getBlockPos());
            entity.loc.set(position);
        } else {
            // Slope part
            super.onPreMove(member);
        }
    }

    @Override
    public void onPostMove(MinecartMember<?> member) {
        final CommonMinecart<?> entity = member.getEntity();
        IntVector3 railPos = member.getBlockPos();
        boolean isVertical = this.isVerticalHalf(entity.loc.getY(), railPos);

        // Restrain vertical or sloped movement
        Vector position = entity.loc.vector();
        this.getFixedPosition(position, railPos);
        entity.loc.set(position);

        // Do sloped rail logic. Convert Y-velocity into X/Z velocity.
        if (!isVertical) {
            entity.vel.xz.add(this.getDirection(), entity.vel.getY());
            entity.vel.y.setZero();
            super.onPostMove(member);
        }
    }

    @Override
    public void getFixedPosition(Vector position, IntVector3 railPos) {
        // When dy >= 0.5 of block, move vertical, sloped logic will not apply
        if (isVerticalHalf(position.getY(), railPos)) {
            position.setX(railPos.midX());
            position.setZ(railPos.midZ());
            return;
        }

        // Execute default sloped logic
        super.getFixedPosition(position, railPos);
        position.setY(position.getY() + this.getYOffset());

        // When crossing the boundary to vertical, fix the x/z positions
        if (isVerticalHalf(position.getY(), railPos)) {
            position.setX(railPos.midX());
            position.setZ(railPos.midZ());
        }
    }

    @Override
    public double getGravityMultiplier(MinecartMember<?> member) {
        if (member.getGroup().getProperties().isSlowingDown(SlowdownMode.GRAVITY)) {
            return TCConfig.legacyVerticalGravity ? 
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

    public final double getVertFactor(MinecartMember<?> member) {
        BlockFace dir = member.getDirection();
        if (FaceUtil.isVertical(dir)) {
            return dir.getModY();
        } else {
            return (dir == this.getDirection()) ? 1.0 : -1.0;
        }
    }
}
