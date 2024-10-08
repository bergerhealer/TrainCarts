package com.bergerkiller.bukkit.tc;

import java.util.Locale;

import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.rails.direction.RailEnterDirection;
import com.bergerkiller.bukkit.tc.signactions.SignActionMode;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;

/**
 * Accesses all the information available on the first line of a TrainCarts sign.
 * This class acts both as a read-only container, and as a TC sign identifier.
 */
public class SignActionHeader {
    private boolean is_converted = false;
    private boolean is_empty = false;
    private String rc_name = "";
    private SignRedstoneMode redstoneMode = SignRedstoneMode.ON;
    private SignActionMode mode = SignActionMode.NONE;
    private String directions_str = null;
    private String modeText = "";

    // These are initialized/cached the first time rail enter directions are requested
    private RailPiece rail_enter_dirs_rail = null;
    private BlockFace rail_enter_dirs_fwd = null;
    private RailEnterDirection[] rail_enter_dirs = null;

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
     * Gets whether the header line is completely empty
     *
     * @return True if the first line (header line) is completely empty
     */
    public boolean isEmpty() {
        return is_empty;
    }

    /**
     * Redstone powering states should be inverted for this sign
     * 
     * @return power inverted property
     */
    public boolean isInverted() {
        return redstoneMode.isInverted();
    }

    /**
     * The sign is always active, ignoring Redstone changes
     * 
     * @return power always on
     */
    public boolean isAlwaysOn() {
        return redstoneMode == SignRedstoneMode.ALWAYS;
    }

    /**
     * The sign is always inactive, ignoring Redstone changes
     * 
     * @return power always off
     */
    public boolean isAlwaysOff() {
        return redstoneMode == SignRedstoneMode.NEVER;
    }

    /**
     * Sign should be triggered on the rising edge of Redstone changes
     * 
     * @return power on rising edge (power -> on)
     */
    public boolean onPowerRising() {
        return redstoneMode.isRisingPulse();
    }

    /**
     * Sign should be triggered on the falling edge of Redstone changes
     * 
     * @return power on falling edge (power -> off)
     */
    public boolean onPowerFalling() {
        return redstoneMode.isFallingPulse();
    }

    /**
     * Gets the desired redstone power state that will trigger the sign
     * 
     * @return redstone power state
     */
    public SignRedstoneMode getRedstoneMode() {
        return this.redstoneMode;
    }

