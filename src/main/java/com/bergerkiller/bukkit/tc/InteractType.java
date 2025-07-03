package com.bergerkiller.bukkit.tc;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

/**
 * An active type of Object to interact with
 */
public enum InteractType {
    CHEST, FURNACE, DISPENSER, GROUNDITEM, DROPPER;

    private static final TargetInOut[] COLLECT_TARGETS = new TargetInOut[] {
            new TargetInOut("chest out", CHEST),
            new TargetInOut("dispenser out", DISPENSER),
            new TargetInOut("dropper out", DROPPER),
            new TargetInOut("furnace out", FURNACE),
            new TargetInOut("pickup", GROUNDITEM),
            new TargetInOut("pick up", GROUNDITEM),
    };
    private static final TargetInOut[] DEPOSIT_TARGETS = new TargetInOut[] {
            new TargetInOut("chest in", CHEST),
            new TargetInOut("dispenser in", DISPENSER),
            new TargetInOut("furnace in", FURNACE),
            new TargetInOut("smelt", FURNACE),
            new TargetInOut("drop items", GROUNDITEM),
            new TargetInOut("dropitems", GROUNDITEM)
    };

    /**
     * Gets an array of all unique strings that the second line of the transfer sign should start with
     * to match one of these interaction types.
     *
     * @return Array of all matching identifiers
     */
    public static String[] getAllUniqueTypeIdentifiers() {
        return Stream.concat(
                   Stream.concat(
                       Stream.of(COLLECT_TARGETS),
                       Stream.of(DEPOSIT_TARGETS)
                   ).map(target -> target.prefix),
                   Stream.of("collect", "deposit")
               ).toArray(String[]::new);
    }

    /**
     * Parses all the active interactables represented by the root and name
     *
     * @param root of the active object operation (collect/deposit)
     * @param name of the object (textual expression)
     * @return A collection of InteractTypes represented
     */
    public static Collection<InteractType> parse(String root, String name) {
        name = name.toLowerCase();
        LinkedHashSet<InteractType> typesToCheck = new LinkedHashSet<>();
        if (root.equals("collect")) {
            for (TargetInOut target : COLLECT_TARGETS) {
                if (name.startsWith(target.prefix)) {
                    typesToCheck.add(target.type);
                    break;
                }
            }
        } else if (root.equals("deposit")) {
            for (TargetInOut target : DEPOSIT_TARGETS) {
                if (name.startsWith(target.prefix)) {
                    typesToCheck.add(target.type);
                    break;
                }
            }
        }
        if (name.startsWith(root + ' ')) {
            String types = name.substring(root.length() + 1).toLowerCase();
            if (types.startsWith("chest")) {
                typesToCheck.add(CHEST);
            } else if (types.startsWith("furn")) {
                typesToCheck.add(FURNACE);
            } else if (types.startsWith("disp")) {
                typesToCheck.add(DISPENSER);
            } else if (types.startsWith("drop")) {
                typesToCheck.add(DROPPER);
            } else if (types.startsWith("ground")) {
                typesToCheck.add(GROUNDITEM);
            } else {
                for (char c : types.toCharArray()) {
                    if (c == 'c') {
                        typesToCheck.add(CHEST);
                    } else if (c == 'f') {
                        typesToCheck.add(FURNACE);
                    } else if (c == 'd') {
                        typesToCheck.add(DISPENSER);
                    } else if (c == 'g') {
                        typesToCheck.add(GROUNDITEM);
                    }
                }
            }
        }
        if (name.startsWith(root) && typesToCheck.isEmpty()) {
            typesToCheck.add(CHEST);
            typesToCheck.add(FURNACE);
            typesToCheck.add(DISPENSER);
            typesToCheck.add(DROPPER);
        }
        return typesToCheck;
    }

    private static class TargetInOut {
        public final String prefix;
        public final InteractType type;

        public TargetInOut(String prefix, InteractType type) {
            this.prefix = prefix;
            this.type = type;
        }
    }
}
