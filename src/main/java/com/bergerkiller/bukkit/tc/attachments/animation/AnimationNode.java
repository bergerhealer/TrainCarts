package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.Collection;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
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
    private final String _scene; // null if not a scene start marker

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
        this._scene = null;
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
        this._scene = null;
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
     * @param scene Scene start marker, null for none
     */
    public AnimationNode(Vector position, Vector rotationVector, boolean active, double duration, String scene) {
        this._position = position;
        this._rotationVec = rotationVector;
        this._rotationQuat = null;
        this._active = active;
        this._duration = duration;
        this._scene = scene;
    }

    /**
     * Gets the scene marker name assigned to this animation node. If this node
     * is not the start of a scene, returns null.
     *
     * @return scene marker name, or null if none is assigned
     */
    public String getSceneMarker() {
        return this._scene;
    }

    /**
     * Gets whether a scene start marker name has been set for this node
     *
     * @return True if this node has a scene start marker
     */
    public boolean hasSceneMarker() {
        return this._scene != null;
    }

    /**
     * Sets a scene start marker name for this animation node. If the name changed,
     * a new animation node is returned with the scene name updated.<br>
     * <br>
     * Spaces and tabs are automatically removed from the scene name to avoid
     * glitches during serializing/de-serializing.
     *
     * @param sceneName
     * @return this or an updated animation node
     */
    public AnimationNode setSceneMarker(String sceneName) {
        if (LogicUtil.bothNullOrEqual(this._scene, sceneName)) {
            return this;
        }

        sceneName = sceneName.trim();
        if (sceneName.isEmpty()) {
            sceneName = null;
        } else {
            sceneName = sceneName.replace(' ', '_');
            sceneName = sceneName.replace('\t', '_');
        }
        return new AnimationNode(this._position, this._rotationVec,
                this._active, this._duration, sceneName);
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
        String scene = this.getSceneMarker();

        StringBuilder builder = new StringBuilder(90);
        builder.append("t=").append(this._duration);

        if (!this.isActive())  builder.append(" active=0");
        if (pos.getX() != 0.0) builder.append(" x=").append(pos.getX());
        if (pos.getY() != 0.0) builder.append(" y=").append(pos.getY());
        if (pos.getZ() != 0.0) builder.append(" z=").append(pos.getZ());
        if (ypr.getX() != 0.0) builder.append(" pitch=").append(ypr.getX());
        if (ypr.getY() != 0.0) builder.append(" yaw=").append(ypr.getY());
        if (ypr.getZ() != 0.0) builder.append(" roll=").append(ypr.getZ());
        if (scene != null) builder.append(" scene=" + scene);

        return builder.toString();
    }

    /**
     * Clones this animation node, but omits the scene start marker option if that was set
     *
     * @return cloned node
     */
    public AnimationNode cloneWithoutSceneMarker() {
        return new AnimationNode(this._position.clone(), this._rotationVec.clone(),
                this._active, this._duration);
    }

    @Override
    public AnimationNode clone() {
        return new AnimationNode(this._position.clone(), this._rotationVec.clone(),
                this._active, this._duration, this._scene);
    }

    @Override
    public String toString() {
        return serializeToString();
    }

    /**
     * Parses the contents of an animation node from a configuration String.
     * Invalid configuration will produce an identity node.
     * 
     * @param config
     * @return animation node
     */
    public static AnimationNode parseFromString(String config) {
        return (new Parser(config)).parse();
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

    /**
     * Parses the serialized animation node configuration String into
     * an AnimationNode. Is very lenient with space/= requirements
     * so that also user-specified configurations parse properly.<br>
     * <br>
     * The scene parameter, being non-numeric, should always be
     * put at the end of the configuration, or otherwise be
     * separated by spaces. Spaces are not allowed in the scene
     * name. Scene should always have a = after it to avoid confusion.
     *
     * Examples:
     * <ul>
     * <li>x=50.3 y=30.2 z=0.63 yaw=0.32 pitch=330.2 roll=-332.3
     * <li> x50.3z0.63yaw0.32pitch330.2roll-332.3y=30.2
     * <li>x=50 y=30 scene=myscene yaw=20.0
     * <li>x5 y7 z=7 scene=12helloworld
     * </ul>
     */
    private static class Parser {
        private final String config;
        private final int config_length;
        private int index = 0;
        private Vector position = new Vector();
        private Vector rotation = new Vector();
        private String scene = null;
        private boolean active = true;
        private double time = 1.0;

        public Parser(String config) {
            this.config = config;
            this.config_length = config.length();
        }

        private void skip(CharFilter filter) {
            while (index < config_length && filter.filter(config.charAt(index))) {
                index++;
            }
        }

        private String nextName() {
            // Find start of name, skipping non-letters
            skip(ch -> !Character.isLetter(ch));
            int name_start = index;

            // Find end of word
            skip(Character::isLetter);

            return config.substring(name_start, index);
        }

        public AnimationNode parse() {
            while (index < config_length) {
                String name = nextName();
                if (name.isEmpty()) {
                    continue;
                }

                // All parameters are numbers except for 'scene' which can be any number of characters
                // For this reason scene is typically put at the end of the configuration
                if ("scene".equals(name)) {
                    // Skip all spaces
                    skip(ch -> (ch == ' ' || ch == '\t'));
                    if (index >= config_length) {
                        break;
                    }

                    // If starts with =, omit. Then skip all spaces after.
                    if (config.charAt(index) == '=') {
                        index++;
                    }
                    skip(ch -> (ch == ' ' || ch == '\t'));

                    // Remainder, up until the next space, is considered the scene name
                    int valueStart = index;
                    skip(ch -> (ch != ' ' && ch != '\t'));
                    scene = config.substring(valueStart, index);
                    if (scene.isEmpty()) {
                        scene = null;
                    }
                } else {
                    int value_start, value_end;
                    double value;

                    // Find start of value, skipping non-numeric and non-.
                    skip(ch -> !AnimationNode.isNumericChar(ch));
                    value_start = index;

                    // Find end of value
                    skip(AnimationNode::isNumericChar);
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

                    if ("t".equals(name)) {
                        time = value;
                    } else if ("x".equals(name)) {
                        position.setX(value);
                    } else if ("y".equals(name)) {
                        position.setY(value);
                    } else if ("z".equals(name)) {
                        position.setZ(value);
                    } else if ("pitch".equals(name)) {
                        rotation.setX(value);
                    } else if ("yaw".equals(name)) {
                        rotation.setY(value);
                    } else if ("roll".equals(name)) {
                        rotation.setZ(value);
                    } else if ("active".equals(name)) {
                        active = (value != 0.0);
                    }
                }
            }

            return new AnimationNode(position, rotation, active, time, scene);
        }

        @FunctionalInterface
        private static interface CharFilter {
            boolean filter(char ch);
        }
    }
}
