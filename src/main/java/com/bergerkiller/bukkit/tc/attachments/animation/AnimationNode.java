package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.Collection;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * A single keyframe of an animation
 */
public class AnimationNode implements Cloneable {
    private final Vector _position;
    private Vector _rotationVec; // rotation as yaw/pitch/roll angles
    private Quaternion _rotationQuat; // rotation as Quaternion
    private final boolean _active;
    private final double _duration;

    /**
     * Initializes a new Animation Node with a position, rotation and duration to the next node.
     * The rotation is initialized using a Quaternion, with the yaw/pitch/roll rotation vector
     * initialized on first use.
     * 
     * @param position, null to deactivate the attachment
     * @param rotationQuaternion
     * @param active whether the attachment is active (and visible) when this node is reached
     * @param duration
     */
    public AnimationNode(Vector position, Quaternion rotationQuaternion, boolean active, double duration) {
        this._position = position;
        this._rotationVec = null;
        this._rotationQuat = rotationQuaternion;
        this._active = active;
        this._duration = duration;
    }

    /**
     * Initializes a new Animation Node with a position, rotation and duration to the next node.
     * The rotation is initialized using a yaw/pitch/roll vector, with the quaternion rotation
     * initialized on first use.
     * 
     * @param position, null to deactivate the attachment
     * @param rotationVector
     * @param active whether the attachment is active (and visible) when this node is reached
     * @param duration
     */
    public AnimationNode(Vector position, Vector rotationVector, boolean active, double duration) {
        this._position = position;
        this._rotationVec = rotationVector;
        this._rotationQuat = null;
        this._active = active;
        this._duration = duration;
    }

    /**
     * Gets the relative position of this animation node.
     * 
     * @return relative position
     */
    public Vector getPosition() {
        return this._position;
    }

    /**
     * Gets the relative rotation of this animation node as a Vector.
     * This value is used when displayed to a human, and not
     * for actual calculations during the animation.
     * 
     * @return relative rotation vector (x=pitch, y=yaw, z=roll)
     */
    public Vector getRotationVector() {
        if (this._rotationVec == null) {
            this._rotationVec = this._rotationQuat.getYawPitchRoll();
        }
        return this._rotationVec;
    }

    /**
     * Gets the relative rotation of this animation node as a Quaternion.
     * This value is used when performing the actual calculations during
     * the animation, and is not suitable to display to a human.
     * 
     * @return relative rotation quaternion
     */
    public Quaternion getRotationQuaternion() {
        if (this._rotationQuat == null) {
            this._rotationQuat = Quaternion.fromYawPitchRoll(this._rotationVec);
        }
        return this._rotationQuat;
    }

    /**
     * Duration animating from this node to the next one in seconds.
     * For the last node, the duration is used when looping around to the beginning.
     * 
     * @return duration
     */
    public double getDuration() {
        return this._duration;
    }

    /**
     * Gets whether the attachment is active during the time this animation
     * node is actively playing. An inactive attachment is generally invisible.
     * 
     * @return active
     */
    public boolean isActive() {
        return this._active;
    }

    /**
     * Applies the position and rotation transformation of this animation node to a 4x4 transformation matrix
     * 
     * @param transform
     */
    public void apply(Matrix4x4 transform) {
        transform.translate(this.getPosition());
        transform.rotate(this.getRotationQuaternion());
    }

    /**
     * Serializes the contents of this AnimationNode to a String that
     * {@link #parseFromString(String)} accepts as input.
     * 
     * @return serialized String
     */
    public String serializeToString() {
        Vector pos = this.getPosition();
        Vector ypr = this.getRotationVector().clone();
        ypr.setX(MathUtil.round(ypr.getX(), 6));
        ypr.setY(MathUtil.round(ypr.getY(), 6));
        ypr.setZ(MathUtil.round(ypr.getZ(), 6));

        StringBuilder builder = new StringBuilder(90);
        builder.append("t=").append(this._duration);

        if (!this.isActive())  builder.append(" active=0");
        if (pos.getX() != 0.0) builder.append(" x=").append(pos.getX());
        if (pos.getY() != 0.0) builder.append(" y=").append(pos.getY());
        if (pos.getZ() != 0.0) builder.append(" z=").append(pos.getZ());
        if (ypr.getX() != 0.0) builder.append(" pitch=").append(ypr.getX());
        if (ypr.getY() != 0.0) builder.append(" yaw=").append(ypr.getY());
        if (ypr.getZ() != 0.0) builder.append(" roll=").append(ypr.getZ());

        return builder.toString();
    }

    @Override
    public AnimationNode clone() {
        return new AnimationNode(this._position.clone(), this._rotationVec.clone(), this._active, this._duration);
    }

