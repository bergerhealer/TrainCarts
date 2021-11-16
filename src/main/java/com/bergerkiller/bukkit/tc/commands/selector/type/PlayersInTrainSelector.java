package com.bergerkiller.bukkit.tc.commands.selector.type;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.commands.selector.SelectorException;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandler;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorHandlerConditionOption;
import com.bergerkiller.bukkit.tc.commands.selector.SelectorCondition;
import com.bergerkiller.bukkit.tc.commands.selector.TCSelectorHandlerRegistry;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;

/**
 * Selector that returns the player names in a given train
 */
public class PlayersInTrainSelector implements SelectorHandler {
    private final TCSelectorHandlerRegistry registry;

    public PlayersInTrainSelector(TCSelectorHandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Collection<String> handle(CommandSender sender, String selector, List<SelectorCondition> conditions) throws SelectorException {
        List<String> playerNames = this.registry.matchTrains(sender, conditions).stream()
                .map(TrainProperties::getHolder)
                .filter(Objects::nonNull)
                .flatMap(group -> group.stream())
                .flatMap(member -> member.getEntity().getPlayerPassengers().stream())
                .map(Player::getName)
                .collect(Collectors.toList());

        if (playerNames.isEmpty()) {
            throw new SelectorException("No player passengers are inside any of the matched trains");
        }

        return playerNames;
    }

    @Override
    public List<SelectorHandlerConditionOption> options(CommandSender sender, String selector, List<SelectorCondition> conditions) {
        return this.registry.matchOptions(sender, conditions);
    }
}
