package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.utils.SlowdownMode;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class RailLogicVertical extends RailLogic {
    private static final RailLogicVertical[] values = new RailLogicVertical[4];

    static {
        for (int i = 0; i < 4; i++) {
            values[i] = new RailLogicVertical(FaceUtil.notchToFace(i << 1));
        }
    }

    private RailLogicVertical(BlockFace direction) {
        super(direction);
    }

    /**
     * Gets the vertical rail logic for the direction specified
     *
     * @param direction of the rail
     * @return Rail Logic
     */
    public static RailLogicVertical get(BlockFace direction) {
        return values[FaceUtil.faceToNotch(direction) >> 1];
    }

    @Override
    public BlockFace getMovementDirection(MinecartMember<?> member, BlockFace endDirection) {
        return (endDirection == BlockFace.UP) ? BlockFace.UP : BlockFace.DOWN;
    }

    @Override
    public double getForwardVelocity(MinecartMember<?> member) {
        return member.getDirection().getModY() * member.getEntity().vel.getY();
    }

    @Override
    public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
        return new Vector(railPos.midX(), y, railPos.midZ());
    }

    @Override
    public void setForwardVelocity(MinecartMember<?> member, double force) {
        member.getEntity().vel.setY(member.getDirection().getModY() * force);
    }

    @Override
    public boolean hasVerticalMovement() {
        return true;
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
    public void onPreMove(MinecartMember<?> member) {
        final CommonMinecart<?> entity = member.getEntity();
        // Horizontal rail force to motY
        entity.vel.y.add(entity.vel.xz.length() * member.getDirection().getModY());
        entity.vel.xz.setZero();
        // Position update
        entity.loc.set(this.getFixedPosition(entity, entity.loc.getX(), entity.loc.getY(), entity.loc.getZ(), member.getBlockPos()));
    }
}
