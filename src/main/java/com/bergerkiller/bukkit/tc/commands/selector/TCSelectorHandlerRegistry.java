package com.bergerkiller.bukkit.tc.commands.selector;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.type.PlayersInTrainSelector;
import com.bergerkiller.bukkit.tc.commands.selector.type.TrainNameSelector;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.api.IPropertySelectorCondition;

/**
 * TrainCarts selector handler registry. Pre-registers TrainCarts
 * built-in selectors.
 */
public class TCSelectorHandlerRegistry extends SelectorHandlerRegistry {
    private final Map<String, IPropertySelectorCondition> conditions;

    public TCSelectorHandlerRegistry(TrainCarts plugin) {
        super(plugin);
        this.conditions = new HashMap<String, IPropertySelectorCondition>();
    }

    @Override
    public void enable() {
        super.enable();
        register("ptrain", new PlayersInTrainSelector(this));
        register("train", new TrainNameSelector(this));
    }

    /**
     * Registers a new @train or @ptrain condition, that can after registration
     * be used to filter train properties.
     *
     * @param name Name of the condition
     * @param condition The condition to register
     */
    public void registerCondition(String name, IPropertySelectorCondition condition) {
        this.conditions.put(name, condition);
    }

    /**
     * Un-registers a @train or @ptrain condition, that can after registration
     * be used to filter train properties.
     *
     * @param name Name of the condition to un-register
     */
    public void unregisterCondition(String name) {
        this.conditions.remove(name);
    }

    /**
     * Looks up all the trains on the server that match a range of selector arguments.
     * Used by @train and @ptrain to select the trains to operate on.
     *
     * @param arguments Arguments to query
     * @return all train properties that match the query
     * @throws SelectorException If the selection failed, or an incorrect selector argument
     *                           was specified
     */
    public Collection<TrainProperties> matchTrains(List<SelectorCondition> conditions) throws SelectorException {
        // Check, don't allow returning 'all'
        if (conditions.isEmpty()) {
            throw new SelectorException("No selector conditions were specified");
        }

        // Filter the stream by all conditions in sequence
        Stream<TrainProperties> stream = TrainPropertiesStore.getAll().stream();
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
}
