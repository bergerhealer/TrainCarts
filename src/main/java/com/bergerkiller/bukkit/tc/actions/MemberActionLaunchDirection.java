package com.bergerkiller.bukkit.tc.actions;

import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.controller.components.ActionTracker;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.utils.LauncherConfig;

import java.io.DataInputStream;
import java.io.IOException;

public class MemberActionLaunchDirection extends MemberActionLaunch implements MovementAction {
    private BlockFace direction;
    private Vector directionVector;
    private boolean isFaceDirection;
    private boolean directionWasCorrected;

    public MemberActionLaunchDirection() {
        this.direction = BlockFace.SELF;
        this.directionVector = new Vector();
        this.isFaceDirection = true;
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

    public void init(LauncherConfig config, double targetvelocity, double targetspeedlimit, Vector direction) {
        this.setDirection(direction);
        this.init(config, targetvelocity, targetspeedlimit);
    }

    public void init(LauncherConfig config, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.init(config, targetvelocity);
    }

    public void init(LauncherConfig config, double targetvelocity, Vector direction) {
        this.setDirection(direction);
        this.init(config, targetvelocity);
    }

    public void initTime(int timeTicks, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.initTime(timeTicks, targetvelocity);
    }

    public void initTime(int timeTicks, double targetvelocity, Vector direction) {
        this.setDirection(direction);
        this.initTime(timeTicks, targetvelocity);
    }

    public void initDistance(double targetdistance, double targetvelocity, BlockFace direction) {
        this.setDirection(direction);
        this.initDistance(targetdistance, targetvelocity);
    }

    public void initDistance(double targetdistance, double targetvelocity, Vector direction) {
        this.setDirection(direction);
        this.initDistance(targetdistance, targetvelocity);
    }

    public void setDirection(BlockFace direction) {
        if (direction == null) {
            throw new IllegalArgumentException("Direction is null");
        }

        this.direction = direction;
        this.directionVector = (direction == BlockFace.SELF)
                ? new Vector() : FaceUtil.faceToVector(direction).normalize();
        this.isFaceDirection = true;
    }

    public void setDirection(Vector direction) {
        if (direction == null) {
            throw new IllegalArgumentException("Direction is null");
        }

        this.direction = FaceUtil.vectorToBlockFace(direction, true);
        this.directionVector = direction;
        this.isFaceDirection = false;
    }

    public BlockFace getDirection() {
        return this.direction;
    }

    public Vector getDirectionVector() {
        return this.directionVector;
    }

    /**
     * Gets whether the direction launched into is an exact BlockFace direction.
     * Basically tracks which of the setDirection() methods was used to set up
     * this launch action. Internal use, mostly.
     *
     * @return True if the direction is that of a BlockFace
     */
    public boolean isFaceDirection() {
        return this.isFaceDirection;
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
                if (vel.dot(this.directionVector) < 0.0) {
                    this.getGroup().reverse();
                }
            }
        }

        return success;
    }

    public static class Serializer extends BaseSerializer<MemberActionLaunchDirection> {
        @Override
        public MemberActionLaunchDirection create(OfflineDataBlock data) throws IOException {
            return new MemberActionLaunchDirection();
        }
    }

    public static abstract class BaseSerializer<T extends MemberActionLaunchDirection> extends MemberActionLaunch.BaseSerializer<T> {
        @Override
        public boolean save(T action, OfflineDataBlock data, ActionTracker tracker) throws IOException {
            super.save(action, data, tracker);

            // Save the direction information
            data.addChild("launch-direction", stream -> {
                Util.writeVariableLengthInt(stream, action.getDirection().ordinal());
                stream.writeBoolean(action.isDirectionCorrected());
            });
            if (!action.isFaceDirection()) {
                data.addChild("launch-direction-vector", stream -> {
                    Vector v = action.getDirectionVector();
                    stream.writeDouble(v.getX());
                    stream.writeDouble(v.getY());
                    stream.writeDouble(v.getZ());
                });
            }

            return true;
        }

        @Override
        public T load(OfflineDataBlock data, ActionTracker tracker) throws IOException {
            T action = super.load(data, tracker);

            // Load the direction information
            try (DataInputStream stream = data.findChildOrThrow("launch-direction").readData()) {
                int blockFaceOrd = Util.readVariableLengthInt(stream);
                BlockFace[] faces = BlockFace.values(); // Can this change?
                action.setDirection((blockFaceOrd >= 0 && blockFaceOrd < faces.length)
                        ? faces[blockFaceOrd] : BlockFace.NORTH);
                action.setDirectionCorrected(stream.readBoolean());
            }

            // If a direction vector was also set, that one overrides the BlockFace one
            // We keep the direction corrected information we read before
            data.tryReadChild("launch-direction-vector", stream -> {
                Vector v = new Vector(stream.readDouble(), stream.readDouble(), stream.readDouble());
                action.setDirection(v);
            });

            return action;
        }
    };
}
