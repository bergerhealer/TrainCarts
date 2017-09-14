package com.bergerkiller.bukkit.tc;

import java.util.Locale;

import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

/**
 * Accesses all the information available on the first line of a TrainCarts sign.
 * This class acts both as a read-only container, and as a TC sign identifier.
 */
public class SignActionHeader {
    private boolean power_inverted = false;
    private boolean power_always_on = false;
    private boolean power_rising = false;
    private boolean power_falling = false;
    private boolean is_converted = false;
    private String rc_name = "";
    private SignActionMode mode = SignActionMode.NONE;
    private Direction[] directions = null;

    /**
     * Checks whether this header is a valid TrainCarts sign header.
     * 
     * @return True if valid, False if not
     */
    public boolean isValid() {
        return this.mode != SignActionMode.NONE;
    }

    /**
     * Gets whether the format of the sign had been converted from a legacy format.
     * This function will eventually disappear, do not use it!
     * 
     * @return is legacy converted
     */
    @Deprecated
    public boolean isLegacyConverted() {
        return is_converted;
    }

    /**
     * Redstone powering states should be inverted for this sign
     * 
     * @return power inverted property
     */
    public boolean isInverted() {
        return power_inverted;
    }

    /**
     * The sign is always active, ignoring Redstone changes
     * 
     * @return power always on
     */
    public boolean isAlwaysOn() {
        return power_always_on;
    }

    /**
     * Sign should be triggered on the rising edge of Redstone changes
     * 
     * @return power on rising edge (power -> on)
     */
    public boolean onPowerRising() {
        return power_rising;
    }

    /**
     * Sign should be triggered on the falling edge of Redstone changes
     * 
     * @return power on falling edge (power -> off)
     */
    public boolean onPowerFalling() {
        return power_falling;
    }

    /**
     * Gets the desired redstone power state that will trigger the sign
     * 
     * @return redstone power state
     */
    public SignRedstoneMode getRedstoneMode() {
        if (this.power_always_on) {
            return SignRedstoneMode.ALWAYS;
        } else if (this.power_rising && this.power_falling) {
            return SignRedstoneMode.PULSE_ALWAYS;
        } else if (this.power_falling) {
            return this.power_inverted ? SignRedstoneMode.PULSE_ON : SignRedstoneMode.PULSE_OFF;
        } else if (this.power_rising) {
            return this.power_inverted ? SignRedstoneMode.PULSE_OFF : SignRedstoneMode.PULSE_ON;
        } else if (this.power_inverted) {
            return SignRedstoneMode.OFF;
        } else {
            return SignRedstoneMode.ON;
        }
    }

    /**
     * Sets the desired redstone power state that will trigger the sign
     * 
     * @param mode to set to
     */
    public void setRedstoneMode(SignRedstoneMode mode) {
        switch (mode) {
        case ALWAYS:
            this.power_always_on = true;
            this.power_inverted = false;
            this.power_falling = false;
            this.power_rising = false;
            break;
        case ON:
            this.power_always_on = false;
            this.power_inverted = false;
            this.power_falling = false;
            this.power_rising = false;
            break;
        case OFF:
            this.power_always_on = false;
            this.power_inverted = true;
            this.power_falling = false;
            this.power_rising = false;
            break;
        case PULSE_ALWAYS:
            this.power_always_on = false;
            this.power_inverted = false;
            this.power_falling = true;
            this.power_rising = true;
            break;
        case PULSE_ON:
            this.power_always_on = false;
            this.power_inverted = false;
            this.power_falling = false;
            this.power_rising = true;
            break;
        case PULSE_OFF:
            this.power_always_on = false;
            this.power_inverted = false;
            this.power_falling = true;
            this.power_rising = false;
            break;
        }
    }

    /**
     * Gets the Sign Action Mode of this sign, indicating whether it is for trains, carts, or remote-control
     * 
     * @return sign action mode
     */
    public SignActionMode getMode() {
        return mode;
    }

    /**
     * Sets the Sign Action Mode of this sign, indicating whether it is for trains, carts, or remote-control
     * 
     * @param mode to set to
     */
    public void setMode(SignActionMode mode) {
        this.mode = mode;
    }

    /**
     * For remote-control signs, read the match string for remote train names affected by this sign
     * 
     * @return remote-control remote train name match string
     */
    public String getRemoteName() {
        return rc_name;
    }

    /**
     * Sets a new remote-control train name
     * 
     * @param name to set to
     */
    public void setRemoteName(String name) {
        rc_name = name;
    }

