package com.bergerkiller.bukkit.tc.rails.direction;

import com.bergerkiller.bukkit.tc.Direction;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.utils.FaceUtil;

/**
 * Some helper stuff nobody else has to be concerned about
 */
class RailEnterDirectionImpl {
    public static final RailEnterDirection[] NONE = new RailEnterDirection[0];
    public static final RailEnterDirection[] ALL;
    private static final Map<String, DirectionEnterDirection> DIRECTION_BY_NAME;
    private static final Map<Character, Direction> DIRECTION_BY_CHAR;
    static {
        ALL = new RailEnterDirection[FaceUtil.BLOCK_SIDES.length];
        for (int i = 0; i < ALL.length; i++) {
            ALL[i] = RailEnterDirection.toFace(FaceUtil.BLOCK_SIDES[i]);
        }

        DIRECTION_BY_NAME = new HashMap<>();
        DIRECTION_BY_CHAR = new HashMap<>();
        for (Direction direction : Direction.values()) {
            if (direction != Direction.NONE) {
                // Convert to a RailEnterDirection generator. Optimization possible for absolute directions.
                DirectionEnterDirection enterDirection;
                if (direction.isAbsolute()) {
                    final RailEnterDirection[] constant = RailEnterDirectionToFace.arrayFromFace(
                            direction.getDirection(BlockFace.DOWN));
                    enterDirection = a -> constant;
                } else {
                    final Direction d = direction;
                    enterDirection = a -> RailEnterDirectionToFace.arrayFromFace(d.getDirection(a));
                }

                // Map it to all the names
                for (String name : direction.aliases()) {
                    String name_lower = name.toLowerCase(Locale.ENGLISH);
                    String name_upper = name.toUpperCase(Locale.ENGLISH);

                    DIRECTION_BY_NAME.put(name_lower, enterDirection);
                    DIRECTION_BY_NAME.put(name_upper, enterDirection);
                    if (name.length() == 1) {
                        DIRECTION_BY_CHAR.put(name_lower.charAt(0), direction);
                        DIRECTION_BY_CHAR.put(name_upper.charAt(0), direction);
                    }
                }
            }
        }

        // "All" constant
        DIRECTION_BY_NAME.put("*", a -> ALL);
        DIRECTION_BY_NAME.put("all", a -> ALL);
        DIRECTION_BY_NAME.put("ALL", a -> ALL);

        // None constant. Important, because empty text token has a special meaning during parsing
        DIRECTION_BY_NAME.put("", a -> NONE);
    }

    public static RailEnterDirection[] parseAll(RailPiece rail, BlockFace forwardDirection, String text) {
        // Try to use the map first without doing any toLowerCase stuff
        // People really should just never be using mixed uppercase characters...
        {
            DirectionEnterDirection dir = DIRECTION_BY_NAME.get(text);
            if (dir != null) {
                return dir.get(forwardDirection);
            }
        }

        // Try lowercased
        {
            DirectionEnterDirection dir = DIRECTION_BY_NAME.get(text.toLowerCase(Locale.ENGLISH));
            if (dir != null) {
                return dir.get(forwardDirection);
            }
        }

        // Start tokenizing the input text
        DirectionList result = new DirectionList(text);

        // Try to identify junction names in the input text, repeat
        // For this, ignore junction names which already equal a DIRECTION character
        // These are the vanilla rails, for which we don't care about the junctions...
        for (RailJunction junction : rail.getJunctions()) {
            String name = junction.name();
            int nameLen = name.length();
            if (nameLen == 0 || (nameLen == 1 && DIRECTION_BY_CHAR.containsKey(name.charAt(0)))) {
                continue;
            }

            result.matchJunction(name, junction);
        }

        // For what remains, try to match them against the known direction characters
        // Remove text elements that match nothing
        result.finish(forwardDirection);

        // Finally, compile the full list into an array
        // Duplicate checking is kind of a waste of time
        return result.toArray();
    }

    private static class DirectionList {
        final LinkedList<DirectionToken> list = new LinkedList<>();

        public DirectionList(String text) {
            list.add(new DirectionToken(text));
        }

        public void matchJunction(String name, RailJunction junction) {
            ListIterator<DirectionToken> iter = list.listIterator();
            while (iter.hasNext()) {
                DirectionToken token = iter.next();
                int index = token.text.indexOf(name);
                if (index == -1) {
                    continue;
                }

                int len = name.length();
                if (index == 0) {
                    if (token.text.length() == len) {
                        // Whole word, simply overwrite these contents
                        token.text = "";
                        token.direction = RailEnterDirection.fromJunction(junction);
                    } else {
                        // Detected to the left, so put a new element before the previous element
                        token.text = token.text.substring(index + len);
                        iter.previous();
                        iter.add(new DirectionToken(RailEnterDirection.fromJunction(junction)));
                        // Note: next() skips the element we just added
                    }
                } else {
                    // Add junction after this token
                    iter.add(new DirectionToken(RailEnterDirection.fromJunction(junction)));

                    // Check whether there is a remainder on the right-hand-side
                    // Add it as a separate token if it exists
                    if ((index + len) < token.text.length()) {
                        iter.add(new DirectionToken(token.text.substring(index + len)));
                        iter.previous(); // Iterate backwards again
                    }

                    // Keep only left-hand portion
                    token.text = token.text.substring(0, index);

                    // Test this same token again, it might match multiple times
                    // Note: we also have to skip the elements we added for whatever reason
                    iter.previous();
                    iter.previous();
                }
            }
        }

        public void finish(BlockFace forwardDirection) {
            ListIterator<DirectionToken> iter = list.listIterator();
            while (iter.hasNext()) {
                DirectionToken token = iter.next();
                if (token.text.isEmpty()) {
                    continue; // Result element, skip it
                }

                // Keep inserting matched directions for every character matched
                // Set the direction of this element for the first element
                int len = token.text.length();
                boolean first = true;
                for (int ch_idx = 0; ch_idx < len; ch_idx++) {
                    Direction ch_dir = DIRECTION_BY_CHAR.get(token.text.charAt(ch_idx));
                    if (ch_dir != null) {
                        RailEnterDirection enterDir = RailEnterDirectionToFace.fromFace(ch_dir.getDirection(forwardDirection));
                        if (first) {
                            first = false;
                            token.direction = enterDir;
                        } else {
                            // Add one element after
                            // Note: next() will skip this newly inserted element
                            iter.add(new DirectionToken(enterDir));
                        }
                    }
                }

                // Safety I guess?
                token.text = "";

                // If not matched, remove this element
                if (first) {
                    iter.remove();
                }
            }
        }

        public RailEnterDirection[] toArray() {
            RailEnterDirection[] result = new RailEnterDirection[list.size()];
            int i = -1;
            for (DirectionToken token : list) {
                result[++i] = token.direction;
            }
            return result;
        }
    }

    private static class DirectionToken {
        String text;
        RailEnterDirection direction;

        public DirectionToken(String text) {
            this.text = text;
            this.direction = null;
        }

        public DirectionToken(RailEnterDirection direction) {
            this.text = "";
            this.direction = direction;
        }
    }

    @FunctionalInterface
    private static interface DirectionEnterDirection {
        RailEnterDirection[] get(BlockFace forwardDirection);
    }
}
