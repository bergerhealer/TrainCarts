package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.offline.train.format.DataBlock;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

import java.io.DataInputStream;
import java.io.IOException;

public class MemberActionLaunchDirection extends MemberActionLaunch implements MovementAction {
    private BlockFace direction;
    private boolean directionWasCorrected;

    public MemberActionLaunchDirection() {
        this.direction = BlockFace.SELF;
        this.directionWasCorrected = false;
    }

    /**
     * Deprecated: use initDistance/initTime instead, combined with the default constructor
     */
    @Deprecated
    public MemberActionLaunchDirection(double targetdistance, double targetvelocity, final BlockFace direction) {
        this.setDirection(direction);
        this.initDistance(targetdistance, targetvelocity, direction);
    }

    public void init(LauncherConfig config, double targetvelocity, double targetspeedlimit, BlockFace direction) {
        this.setDirection(direction);
        this.init(config, targetvelocity, targetspeedlimit);
    }

    public void init(LauncherConfig config, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.init(config, targetvelocity);
    }

    public void initTime(int timeTicks, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.initTime(timeTicks, targetvelocity);
    }

    public void initDistance(double targetdistance, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.initDistance(targetdistance, targetvelocity);
    }

    public void setDirection(BlockFace direction) {
        if (direction == null) {
            throw new IllegalArgumentException("Direction is null");
        }

        this.direction = direction;
    }

    public BlockFace getDirection() {
        return this.direction;
    }

    public void setDirectionCorrected(boolean corrected) {
        this.directionWasCorrected = corrected;
    }

    /**
     * Gets whether the direction was corrected using the train's current position on the track.
     * This is done once to ensure the launch follows the same direction path along the track,
     * even if taking curves.
     *
     * @return True if the direction was corrected
     */
    public boolean isDirectionCorrected() {
        return directionWasCorrected;
    }

    @Override
    public boolean update() {
        boolean success = super.update();

        // Once speed of the train increases to the point it is moving, we
        // can correct direction to make sure it leads into the direction that
        // was configured. We then stop checking. The direction should only change
        // at the very beginning of the launch. We do this also when success is
        // true right away, to properly handle instantaneous launches. Note that
        // the member direction property can't be used for this, as it isn't updated
        // when the speed is changed.
        if (!this.directionWasCorrected) {
            Vector vel = this.getMember().getEntity().getVelocity();
            if (vel.lengthSquared() > 1e-20) {
                this.directionWasCorrected = true;
                if (vel.dot(FaceUtil.faceToVector(this.direction)) < 0.0) {
                    this.getGroup().reverse();
                }
            }
        }

        return success;
    }

    public static class Serializer extends BaseSerializer<MemberActionLaunchDirection> {
        @Override
        public MemberActionLaunchDirection create(DataBlock data) throws IOException {
            return new MemberActionLaunchDirection();
        }
    }

    public static abstract class BaseSerializer<T extends MemberActionLaunchDirection> extends MemberActionLaunch.BaseSerializer<T> {
        @Override
        public boolean save(T action, DataBlock data) throws IOException {
            super.save(action, data);

            // Save the direction information
            data.addChild("launch-direction", stream -> {
                Util.writeVariableLengthInt(stream, action.getDirection().ordinal());
                stream.writeBoolean(action.isDirectionCorrected());
            });
            return true;
        }

        @Override
        public T load(DataBlock data) throws IOException {
            T action = super.load(data);

            // Load the direction information
            try (DataInputStream stream = data.findChildOrThrow("launch-direction").readData()) {
                int blockFaceOrd = Util.readVariableLengthInt(stream);
                BlockFace[] faces = BlockFace.values(); // Can this change?
                action.setDirection((blockFaceOrd >= 0 && blockFaceOrd < faces.length)
                        ? faces[blockFaceOrd] : BlockFace.NORTH);
                action.setDirectionCorrected(stream.readBoolean());
            }

            return action;
        }
    };
}
