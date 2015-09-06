package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;

public enum SignActionMode {
    TRAIN, CART, RCTRAIN, NONE;

    public static SignActionMode fromString(String nameIn) {
        String name = convertOldSignString(nameIn);
        if (name.endsWith("]") && name.startsWith("[")) {
            name = name.substring(1, name.length() - 1);
            if (name.startsWith("!") || name.startsWith("+")) {
                name = name.substring(1);
            }
            name = name.toLowerCase();
            // further parsing
            if (name.startsWith("train ") && name.length() > 6) {
                return RCTRAIN;
            } else if (name.startsWith("t ") && name.length() > 2) {
                return RCTRAIN;
            } else if (name.startsWith("train")) {
                return TRAIN;
            } else if (name.startsWith("cart")) {
                return CART;
            } else if (name.startsWith("Train ") && name.length() > 6) {
                return RCTRAIN;
            } else if (name.startsWith("T ") && name.length() > 2) {
                return RCTRAIN;
            } else if (name.startsWith("Train")) {
                return TRAIN;
            } else if (name.startsWith("Cart")) {
                return CART;
            }
        }
        return NONE;
    }

    public static SignActionMode fromSign(Sign sign) {
        return sign == null ? NONE : fromString(sign.getLine(0));
    }

    public static SignActionMode fromEvent(SignActionEvent event) {
        return fromSign(event.getSign());
    }

    public static SignActionMode fromEvent(SignChangeEvent event) {
        return fromString(event.getLine(0));
    }

    /*
     * Going from Minecraft 1.7 to 1.8, signs with "[ ... ]" had the [] removed.
     * Set configuration option parseOldSigns = true if you are upgrading from an
     * older version to 1.8 and you want your old signs to work. This code adds back
     * [] to things that "look like" train signs.
     */

    public static String convertOldSignString(String nameIn) {
        if (!TrainCarts.parseOldSigns) {
            return nameIn;
        }
        if (nameIn.endsWith("]") && nameIn.startsWith("[")) {
            return nameIn;
        }
        if (nameIn.length() >= 15) {
            // Adding [ ] would make the line too long (16 characters per line)
            return nameIn;
        }
        String name = nameIn;
        if (name.startsWith("!") || name.startsWith("+")) {
            name = name.substring(1);
        }
        if (name.startsWith("train") || name.startsWith("t ") || name.startsWith("cart") || name.startsWith("Train") || name.startsWith("T ") || name.startsWith("Cart")) {
            return String.format("[%s]", nameIn);
        }
        return nameIn;
    }
}
