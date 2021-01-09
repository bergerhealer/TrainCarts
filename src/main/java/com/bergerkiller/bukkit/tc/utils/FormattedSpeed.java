package com.bergerkiller.bukkit.tc.utils;

import java.util.Locale;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;

/**
 * Speed parsed from text. Optionally declares whether the speed
 * change is relative (starts with a + or -). Also stores the
 * unit that was used.
 */
public class FormattedSpeed {
    public static final FormattedSpeed ZERO = of(0.0);
    private final double _value;
    private final boolean _relative;
    private final double _unitMultiplier;
    private final String _unitName;

    public FormattedSpeed(double value, boolean isRelative, double unitMultiplier, String unitName) {
        this._value = value;
        this._relative = isRelative;
        this._unitMultiplier = unitMultiplier;
        this._unitName = unitName;
    }

    public double getValue() {
        return this._value;
    }

    public boolean isRelative() {
        return this._relative;
    }

    public double getUnitMultiplier() {
        return this._unitMultiplier;
    }

    public String getUnitName() {
        return this._unitName;
    }

    /**
     * Creates a formatted speed in the default b/t unit, with the given
     * speed value.
     *
     * @param value Speed value in b/t
     * @return formatted speed
     */
    public static FormattedSpeed of(double value) {
        return new FormattedSpeed(value, false, 1.0, "b/t");
    }

    /**
     * Parses a number with optional velocity unit to a formatted speed value.
     * Assumes 1 block is 1 meter.
     * Supports the following formats:
     * <ul>
     * <li>12     =  12 blocks/tick</li>
     * <li>12.5   =  12.5 blocks/tick</li>
     * <li>20m/s  =  20 meters/second (1 blocks/tick)</li>
     * <li>20km/h =  20 kilometers/hour (0.27778 blocks/tick)</li>
     * <li>20mi/h =  20 miles/hour (0.44704 blocks/tick)</li>
     * <li>3.28ft/s = same as 1 meters/second (0.05 blocks/tick)</li>
     * <li>20kmh  =  same as 20km/h</li>
     * <li>20kmph =  same as 20km/h</li>
     * <li>20mph  =  same as 20mi/h</li>
     * </ul>
     *
     * @param velocityString The text to parse
     * @param defaultValue The default value to return if parsing fails
     * @return parsed velocity in blocks/tick
     */
    public static FormattedSpeed parse(String velocityString, FormattedSpeed defaultValue) {
        String numberText = velocityString;
        String unitText = "";
        for (int i = 0; i < velocityString.length(); i++) {
            char c = velocityString.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != ',' && c != ' ' && c != '-' && c != '+') {
                numberText = velocityString.substring(0, i);
                unitText = velocityString.substring(i).replace(" ", "").trim().toLowerCase(Locale.ENGLISH);
                break;
            }
        }
        boolean relative = numberText.startsWith("-") || numberText.startsWith("+");
        double value = ParseUtil.parseDouble(numberText, Double.NaN);
        if (Double.isNaN(value)) {
            return defaultValue;
        }

        double unitMultiplier = 1.0;
        if (unitText.length() >= 3) {
            // Perform a few common translations
            if (unitText.equals("mph") || unitText.equals("mphr")) {
                unitText = "mi/h";
            } else if (LogicUtil.contains(unitText, "kmh", "kmph", "kmphr")) {
                unitText = "km/h";
            }

            // Try to convert the value based on the unit
            int slashIndex = unitText.indexOf('/', 1);
            if (slashIndex != -1) {
                // Get the numerator / denominator part of the speed unit fraction
                String num = unitText.substring(0, slashIndex);
                String den = unitText.substring(slashIndex + 1);
                if (num.equals("k") || num.equals("km")) {
                    unitMultiplier *= 1000.0; // Kilometers
                } else if (num.equals("mi")) {
                    unitMultiplier *= 1609.344; // Miles
                } else if (num.equals("ft")) {
                    unitMultiplier *= (1.0 / 3.28); // Feet
                }
                if (LogicUtil.contains(den, "s", "sec", "second")) {
                    unitMultiplier /= 20.0;
                } else if (LogicUtil.contains(den, "h", "hr", "hour")) {
                    unitMultiplier /= (20.0 * 3600.0);
                }
            }

            value *= unitMultiplier;
        }
        return new FormattedSpeed(value, relative, unitMultiplier, unitText);
    }
}
