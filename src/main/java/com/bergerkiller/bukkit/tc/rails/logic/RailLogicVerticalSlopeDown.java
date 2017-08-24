package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/**
 * Handles the rail logic of a sloped rail with a vertical rail above
 */
public class RailLogicVerticalSlopeDown extends RailLogicSloped {
    private static final RailLogicVerticalSlopeDown[] values = new RailLogicVerticalSlopeDown[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVerticalSlopeDown(FaceUtil.notchToFace(i << 1));
        }
    }

    private RailLogicVerticalSlopeDown(BlockFace direction) {
        super(direction);
    }

    /**
     * Gets the sloped-vertical rail logic for the the sloped track leading upwards facing the direction specified
     *
     * @param direction of the sloped rail
     * @return Rail Logic
     */
    public static RailLogicVerticalSlopeDown get(BlockFace direction) {
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
        if (entity.loc.getY() >= (member.getBlockPos().midY() + Y_POS_OFFSET)) {
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
        double slope_move_y = entity.loc.getY() - (member.getBlockPos().midY() + Y_POS_OFFSET);
        if (slope_move_y >= 0.0) {
            // Restrain vertical movement to within fixed x/z
            entity.loc.set(getFixedPosition(entity, entity.loc.getX(), entity.loc.getY(), entity.loc.getZ(), railPos));
        } else {
            // We may have moved vertically downwards before
            // Correct the x/z position of the minecart so it follows slope logic
            if (entity.vel.getY() < 0.0) {
                entity.loc.setX(railPos.midX() + (slope_move_y * this.getDirection().getModX()));
                entity.loc.setZ(railPos.midZ() + (slope_move_y * this.getDirection().getModZ()));
            }

            // Slope part
            super.onPostMove(member);
        }
    }

    @Override
    public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
        // When dy >= 0.5 of block, move vertical, sloped logic will not apply
        if (y >= (railPos.midY() + Y_POS_OFFSET)) {
            return new Vector(railPos.midX(), y, railPos.midZ());
        }

        // Execute default sloped logic
        Vector pos = super.getFixedPosition(entity, x, y, z, railPos);

        // When crossing the boundary to vertical, fix the x/z positions
        if (pos.getY() >= (railPos.midY() + Y_POS_OFFSET)) {
            pos.setX(railPos.x + 0.5);
            pos.setZ(railPos.z + 0.5);
        }
        return pos;
    }

    @Override
    public double getGravityMultiplier(MinecartMember<?> member) {
        return member.getGroup().getProperties().isSlowingDown() ? MinecartMember.VERTRAIL_MULTIPLIER : 0.0;
    }

    private static final boolean isVerticalHalf(MinecartMember<?> member) {
        return isVerticalHalf(member.getEntity().loc.getY(), member.getBlockPos());
    }

    private static final boolean isVerticalHalf(double y, IntVector3 blockPos) {
        return y >= (blockPos.midY() + Y_POS_OFFSET);
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
