package com.bergerkiller.bukkit.tc.attachments.animation;

import java.util.Locale;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.google.common.base.Objects;

/**
 * Options for playing an animation to one or more attachments
 */
public class AnimationOptions implements Cloneable {
    private String _name;
    private String _sceneBegin;
    private String _sceneEnd;
    private boolean _hasSceneOption;
    private double _speed;
    private double _delay;
    private boolean _looped;
    private boolean _hasLoopOption;
    private boolean _reset;
    private boolean _queue;
    private boolean _hasMovementControlledOption;
    private boolean _movementControlled;
    private boolean _autoplay;

    protected AnimationOptions(AnimationOptions source) {
        this._name = source._name;
        this._sceneBegin = source._sceneBegin;
        this._sceneEnd = source._sceneEnd;
        this._hasSceneOption = source._hasSceneOption;
        this._speed = source._speed;
        this._delay = source._delay;
        this._looped = source._looped;
        this._hasLoopOption = source._hasLoopOption;
        this._reset = source._reset;
        this._queue = source._queue;
        this._hasMovementControlledOption = source._hasMovementControlledOption;
        this._movementControlled = source._movementControlled;
        this._autoplay = source._autoplay;
    }

    public AnimationOptions() {
        this("");
    }

    public AnimationOptions(String name) {
        this._name = name;
        this._sceneBegin = null;
        this._sceneEnd = null;
        this._hasSceneOption = false;
        this._speed = 1.0;
        this._delay = 0.0;
        this._looped = false;
        this._hasLoopOption = false;
        this._reset = false;
        this._queue = false;
        this._hasMovementControlledOption = false;
        this._movementControlled = false;
        this._autoplay = false;
    }

    /**
     * Sets the name of the animation to play
     * 
     * @param name of the animation to play
     */
    public void setName(String name) {
        this._name = name;
    }

    /**
     * Gets the name of the animation to play
     * 
     * @return animation name
     */
    public String getName() {
        return this._name;
    }

    /**
     * Configures the scene to play. Will skip the current animation
     * being played and go straight to this scene. If looped is set,
     * will loop this scene over and over.
     *
     * @param scene Scene to play
     */
    public void setScene(String scene) {
        this._sceneBegin = scene;
        this._sceneEnd = scene;
        this._hasSceneOption = true;
    }

    /**
     * Configures a beginning and ending scene to play. Look at the
     * descriptions of {@link #getSceneBegin()} and {@link #getSceneEnd()}
     * for more information.
     *
     * @param sceneBegin Scene to start playing at, null for beginning
     * @param sceneEnd Scene to stop playing at, null for the end
     */
    public void setScene(String sceneBegin, String sceneEnd) {
        this._sceneBegin = sceneBegin;
        this._sceneEnd = sceneEnd;
        this._hasSceneOption = true;
    }

    /**
     * Resets the scene option so that none will be set when playing the
     * animation
     */
    public void resetScene() {
        this._sceneBegin = null;
        this._sceneEnd = null;
        this._hasSceneOption = false;
    }

    /**
     * Gets the name of the scene marker from which the animation
     * should start playing, inclusive.<br>
     * <br>
     * If null and {@link #getSceneEnd()} is not null, then the animation
     * will start playing from the beginning of the animation, but stop
     * at the end scene. If looped is also set, then the animation will
     * loop between the start and the end marker over and over.
     *
     * @return Scene start marker, null to play from the beginning
     */
    public String getSceneBegin() {
        return this._sceneBegin;
    }

    /**
     * Gets the name of the scene marker where the animation should
     * stop playing, inclusive.<br>
     * <br>
     * If null and {@link #getSceneBegin()} is not null, then the animation
     * will keep on playing after this point. If looped is also set, then
     * the animation will loop from the start marker to the end over and over.
     *
     * @return Scene end marker, null to continue playing past it
     */
    public String getSceneEnd() {
        return this._sceneEnd;
    }

