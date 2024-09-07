package com.bergerkiller.bukkit.tc.controller.spawnable;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

/**
 * Represents a parsed train spawning pattern. This parsed format is used to generate
 * a {@link SpawnableGroup}. This parsing was broken out of the main parsing code
 * so it can be unit tested. It has no use otherwise.
 */
public abstract class TrainSpawnPattern {
    /**
     * Absolute maximum amount of carts that can ever be parsed and then spawned.
     * This guards against an out of memory exploit by trying to spawn extremely long trains.
     * This limit is checked when building the train configuration.
     */
    public static final int MAX_SPAWNABLE_TRAIN_LENGTH = 1024;

    private final QuantityPrefix quantity;

    protected TrainSpawnPattern(QuantityPrefix quantity) {
        this.quantity = quantity;
    }

    /**
     * Gets the amount of times the contained information is repeated.
     * This is for number prefixes.
     *
     * @return Quantity of this spawn pattern
     */
    public QuantityPrefix quantity() {
        return quantity;
    }

    /**
     * Gets the {@link QuantityPrefix#amount} of the quantity. This is how often
     * this pattern repeats.
     *
     * @return Amount
     */
    public int amount() {
        return quantity().amount;
    }

    /**
     * Returns a new callback function which applies this format to a group.
     * When the returned consumer is invoked the represented information
     * is added to the group. It can be invoked more than once to repeat
     * the add operation.<br>
     * <br>
     * The callback can throw a TrainTooLongException if the number of members has
     * exceeded {@link #MAX_SPAWNABLE_TRAIN_LENGTH}
     *
     * @return Applier consumer function
     */
    protected abstract Applier newGroupApplier();

    protected Applier repeatWithAmount(Applier callback) {
        final int amount = quantity().amount;
        if (amount <= 0) {
            return (group, random) -> {};
        } else if (amount == 1) {
            return callback;
        } else {
            return (group, random) -> {
                for (int n = 0; n < amount; n++) {
                    callback.apply(group, random);
                }
            };
        }
    }

    /**
     * Parses a spawn pattern String into a parsed TrainSpawnPattern, which can then be used to create
     * new {@link SpawnableGroup} instances.
     *
     * @param spawnPattern Pattern String to parse
     * @param savedTrainMatcher Matches saved trains. When the input String starts with a known saved train
     *                          name, that saved train name (longest name) is returned.
     * @return ParsedSpawnPattern
     */
    public static ParsedSpawnPattern parse(String spawnPattern, Function<String, String> savedTrainMatcher) {
        Parser parser = new Parser(spawnPattern, 0, savedTrainMatcher);
        parser.parse();
        return parser.createSpawnPattern();
    }

    /**
     * Parses (part of) a spawn pattern. Keeps track of the amount prefix as it is parsing.
     */
    private static class Parser {
        private final StringBuilder quantityBuilder = new StringBuilder();
        private final String spawnPattern;
        private final int startIndex;
        private final Function<String, String> savedTrainMatcher;
        public final List<TrainSpawnPattern> patterns = new ArrayList<>();
        public SpawnableGroup.CenterMode centerMode = SpawnableGroup.CenterMode.NONE;
        private boolean foundSequenceEnd = false;

        public Parser(String spawnPattern, int startIndex, Function<String, String> savedTrainMatcher) {
            this.spawnPattern = spawnPattern;
            this.startIndex = startIndex;
            this.savedTrainMatcher = savedTrainMatcher;
        }

        public boolean hasPatterns() {
            return !patterns.isEmpty();
        }

        public boolean hasParsedContent() {
            // Check a pattern was parsed
            // Check amount builder has anything. Exclude whitespace.
            return !patterns.isEmpty() || !quantityBuilder.toString().trim().isEmpty();
        }

        public SequenceSpawnPattern toSequence(QuantityPrefix quantity) {
            return new SequenceSpawnPattern(quantity, patterns);
        }

        public void addPattern(TrainSpawnPattern pattern) {
            this.patterns.add(pattern);
        }