    /**
     * Sets the desired redstone power state that will trigger the sign
     * 
     * @param mode to set to
     */
    public void setRedstoneMode(SignRedstoneMode mode) {
        this.redstoneMode = mode;
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
     * Gets the text that was used to parse {@link #getMode()}, remote control names
     * and/or direction statements. If the mode is NONE (invalid), then this text can
     * be used to match other types of headers providing they use [] syntax.
     *
     * @return Mode text
     */
    public String getModeText() {
        return modeText;
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
     * @deprecated Use {@link #hasEnterDirections()} instead
     */
    @Deprecated
    public boolean hasDirections() {
        return directions_str != null;
    }

    /**
     * Gets the directions specified in the sign header
     * 
     * @return directions
     * @deprecated Doesn't support junctions, should use {@link #getEnterDirections(RailPiece, BlockFace)}
     */
    @Deprecated
    public Direction[] getDirections() {
        return directions_str == null ? null : Direction.parseAll(directions_str);
    }

    /**
     * Gets whether sign activation rail-enter directions are defined on the
     * first line of the sign. If this returns True, the user-specified directions
     * should be used. If this returns False, environment-specific rail
     * enter directions should be used, instead.
     *
     * @return True if rail-enter trigger directions are defined
     */
    public boolean hasEnterDirections() {
        return directions_str != null;
    }

    /**
     * Gets the rail-enter directions that activate the sign
     *
     * @param rail Rail to resolve junction names
     * @param forwardDirection Forward direction relative to which left/right/etc. directions are resolved.
     *                         Supports all cardinal directions including sub-cardinal ones like north-west.
     * @return RailEnterDirection array, or null if {@link #hasEnterDirections()} returns false
     */
    public RailEnterDirection[] getEnterDirections(RailPiece rail, BlockFace forwardDirection) {
        if (directions_str == null) {
            return null;
        }
        if (rail_enter_dirs_rail == rail && rail_enter_dirs_fwd == forwardDirection) {
            return rail_enter_dirs;
        }

        rail_enter_dirs_rail = rail;
        rail_enter_dirs_fwd = forwardDirection;
        return rail_enter_dirs = RailEnterDirection.parseAll(rail, forwardDirection, directions_str);
    }

    /**
     * Sets the enter directions of this header using a text expression
     *
     * @param text
     */
    public void setEnterDirectionsText(String text) {
        directions_str = text;
        // Reset these
        rail_enter_dirs_rail = null;
        rail_enter_dirs_fwd = null;
        rail_enter_dirs = null;
    }

    /**
     * Sets the absolute enter directions of this header
     *
     * @param directions
     */
    public void setEnterDirections(RailEnterDirection[] directions) {
        if (directions == null) {
            setEnterDirectionsText(null);
        } else if (directions.length == 0) {
            setEnterDirectionsText("");
        } else if (directions.length == 1) {
            setEnterDirectionsText(directions[0].name());
        } else {
            StringBuilder str = new StringBuilder(directions.length * 2);
            for (RailEnterDirection dir : directions) {
                str.append(dir.name());
            }
            setEnterDirectionsText(str.toString());
        }
    }

    /**
     * Sets the directions specified in the sign header
     * 
     * @param directions
     * @deprecated Use {@link #setEnterDirections(RailEnterDirection[])} instead
     */
    @Deprecated
    public void setDirections(Direction[] directions) {
        if (directions == null) {
            setEnterDirectionsText(null);
        } else {
            // Ugh. Hardcode it I guess.
            if (directions.length == 0) {
                setEnterDirectionsText("");
            } else if (directions.length == 1) {
                Direction d = directions[0];
                setEnterDirectionsText(isValidDirection(d) ? d.aliases()[0] : "");
            } else {
                StringBuilder str = new StringBuilder();
                for (Direction d : directions) {
                    if (isValidDirection(d)) {
                        str.append(d.aliases()[0]);
                    }
                }
                setEnterDirectionsText(str.toString());
            }
        }
    }

    private boolean isValidDirection(Direction direction) {
        return direction != Direction.NONE &&
               direction != Direction.CONTINUE &&
               direction != Direction.REVERSE;
    }

    /**
     * Transforms the directions specified on this sign into BlockFaces using a known absolute direction
     * 
     * @param absoluteDirection to convert the directions with
     * @return BlockFace watched faces
     * @deprecated Doesn't support junctions, use {@link #getEnterDirections(RailPiece, BlockFace)} instead
     */
    @Deprecated
    public BlockFace[] getFaces(BlockFace absoluteDirection) {
        if (directions_str == null) {
            return FaceUtil.AXIS; // fallback: all directions
        }

        return RailEnterDirection.toFacesOnly(this.getEnterDirections(RailPiece.NONE, absoluteDirection));
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
        return this.redstoneMode.getRedstoneAction(newPowerState);
    }

    /**
     * Gets whether a particular sign action is allowed to be executed according to this header
     * 
     * @param type to check
     * @return True if execution should be cancelled, False if not and execution can continue
     */
    public boolean isActionFiltered(SignActionType type) {
        if (type == SignActionType.NONE) {
            return false;
        }
        if (!this.redstoneMode.isRespondingToRedstone() &&
            (type == SignActionType.REDSTONE_ON || type == SignActionType.REDSTONE_OFF)) {
            return true;
        }
        if ((this.redstoneMode.isRisingPulse() || this.redstoneMode.isFallingPulse()) && !type.isRedstone()) {
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
        String prefix = "[" + this.redstoneMode.getPattern();

        // :N
        String postfix = "";
        if (this.directions_str != null) {
            postfix += ":" + this.directions_str;
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
            header.is_empty = true;
            return header;
        }

        boolean validStart = (line.charAt(0) == '[');
        boolean validEnd = (line.charAt(line.length() - 1) == ']');

        if (TCConfig.allowParenthesesFormat) {
            validStart |= (line.charAt(0) == '(');
            validEnd |= (line.charAt(line.length() - 1) == ')');
        }
        if (TCConfig.parseOldSigns && !validStart && !validEnd) {
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
        SignRedstoneMode.ParseResult redstoneParseResult = SignRedstoneMode.parse(line, 1);
        header.setRedstoneMode(redstoneParseResult.mode);

        int idx = redstoneParseResult.endIndex;

        // There must now be either a train, cart, or remote-control identification token here
        String token = line.substring(idx, line.length() - 1).toLowerCase(Locale.ENGLISH);
        String after_token = "";

        header.modeText = token;
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
                header.directions_str = after_token;
            }
        }

        // Done parsing!
        return header;
    }

}