    /**
     * Gets whether only a single scene is specified to be played. This
     * means only this one scene needs to be played without playing the
     * frames before or after. If looped is also set, then this scene is
     * played looped over and over.
     *
     * @return True if a single scene name is specified (both begin and end)
     */
    public boolean isSingleScene() {
        return this._sceneBegin != null && this._sceneBegin.equals(this._sceneEnd);
    }

    /**
     * Gets whether a scene has been set. May also return true if
     * an explicit null scene was set for both beginning and end. This
     * is used to reset the scene if one was set before.
     *
     * @return True if {@link #setScene(String)} was called to set a scene.
     */
    public boolean hasSceneOption() {
        return this._hasSceneOption;
    }

    /**
     * Gets the animation speed.
     * This is a multiplier to the durations specified in the nodes.
     * 
     * @return speed
     */
    public double getSpeed() {
        return this._speed;
    }

    /**
     * Sets a new animation speed.
     * This is a multiplier to the durations specified in the nodes.
     * 
     * @param speed
     */
    public void setSpeed(double speed) {
        this._speed = speed;
    }

    /**
     * Gets whether the animation is played in reverse.
     * This happens when the speed is negative.
     * 
     * @return True if reversed
     */
    public boolean isReversed() {
        return this._speed < 0.0;
    }

    /**
     * Gets the time delay until which this animation plays.
     * This delay is after the speed factor is applied.
     * 
     * @return animation start delay in seconds
     */
    public double getDelay() {
        return this._delay;
    }

    /**
     * Sets the time delay until which this animation plays.
     * This delay is after the speed factor is applied.
     * 
     * @param delay in seconds
     */
    public void setDelay(double delay) {
        this._delay = delay;
    }

    /**
     * Sets whether this animation is looped
     * 
     * @param looped
     */
    public void setLooped(boolean looped) {
        this._looped = looped;
        this._hasLoopOption = true;
    }

    /**
     * Resets the looped option, making it as if it was never set.
     * This assumes the looped setting of the animation itself, if set.
     */
    public void resetLooped() {
        this._hasLoopOption = false;
    }

    /**
     * Whether the looped option was at all specified using {@link #setLooped(boolean)}
     * 
     * @return True if looped setting was set
     */
    public boolean hasLoopOption() {
        return this._hasLoopOption;
    }

    /**
     * Gets whether this animation is looped
     * 
     * @return looped
     */
    public boolean isLooped() {
        return this._looped;
    }

    /**
     * Gets whether auto-play is set. If true, then the animation will play as soon as the
     * attachment is created.
     *
     * @return True if auto-play is active
     */
    public boolean isAutoPlay() {
        return this._autoplay;
    }

    /**
     * Sets whether auto-play is set. If true, then the animation will play as soon as the
     * attachment is created.
     *
     * @param autoplay Whether to automatically play the animation on attachment creation
     */
    public void setAutoPlay(boolean autoplay) {
        this._autoplay = autoplay;
    }

    /**
     * Sets whether the animation should be reset to the beginning before playing the animation.
     * When playing in reverse, it resets to the end of the animation.
     * 
     * @param reset option
     */
    public void setReset(boolean reset) {
        this._reset = reset;
    }

    /**
     * Gets whether the animation should be reset to the beginning before playing the animation.
     * When playing in reverse, it resets to the end of the animation.
     * 
     * @return True if reset
     */
    public boolean getReset() {
        return this._reset;
    }

    /**
     * Sets whether the animation should be queued to play after previous animations finished,
     * instead of resuming from or resetting the current animation.
     * 
     * @param queue option
     */
    public void setQueue(boolean queue) {
        this._queue = queue;
    }

    /**
     * Gets whether the animation should be queued to play after previous animations finished,
     * instead of resuming from or resetting the current animation.
     * 
     * @return True if queue is set
     */
    public boolean getQueue() {
        return this._queue;
    }