        public QuantityPrefix consumeQuantity() {
            try {
                // Look for a % chance sign. Parse all before it as the chance weight, rest as amount (if any)
                int chanceIndex = this.quantityBuilder.indexOf("%");
                if (chanceIndex != -1) {
                    double chanceWeight = ParseUtil.parseDouble(this.quantityBuilder.substring(0, chanceIndex), 0.0);
                    if (chanceWeight <= 0.0) {
                        // 0 causes some horrible math issues, so just pretend it's a zero amount to make it work
                        return QuantityPrefix.ZERO;
                    }

                    int amount = ParseUtil.parseInt(this.quantityBuilder.substring(chanceIndex + 1), 1);
                    return new QuantityPrefix(amount, chanceWeight);
                }

                // Amount only
                int amount = ParseUtil.parseInt(quantityBuilder.toString(), 1);
                return new QuantityPrefix(amount);
            } finally {
                quantityBuilder.setLength(0);
            }
        }

        public ParsedSpawnPattern createSpawnPattern() {
            return this.toSequence(QuantityPrefix.ONE).simplify().asSpawnPattern(this.centerMode);
        }

        public int parse() {
            int index = startIndex;
            String spawnPattern = this.spawnPattern;
            while (index < spawnPattern.length()) {
                char c = spawnPattern.charAt(index);

                // Check for the start of sub-sequences
                // This doubles as the centering mode parameter for RIGHT or MIDDLE
                if (LogicUtil.containsChar(c, "[<({")) {
                    Parser subParser = new Parser(spawnPattern, index + 1, savedTrainMatcher);
                    int subEndIndex = subParser.parse();

                    // If no other pattern was parsed yet, then this [ could signify a centering mode parameter
                    // This only applies for the top-level substring (start index is 0)
                    if (subEndIndex == spawnPattern.length() && this.startIndex == 0 && !this.hasParsedContent()) {
                        // For sure the 'RIGHT' center mode is active. But was a 'LEFT' one (closing ]) specified?
                        // Check this by asking the sub-parser whether a ] character was encountered at the end
                        this.centerMode = subParser.foundSequenceEnd
                                ? SpawnableGroup.CenterMode.MIDDLE : SpawnableGroup.CenterMode.RIGHT;

                        // Add all patterns parsed as if we parsed it
                        for (TrainSpawnPattern pattern : subParser.patterns) {
                            this.addPattern(pattern);
                        }
                        return subEndIndex;
                    }

                    // Sub-pattern encountered, add it with the quantity prefix
                    this.addPattern(subParser.toSequence(this.consumeQuantity()));
                    index = subEndIndex;
                    continue;
                }

                // Check for the end of this sub-sequence
                // This doubles as the centering mode parameter for LEFT if encountered at the top level
                if (LogicUtil.containsChar(c, "]>)}")) {
                    // For sub-sequences this counts as the end. Stop parsing.
                    if (this.startIndex > 0) {
                        this.foundSequenceEnd = true;
                        return index + 1;
                    }

                    // At the top level a stray ] indicates center mode LEFT
                    // Continue parsing the rest of the String, although anything more
                    // is technically a syntax error.
                    this.centerMode = SpawnableGroup.CenterMode.LEFT;
                    ++index;
                    continue;
                }

                // Attempt to parse a saved train name
                String name = savedTrainMatcher.apply(spawnPattern.substring(index));
                if (name != null && (name.length() > 1 || !SpawnableGroup.VanillaCartType.parse(c).isPresent())) {
                    // Saved train name matched. Continue parsing past this point. Add the train.
                    index += name.length();
                    addPattern(new SavedTrainSpawnPattern(consumeQuantity(), name));
                    continue;
                }

                // Attempt to parse a vanilla Minecart type character
                Optional<SpawnableGroup.VanillaCartType> type = SpawnableGroup.VanillaCartType.parse(c);
                if (type.isPresent()) {
                    // Vanilla cart matched. Continue parsing past this point. Add the cart.
                    ++index;
                    addPattern(new VanillaCartSpawnPattern(consumeQuantity(), type.get()));
                    continue;
                }

                // Anything else is accumulated as the amount prefix
                ++index;
                if (Character.isDigit(c) || c == '.' || c == '%') {
                    quantityBuilder.append(c);
                }
            }

            return index;
        }
    }

