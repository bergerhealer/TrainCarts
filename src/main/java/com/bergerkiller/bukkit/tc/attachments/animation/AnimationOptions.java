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
    private double _speed;
    private double _delay;
    private boolean _looped;
    private boolean _hasLoopOption;
    private boolean _reset;
    private boolean _queue;

    protected AnimationOptions(AnimationOptions source) {
        this._name = source._name;
        this._sceneBegin = source._sceneBegin;
        this._sceneEnd = source._sceneEnd;
        this._speed = source._speed;
        this._delay = source._delay;
        this._looped = source._looped;
        this._hasLoopOption = source._hasLoopOption;
        this._reset = source._reset;
        this._queue = source._queue;
    }

    public AnimationOptions() {
        this("");
    }

    public AnimationOptions(String name) {
        this._name = name;
        this._sceneBegin = null;
        this._sceneEnd = null;
        this._speed = 1.0;
        this._delay = 0.0;
        this._looped = false;
        this._hasLoopOption = false;
        this._reset = false;
        this._queue = false;
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
            }
        }

        // Name
        this.setName(info.getLine(2));

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
     * Loads the contents of these options from commandline arguments
     * 
     * @param args
     */
    public void loadCommandArgs(String[] args) {
        boolean found_name = false;
        boolean found_speed = false;
        for (String arg : args) {
            String lower_arg = arg.toLowerCase(Locale.ENGLISH);
            if (lower_arg.equals("noloop") || lower_arg.equals("unlooped")) {
                this.setLooped(false);
            } else if (lower_arg.equals("loop") || lower_arg.equals("looped")) {
                this.setLooped(true);
            } else if (lower_arg.equals("reset")) {
                this.setReset(true);
            } else if (lower_arg.equals("queue")) {
                this.setQueue(true);
            } else if (!found_name && !ParseUtil.isNumeric(arg)) {
                this.setName(arg);
                found_name = true;
            } else if (!found_speed) {
                this.setSpeed(ParseUtil.parseDouble(arg, 1.0));
                found_speed = true;
            } else {
                this.setDelay(ParseUtil.parseDouble(arg, 0.0));
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
