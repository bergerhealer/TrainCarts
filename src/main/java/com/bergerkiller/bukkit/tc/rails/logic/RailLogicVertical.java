package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class RailLogicVertical extends RailLogic {
    private static final RailLogicVertical[] values = new RailLogicVertical[4];
    public static final double XZ_POS_OFFSET = (0.5 - RailLogicHorizontal.Y_POS_OFFSET);

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
    public BlockFace getMovementDirection(BlockFace endDirection) {
        if (FaceUtil.isVertical(endDirection)) {
            return endDirection;
        }

        // This happens when we go onto the vertical rail from the side: Always go down
        return BlockFace.DOWN;
    }

    @Override
    public double getForwardVelocity(MinecartMember<?> member) {
        final CommonEntity<?> e = member.getEntity();

        double dot = member.getDirection().getModY() * e.vel.getY();
        return MathUtil.invert(e.vel.length(), dot < 0.0);
    }

    @Override
    public void setForwardVelocity(MinecartMember<?> member, double force) {
        member.getEntity().vel.setY(member.getDirection().getModY() * force);
    }

    @Override
    protected RailPath createPath() {
        // Initialize the rail path, making use of getFixedPosition for each node
        double dx = 0.5 + XZ_POS_OFFSET * this.getDirection().getModX();
        double dz = 0.5 + XZ_POS_OFFSET * this.getDirection().getModZ();
        Vector p1 = new Vector(dx, 0.0, dz);
        Vector p2 = new Vector(dx, 1.0, dz);
        return new RailPath.Builder()
                .up(this.getDirection().getOppositeFace())
                .add(p1).add(p2).build();
    }

    @Override
    public boolean hasVerticalMovement() {
        return true;
    }

}
