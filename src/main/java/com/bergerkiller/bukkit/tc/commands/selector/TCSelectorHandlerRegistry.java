package com.bergerkiller.bukkit.tc.commands.selector;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.commands.selector.type.PlayersInTrainSelector;

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
        System.out.println("ENABLE()!");
        plugin.register(this);
        register("ptrain", new PlayersInTrainSelector());
    }
}