    /**
     * Parses the contents of an animation node from a configuration String.
     * Invalid configuration will produce an identity node.
     * 
     * @param config
     * @return animation node
     */
    public static AnimationNode parseFromString(String config) {
        // x=50.3 y=30.2 z=0.63 yaw=0.32 pitch=330.2 roll=-332.3
        // x50.3z0.63yaw0.32pitch330.2roll-332.3y=30.2
        // Parsing is very robust, handling lack (or over-abundance) of spaces or =
        // Out of order works too.
        Vector position = new Vector();
        Vector rotation = new Vector();
        boolean active = true;
        double time = 1.0;
        int index = 0;
        int name_start, name_len, value_start, value_end;
        double value;
        int config_length = config.length();
        while (true) {

            // Find start of name, skipping non-letters
            while (index < config_length && !Character.isLetter(config.charAt(index))) {
                index++;
            }
            name_start = index;

            // Find end of word
            while (index < config_length && Character.isLetter(config.charAt(index))) {
                index++;
            }
            name_len = index - name_start;

            // Find start of value, skipping non-numeric and non-.
            while (index < config_length && !isNumericChar(config.charAt(index))) {
                index++;
            }
            value_start = index;

            // Find end of value
            while (index < config_length && isNumericChar(config.charAt(index))) {
                index++;
            }
            value_end = index;

            // Abort if index is out of range somehow. Should never happen, but to be safe...
            if (value_start >= config_length) {
                break;
            }

            // Parse value
            try {
                value = Double.parseDouble(config.substring(value_start, value_end));
            } catch (NumberFormatException ex) {
                value = 0.0; // meh.
            }

            if ("t".regionMatches(0, config, name_start, name_len)) {
                time = value;
            } else if ("x".regionMatches(0, config, name_start, name_len)) {
                position.setX(value);
            } else if ("y".regionMatches(0, config, name_start, name_len)) {
                position.setY(value);
            } else if ("z".regionMatches(0, config, name_start, name_len)) {
                position.setZ(value);
            } else if ("pitch".regionMatches(0, config, name_start, name_len)) {
                rotation.setX(value);
            } else if ("yaw".regionMatches(0, config, name_start, name_len)) {
                rotation.setY(value);
            } else if ("roll".regionMatches(0, config, name_start, name_len)) {
                rotation.setZ(value);
            } else if ("active".regionMatches(0, config, name_start, name_len)) {
                active = (value != 0.0);
            }
        }
        return new AnimationNode(position, rotation, active, time);
    }

    /**
     * Parses all nodes in a list of configurations to an array of animation nodes.
     * 
     * @param configList
     * @return array of animation nodes with the same length as configList
     */
    public static AnimationNode[] parseAllFromStrings(List<String> configList) {
        AnimationNode[] nodes = new AnimationNode[configList.size()];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = AnimationNode.parseFromString(configList.get(i));
        }
        return nodes;
    }

    /**
     * Interpolates two nodes
     * 
     * @param nodeA
     * @param nodeB
     * @param theta
     * @return interpolated node
     */
    public static AnimationNode interpolate(AnimationNode nodeA, AnimationNode nodeB, double theta) {
        if (theta <= 0.0) {
            return nodeA;
        } else if (theta >= 1.0) {
            return nodeB;
        } else {
            Vector lerp_position = MathUtil.lerp(nodeA.getPosition(), nodeB.getPosition(), theta);
            Quaternion lerp_rotation = Quaternion.slerp(nodeA.getRotationQuaternion(), nodeB.getRotationQuaternion(), theta);
            return new AnimationNode(lerp_position, lerp_rotation, nodeA.isActive(), 1.0);
        }
    }

    /**
     * Creates an identity animation node (that does not do anything)
     * 
     * @return identity
     */
    public static AnimationNode identity() {
        return new AnimationNode(new Vector(), new Quaternion(), true, 1.0);
    }

    /**
     * Computes the average transformation of multiple animation nodes. If only
     * one node exists, then that node is returned. If no nodes are specified,
     * identity is returned.
     * 
     * @param nodes
     * @return average animation transformations of all nodes
     */
    public static AnimationNode average(Collection<AnimationNode> nodes) {
        if (nodes.size() == 1) {
            return nodes.iterator().next();
        } else if (nodes.isEmpty()) {
            return identity();
        }

        double fact = 1.0 / (double) nodes.size();
        Vector pos = new Vector();
        Vector rot = new Vector();

        int num_active = 0;
        double duration = 0.0;
        for (AnimationNode node : nodes) {
            pos.setX(pos.getX() + fact * node.getPosition().getX());
            pos.setY(pos.getY() + fact * node.getPosition().getY());
            pos.setZ(pos.getZ() + fact * node.getPosition().getZ());
            rot.setX(rot.getX() + fact * node.getRotationVector().getX());
            rot.setY(rot.getY() + fact * node.getRotationVector().getY());
            rot.setZ(rot.getZ() + fact * node.getRotationVector().getZ());
            duration += fact * node.getDuration();
            if (node.isActive()) {
                num_active++;
            }
        }
        rot.setX(MathUtil.wrapAngle(rot.getX()));
        rot.setY(MathUtil.wrapAngle(rot.getY()));
        rot.setZ(MathUtil.wrapAngle(rot.getZ()));
        return new AnimationNode(pos, rot, num_active >= (nodes.size()>>1), duration);
    }

    private static boolean isNumericChar(char ch) {
        return Character.isDigit(ch) || ch == '.' || ch == '-';
    }

}
