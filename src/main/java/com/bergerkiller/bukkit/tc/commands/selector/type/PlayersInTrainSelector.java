package com.bergerkiller.bukkit.tc.commands.selector.type;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            throw new SelectorException("No train name pattern was provided");
        }

        Collection<TrainProperties> foundTrains = TrainPropertiesStore.matchAll(trainName);
        if (foundTrains.isEmpty()) {
            throw new SelectorException("No train with name pattern '" + trainName + "' could be found");
        }

        List<String> playerNames = foundTrains.stream()
                .map(TrainProperties::getHolder)
                .filter(Objects::nonNull)
                .flatMap(group -> group.stream())
                .flatMap(member -> member.getEntity().getPlayerPassengers().stream())
                .map(Player::getName)
                .collect(Collectors.toList());

        if (playerNames.isEmpty()) {
            throw new SelectorException("No player passengers found");
        }

        return playerNames;
    }
}