    /**
     * Searches for the input name in the alphabetically sorted list of names.
     * This looks for an entry where the input text starts with the name
     * in the sorted name list.
     *
     * @param sortedNames Alphabetically sorted list of names
     * @param input Input name to match
     * @return Found name, or null if none matched
     */
    public static String findNameInSortedList(List<String> sortedNames, String input) {
        // Binary search to see where roughly we need to look
        int index = Collections.binarySearch(sortedNames, input);
        if (index >= 0) {
            return sortedNames.get(index); // Exact match
        }

        // Walk steps back and find the longest matching prefix
        String longestPrefix = null;
        ListIterator<String> iter = sortedNames.listIterator(-(index + 1));
        while (iter.hasPrevious()) {
            String name = iter.previous();
            if (input.startsWith(name) && (longestPrefix == null || name.length() > longestPrefix.length())) {
                longestPrefix = name;
            } else if (longestPrefix != null) {
                break;
            }
        }
        return longestPrefix;
    }

    /**
     * Final parsed pattern. The result of parsing a pattern String. In addition to the
     * parsed formats, includes the centering mode when spawning.
     */
    public static class ParsedSpawnPattern extends SequenceSpawnPattern {
        private final SpawnableGroup.CenterMode centerMode;

        protected ParsedSpawnPattern(SequenceSpawnPattern sequence, SpawnableGroup.CenterMode centerMode) {
            super(sequence);
            this.centerMode = centerMode;
        }

        /**
         * Gets the way the train should be centered when spawning. This is changed
         * by starting or ending the pattern with characters like [].
         *
         * @return CenterMode
         */
        public SpawnableGroup.CenterMode centerMode() {
            return centerMode;
        }

        @Override
        public String toString() {
            String str = super.toString();
            return str.substring(1, str.length() - 1); // Omit surrounding []
        }
    }

    /**
     * Spawns a single vanilla-type Minecart. Is used for single-character
     * codes such as 'm'.
     */
    public static class VanillaCartSpawnPattern extends TrainSpawnPattern {
        private final SpawnableGroup.VanillaCartType type;

        public VanillaCartSpawnPattern(QuantityPrefix quantity, SpawnableGroup.VanillaCartType type) {
            super(quantity);
            this.type = type;
        }

        /**
         * Gets the type of vanilla Minecart to spawn
         *
         * @return Type
         */
        public SpawnableGroup.VanillaCartType type() {
            return type;
        }

        @Override
        public String toString() {
            return quantity().toString() + type.getCode();
        }

        @Override
        protected Applier newGroupApplier() {
            return repeatWithAmount(twoStage(group -> {
                ConfigurationNode standardCartConfig = TrainPropertiesStore.getDefaultsByName("spawner").getConfig().clone();
                standardCartConfig.remove("carts");
                group.addTrainWithConfig(standardCartConfig);
                standardCartConfig.set("entityType", type.getType());
                return Collections.singletonList(group.addMember(standardCartConfig));
            }));
        }
    }

    /**
     * Spawns a train from the saved train store
     */
    public static class SavedTrainSpawnPattern extends TrainSpawnPattern {
        private final String name;

        public SavedTrainSpawnPattern(QuantityPrefix quantity, String name) {
            super(quantity);
            this.name = name;
        }

        /**
         * Gets the name of the saved train
         *
         * @return Saved train name
         */
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return quantity().toString() + name;
        }

