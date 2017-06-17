package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;

/**
 * Stores all the configuration parsed from a launch statement on a sign.
 * This will parse launch distance or time, and selects the launch function to use.
 */
public class LauncherConfig {
    private double _distance;
    private int _duration;
    private Class<? extends LaunchFunction> _launchFunction;

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
     * Gets the number of ticks launching should occur, or -1 if unused
     * 
     * @return launch tick time duration
     */
    public int getDuration() {
        return this._duration;
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
        this._distance = distance;
        if (distance >= 0.0) {
            this._duration = -1;
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
     * </ul>
     * 
     * @param text to parse
     * @return launcher configuration
     */
    public static LauncherConfig parse(String text) {
        LauncherConfig config = createDefault();
        String textFilt = text;
        int idx = 0;
        while (idx < textFilt.length()) {
            char c = textFilt.charAt(idx);
            if (c == 'b') {
                config._launchFunction = LaunchFunction.Bezier.class;
            } else if (c == 'l') {
                config._launchFunction = LaunchFunction.Linear.class;
            } else {
                idx++;
                continue;
            }

            // Parsed the character. Remove it.
            textFilt = textFilt.substring(0, idx) + textFilt.substring(idx + 1);
        }
        config._duration = Util.parseTimeTicks(textFilt);
        if (config._duration < 0) {
            config._distance = ParseUtil.parseDouble(textFilt, -1.0);
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
        if (TrainCarts.launchFunctionType.equalsIgnoreCase("linear")) {
            config._launchFunction = LaunchFunction.Linear.class;
        } else if (TrainCarts.launchFunctionType.equalsIgnoreCase("bezier")) {
            config._launchFunction = LaunchFunction.Bezier.class;
        } else {
            config._launchFunction = LaunchFunction.Bezier.class;
        }
        config._duration = -1;
        config._distance = -1.0;
        return config;
    }
}
