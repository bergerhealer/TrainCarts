package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.utils.ListCallbackCollector;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Tracks, stores and creates traincarts player representations for Players
 */
public class TrainCartsPlayerStore implements TrainCarts.Provider {
    private final TrainCarts traincarts;
    private final Map<UUID, TrainCartsPlayer> players = new HashMap<>();

    public TrainCartsPlayerStore(TrainCarts traincarts) {
        this.traincarts = traincarts;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    /**
     * Gets or creates the TrainCarts player information tracked for the Player with
     * the specified UUID
     *
     * @param playerUUID UUID of the Player
     * @return TrainCarts Player information
     */
    public synchronized TrainCartsPlayer get(UUID playerUUID) {
        return players.computeIfAbsent(playerUUID, u -> new TrainCartsPlayer(traincarts, u));
    }

    /**
     * Gets or creates the TrainCarts player information tracked for the Player specified.
     *
     * @param player Player
     * @return TrainCarts Player information
     */
    public synchronized TrainCartsPlayer get(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), u -> new TrainCartsPlayer(traincarts, player));
    }

    /**
     * Gets all TrainCarts players that match a certain condition
     *
     * @param condition Condition to filter by
     * @return Unmodifiable List of all TrainCarts Players that match
     */
    public synchronized List<TrainCartsPlayer> find(Predicate<TrainCartsPlayer> condition) {
        ListCallbackCollector<TrainCartsPlayer> collector = new ListCallbackCollector<>();
        for (TrainCartsPlayer player : players.values()) {
            if (condition.test(player)) {
                collector.accept(player);
            }
        }
        return collector.result();
    }
}
