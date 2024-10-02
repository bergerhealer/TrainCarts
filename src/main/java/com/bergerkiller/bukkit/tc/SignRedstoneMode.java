package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.tc.signactions.SignActionType;

/**
 * A mode in which a TrainCarts sign can respond to redstone changes
 */
public enum SignRedstoneMode {
    /** Sign is active if powered by redstone */
    ON("", true, false, false, false),
    /** Sign is active if NOT powered by redstone */
    OFF("!", true, true, false, false),
    /** Sign is always powered, ignoring redstone */
    ALWAYS("+", false, false, false, false),
    /** Sign is never powered, ignoring redstone */
    NEVER("-", false, false, false, false),
    /** Sign is powered when redstone signal pulses from off to on */
    PULSE_ON("/", true, false, true, false),
    /** Sign is powered when redstone signal pulses from on to off */
    PULSE_OFF("\\", true, false, false, true),
    /** Sign is powered when redstone signal changes between on or off */
    PULSE_ALWAYS("/\\", true, false, true, true),
    /** Sign is powered OFF when redstone signal pulses from off to on */
    INVERTED_PULSE_ON("!/", true, true, true, false),
    /** Sign is powered OFF when redstone signal pulses from on to off */
    INVERTED_PULSE_OFF("!\\", true, true, false, true),
    /** Sign is powered OFF when redstone signal changes between on or off */
    INVERTED_PULSE_ALWAYS("!/\\", true, true, true, true);

    private final String pattern;
    private final boolean respondsToRedstone;
    private final boolean isInverted;
    private final boolean isRisingPulse;
    private final boolean isFallingPulse;

    SignRedstoneMode(String pattern, boolean respondsToRedstone, boolean isInverted, boolean isRisingPulse, boolean isFallingPulse) {
        this.pattern = pattern;
        this.respondsToRedstone = respondsToRedstone;
        this.isInverted = isInverted;
        this.isRisingPulse = isRisingPulse;
        this.isFallingPulse = isFallingPulse;
    }

    /**
     * Gets the pattern that corresponds with parsing this Redstone mode
     *
     * @return Pattern String
     * @see #parse(String, int)
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Gets whether signs with this Redstone mode respond to changes in Redstone powered level.
     * Modes {@link #ALWAYS} and {@link #NEVER} don't.
     *
     * @return True if signs respond to Redstone changes when using this mode
     */
    public boolean isRespondingToRedstone() {
        return respondsToRedstone;
    }

    /**
     * Gets whether signs with this Redstone mode only respond to changes in Redstone, with
     * detecting a change from off to on.
     *
     * @return True if signs activate with a rising Redstone pulse
     */
    public boolean isRisingPulse() {
        return isRisingPulse;
    }

    /**
     * Gets whether signs with this Redstone mode only respond to changes in Redstone, with
     * detecting a change from on to off.
     *
     * @return True if signs activate with a falling Redstone pulse
     */
    public boolean isFallingPulse() {
        return isFallingPulse;
    }

    /**
     * Gets whether this mode is inverted (!) compared to the normal operation
     *
     * @return True if inverted
     */
    public boolean isInverted() {
        return isInverted;
    }

    /**
     * Produces an action type in response to a change in Redstone powered state
     *
     * @param newPowerState The new Redstone power level
     * @return
     */
    public SignActionType getRedstoneAction(boolean newPowerState) {
        switch (this) {
            // ALWAYS/NEVER don't respond to Redstone, only to when trains drive onto the sign
            case ALWAYS:
            case NEVER:
                return SignActionType.NONE;

            // Sends a REDSTONE_ON when Redstone state changes
            case PULSE_ALWAYS:
                return SignActionType.REDSTONE_ON;
            case PULSE_ON:
                return newPowerState ? SignActionType.REDSTONE_ON : SignActionType.NONE;
            case PULSE_OFF:
                return newPowerState ? SignActionType.NONE : SignActionType.REDSTONE_ON;

            // Sends a REDSTONE_OFF when Redstone state changes
            // There might be some niche applications for that...
            case INVERTED_PULSE_ALWAYS:
                return SignActionType.REDSTONE_OFF;
            case INVERTED_PULSE_ON:
                return newPowerState ? SignActionType.REDSTONE_OFF : SignActionType.NONE;
            case INVERTED_PULSE_OFF:
                return newPowerState ? SignActionType.NONE : SignActionType.REDSTONE_OFF;

            // Default and inverted modes
            case OFF:
                return newPowerState ? SignActionType.REDSTONE_OFF : SignActionType.REDSTONE_ON;
            default:
                return newPowerState ? SignActionType.REDSTONE_ON : SignActionType.REDSTONE_OFF;
        }
    }

    /**
     * Used to parse the redstone mode operator part of the TrainCarts sign header.
     * Returns the parsed mode, and the index of the character that is past the
     * last decoded operator character.
     *
     * @param input Input String to parse
     * @param startIndex Start index into the String ot parse
     * @return Updated mode, or null if the input character is invalid
     */
    public static ParseResult parse(String input, int startIndex) {
        boolean power_inverted = false;
        boolean power_always_on = false;
        boolean power_always_off = false;
        boolean power_rising = false;
        boolean power_falling = false;

        int len = input.length();
        int idx;
        for (idx = startIndex; idx < len; idx++) {
            char c = input.charAt(idx);
            if (c == '!') {
                power_inverted = true;
            } else if (c == '+') {
                power_always_on = true;
            } else if (c == '-') {
                power_always_off = true;
            } else if (c == '/') {
                power_rising = true;
            } else if (c == '\\') {
                power_falling = true;
            } else {
                break;
            }
        }

        // Turn into a sensible redstone mode
        if (power_always_on) {
            return new ParseResult(idx, ALWAYS);
        } else if (power_always_off) {
            return new ParseResult(idx, NEVER);
        } else if (power_rising && power_falling) {
            return new ParseResult(idx, PULSE_ALWAYS);
        } else if (power_rising) {
            return new ParseResult(idx, power_inverted ? INVERTED_PULSE_ON : PULSE_ON);
        } else if (power_falling) {
            return new ParseResult(idx, power_inverted ? INVERTED_PULSE_OFF : PULSE_OFF);
        } else if (power_inverted) {
            return new ParseResult(idx, OFF);
        } else {
            return new ParseResult(idx, ON);
        }
    }

    public static class ParseResult {
        public final int endIndex;
        public final SignRedstoneMode mode;

        public ParseResult(int endIndex, SignRedstoneMode mode) {
            this.endIndex = endIndex;
            this.mode = mode;
        }
    }
}
