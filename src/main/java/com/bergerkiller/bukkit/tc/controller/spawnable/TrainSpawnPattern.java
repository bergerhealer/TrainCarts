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
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a parsed train spawning pattern. This parsed format is used to generate
 * a {@link SpawnableGroup}. This parsing was broken out of the main parsing code
 * so it can be unit tested. It has no use otherwise.
 */
public abstract class TrainSpawnPattern {
    private final int amount;

    protected TrainSpawnPattern(int amount) {
        this.amount = amount;
    }

    /**
     * Returns a new callback function which applies this format to a group.
     * When the returned consumer is invoked the represented information
     * is added to the group. It can be invoked more than once to repeat
     * the add operation.
     *
     * @return Applier consumer function
     */
    protected abstract Consumer<SpawnableGroup> newGroupApplier();

    protected Consumer<SpawnableGroup> repeatWithAmount(Consumer<SpawnableGroup> callback) {
        if (amount() <= 0) {
            return group -> {};
        } else if (amount() == 1) {
            return callback;
        } else {
            final int amount = amount();
            return group -> {
                for (int n = 0; n < amount; n++) {
                    callback.accept(group);
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
        StringBuilder amountBuilder = new StringBuilder();
        SpawnableGroup.CenterMode centerMode = SpawnableGroup.CenterMode.NONE;
        List<TrainSpawnPattern> patterns = new ArrayList<>();

        for (int typeTextIdx = 0; typeTextIdx < spawnPattern.length(); typeTextIdx++) {
            // First check centering mode changing characters
            char c = spawnPattern.charAt(typeTextIdx);
            if (LogicUtil.containsChar(c, "]>)}")) {
                centerMode = centerMode.next(SpawnableGroup.CenterMode.LEFT);
                continue;
            }
            if (LogicUtil.containsChar(c, "[<({")) {
                centerMode = centerMode.next(SpawnableGroup.CenterMode.RIGHT);
                continue;
            }

            // Attempt to parse a saved train name
            String name = savedTrainMatcher.apply(spawnPattern.substring(typeTextIdx));
            if (name != null && (name.length() > 1 || !SpawnableGroup.VanillaCartType.parse(c).isPresent())) {
                typeTextIdx += name.length() - 1;
                int amount = ParseUtil.parseInt(amountBuilder.toString(), 1);
                amountBuilder.setLength(0);
                patterns.add(new SavedTrainSpawnPattern(amount, name));
            } else {
                Optional<SpawnableGroup.VanillaCartType> type = SpawnableGroup.VanillaCartType.parse(c);
                if (type.isPresent()) {
                    int amount = ParseUtil.parseInt(amountBuilder.toString(), 1);
                    amountBuilder.setLength(0);
                    patterns.add(new VanillaCartSpawnPattern(amount, type.get()));
                } else {
                    amountBuilder.append(c);
                }
            }
        }

        return new ParsedSpawnPattern(patterns, centerMode);
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
     * Gets the amount of times the contained information is repeated.
     * This is for number prefixes.
     *
     * @return Amount of times this format repeats
     */
    public int amount() {
        return amount;
    }

    /**
     * Final parsed pattern. The result of parsing a pattern String. In addition to the
     * parsed formats, includes the centering mode when spawning.
     */
    public static class ParsedSpawnPattern extends SequenceSpawnPattern {
        private final SpawnableGroup.CenterMode centerMode;

        public ParsedSpawnPattern(List<TrainSpawnPattern> patterns, SpawnableGroup.CenterMode centerMode) {
            super(1, patterns);
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
    }

    /**
     * Spawns a single vanilla-type Minecart. Is used for single-character
     * codes such as 'm'.
     */
    public static class VanillaCartSpawnPattern extends TrainSpawnPattern {
        private final SpawnableGroup.VanillaCartType type;

        public VanillaCartSpawnPattern(int amount, SpawnableGroup.VanillaCartType type) {
            super(amount);
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
        protected Consumer<SpawnableGroup> newGroupApplier() {
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

        public SavedTrainSpawnPattern(int amount, String name) {
            super(amount);
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
        protected Consumer<SpawnableGroup> newGroupApplier() {
            return repeatWithAmount(twoStage(group -> group.addTrainWithConfig(
                    group.getTrainCarts().getSavedTrains().getProperties(name))));
        }
    }

    /**
     * Spawns a sequence of other nested formats a certain amount of times
     */
    public static class SequenceSpawnPattern extends TrainSpawnPattern {
        private final List<TrainSpawnPattern> patterns;

        public SequenceSpawnPattern(int amount, List<TrainSpawnPattern> patterns) {
            super(amount);
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

        @Override
        protected Consumer<SpawnableGroup> newGroupApplier() {
            final List<Consumer<SpawnableGroup>> appliers = new ArrayList<>(patterns.size());
            for (TrainSpawnPattern pattern : patterns) {
                appliers.add(pattern.newGroupApplier());
            }
            return repeatWithAmount(group -> {
                appliers.forEach(applier -> applier.accept(group));
            });
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
    private static Consumer<SpawnableGroup> twoStage(Function<SpawnableGroup, List<SpawnableMember>> initializer) {
        return new TwoStageApplier(initializer);
    }

    /**
     * @see #twoStage(Function) 
     */
    private static class TwoStageApplier implements Consumer<SpawnableGroup> {
        private final Function<SpawnableGroup, List<SpawnableMember>> initializer;
        private List<SpawnableMember> initializedMembers = null;

        public TwoStageApplier(Function<SpawnableGroup, List<SpawnableMember>> initializer) {
            this.initializer = initializer;
        }

        @Override
        public void accept(SpawnableGroup spawnableGroup) {
            List<SpawnableMember> initializedMembers = this.initializedMembers;
            if (initializedMembers != null) {
                initializedMembers.forEach(spawnableGroup::addMember);
            } else {
                this.initializedMembers = this.initializer.apply(spawnableGroup);
            }
        }
    }
}
