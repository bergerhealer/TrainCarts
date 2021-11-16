package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.type.PlayersInTrainSelector;
import com.bergerkiller.bukkit.tc.commands.selector.type.TrainNameSelector;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.IPropertySelectorCondition;

/**
 * TrainCarts selector handler registry. Pre-registers TrainCarts
 * built-in selectors.
 */
public class TCSelectorHandlerRegistry extends SelectorHandlerRegistry {
    private final Map<String, IPropertySelectorCondition> conditions;
    private final List<SelectorHandlerConditionOption> options;

    public TCSelectorHandlerRegistry(TrainCarts plugin) {
        super(plugin);
        this.conditions = new HashMap<>();
        this.options = new ArrayList<>();

        // These options always exist - are part of the TCSelectorLocationFilter logic
        //TODO: Needs special types, such as world name / number expression suggestions
        for (String s : new String[] {"world", "x", "y", "z", "dx", "dy", "dz", "distance"}) {
            this.options.add(SelectorHandlerConditionOption.optionString(s));
        }
    }

    @Override
    public void enable() {
        super.enable();
        register("ptrain", new PlayersInTrainSelector(this));
        register("train", new TrainNameSelector(this));

        // Some special selector conditions that aren't related to a train property
        {
            IPropertySelectorCondition speedCondition = (properties, condition) -> {
                MinecartGroup group = properties.getHolder();
                double speed = (group == null || group.isEmpty())
                        ? 0.0 : group.head().getRealSpeedLimited();
                return condition.matchesNumber(speed);
            };
            registerCondition("speed", speedCondition);
            registerCondition("velocity", speedCondition);
        }
    }

    /**
     * Registers a new @train or @ptrain condition, that can after registration
     * be used to filter train properties.
     *
     * @param name Name of the condition
     * @param condition The condition to register
     */
    public void registerCondition(String name, IPropertySelectorCondition condition) {
        if (this.conditions.put(name, condition) != null) {
            removeOption(name); // Overwritten, remove previous
        }

        //TODO: Is a hack, needs a way to provide aliases/non-important deprecated conditions
        if (!name.equals("train")) {
            this.options.add(SelectorHandlerConditionOption.optionString(name));
        }
    }

    /**
     * Un-registers a @train or @ptrain condition, that can after registration
     * be used to filter train properties.
     *
     * @param name Name of the condition to un-register
     */
    public void unregisterCondition(String name) {
        if (this.conditions.remove(name) != null) {
            removeOption(name);
        }
    }

    private void removeOption(String name) {
        Iterator<SelectorHandlerConditionOption> iter = this.options.iterator();
        while (iter.hasNext()) {
            if (iter.next().name().equals(name)) {
                iter.remove();
                break;
            }
        }
    }

    /**
     * Looks up all the trains on the server that match a range of selector arguments.
     * Used by @train and @ptrain to select the trains to operate on.
     *
     * @param sender Sender that executed the command, can use console sender
     * @param arguments Arguments to query
     * @return all train properties that match the query
     * @throws SelectorException If the selection failed, or an incorrect selector argument
     *                           was specified
     */
    public Collection<TrainProperties> matchTrains(CommandSender sender, List<SelectorCondition> conditions) throws SelectorException {
        // Check, don't allow returning 'all'
        if (conditions.isEmpty()) {
            throw new SelectorException("No selector conditions were specified");
        }

        // Make mutable
        conditions = new ArrayList<>(conditions);

        // Stream the properties of all trains on the server
        Stream<TrainProperties> stream = TrainPropertiesStore.getAll().stream();

        // Filter trains by world and/or the location coordinates of the carts
        // Mutates the conditions list to remove the matchers used
        TCSelectorLocationFilter locationFilter = new TCSelectorLocationFilter();
        locationFilter.read(sender, conditions);
        if (locationFilter.hasFilters()) {
            stream = stream.filter(locationFilter::filter);
        }

        // Filter the stream by all remaining conditions in sequence
        for (SelectorCondition selectorCondition : conditions) {
            IPropertySelectorCondition condition = this.conditions.get(selectorCondition.getKey());
            if (condition != null) {
                stream = stream.filter(properties -> condition.matches(properties, selectorCondition));
            } else {
                throw new SelectorException("Unknown condition: " + selectorCondition.getKey());
            }
        }

        // Obtain the results, check if there are any
        List<TrainProperties> result = stream.collect(Collectors.toList());
        if (result.isEmpty()) {
            throw new SelectorException("No trains matched these conditions");
        }

        return result;
    }

    /**
     * Checks the registry what options are compatible to be specified
     *
     * @param sender
     * @param conditions
     * @return list of options
     */
    public List<SelectorHandlerConditionOption> matchOptions(CommandSender sender, List<SelectorCondition> conditions) {
        return this.options;
    }
}
