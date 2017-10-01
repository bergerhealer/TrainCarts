package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

/**
 * Handles rail logic from a vertical rail to an upside-down slope
 */
public class RailLogicVerticalSlopeUpsideDown extends RailLogicSloped {
    private static final RailLogicVerticalSlopeUpsideDown[] values = new RailLogicVerticalSlopeUpsideDown[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVerticalSlopeUpsideDown(FaceUtil.notchToFace(i << 1));
        }
    }

    private RailLogicVerticalSlopeUpsideDown(BlockFace direction) {
        super(direction, true);
    }

    /**
     * Gets the sloped-vertical rail logic for the the sloped track leading upwards facing the direction specified
     *
     * @param direction of the sloped rail
     * @return Rail Logic
     */
    public static RailLogicVerticalSlopeUpsideDown get(BlockFace direction) {
        return values[FaceUtil.faceToNotch(direction) >> 1];
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
            if (entity.vel.getY() > 0.0) {
                entity.loc.setX(railPos.midX() - (slope_move_y * this.getDirection().getModX()));
                entity.loc.setZ(railPos.midZ() - (slope_move_y * this.getDirection().getModZ()));
            }

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

    private static final boolean isVerticalHalf(MinecartMember<?> member) {
        return isVerticalHalf(member.getEntity().loc.getY(), member.getBlockPos());
    }

    private static final boolean isVerticalHalf(double y, IntVector3 blockPos) {
        return getSlopeRatio(y, blockPos) >= 0.0;
    }

    private static final double getSlopeRatio(double y, IntVector3 blockPos) {
        double d = ((double) blockPos.y - y - 0.6);
        
        //System.out.println("POS " + d);
        return d;
    }

    private final double getVertFactor(MinecartMember<?> member) {
        BlockFace dir = member.getDirection();
        if (FaceUtil.isVertical(dir)) {
            return dir.getModY();
        } else {
            return (dir == this.getDirection()) ? 1.0 : -1.0;
        }
    }
}
