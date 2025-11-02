package com.bergerkiller.bukkit.tc.commands.selector.type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
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
        // If one or more seat selectors were specified, only return Player passengers
        // that are in these seats. Otherwise, just return all passengers of the cart(s) unfiltered.
        final List<SelectorCondition> seatConditions = new ArrayList<>(2);
        for (SelectorCondition condition : conditions) {
            if (!condition.getKey().equalsIgnoreCase("seat")) {
                continue;
            }

            // If a boolean condition but matching false (0), ignore it
            // There is no functional reason to support it as it always results in 0 matches
            // This keeps it functional as a train-level filter
            if (condition.isBoolean() && !condition.getBoolean()) {
                continue;
            }

            seatConditions.add(condition);
        }

        // Match the carts on the server
        Stream<MinecartMember<?>> matchedCarts = this.registry.matchTrains(sender, conditions).stream()
                .map(TrainProperties::getHolder)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream);

        List<String> playerNames;
        if (seatConditions.isEmpty()) {
            // No seat conditions, just return all player passengers of the trains that matched
            playerNames = matchedCarts
                    .flatMap(member -> member.getEntity().getPlayerPassengers().stream())
                    .map(Player::getName)
                    .collect(Collectors.toList());

            if (playerNames.isEmpty()) {
                throw new SelectorException("No player passengers are inside any of the matched trains");
            }
        } else {
            // For all seat conditions, retrieve all the entity lists
            // This is an OR of all the conditions, because realistically we cannot support
            // multiple seat conditions in @ptrain for matching players. Players are unlikely
            // to occupy multiple seats at once...
            // This makes it at least work.
            playerNames = matchedCarts.flatMap(member -> {
                final AttachmentNameLookup nameLookup = member.getAttachments().getNameLookup();
                return seatConditions.stream().flatMap(condition ->
                        nameLookup.matchSeatSelector(sender, condition)
                                .filter(e -> e instanceof Player));
            })
                    .distinct()
                    .map(p -> ((Player) p).getName())
                    .collect(Collectors.toList());

            if (playerNames.isEmpty()) {
                throw new SelectorException("No player passengers are inside any of the matched seats");
            }
        }

        return playerNames;
    }

    @Override
    public List<SelectorHandlerConditionOption> options(CommandSender sender, String selector, List<SelectorCondition> conditions) {
        return this.registry.matchOptions(sender, conditions);
    }
}