        @Override
        protected Applier newGroupApplier() {
            return repeatWithAmount(twoStage(group -> group.addTrainWithConfig(
                    group.getTrainCarts().getSavedTrains().getProperties(name))));
        }
    }

    /**
     * Spawns a sequence of other nested formats a certain amount of times
     */
    public static class SequenceSpawnPattern extends TrainSpawnPattern {
        private final List<TrainSpawnPattern> patterns;

        public SequenceSpawnPattern(SequenceSpawnPattern copy) {
            this(copy.quantity(), copy.patterns());
        }

        public SequenceSpawnPattern(QuantityPrefix quantity, List<TrainSpawnPattern> patterns) {
            super(quantity);
            this.patterns = patterns;
        }

        /**
         * Gets the nested patterns that are spawned
         *
         * @return Patterns
         */
        public List<TrainSpawnPattern> patterns() {
            return patterns;
        }

        /**
         * Simplifies this sequence spawn pattern. If this sequence consists of only one element,
         * and the quantity of this element is 1, and that element is also a sequence, returns that element.
         * Otherwise, returns this.
         *
         * @return Simplified sequence
         */
        public SequenceSpawnPattern simplify() {
            if (this.patterns.size() == 1 && this.quantity().isOne()) {
                TrainSpawnPattern p = this.patterns.get(0);
                if (p instanceof SequenceSpawnPattern) {
                    return (SequenceSpawnPattern) p;
                }
            }
            return this;
        }

        /**
         * Clones this sequence as a spawn pattern, including centering mode information
         *
         * @param centerMode CenterMode to set
         * @return new ParsedSpawnPattern
         */
        public ParsedSpawnPattern asSpawnPattern(SpawnableGroup.CenterMode centerMode) {
            return new ParsedSpawnPattern(this, centerMode);
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append(quantity().toString());
            str.append("[");
            boolean first = true;
            for (TrainSpawnPattern p : patterns) {
                if (first) {
                    first = false;
                } else {
                    str.append(" ");
                }
                str.append(p.toString());
            }
            str.append("]");
            return str.toString();
        }

        @Override
        protected Applier newGroupApplier() {
            // Check whether any of the patterns contain a chance weight
            boolean hasChanceWeight = false;
            for (TrainSpawnPattern pattern : patterns) {
                if (pattern.quantity().hasChanceWeight()) {
                    hasChanceWeight = true;
                    break;
                }
            }

            // With chance weight we must only activate the consumers when weight matches
            // For this, assign a weight position for each consumer and compute an index into
            // this every time it is consumed.
            // Entries without a chance weight are always active.
            if (hasChanceWeight) {
                final List<WeightedApplier> appliers = new ArrayList<>(patterns.size());
                final double totalChanceWeight;
                {
                    double chanceWeightPosition = 0.0;
                    for (TrainSpawnPattern pattern : patterns) {
                        if (pattern.quantity().hasChanceWeight()) {
                            double nextChanceWeightPosition = chanceWeightPosition + pattern.quantity().chanceWeight;
                            appliers.add(new WeightedApplier(pattern.newGroupApplier(), chanceWeightPosition, nextChanceWeightPosition));
                            chanceWeightPosition = nextChanceWeightPosition;
                        } else {
                            appliers.add(new WeightedApplier(pattern.newGroupApplier()));
                        }
                    }
                    totalChanceWeight = chanceWeightPosition;
                }
                return repeatWithAmount((group, random) -> {
                    double chanceWeightPosition = random.nextDouble(totalChanceWeight);
                    appliers.forEach(applier -> applier.apply(group, random, chanceWeightPosition));
                });
            }

            // Without chance weights this is a simple operation of macro-ing the applier callbacks
            final List<Applier> appliers = new ArrayList<>(patterns.size());
            for (TrainSpawnPattern pattern : patterns) {
                appliers.add(pattern.newGroupApplier());
            }
            return repeatWithAmount((group, random) -> {
                appliers.forEach(applier -> applier.apply(group, random));
            });
        }

        private static class WeightedApplier {
            private final Applier applier;
            private final boolean always;
            private final double chanceWeightRangeStart;
            private final double chanceWeightRangeEnd;

            public WeightedApplier(Applier applier) {
                this.applier = applier;
                this.always = true;
                this.chanceWeightRangeStart = Double.NaN;
                this.chanceWeightRangeEnd = Double.NaN;
            }

            public WeightedApplier(Applier applier, double chanceWeightRangeStart, double chanceWeightRangeEnd) {
                this.applier = applier;
                this.always = false;
                this.chanceWeightRangeStart = chanceWeightRangeStart;
                this.chanceWeightRangeEnd = chanceWeightRangeEnd;
            }

            public void apply(SpawnableGroup group, Random random, double chanceWeightPosition) {
                if (always || (chanceWeightPosition >= chanceWeightRangeStart && chanceWeightPosition < chanceWeightRangeEnd)) {
                    applier.apply(group, random);
                }
            }
        }
    }

    /**
     * The quantity prefix of (part of) a spawn pattern. This controls how often the
     * pattern repeats.
     */
    public static class QuantityPrefix {
        public static final QuantityPrefix ZERO = new QuantityPrefix(0);
        public static final QuantityPrefix ONE = new QuantityPrefix(1);
        public final int amount;
        public final double chanceWeight;

        public QuantityPrefix(int amount) {
            this(amount, Double.NaN);
        }

        public QuantityPrefix(int amount, double chanceWeight) {
            this.amount = amount;
            this.chanceWeight = chanceWeight;
        }

        public boolean isOne() {
            return amount == 1 && Double.isNaN(chanceWeight);
        }

        public boolean hasChanceWeight() {
            return !Double.isNaN(chanceWeight);
        }

        @Override
        public String toString() {
            if (hasChanceWeight()) {
                StringBuilder str = new StringBuilder();
                if (chanceWeight == Math.floor(chanceWeight)) {
                    str.append((int) chanceWeight);
                } else {
                    str.append(chanceWeight);
                }
                str.append('%');
                if (amount != 1) {
                    str.append(amount);
                }
                return str.toString();
            }

            return isOne() ? "" : Integer.toString(amount);
        }
    }

    /**
     * Applies information to a train in two stages. The first invocation the spawnable members
     * are parsed and created. In subsequent invocations the existing, already-added members
     * are cloned and added again. This avoids extra parsing overhead.
     *
     * @param initializer Initializer function that first adds the members to the group
     * @return Applier callback
     */
    private static Applier twoStage(Function<SpawnableGroup, List<SpawnableMember>> initializer) {
        return new TwoStageApplier(initializer);
    }

    /**
     * @see #twoStage(Function) 
     */
    private static class TwoStageApplier implements Applier {
        private final Function<SpawnableGroup, List<SpawnableMember>> initializer;
        private List<SpawnableMember> initializedMembers = null;

        public TwoStageApplier(Function<SpawnableGroup, List<SpawnableMember>> initializer) {
            this.initializer = initializer;
        }

        @Override
        public void apply(SpawnableGroup spawnableGroup, Random random) {
            List<SpawnableMember> initializedMembers = this.initializedMembers;
            if (initializedMembers != null) {
                if ((spawnableGroup.getMembers().size() + initializedMembers.size()) > MAX_SPAWNABLE_TRAIN_LENGTH) {
                    throw new TrainTooLongException();
                }

                initializedMembers.forEach(spawnableGroup::addMember);
            } else {
                this.initializedMembers = this.initializer.apply(spawnableGroup);
                if (spawnableGroup.getMembers().size() > MAX_SPAWNABLE_TRAIN_LENGTH) {
                    // Remove again & fail
                    for (int n = 0; n < initializedMembers.size() && !spawnableGroup.getMembers().isEmpty(); n++) {
                        spawnableGroup.getMembers().remove(spawnableGroup.getMembers().size() - 1);
                    }
                    throw new TrainTooLongException();
                }
            }
        }
    }

    /**
     * Applies a spawn pattern to a spawnable group, populating its members
     */
    public interface Applier {
        void apply(SpawnableGroup group, Random random);
    }

    public static class TrainTooLongException extends RuntimeException {
    }
}
