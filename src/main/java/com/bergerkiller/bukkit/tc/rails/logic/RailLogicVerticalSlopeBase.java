package com.bergerkiller.bukkit.tc.rails.logic;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

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
    public void onPreMove(MinecartMember<?> member) {
        // Select correct segment to align the velocity along
        final CommonMinecart<?> entity = member.getEntity();
        RailPath.Segment s;
        if (isVerticalHalf(entity.loc.getY(), member.getBlockPos())) {
            // Vertical part (select correct segment)
            s = getPath().getSegments()[0];
            if (s.dt_norm.x != 0.0 || s.dt_norm.z != 0.0) {
                s = getPath().getSegments()[1];
            }
        } else {
            // Slope part (select correct segment)
            s = getPath().getSegments()[1];
            if (s.dt_norm.x == 0.0 && s.dt_norm.z == 0.0) {
                s = getPath().getSegments()[0];
            }
        }

        // Align velocity along the segment we are at
        RailPath.Position pos = new RailPath.Position();
        pos.setMotion(member.getDirection());
        s.calcDirection(pos);
        double vel = entity.vel.length();
        entity.vel.set(pos.motX * vel, pos.motY * vel, pos.motZ * vel);

        // Restrain position before move
        Vector position = entity.loc.vector();
        this.getFixedPosition(position, member.getBlockPos());
        entity.loc.set(position);
    }

    @Override
    public void onPostMove(MinecartMember<?> member) {
        // Restrain vertical or sloped movement
        final CommonMinecart<?> entity = member.getEntity();
        Vector position = entity.loc.vector();
        this.getFixedPosition(position, member.getBlockPos());
        entity.loc.set(position);
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