    /**
     * Gets whether the {@link #setMovementControlled(boolean)} option was set
     * for these options or not.
     *
     * @return True if movement controlled was set
     */
    public boolean hasMovementControlledOption() {
        return this._hasMovementControlledOption;
    }

    /**
     * Gets whether the animation speed is controlled by the forward-movement
     * of the attachment itself. The speed option is multiplied with that speed.
     *
     * @return True if the animation speed is controlled by attachment movement
     */
    public boolean isMovementControlled() {
        return this._movementControlled;
    }

    /**
     * Sets whether the animation speed is controlled by the forward-movement
     * of the attachment itself. The speed option is multiplied with that speed.
     *
     * @param controlled Whether movement controls speed
     */
    public void setMovementControlled(boolean controlled) {
        this._movementControlled = controlled;
        this._hasMovementControlledOption = true;
    }

    /**
     * Disables a previously set movement controlled option
     * 
     * @see #setMovementControlled(boolean)
     */
    public void clearMovementControlled() {
        this._movementControlled = false;
        this._hasMovementControlledOption = false;
    }

    /**
     * Applies additional animation options to this one.
     * The speed, delay and looping options are updated.
     * 
     * @param options to apply
     */
    public void apply(AnimationOptions options) {
        this.setDelay(this.getDelay() + this.getSpeed() * options.getSpeed() * options.getDelay());
        this.setSpeed(this.getSpeed() * options.getSpeed());
        if (options.hasLoopOption()) {
            this.setLooped(options.isLooped());
        }
        if (options.hasMovementControlledOption()) {
            this.setMovementControlled(options.isMovementControlled());
        }
        if (options.isAutoPlay()) {
            this.setAutoPlay(true);
        }
        if (options.hasSceneOption()) {
            this.setScene(options.getSceneBegin(), options.getSceneEnd());
        }
        this.setReset(options.getReset());
        this.setQueue(options.getQueue());
    }

    /**
     * Loads the contents of these options from configuration.
     * The animation name and scenes are not loaded.
     * 
     * @param config to load from
     */
    public void loadFromConfig(ConfigurationNode config) {
        this._speed = config.contains("speed") ? config.get("speed", 1.0) : 1.0;
        this._delay = config.contains("delay") ? config.get("delay", 0.0) : 0.0;

        // Looped
        this._hasLoopOption = config.contains("looped");
        if (this._hasLoopOption) {
            this._looped = config.get("looped", false);
        } else {
            this._looped = false;
        }

        // Movement controlled
        this._hasMovementControlledOption = config.contains("movementControlled");
        if (this._hasMovementControlledOption) {
            this._movementControlled = config.get("movementControlled", false);
        } else {
            this._movementControlled = false;
        }

        // Autoplay
        this._autoplay = config.contains("autoplay") && config.get("autoplay", false);
    }

    /**
     * Saves the contents of these options to configuration.
     * The animation name and scenes are not saved.
     * This is only used when saving animations to train properties,
     * so it should not save things like 'reset' or 'queue' which are
     * part of starting animations.
     * 
     * @param config to save to
     */
    public void saveToConfig(ConfigurationNode config) {
        if (this._speed == 1.0) {
            config.remove("speed");
        } else {
            config.set("speed", this._speed);
        }
        if (this._delay == 0.0) {
            config.remove("delay");
        } else {
            config.set("delay", this._delay);
        }

        // Looped
        if (this._hasLoopOption) {
            config.set("looped", this._looped);
        } else {
            config.remove("looped");
        }

        // Movement controlled
        if (this._hasMovementControlledOption) {
            config.set("movementControlled", this._movementControlled);
        } else {
            config.remove("movementControlled");
        }

        // Autoplay
        if (this._autoplay) {
            config.set("autoplay", true);
        } else {
            config.remove("autoplay");
        }
    }