    /**
     * Checks whether the mode set matches the one specified
     * 
     * @param mode to check
     * @return true if the mode matches
     */
    public boolean isMode(SignActionMode mode) {
        return this.mode == mode;
    }

    /**
     * This is a sign meant for trains
     * 
     * @return is train sign
     */
    public boolean isTrain() {
        return mode == SignActionMode.TRAIN;
    }

    /**
     * This sign is meant for carts
     * 
     * @return is cart sign
     */
    public boolean isCart() {
        return mode == SignActionMode.CART;
    }

    /**
     * This sign is meant for remote-controlling trains by name
     * 
     * @return is RC sign
     */
    public boolean isRC() {
        return mode == SignActionMode.RCTRAIN;
    }

    /**
     * Gets whether the watched directions of this Sign are defined.
     * If this returns True, user-specified watched directions are used.
     * If this returns False, environment-specific watched directions are used.
     * 
     * @return has directions
     */
    public boolean hasDirections() {
        return directions != null;
    }

    /**
     * Gets the directions specified in the sign header
     * 
     * @return directions
     */
    public Direction[] getDirections() {
        return directions;
    }

    /**
     * Sets the directions specified in the sign header
     * 
     * @param directions
     */
    public void setDirections(Direction[] directions) {
        this.directions = directions;
    }

