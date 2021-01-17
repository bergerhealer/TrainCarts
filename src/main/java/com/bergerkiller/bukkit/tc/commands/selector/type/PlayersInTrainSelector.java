package com.bergerkiller.bukkit.tc.commands.selector.type;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.commands.selector.SelectorException;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.tc.properties.TrainPropertiesStore;

/**
 * Selector that returns the player names in a given train
 */
public class PlayersInTrainSelector implements SelectorHandler {

    @Override
    public Collection<String> handle(CommandSender sender, String selector, Map<String, String> arguments) throws SelectorException {
        String trainName = arguments.get("train");
        if (trainName == null) {
            throw new SelectorException("No train name was provided");
        } else if (!TrainPropertiesStore.exists(trainName)) {
            throw new SelectorException("Train with name '" + trainName + "' does not exist");
        }

        TrainProperties properties = TrainPropertiesStore.get(trainName);
        if (properties.isLoaded()) {
            return properties.getHolder().stream()
                .filter(m -> !m.isUnloaded())
                .flatMap(m ->  m.getEntity().getPlayerPassengers().stream())
                .map(Player::getName)
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
