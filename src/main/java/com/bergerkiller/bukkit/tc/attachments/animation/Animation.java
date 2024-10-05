package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * An animation consisting of key frame nodes with time-domain transformations.
 * Class can be inherited overriding {@link #update(double, Matrix4x4)} returning a custom position for animations
 * controlled by external input.
 */
public class Animation implements Cloneable {
    private AnimationOptions _options;
    private final AnimationNode[] _nodes;
    private final Map<String, Scene> _scenes;
    private final Scene _entireAnimationScene;
    private Scene _currentScene;
    private MovementSpeedController _speedControl;
    private double _time;
    private boolean _startedPlaying;
    private boolean _reachedEnd;

    protected Animation(Animation source) {
        this._options = source._options.clone();
        this._nodes = source._nodes;
        this._scenes = source._scenes;
        this._entireAnimationScene = source._entireAnimationScene;
        this._currentScene = source._currentScene;
        this._speedControl = null; // Reset it
        this._time = source._time;
        this._startedPlaying = source._startedPlaying;
        this._reachedEnd = source._reachedEnd;
    }

    public Animation(String name, String... nodes_config) {
        this(name, Arrays.asList(nodes_config));
    }

    public Animation(String name, List<String> nodes_config) {
        this(name, AnimationNode.parseAllFromStrings(nodes_config));
    }

    public Animation(String name, AnimationNode[] nodes) {
        this._options = new AnimationOptions(name);
        this._nodes = nodes;
        this._time = 0.0;
        this._startedPlaying = false;
        this._reachedEnd = false;

        // Compute scenes mapping using the nodes
        this._scenes = new LinkedHashMap<>();
        {
            String lastSceneName = null;
            int lastSceneBegin = -1;
            double lastSceneDuration = 0.0;
            for (int i = 0; i < nodes.length; i++) {
                AnimationNode node = nodes[i];
                if (node.hasSceneMarker() && !node.getSceneMarker().equals(lastSceneName)) {
                    if (lastSceneName != null) {
                        this._scenes.put(lastSceneName, new Scene(lastSceneBegin, i-1, lastSceneDuration));
                    }

                    lastSceneName = node.getSceneMarker();
                    lastSceneDuration = node.getDuration();
                    lastSceneBegin = i;
                } else {
                    lastSceneDuration += node.getDuration();
                }
            }
            if (lastSceneName != null) {
                this._scenes.put(lastSceneName, new Scene(lastSceneBegin, nodes.length - 1, lastSceneDuration));
            }
        }

        // Calculate loop duration when playing the entire animation
        if (nodes.length > 0) {
            double total = 0.0;
            for (AnimationNode node : nodes) {
                total += node.getDuration();
            }
            this._entireAnimationScene = new Scene(0, nodes.length - 1, total);
        } else {
            this._entireAnimationScene = new Scene(0, 0, 0.0);
        }
        this._currentScene = this._entireAnimationScene;
    }

    /**
     * Gets all the options for this animation, which include the animation name.
     * The options are writable, although using {@link #applyOptions(AnimationOptions)} is preferred.
     * 
     * @return animation options
     */
    public final AnimationOptions getOptions() {
        return this._options;
    }

    /**
     * Gets all the names of scenes defined for this animation
     *
     * @return set of animation scene names
     */
    public final Set<String> getSceneNames() {
        return Collections.unmodifiableSet(this._scenes.keySet());
    }

    /**
     * Sets all the options for this animation, which include the animation name.
     * This erases any options previously applied. The current animation moment is preserved
     * when setting these options, that is, the delay change is kept in mind.
     * 
     * @param options to set
     * @return this animation (for chained calls)
     */
    public Animation setOptions(AnimationOptions options) {
        double old_delay = this._options.getDelay();
        this._options = options;
        this._time -= (this._options.getDelay() - old_delay);
        this.updateScene(this.createScene(options));
        this._reachedEnd = false;
        if (!this._options.hasMovementControlledOption()) {
            this._speedControl = null; // Reset
        }
        return this;
    }

    /**
     * Gets whether the animation reached the end. When the loop option is set,
     * this end is never reached.
     * 
     * @return True if the end was reached
     */
    public boolean hasReachedEnd() {
        return this._reachedEnd;
    }

    /**
     * Updates the animation parameters while the animation is possibly still running. This
     * updates the speed, delay or looping option without causing a jump in the animation.
     * 
     * @param options to apply
     * @return this animation (for chained calls)
     */
    public Animation applyOptions(AnimationOptions options) {
        double old_delay = this._options.getDelay();
        this._options.apply(options);
        this._time -= (this._options.getDelay() - old_delay);
        this.updateScene(this.createScene(options));
        this._reachedEnd = false;
        if (!this._options.hasMovementControlledOption()) {
            this._speedControl = null; // Reset
        }
        return this;
    }

    /**
     * Resets the animation to the beginning, setting the running time to be
     * most appropriate for the animation options currently used. Use
     * {@link #applyOptions(AnimationOptions)} prior to starting to set these options.
     */
    public void start() {
        if (this._options.isReversed()) {
            this._time = this._currentScene.duration();
            if (this._nodes.length >= 1) {
                this._time -= this._nodes[this._currentScene.nodeEndIndex()].getDuration();
            }
        } else {
            this._time = 0.0;
        }
        this._time -= this._options.getDelay();
        this._startedPlaying = false;
        this._reachedEnd = false;
    }

    /**
     * Gets whether this animation is the same as another animation.
     * When this is the case, the animation is played/resumed from the last time it played.
     * Can be overrided to disable this functionality for custom animations.
     * 
     * @param animation
     * @return True if the animations are the same
     */
    public boolean isSame(Animation animation) {
        return animation.getOptions().getName().equals(this.getOptions().getName());
    }

    /**
     * Gets the backing array of animation nodes
     * 
     * @return nodes
     */
    public AnimationNode[] getNodeArray() {
        return this._nodes;
    }

    /**
     * Gets the animation node at an index
     * 
     * @param index
     * @return node at this index
     */
    public AnimationNode getNode(int index) {
        return this._nodes[index];
    }

    /**
     * Gets the number of nodes in this animation
     * 
     * @return node count
     */
    public int getNodeCount() {
        return this._nodes.length;
    }

    @Override
    public Animation clone() {
        return new Animation(this);
    }

    /**
     * Updates this animation a single time step
     *
     * @param dt Delta time in seconds since previous update
     * @param speedControlTransform If movement speed control is active, used to control animation speed
     * @return animation node, null if animation is disabled at this time
     */
    public AnimationNode update(double dt, Matrix4x4 speedControlTransform) {
        // Missing animation check - do nothing
        if (this._nodes.length == 0) {
            this._startedPlaying = false;
            this._reachedEnd = true;
            return null;
        }

        Scene scene = this._currentScene;

        // When animation is too short, always return node 0.
        if (scene.isSingleFrame()) {
            this._startedPlaying = true;
            this._reachedEnd = true;
        }

        // If reached end, don't do any more time updates
        if (this._reachedEnd) {
            return this._nodes[this._options.isReversed() ? scene.nodeBeginIndex() : scene.nodeEndIndex()];
        }

        // Movement speed control
        if (this._options.isMovementControlled()) {
            MovementSpeedController control = this._speedControl;
            if (control == null) {
                this._speedControl = new MovementSpeedController(speedControlTransform);
                dt = 0.0; // No movement in the first tick
            } else {
                // We omit the original dt to keep animation synchronized with movement
                dt = control.update(speedControlTransform);
            }
        }

        // Use time before the update to allow for t=0 to display
        double curr_time = this._time;
        this._time += dt * this._options.getSpeed();

        // Check if we have started playing yet
        // This returns null until the start delay has elapsed
        if (!this._startedPlaying) {
            if (!this._options.isLooped()) {
                AnimationNode endNode = this._nodes[scene.nodeEndIndex()];
                double animEnd = scene.duration() - endNode.getDuration();
                if (this._options.isReversed()) {
                    if (curr_time > animEnd) {
                        // Keep within range
                        if (this._time < 0.0) {
                            this._time = 0.0;
                        }
                        return null;
                    }
                } else {
                    if (curr_time < 0.0) {
                        // Keep within range
                        if (this._time > animEnd) {
                            this._time = animEnd;
                        }
                        return null;
                    }
                }
            }

            // No longer waiting for a pre-start delay to elapse
            this._startedPlaying = true;
        }

        if (this._options.isLooped()) {
            // Looped:
            // Take modulo of time vs loop duration in order for it to loop around
            // This causes any sort of delay to act more like a phase shift
            this._time = (this._time % scene.duration());
            if (this._time < 0.0) {
                this._time += scene.duration(); // nega
            }
        } else {
            // When not looped, check whether the animation finished playing fully,
            // or whether the animation is yet to start
            // Clamp time to the end-time when this happens (!)
            if (this._options.isReversed()) {
                if (curr_time == 0.0) {
                    // Reached the beginning of the animation, stop playing
                    this._time = 0.0;
                    this._reachedEnd = true;
                    return this._nodes[scene.nodeBeginIndex()];
                } else if (this._time < 0.0) {
                    // Clamp at t=0, next time it will stop playing
                    this._time = 0.0;
                }
            } else {
                AnimationNode endNode = this._nodes[scene.nodeEndIndex()];
                double animEnd = scene.duration() - endNode.getDuration();
                if (curr_time == animEnd) {
                    // Reached the end of the animation, stop playing
                    this._time = animEnd;
                    this._reachedEnd = true;
                    return endNode;
                } else if (curr_time > animEnd) {
                    // When beyond the end of the animation, it resumes playing to the beginning
                    // Make sure to wrap around the time back to 0 when this happens
                    if (this._time >= scene.duration()) {
                        this._time -= scene.duration();
                        if (this._time > animEnd) {
                            this._time = animEnd;
                        }
                    }
                } else if (this._time > animEnd) {
                    // Clamp to end, next time it will stop playing
                    this._time = animEnd;
                }
            }
        }

        return findPlayPosition(scene, curr_time).toNode();
    }

    /**
     * Calculates the exact play position into this animation for a given scene
     * and time.
     *
     * @param scene Scene
     * @param elapsedTime Time since the beginning of the scene
     * @return Play Position
     */
    private PlayPosition findPlayPosition(Scene scene, double elapsedTime) {
        if (scene.isSingleFrame()) {
            return new PlayPosition(elapsedTime, scene.nodeBeginIndex(), this._nodes[scene.nodeBeginIndex()]);
        }

        // Interpolate to find the correct animation node
        boolean playReversed = this._options.isReversed();
        for (PlayPosition scenePosition : scene.iteratePlayPositions(this._nodes, this._options.isLooped())) {
            PlayPosition result = scenePosition.findPosition(elapsedTime, playReversed);
            if (result != null) {
                return result;
            }
        }

        // Should never be reached
        return new PlayPosition(scene.duration(), scene.nodeEndIndex(), this._nodes[scene.nodeEndIndex()]);
    }

    /**
     * Looks up the time to play from the start of a scene to the play position specified
     *
     * @param scene Scene
     * @param playPosition PlayPositionBetween
     * @return Time from start of the scene
     */
    private double findTime(Scene scene, PlayPosition playPosition) {
        double time = 0.0;
        for (PlayPosition scenePosition : scene.iteratePlayPositions(this._nodes, this._options.isLooped())) {
            if (scenePosition.node0Index() == playPosition.node0Index()) {
                return scenePosition.elapsedTime() + playPosition.deltaTime();
            } else {
                time = scenePosition.elapsedTime();
            }
        }

        // Probably never gets here?
        return time;
    }

    /**
     * Updates the scene being played while preserving the time moment
     * of playback.
     *
     * @param scene
     */
    private void updateScene(Scene scene) {
        // Find current play position node-wise
        PlayPosition playPosition = findPlayPosition(this._currentScene, this._time);

        // Is this position inside the new scene too?
        // If not, hard reset to the beginning (or end, if reversed) of the scene
        // to play the scene all over, acting as a hard reset.
        if (scene.containsPosition(playPosition)) {
            this._time = findTime(scene, playPosition);
        } else {
            this._time = this._options.isReversed() ? scene.duration() : 0.0;
        }

        // Assign
        this._currentScene = scene;
    }

    /**
     * Uses the animation options to define the scene being played.
     *
     * @param options Options
     * @return Scene to play
     */
    private Scene createScene(AnimationOptions options) {
        // Don't bother.
        if (this._nodes.length == 0 || !options.hasSceneOption()) {
            return this._entireAnimationScene;
        }

        // Single scene
        if (options.isSingleScene()) {
            return this._scenes.getOrDefault(options.getSceneBegin(), this._entireAnimationScene);
        }

        // Range of scenes, potentially
        int beginIndex = 0;
        int endIndex = this._nodes.length - 1;
        if (options.getSceneBegin() != null) {
            beginIndex = this._scenes.getOrDefault(options.getSceneBegin(), this._entireAnimationScene)
                    .nodeBeginIndex();
        }
        if (options.getSceneEnd() != null) {
            endIndex = this._scenes.getOrDefault(options.getSceneEnd(), this._entireAnimationScene)
                    .nodeEndIndex();
        }

        // Use iterator to calculate the total duration of this scene
        // This is including the loop-around bit!
        double duration = 0.0;
        for (PlayPosition position : PlayPosition.iterate(this._nodes, beginIndex, endIndex, true)) {
            duration = position.elapsedTime();
        }
        return new Scene(beginIndex, endIndex, duration);
    }

    /**
     * Saves this animation to a configuration
     * 
     * @param config
     */
    public void saveToConfig(ConfigurationNode config) {
        this.getOptions().saveToConfig(config);

        List<String> nodes_str = new ArrayList<String>(this._nodes.length);
        for (AnimationNode node : this._nodes) {
            nodes_str.add(node.serializeToString());
        }
        config.set("nodes", nodes_str);
    }

    /**
     * Saves this animation as a new node of a parent configuration.
     * The name of the node is taken from this animation.
     * 
     * @param parentConfig
     */
    public void saveToParentConfig(ConfigurationNode parentConfig) {
        saveToConfig(parentConfig.getNode(this.getOptions().getName()));
    }

    /**
     * Loads an animation from configuration
     * 
     * @param config
     * @return animation
     */
    public static Animation loadFromConfig(ConfigurationNode config) {
        String name = config.getName();
        List<String> nodes_str = config.getList("nodes", String.class);
        Animation animation = new Animation(name, nodes_str);
        animation.getOptions().loadFromConfig(config);
        return animation;
    }

    /**
     * A single range of an animation to play
     */
    public static final class Scene {
        private final int _nodeBegin;
        private final int _nodeEnd;
        private final double _duration;

        public Scene(int nodeBegin, int nodeEnd, double duration) {
            this._nodeBegin = nodeBegin;
            this._nodeEnd = nodeEnd;
            this._duration = duration;
        }

        public int nodeBeginIndex() {
            return this._nodeBegin;
        }

        public int nodeEndIndex() {
            return this._nodeEnd;
        }

        /**
         * Gets whether the scene is inside-out. In that case, the animation plays
         * from the beginning, loops to the end of the animation, and then
         * plays until the end node from the beginning of the animation.
         *
         * @return True if inside-out
         */
        public boolean isInsideOut() {
            return this._nodeBegin > this._nodeEnd;
        }

        public double duration() {
            return this._duration;
        }

        /**
         * Whether this scene is only a single frame. In that case no animation
         * is being played, and just this one frame is updated.
         *
         * @return True if this is a single-frame animation
         */
        public boolean isSingleFrame() {
            return this._nodeBegin == this._nodeEnd || this._duration <= 1e-20;
        }

        /**
         * Gets whether a particular playback position is contained within this scene.
         *
         * @param playPosition PlayPositionBetween
         * @return True if contained
         */
        public boolean containsPosition(PlayPosition playPosition) {
            if (playPosition instanceof PlayPositionBetween) {
                PlayPositionBetween between = (PlayPositionBetween) playPosition;
                return isNodePlayed(between.node0Index()) && isNodePlayed(between.node1Index());
            } else {
                return isNodePlayed(playPosition.node0Index());
            }
        }

        private boolean isNodePlayed(int nodeIndex) {
            if (isInsideOut()) {
                return nodeIndex >= this._nodeBegin || nodeIndex <= this._nodeEnd;
            } else {
                return nodeIndex >= this._nodeBegin && nodeIndex <= this._nodeEnd;
            }
        }

        /**
         * Iterates all the nodes that are part of this scene. All except the first node are iterated
         * as {@link PlayPositionBetween} to have access to both nodes.
         *
         * @param nodes All animation nodes of the Animation
         * @param looped Whether to loop around as part of the scene. In this case, an additional 'between'
         *               is included to loop from the end back to the beginning.
         * @return Iterable of PlayPosition (and PlayPositionBetween) values
         */
        public Iterable<PlayPosition> iteratePlayPositions(final AnimationNode[] nodes, final boolean looped) {
            return PlayPosition.iterate(nodes, nodeBeginIndex(), nodeEndIndex(), looped);
        }

        @Override
        public String toString() {
            return "Scene{duration=" + this._duration + ", start=" + this._nodeBegin + ", end=" + this._nodeEnd + "}";
        }
    }

    /**
     * The play position of an animation. Contains the time (since the start node of the scene),
     * and access the elapsed time and final (interpolated) node at this play position.
     * Is an instance of {@link PlayPositionBetween} if interpolating between two
     * animation nodes.
     */
    public static class PlayPosition {
        private final double elapsedTime;
        private final AnimationNode node0;
        private final int node0Index;

        public PlayPosition(double elapsedTime, int node0Index, AnimationNode node0) {
            this.elapsedTime = elapsedTime;
            this.node0Index = node0Index;
            this.node0 = node0;
        }

        /**
         * Gets the total amount of elapsed time since the start of the scene of the animation.
         *
         * @return Elapsed time
         */
        public double elapsedTime() {
            return elapsedTime;
        }

        /**
         * Gets the index of the node at theta 0 of interpolation.
         *
         * @return Index of Node0
         */
        public int node0Index() {
            return node0Index;
        }

        /**
         * Gets the node at theta 0 of interpolation. Same as {@link #toNode()} if
         * this is an exact node play position in the animation.
         *
         * @return Node0
         */
        public AnimationNode node0() {
            return this.node0;
        }

        /**
         * Gets or calculated the animation node at the {@link #elapsedTime()}
         *
         * @return Node
         */
        public AnimationNode toNode() {
            return this.node0; // No interpolation
        }

        /**
         * Gets the amount of time that has elapsed interpolating this play position.
         *
         * @return Delta time
         */
        public double deltaTime() {
            return 0.0;
        }

        /**
         * Attempts to find another PlayPosition that that is within this same play
         * position range, for the elapsedTime specified. Returns <i>null</i> if no such
         * play position exists.
         *
         * @param elapsedTime Elapsed time
         * @param playReversed Whether playback is reversed. Has special implications for dt=0 nodes
         * @return Play Position
         */
        public PlayPosition findPosition(double elapsedTime, boolean playReversed) {
            // For a non-between node, only exact time works
            return (this.elapsedTime() == elapsedTime) ? this : null;
        }

        @Override
        public String toString() {
            return "Position{t=" + elapsedTime() + ", @ " + node0Index() + "}";
        }

        /**
         * Iterates a range of nodes. All except the first node are iterated
         * as {@link PlayPositionBetween} to have access to both nodes.
         *
         * @param nodes All animation nodes of the Animation
         * @param nodeBeginIndex Index of the first node of the animation (inclusive), indexed into the nodes array
         * @param nodeEndIndex Index of the last node of the animation (inclusive), indexed into the nodes array
         * @param looped Whether to loop around at the end. In this case, an additional 'between'
         *               is included to loop from the end back to the beginning.
         * @return Iterable of PlayPosition (and PlayPositionBetween) values
         */
        public static Iterable<PlayPosition> iterate(
                final AnimationNode[] nodes,
                final int nodeBeginIndex,
                final int nodeEndIndex,
                final boolean looped
        ) {
            // Simplified if frozen on one node
            if (nodeBeginIndex == nodeEndIndex) {
                return Collections.singletonList(new PlayPosition(0.0, nodeBeginIndex, nodes[nodeBeginIndex]));
            }

            if (nodeBeginIndex > nodeEndIndex) {
                // Iterate from the beginning to the end of the animation, and then around and til the end of the scene
                return () -> new Iterator<PlayPosition>() {
                    private double totalElapsedTime = 0.0;
                    private int nodeIndex = nodeBeginIndex;
                    private boolean isLoopedToBeginning = false;
                    private boolean isLoopedToEndOfAnimation = false;

                    @Override
                    public boolean hasNext() {
                        return nodeIndex >= 0;
                    }

                    @Override
                    public PlayPosition next() {
                        int currNodeIndex = this.nodeIndex;
                        if (currNodeIndex < 0) {
                            throw new NoSuchElementException();
                        }

                        AnimationNode currNode = nodes[currNodeIndex];
                        double currElapsed = this.totalElapsedTime;
                        this.totalElapsedTime += currNode.getDuration();

                        if (isLoopedToBeginning) {
                            this.nodeIndex = -1; // End
                            return new PlayPosition(currElapsed, currNodeIndex, currNode);
                        }

                        int nextNodeIndex = currNodeIndex + 1;
                        if (this.isLoopedToEndOfAnimation) {
                            if (nextNodeIndex > nodeEndIndex) {
                                if (looped) {
                                    isLoopedToBeginning = true;
                                    nextNodeIndex = nodeBeginIndex;
                                } else {
                                    this.nodeIndex = -1; // End
                                    return new PlayPosition(currElapsed, currNodeIndex, currNode);
                                }
                            }
                        } else if (nextNodeIndex >= nodes.length) {
                            isLoopedToEndOfAnimation = true;
                            nextNodeIndex = 0;
                        }

                        this.nodeIndex = nextNodeIndex;
                        return new PlayPositionBetween(currElapsed, 0.0,
                                currNodeIndex, currNode,
                                nextNodeIndex, nodes[nextNodeIndex]);
                    }
                };
            } else {
                // Iterate from scene beginning to scene end
                return () -> new Iterator<PlayPosition>() {
                    private double totalElapsedTime = 0.0;
                    private int nodeIndex = nodeBeginIndex;
                    private boolean isLoopedToBeginning = false;

                    @Override
                    public boolean hasNext() {
                        return nodeIndex >= 0;
                    }

                    @Override
                    public PlayPosition next() {
                        int currNodeIndex = this.nodeIndex;
                        if (currNodeIndex < 0) {
                            throw new NoSuchElementException();
                        }

                        AnimationNode currNode = nodes[currNodeIndex];
                        double currElapsed = this.totalElapsedTime;
                        this.totalElapsedTime += currNode.getDuration();

                        int nextNodeIndex = currNodeIndex + 1;
                        if (isLoopedToBeginning) {
                            this.nodeIndex = -1; // End
                            return new PlayPosition(currElapsed, currNodeIndex, currNode);
                        }
                        if (nextNodeIndex > nodeEndIndex) {
                            if (looped) {
                                isLoopedToBeginning = true;
                                nextNodeIndex = nodeBeginIndex;
                            } else {
                                this.nodeIndex = -1; // End
                                return new PlayPosition(currElapsed, currNodeIndex, currNode);
                            }
                        }

                        this.nodeIndex = nextNodeIndex;
                        return new PlayPositionBetween(currElapsed, 0.0,
                                currNodeIndex, currNode,
                                nextNodeIndex, nodes[nextNodeIndex]);
                    }
                };
            }
        }
    }

    /**
     * The play position of an animation. Contains the time (since the start node of the scene),
     * the two animation nodes and the theta interpolation position between the two.
     */
    public static class PlayPositionBetween extends PlayPosition {
        public final double theta;
        public final AnimationNode node1;
        public final int node1Index;

        public PlayPositionBetween(double elapsedTime, double theta, int node0Index, AnimationNode node0, int node1Index, AnimationNode node1) {
            super(elapsedTime, node0Index, node0);
            this.theta = theta;
            this.node1 = node1;
            this.node1Index = node1Index;
        }

        /**
         * Gets the index of the node at theta 1 of interpolation.
         *
         * @return Index of Node1
         */
        public int node1Index() {
            return node1Index;
        }

        /**
         * Gets the node at theta 1 of interpolation.
         *
         * @return Node1
         */
        public AnimationNode node1() {
            return this.node1;
        }

        /**
         * Gets the theta (0.0 .. 1.0) of interpolation between the two nodes this
         * play position is at.
         *
         * @return Theta
         */
        public double theta() {
            return theta;
        }

        @Override
        public PlayPosition findPosition(double elapsedTime, boolean playReversed) {
            double delta = (elapsedTime - this.elapsedTime());
            double duration = this.node0().getDuration();
            if (delta == 0.0) {
                if (duration > 0.0 || playReversed) {
                    return new PlayPosition(elapsedTime, this.node0Index(), this.node0());
                } else {
                    return new PlayPosition(elapsedTime, this.node1Index(), this.node1());
                }
            } else {
                if (delta == duration) {
                    return new PlayPosition(elapsedTime, this.node1Index(), this.node1());
                } else if (delta < duration) {
                    return new PlayPositionBetween(elapsedTime, delta / duration,
                            this.node0Index(), this.node0(),
                            this.node1Index(), this.node1());
                } else {
                    return null;
                }
            }
        }

        @Override
        public AnimationNode toNode() {
            return AnimationNode.interpolate(node0(), node1(), theta());
        }

        @Override
        public double deltaTime() {
            return theta * this.node0().getDuration();
        }

        @Override
        public String toString() {
            return "PositionBetween{t=" + elapsedTime() + ", [" + node0Index() + " / " + node1Index() + "] @ " + theta() + "}";
        }
    }

    /**
     * Uses a change in transformation matrix to control the speed of the animation
     */
    private static final class MovementSpeedController {
        private final Vector prevPosition;
        private final Vector prevForward;

        public MovementSpeedController(Matrix4x4 initial) {
            this.prevPosition = initial.toVector();
            this.prevForward = initial.getRotation().forwardVector();
        }

        public double update(Matrix4x4 transform) {
            Vector newPosition = transform.toVector();

            // Compute difference in position
            Vector diff = newPosition.clone().subtract(this.prevPosition);
            // Dot by forward vector of original transform
            double d = diff.dot(prevForward);
            // Update
            MathUtil.setVector(this.prevPosition, newPosition);
            MathUtil.setVector(this.prevForward, transform.getRotation().forwardVector());
            return d;
        }
    }
}