    /**
     * Transforms the directions specified on this sign into BlockFaces using a known absolute direction
     * 
     * @param absoluteDirection to convert the directions with
     * @return BlockFace watched faces
     */
    public BlockFace[] getFaces(BlockFace absoluteDirection) {
        if (directions == null) {
            return FaceUtil.AXIS; // fallback: all directions
        }
        BlockFace[] faces = new BlockFace[directions.length];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = directions[i].getDirection(absoluteDirection);
        }
        return faces;
    }

    /**
     * Returns a REDSTONE_ON or REDSTONE_OFF action type depending on the power state
     * and the setting applied to this sign header.
     * When the redstone change should not produce an event, NONE is returned.
     * 
     * @param newPowerState to get the event for
     * @return sign action type to send to the sign action handlers
     */
    public SignActionType getRedstoneAction(boolean newPowerState) {
        if (this.power_always_on) {
            // Always powered; redstone events are ignored
            return SignActionType.NONE;
        }

        // When we handle both rising and falling edge, send a REDSTONE_ON signal
        // When inverted, the signal becomes REDSTONE_OFF instead
        if (this.power_rising && this.power_falling) {
            return this.power_inverted ? SignActionType.REDSTONE_OFF : SignActionType.REDSTONE_ON;
        }

        // When we handle the rising edge only, send a REDSTONE_ON signal when power state goes high
        // When inverted, the signal becomes REDSTONE_OFF instead
        if (this.power_rising && !this.power_falling) {
            if (newPowerState) {
                return this.power_inverted ? SignActionType.REDSTONE_OFF : SignActionType.REDSTONE_ON;
            } else {
                return SignActionType.NONE;
            }
        }

        // When we handle the falling edge only, send a REDSTONE_ON signal when power state goes low
        // When inverted, the signal becomes REDSTONE_OFF instead
        if (!this.power_rising && this.power_falling) {
            if (!newPowerState) {
                return this.power_inverted ? SignActionType.REDSTONE_OFF : SignActionType.REDSTONE_ON;
            } else {
                return SignActionType.NONE;
            }
        }

        // When we don't handle rising/falling edge, switch between
        return newPowerState != this.power_inverted ?
                SignActionType.REDSTONE_ON : SignActionType.REDSTONE_OFF;
    }

    /**
     * Gets whether a particular sign action is allowed to be executed according to this header
     * 
     * @param type to check
     * @return True if execution should be cancelled, False if not and execution can continue
     */
    public boolean isActionFiltered(SignActionType type) {
        if (this.power_always_on && type.isRedstone()) {
            return true;
        }
        if ((this.power_rising || this.power_falling) && !type.isRedstone()) {
            return true;
        }
        return false;
    }

    /**
     * Converts this header information into a valid TrainCarts first-line sign format
     */
    @Override
    public String toString() {
        if (!this.isValid()) {
            return "";
        }

        // [+
        String prefix = "[";
        if (this.power_always_on) prefix += "+";
        if (this.power_inverted) prefix += "!";
        if (this.power_rising) prefix += "/";
        if (this.power_falling) prefix += "\\";

        // :N
        String postfix = "";
        if (this.hasDirections()) {
            postfix += ":";
            for (Direction d : this.directions) {
                postfix += d.aliases()[0];
            }
        }
        postfix += "]";

        // Combine with mode
        if (this.mode == SignActionMode.TRAIN) {
            return prefix + "train" + postfix;
        } else if (this.mode == SignActionMode.CART) {
            return prefix + "cart" + postfix;
        } else if (this.mode == SignActionMode.RCTRAIN) {
            postfix = this.rc_name + "]";
            if ((postfix.length() + prefix.length()) >= (16-6)) {
                return prefix + "t " + postfix;
            } else {
                return prefix + "train " + postfix;
            }
        } else {
            return prefix + "?" + postfix; // fallback
        }
    }

    /**
     * Parses the first line of a sign to a SignActionHeader.
     * Returned header will signal {@link #isValid()} when a valid TrainCarts header could be detected.
     * 
     * @param event the event
     * @return SignActionHeader
     */
    public static SignActionHeader parseFromEvent(SignActionEvent event) {
        return parse(event.getLine(0));
    }

    /**
     * Parses the first line in a SignChangeEvent to a SignActionHeader.
     * Returned header will signal {@link #isValid()} when a valid TrainCarts header could be detected.
     * 
     * @param event the event
     * @return SignActionHeader
     */
    public static SignActionHeader parseFromEvent(SignChangeEvent event) {
        return parse(Util.getCleanLine(event, 0));
    }

    /**
     * Parses the first line of a sign to a SignActionHeader.
     * Returned header will signal {@link #isValid()} when a valid TrainCarts header could be detected.
     * 
     * @param sign
     * @return SignActionHeader
     */
    public static SignActionHeader parseFromSign(Sign sign) {
        return parse(Util.getCleanLine(sign, 0));
    }

    /**
     * Parses the first line of a sign to a SignActionHeader.
     * Returned header will signal {@link #isValid()} when a valid TrainCarts header could be detected.
     * 
     * @param line to parse
     * @return SignActionHeader
     */
    public static SignActionHeader parse(String line) {
        SignActionHeader header = new SignActionHeader();
        if (line == null || line.isEmpty()) {
            header.mode = SignActionMode.NONE;
            return header;
        }

        boolean validStart = (line.charAt(0) == '[');
        boolean validEnd = (line.charAt(line.length() - 1) == ']');

        if (TrainCarts.allowParenthesesFormat) {
            validStart |= (line.charAt(0) == '(');
            validEnd |= (line.charAt(line.length() - 1) == ')');
        }
        if (TrainCarts.parseOldSigns && !validStart && !validEnd) {
            String s = line.toLowerCase(Locale.ENGLISH);
            if (s.startsWith("!") || s.startsWith("+")) {
                s = s.substring(1);
            }
            if (s.startsWith("train") || s.startsWith("t ") || s.startsWith("cart")) {
                header.is_converted = true;
                line = String.format("[%s]", line);
                validStart = true;
                validEnd = true;
            }
        }

        // Verify format
        if (!validStart || !validEnd) {
            header.mode = SignActionMode.NONE;
            return header;
        }

        // Parse special mode characters at the start of the line
        int idx = 1;
        while (line.length() > idx) {
            char c = line.charAt(idx);
            if (c == '!') {
                header.power_inverted = true;
            } else if (c == '+') {
                header.power_always_on = true;
            } else if (c == '/') {
                header.power_rising = true;
            } else if (c == '\\') {
                header.power_falling = true;
            } else {
                break;
            }
            idx++;
        }

        // There must now be either a train, cart, or remote-control identification token here
        String token = line.substring(idx, line.length() - 1).toLowerCase(Locale.ENGLISH);
        String after_token = "";
        header.mode = SignActionMode.NONE;
        if (token.startsWith("train ") && token.length() > 6) {
            header.mode = SignActionMode.RCTRAIN;
            after_token = line.substring(idx + 6, line.length() - 1);
        } else if (token.startsWith("t ") && token.length() > 2) {
            header.mode = SignActionMode.RCTRAIN;
            after_token = line.substring(idx + 2, line.length() - 1);
        } else if (token.startsWith("train")) {
            header.mode = SignActionMode.TRAIN;
            after_token = line.substring(idx + 5, line.length() - 1);
        } else if (token.startsWith("cart")) {
            header.mode = SignActionMode.CART;
            after_token = line.substring(idx + 4, line.length() - 1);
        } else {
            header.mode = SignActionMode.NONE;
            return header; // invalid header!
        }

        if (header.mode == SignActionMode.RCTRAIN) {
            // Remote-control does not use directions
            header.rc_name = after_token;
        } else {
            // Check for directions defined following a :
            if (after_token.startsWith(":")) {
                after_token = after_token.substring(1);
                header.directions = Direction.parseAll(after_token);
            }
        }

        // Done parsing!
        return header;
    }

}
