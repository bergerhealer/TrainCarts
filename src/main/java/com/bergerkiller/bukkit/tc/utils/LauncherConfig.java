package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.Util;

/**
 * Stores all the configuration parsed from a launch statement on a sign.
 * This will parse launch distance or time, and selects the launch function to use.
 */
public class LauncherConfig implements Cloneable {
    private String _asString = null;
    private double _distance;
    private int _duration;
    private double _acceleration;
    private boolean _launchFunctionIsDefault = true;
    private Class<? extends LaunchFunction> _launchFunction;

    /**
     * Gets whether this is a valid configuration.
     * It is only valid if a distance, duration
     * or acceleration is configured.
     * 
     * @return True if valid
     */
    public boolean isValid() {
        return hasDistance() || hasDuration() || hasAcceleration();
    }

    /**
     * Gets whether a distance was set
     * 
     * @return distance set
     */
    public boolean hasDistance() {
        return this._distance >= 0.0;
    }

    /**
     * Gets whether a duration tick time was set
     * 
     * @return tick time was set
     */
    public boolean hasDuration() {
        return this._duration >= 0;
    }

    /**
     * Gets whether an acceleration is set
     * 
     * @return acceleration was set
     */
    public boolean hasAcceleration() {
        return this._acceleration > 0.0;
    }

    /**
     * Gets the number of ticks launching should occur, or -1 if unused
     * 
     * @return launch tick time duration
     */
    public int getDuration() {
        return this._duration;
    }

    /**
     * Sets the number of ticks launching should occur, or -1 if unused
     * 
     * @param duration to set to, -1 to disable
     */
    public void setDuration(int duration) {
        this._asString = null;
        this._duration = duration;
        if (this._duration >= 0) {
            this._distance = -1.0;
            this._acceleration = -1.0;
        }
    }

    /**
     * Gets the block distance the launch should reach, or negative if unused
     * 
     * @return launch block distance
     */
    public double getDistance() {
        return this._distance;
    }

    /**
     * Sets the launch configuration to use a block distance
     * 
     * @param distance to launch for
     */
    public void setDistance(double distance) {
        this._asString = null;
        this._distance = distance;
        if (distance >= 0.0) {
            this._duration = -1;
            this._acceleration = -1.0;
        }
    }

    /**
     * Gets the acceleration at which a train is launched.
     * This is the number of blocks/tick the speed of the train
     * increases/decreases by every tick.
     * 
     * @return acceleration in blocks/tick per tick
     */
    public double getAcceleration() {
        return this._acceleration;
    }

    /**
     * Sets the acceleration at which a train is launched.
     * This is the number of blocks/tick the speed of the train
     * increases/decreases by every tick.
     * 
     * @param acceleration Acceleration to set to, in blocks/tick per tick
     */
    public void setAcceleration(double acceleration ) {
        this._asString = null;
        this._acceleration = acceleration;
        if (acceleration >= 0.0) {
            this._duration = -1;
            this._distance = -1.0;
        }
    }

    /**
     * Gets the type of launch function used
     * 
     * @return launch function
     */
    public Class<? extends LaunchFunction> getFunction() {
        return this._launchFunction;
    }

    /**
     * Sets the type of launch function used
     * 
     * @param function to set to
     */
    public void setFunction(Class<? extends LaunchFunction> function) {
        this._launchFunction = function;
    }

    @Override
    public LauncherConfig clone() {
        LauncherConfig clone = new LauncherConfig();
        clone._asString = this._asString;
        clone._distance = this._distance;
        clone._duration = this._duration;
        clone._acceleration = this._acceleration;
        clone._launchFunction = this._launchFunction;
        clone._launchFunctionIsDefault = this._launchFunctionIsDefault;
        return clone;
    }

