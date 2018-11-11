package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * A single keyframe of an animation
 */
public class AnimationNode {
    private final Vector _position;
    private final Quaternion _rotation;
    private final double _duration;

    public AnimationNode(Vector position, Quaternion rotation, double duration) {
        this._position = position;
        this._rotation = rotation;
        this._duration = duration;
    }

    /**
     * Gets the relative position of this animation node
     * 
     * @return relative position
     */
    public Vector getPosition() {
        return this._position;
    }

    /**
     * Gets the relative rotation of this animation node
     * 
     * @return relative rotation
     */
    public Quaternion getRotation() {
        return this._rotation;
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
     * Applies the position and rotation transformation of this animation node to a 4x4 transformation matrix
     * 
     * @param transform
     */
    public void apply(Matrix4x4 transform) {
        transform.rotate(this.getRotation());
        transform.translate(this.getPosition());
    }

    /**
     * Serializes the contents of this AnimationNode to a String that
     * {@link #parseFromString(String)} accepts as input.
     * 
     * @return serialized String
     */
    public String serializeToString() {
        Vector ypr = this._rotation.getYawPitchRoll();
        StringBuilder builder = new StringBuilder(90);
        builder.append("t=").append(this._duration);
        builder.append(" x=").append(this._position.getX());
        builder.append(" y=").append(this._position.getY());
        builder.append(" z=").append(this._position.getZ());
        builder.append(" yaw=").append(MathUtil.round(ypr.getY(), 6));
        builder.append(" pitch=").append(MathUtil.round(ypr.getX(), 6));
        builder.append(" roll=").append(MathUtil.round(ypr.getZ(), 6));
        return builder.toString();
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
        double rotation_yaw = 0.0;
        double rotation_pitch = 0.0;
        double rotation_roll = 0.0;
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
            } else if ("yaw".regionMatches(0, config, name_start, name_len)) {
                rotation_yaw = value;
            } else if ("pitch".regionMatches(0, config, name_start, name_len)) {
                rotation_pitch = value;
            } else if ("roll".regionMatches(0, config, name_start, name_len)) {
                rotation_roll = value;
            }
        }
        return new AnimationNode(position, Quaternion.fromYawPitchRoll(rotation_pitch, rotation_yaw, rotation_roll), time);
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
            Quaternion lerp_rotation = Quaternion.slerp(nodeA.getRotation(), nodeB.getRotation(), theta);
            return new AnimationNode(lerp_position, lerp_rotation, 1.0);
        }
    }

    /**
     * Creates an identity animation node (that does not do anything)
     * 
     * @return identity
     */
    public static AnimationNode identity() {
        return new AnimationNode(new Vector(), new Quaternion(), 1.0);
    }

    private static boolean isNumericChar(char ch) {
        return Character.isDigit(ch) || ch == '.' || ch == '-';
    }
}
