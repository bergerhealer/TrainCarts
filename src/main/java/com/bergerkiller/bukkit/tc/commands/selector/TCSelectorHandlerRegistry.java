package com.bergerkiller.bukkit.tc.commands.selector;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.type.PlayersInTrainSelector;
import com.bergerkiller.bukkit.tc.commands.selector.type.TrainNameSelector;

/**
 * TrainCarts selector handler registry. Pre-registers TrainCarts
 * built-in selectors.
 */
public class TCSelectorHandlerRegistry extends SelectorHandlerRegistry {
    private final TrainCarts plugin;

    public TCSelectorHandlerRegistry(TrainCarts plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.register(this);
        register("ptrain", new PlayersInTrainSelector());
        register("train", new TrainNameSelector());
    }
}