    @Override
    public String toString() {
        // Generate a String representation of this launch function if needed
        // This is only used when using one of the setters.
        if (this._asString == null) {
            StringBuilder result = new StringBuilder();
            if (this.hasDistance()) {
                result.append(this.getDistance());
            } else if (this.hasDuration()) {
                result.append(this.getDuration()).append('t');
            } else if (this.hasAcceleration()) {
                result.append(this.getAcceleration()).append("/tt");
            } else {
                return ""; // invalid
            }

            if (!this._launchFunctionIsDefault) {
                if (this._launchFunction == LaunchFunction.Bezier.class) {
                    result.append('b');
                } else if (this._launchFunction == LaunchFunction.Linear.class) {
                    result.append('l');
                }
            }

            this._asString = result.toString();
        }

        return this._asString;
    }

    /**
     * Parses the launcher configuration from text. This supports the following formats:
     * <ul>
     * <li>12   =  distance of 12 blocks, default algorithm</li>
     * <li>12.5 =  distance of 12.5 blocks, default algorithm</li>
     * <li>12s  =  launch for 12 seconds, default algorithm</li>
     * <li>12m  =  launch for 12 minutes, default algorithm</li>
     * <li>50t  =  launch for 50 ticks, default algorithm</li>
     * <li>20l  =  distance of 20 blocks, linear algorithm</li>
     * <li>20b  =  distance of 20 blocks, bezier algorithm</li>
     * <li>10sb =  launch for 10 seconds, bezier algorithm</li>
     * <li>8/tt =  launch at an acceleration of 8 blocks/tick^2</li>
     * <li>8/ss =  launch at an acceleration of 8 blocks/second^2</li>
     * <li>1g   =  launch at an acceleration of 9.81 blocks/second^2</li>
     * </ul>
     * 
     * @param text to parse
     * @return launcher configuration
     */
    public static LauncherConfig parse(String text) {
        LauncherConfig config = createDefault();
        config._asString = text; // preserve

        String textFilt = text;
        int idx = 0;
        boolean is_acceleration_in_g = false;
        while (idx < textFilt.length()) {
            char c = textFilt.charAt(idx);
            if (c == 'b') {
                config._launchFunction = LaunchFunction.Bezier.class;
                config._launchFunctionIsDefault = false;
            } else if (c == 'l') {
                config._launchFunction = LaunchFunction.Linear.class;
                config._launchFunctionIsDefault = false;
            } else if (c == 'g' || c == 'G') {
                is_acceleration_in_g = true;
            } else {
                idx++;
                continue;
            }

            // Parsed the character. Remove it.
            textFilt = textFilt.substring(0, idx) + textFilt.substring(idx + 1);
        }

        int accelerationStart = textFilt.indexOf('/');
        if (accelerationStart != -1) {
            // acceleration specified
            config._duration = -1;
            config._distance = -1.0;
            config._acceleration = Util.parseAcceleration(textFilt, -1.0);

            // If not specified, make sure the launch function used is linear
            // A bezier curve with constant acceleration would make for a weird default
            if (!config._launchFunctionIsDefault) {
                config._launchFunction = LaunchFunction.Linear.class;
            }
        } else if (is_acceleration_in_g) {
            // acceleration specified as a factor by G-factor (value * 9.81 / (20*20))
            config._duration = -1;
            config._distance = -1.0;
            config._acceleration = 0.024525 * ParseUtil.parseDouble(textFilt, -1.0);
        } else {
            // distance or duration specified
            config._acceleration = -1.0;
            config._duration = Util.parseTimeTicks(textFilt);
            if (config._duration < 0) {
                config._distance = ParseUtil.parseDouble(textFilt, -1.0);
            }
        }
        return config;
    }

    /**
     * Creates the defaults for a Launcher Configuration
     * 
     * @return defaults
     */
    public static LauncherConfig createDefault() {
        LauncherConfig config = new LauncherConfig();
        if (TCConfig.launchFunctionType.equalsIgnoreCase("linear")) {
            config._launchFunction = LaunchFunction.Linear.class;
        } else if (TCConfig.launchFunctionType.equalsIgnoreCase("bezier")) {
            config._launchFunction = LaunchFunction.Bezier.class;
        } else {
            config._launchFunction = LaunchFunction.Bezier.class;
        }
        config._launchFunctionIsDefault = true;
        config._duration = -1;
        config._distance = -1.0;
        config._acceleration = -1.0;
        config._asString = ""; // invalid, because it is not configured yet
        return config;
    }
}
