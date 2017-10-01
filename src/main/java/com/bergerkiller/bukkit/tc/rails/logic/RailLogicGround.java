package com.bergerkiller.bukkit.tc.rails.logic;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.entity.type.CommonMinecart;
import com.bergerkiller.bukkit.common.utils.MathUtil;
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
    public void onRotationUpdate(MinecartMember<?> member) {
        //Update yaw and pitch based on motion
        CommonMinecart<?> entity = member.getEntity();
        final double movedX = entity.getMovedX();
        final double movedZ = entity.getMovedZ();
        final float oldyaw = entity.loc.getYaw();
        float newyaw = oldyaw;
        float newpitch = entity.loc.getPitch();
        boolean orientPitch = false;

        // Update yaw
        if (Math.abs(movedX) > 0.01 || Math.abs(movedZ) > 0.01) {
            newyaw = MathUtil.getLookAtYaw(movedX, movedZ);
        }

        // Reduce pitch over time
        if (Math.abs(newpitch) > 0.1) {
            newpitch *= 0.1;
        } else {
            newpitch = 0;
        }
        orientPitch = true;

        member.setRotationWrap(newyaw, newpitch, orientPitch);
    }

    @Override
    public BlockFace getMovementDirection(MinecartMember<?> member, BlockFace endDirection) {
        return endDirection;
    }

    @Override
    public boolean hasVerticalMovement() {
        return true;
    }

    @Override
    public Vector getFixedPosition(CommonMinecart<?> entity, double x, double y, double z, IntVector3 railPos) {
        return new Vector(x, y, z);
    }

    @Override
    public void onPreMove(MinecartMember<?> member) {
        // Apply ground friction
        if (!member.isMovementControlled()) {
            member.getEntity().vel.multiply(member.getEntity().getDerailedVelocityMod());
        }
    }
}