    /**
     * Loads the contents of these options from sign syntax
     * 
     * @param info to load from
     */
    public void loadFromSign(SignActionEvent info) {
        // Looped
        String mode_line = info.getLine(1).toLowerCase(Locale.ENGLISH).trim();
        for (String part : mode_line.split(" ")) {
            if (LogicUtil.contains(part, "noloop", "unlooped", "ul", "nl")) {
                this.setLooped(false);
            } else if (LogicUtil.contains(part, "loop", "looped", "l")) {
                this.setLooped(true);
            } else if (LogicUtil.contains(part, "reset", "rst", "r")) {
                this.setReset(true);
            } else if (LogicUtil.contains(part, "queue", "que", "q")) {
                this.setQueue(true);
            } else if (LogicUtil.contains(part, "move", "mv", "m")) {
                this.setMovementControlled(true);
            }
        }

        // Name and, optionally, the begin/end scene
        String nameAndScenes = info.getLine(2).trim();
        int sceneStart = nameAndScenes.indexOf('[');
        if (sceneStart != -1 && nameAndScenes.endsWith("]")) {
            // Name of the animation
            this.setName(nameAndScenes.substring(0, sceneStart).trim());

            // Parse scene names, split by ':'
            int sceneSplitIdx = nameAndScenes.indexOf(':', sceneStart + 1);
            if (sceneSplitIdx == -1) {
                this.setScene(nameAndScenes.substring(sceneStart+1,
                        nameAndScenes.length()-1).trim());
            } else {
                String begin = nameAndScenes.substring(sceneStart+1, sceneSplitIdx).trim();
                String end = nameAndScenes.substring(sceneSplitIdx+1, nameAndScenes.length()-1).trim();
                this.setScene(begin, end);
            }
        } else {
            // Not specified
            this.setName(nameAndScenes);
        }

        // Speed/delay
        if (!info.getLine(3).isEmpty()) {
            String[] parts = info.getLine(3).split(" ");
            if (parts.length >= 1) {
                this.setSpeed(ParseUtil.parseDouble(parts[0], 1.0));
            }
            if (parts.length >= 2) {
                this.setDelay(ParseUtil.parseDouble(parts[1], 0.0));
            }
        }
    }

    /**
     * Gets the message displayed to the user when executing the animation command successfully
     * 
     * @return success message
     */
    public String getCommandSuccessMessage() {
        String name = this.getName();
        if (this.getSceneBegin() != null || this.getSceneEnd() != null) {
            name += " [";
            if (Objects.equal(this.getSceneBegin(), this.getSceneEnd())) {
                name += this.getSceneBegin();
            } else if (this.getSceneBegin() == null) {
                name += ".. > " + this.getSceneEnd();
            } else if (this.getSceneEnd() == null) {
                name += this.getSceneBegin() + " > ..";
            } else {
                name += this.getSceneBegin() + " > " + this.getSceneEnd();
            }
            name += "]";
        }
        if (this.hasLoopOption()) {
            if (this.isLooped()) {
                name += " (looped)";
            } else {
                name += " (not looped)";
            }
        }
        if (this.hasMovementControlledOption()) {
            if (this.isMovementControlled()) {
                name += " (movement controlled)";
            } else {
                name += " (not movement controlled)";
            }
        }
        if (this._reset) {
            name += " (reset)";
        } else if (this._queue) {
            name += " (queue)";
        }
        return Localization.COMMAND_ANIMATE_SUCCESS.get(name, 
                Double.toString(this.getSpeed()),
                Double.toString(this.getDelay()));
    }

    /**
     * Gets the message displayed to the user when executing the animation command and it fails
     * 
     * @return failure message
     */
    public String getCommandFailureMessage() {
        return Localization.COMMAND_ANIMATE_FAILURE.get(this.getName());
    }

    @Override
    public AnimationOptions clone() {
        return new AnimationOptions(this);
    }
}
