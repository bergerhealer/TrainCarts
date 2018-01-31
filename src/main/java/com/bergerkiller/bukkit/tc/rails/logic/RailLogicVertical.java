package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

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
    public BlockFace getMovementDirection(BlockFace endDirection) {
        if (FaceUtil.isVertical(endDirection)) {
            return endDirection;
        }

        // This happens when we go onto the vertical rail from the side: Always go down
        return BlockFace.DOWN;
    }

    @Override
    public double getForwardVelocity(MinecartMember<?> member) {
        return member.getDirection().getModY() * member.getEntity().vel.getY();
    }

    @Override
    public void getFixedPosition(Vector position, IntVector3 railPos) {
        position.setX(railPos.midX());
        position.setZ(railPos.midZ());
    }

    @Override
    public void setForwardVelocity(MinecartMember<?> member, double force) {
        member.getEntity().vel.setY(member.getDirection().getModY() * force);
    }

    @Override
    public RailPath getPath() {
        if (this.railPath == RailPath.EMPTY) {
            // Initialize the rail path, making use of getFixedPosition for each node
            Vector p1 = new Vector(0.5, 0.0, 0.5);
            Vector p2 = new Vector(0.5, 1.0, 0.5);
            this.getFixedPosition(p1, IntVector3.ZERO);
            this.getFixedPosition(p2, IntVector3.ZERO);
            this.railPath = new RailPath.Builder()
                    .up(this.getDirection().getOppositeFace())
                    .add(p1).add(p2).build();
        }
        return this.railPath;
    }

    @Override
    public boolean hasVerticalMovement() {
        return true;
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        super.onPreMove(member);

        // Position update
        final CommonMinecart<?> entity = member.getEntity();
        Vector position = entity.loc.vector();
        this.getFixedPosition(position, member.getBlockPos());
        entity.loc.set(position);
    }
}
